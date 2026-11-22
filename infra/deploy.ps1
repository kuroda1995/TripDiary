<#
.SYNOPSIS
  TripDiary のデモ環境を AWS に作成・更新する。待機モードからの起動にも使う（docs/deploy.md）。

.DESCRIPTION
  1. 待機モードだった場合は、RDS を止める予約を外し、RDS を起動する
  2. Terraform で ECR を作成し、API の Docker イメージをビルドして push する（-SkipBackend なら今のイメージを使う）
  3. Terraform で残りのリソース（ALB・ECS など）を作成・更新する
  4. フロントエンドをビルドして S3 に配置する（-SkipFrontend なら配置しない）
  5. CloudFront のキャッシュを消し、ECS のサービスが安定するまで待ち、アプリの URL を表示する

  Terraform の apply は、変更内容を表示して確認を求める（-AutoApprove で省略）。
  ゲストのパスワードは TF_VAR_guest_password か infra/terraform/guest.auto.tfvars で渡す（docs/deploy.md 3.3）。

.EXAMPLE
  ./infra/deploy.ps1                                # すべて（初回・コードを更新したとき）
  ./infra/deploy.ps1 -SkipBackend -SkipFrontend     # 待機モードから起動する（今のイメージ・画面のまま）
  ./infra/deploy.ps1 -SkipBackend                   # フロントエンドだけ更新
  ./infra/deploy.ps1 -SkipFrontend                  # API だけ更新
#>
[CmdletBinding()]
param(
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$AutoApprove
)

. (Join-Path $PSScriptRoot 'common.ps1')

$approve = @()
if ($AutoApprove) { $approve = @('-auto-approve') }

Assert-GuestPassword
Invoke-Native 'terraform init' { terraform "-chdir=$tf" init -input=false }

$currentTag = Get-CurrentImageTag

# ---- 待機モードからの起動: RDS を先に起動する ----
# RDS を止める予約が残っていると起動した RDS がまた止められるため、先に外す。
# RDS が止まったまま ECS を作ると、API が DB に接続できずに起動に失敗する
if ((Get-TfOutputOrNull 'standby') -eq 'true') {
    Invoke-Native 'RDS を止める予約を外す' {
        terraform "-chdir=$tf" apply -input=false @approve '-var=standby=false' "-var=image_tag=$currentTag" `
            '-target=aws_scheduler_schedule.stop_db' '-target=aws_iam_role_policy.stop_db' '-target=aws_iam_role.stop_db'
    }
}
$dbStatus = Get-DbStatus
if ($dbStatus -eq 'stopping') {
    Wait-DbStatus 'stopped'
    $dbStatus = 'stopped'
}
if ($dbStatus -eq 'stopped') {
    $dbId = Get-TfOutput 'db_identifier'
    Invoke-Native 'RDS を起動' { aws rds start-db-instance --db-instance-identifier $dbId --query 'DBInstance.DBInstanceStatus' --output text }
}
if ($dbStatus -and $dbStatus -ne 'available') {
    Invoke-Native 'RDS の起動を待つ（数分かかります）' { aws rds wait db-instance-available --db-instance-identifier (Get-TfOutput 'db_identifier') }
}

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
    # 今のタスク定義のイメージタグをそのまま使う（待機モード中も残っている）
    $imageTag = $currentTag
    if (-not $imageTag) { throw 'API がまだデプロイされていません。-SkipBackend を外して実行してください' }
}

# ---- インフラ全体 ----
Invoke-Native 'インフラを作成・更新' {
    terraform "-chdir=$tf" apply -input=false @approve '-var=standby=false' "-var=image_tag=$imageTag"
}

# ---- フロントエンド ----
if (-not $SkipFrontend) {
    $frontend = Join-Path $root 'frontend'
    $bucket = Get-TfOutput 'web_bucket'

    Push-Location $frontend
    try {
        Invoke-Native 'フロントエンドの依存をインストール' { npm ci }
        Invoke-Native 'フロントエンドをビルド' { npm run build }
    }
    finally { Pop-Location }

    $dist = Join-Path $frontend 'dist'
    # ファイル名にハッシュが付く assets/ は長くキャッシュし、index.html などは毎回確認させる。
    # 古い assets/ は、開いたままの画面が読み込めるよう削除しない。
    # standby.html（待機モードのお知らせページ）は Terraform が置くため消さない
    Invoke-Native 'assets を S3 に配置' {
        aws s3 sync (Join-Path $dist 'assets') "s3://$bucket/assets" --cache-control 'public,max-age=31536000,immutable'
    }
    Invoke-Native 'index.html などを S3 に配置' {
        aws s3 sync $dist "s3://$bucket" --delete --exclude 'assets/*' --exclude 'standby.html' --cache-control 'no-cache'
    }
}
Clear-WebCache

# ---- 完了待ち ----
$cluster = Get-TfOutput 'ecs_cluster'
$service = Get-TfOutput 'ecs_service'
Invoke-Native 'API の起動を待つ（数分かかります）' {
    aws ecs wait services-stable --cluster $cluster --services $service
}

# services-stable はタスク数がそろった時点で終わり、ALB のヘルスチェックの合格までは待たない
# （サービスを作り直した直後は、ここで終わっても数分は 502 になる）。URL から実際に届くまで待つ
$url = Get-TfOutput 'web_url'
Write-Host '==> URL から API に届くのを待つ' -ForegroundColor Cyan
$deadline = (Get-Date).AddMinutes(10)
while ($true) {
    try {
        $health = Invoke-WebRequest "$url/actuator/health" -UseBasicParsing -TimeoutSec 10
        if ($health.StatusCode -eq 200) { break }
    }
    catch { }
    if ((Get-Date) -gt $deadline) { throw "10 分待っても $url/actuator/health に届きませんでした（ALB のターゲットの状態と API のログを確認する）" }
    Start-Sleep -Seconds 15
}

Write-Host ''
Write-Host "デプロイが完了しました: $url" -ForegroundColor Green
Write-Host "ログ: aws logs tail $(Get-TfOutput 'log_group') --follow"
Write-Host '使い終わったら待機モードに戻す: powershell -ExecutionPolicy Bypass -File infra/standby.ps1'
