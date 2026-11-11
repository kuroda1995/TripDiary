<#
.SYNOPSIS
  TripDiary のデモ環境を AWS に作成・更新する（docs/deploy.md）。

.DESCRIPTION
  1. Terraform で ECR を作成する（初回のみ実質的な変更あり）
  2. API の Docker イメージをビルドして ECR に push する
  3. Terraform で残りのリソースを作成・更新し、新しいイメージで ECS を更新する
  4. フロントエンドをビルドして S3 に配置し、CloudFront のキャッシュを消す
  5. ECS のサービスが安定するまで待ち、アプリの URL を表示する

  Terraform の apply は、変更内容を表示して確認を求める（-AutoApprove で省略）。

.EXAMPLE
  ./infra/deploy.ps1                 # すべて
  ./infra/deploy.ps1 -SkipBackend    # フロントエンドだけ更新
  ./infra/deploy.ps1 -SkipFrontend   # API だけ更新
#>
[CmdletBinding()]
param(
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$AutoApprove
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tf = Join-Path $PSScriptRoot 'terraform'
$region = 'ap-northeast-1'  # variables.tf の region と合わせる

function Invoke-Native {
    param([string]$Description, [scriptblock]$Command)
    Write-Host "==> $Description" -ForegroundColor Cyan
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "失敗しました: $Description（終了コード $LASTEXITCODE）" }
}

function Get-TfOutput([string]$Name) {
    $value = terraform "-chdir=$tf" output -raw $Name
    if ($LASTEXITCODE -ne 0) { throw "terraform output $Name を取得できませんでした" }
    return $value
}

$approve = @()
if ($AutoApprove) { $approve = @('-auto-approve') }

Invoke-Native 'terraform init' { terraform "-chdir=$tf" init -input=false }

# ---- API イメージ ----
if (-not $SkipBackend) {
    Invoke-Native 'ECR リポジトリを作成' {
        terraform "-chdir=$tf" apply -input=false @approve '-target=aws_ecr_repository.api' '-target=aws_ecr_lifecycle_policy.api'
    }

    $repo = Get-TfOutput 'ecr_repository_url'
    $registry = $repo.Split('/')[0]
    $sha = (git -C $root rev-parse --short HEAD).Trim()
    $imageTag = "$sha-$(Get-Date -Format 'yyyyMMddHHmmss')"

    Invoke-Native "API イメージをビルド（$imageTag）" {
        docker build --platform linux/amd64 --provenance=false -t "${repo}:${imageTag}" (Join-Path $root 'backend')
    }
    # Windows PowerShell 5.1 のパイプは改行・文字コードを変えてしまうため、パイプは cmd で行う
    Invoke-Native 'ECR にログイン' {
        cmd /c "aws ecr get-login-password --region $region | docker login --username AWS --password-stdin $registry"
    }
    Invoke-Native 'API イメージを push' { docker push "${repo}:${imageTag}" }
}
else {
    # 動いているタスク定義のイメージタグをそのまま使う
    $imageTag = $null
    try {
        $cluster = Get-TfOutput 'ecs_cluster'
        $service = Get-TfOutput 'ecs_service'
        $taskDef = aws ecs describe-services --cluster $cluster --services $service --query 'services[0].taskDefinition' --output text
        $image = aws ecs describe-task-definition --task-definition $taskDef --query 'taskDefinition.containerDefinitions[0].image' --output text
        $imageTag = $image.Split(':')[-1]
    }
    catch { }
    if (-not $imageTag) { throw 'API がまだデプロイされていません。-SkipBackend を外して実行してください' }
}

# ---- インフラ全体 ----
Invoke-Native 'インフラを作成・更新' {
    terraform "-chdir=$tf" apply -input=false @approve "-var=image_tag=$imageTag"
}

# ---- フロントエンド ----
if (-not $SkipFrontend) {
    $frontend = Join-Path $root 'frontend'
    $bucket = Get-TfOutput 'web_bucket'
    $distributionId = Get-TfOutput 'web_distribution_id'

    Push-Location $frontend
    try {
        Invoke-Native 'フロントエンドの依存をインストール' { npm ci }
        Invoke-Native 'フロントエンドをビルド' { npm run build }
    }
    finally { Pop-Location }

    $dist = Join-Path $frontend 'dist'
    # ファイル名にハッシュが付く assets/ は長くキャッシュし、index.html などは毎回確認させる。
    # 古い assets/ は、開いたままの画面が読み込めるよう削除しない
    Invoke-Native 'assets を S3 に配置' {
        aws s3 sync (Join-Path $dist 'assets') "s3://$bucket/assets" --cache-control 'public,max-age=31536000,immutable'
    }
    Invoke-Native 'index.html などを S3 に配置' {
        aws s3 sync $dist "s3://$bucket" --delete --exclude 'assets/*' --cache-control 'no-cache'
    }
    Invoke-Native 'CloudFront のキャッシュを削除' {
        aws cloudfront create-invalidation --distribution-id $distributionId --paths '/*' --query 'Invalidation.Id' --output text
    }
}

# ---- 完了待ち ----
$cluster = Get-TfOutput 'ecs_cluster'
$service = Get-TfOutput 'ecs_service'
Invoke-Native 'API の起動を待つ（数分かかります）' {
    aws ecs wait services-stable --cluster $cluster --services $service
}

$url = Get-TfOutput 'web_url'
Write-Host ''
Write-Host "デプロイが完了しました: $url" -ForegroundColor Green
Write-Host "ログ: aws logs tail $(Get-TfOutput 'log_group') --follow"
