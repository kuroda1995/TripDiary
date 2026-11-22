# deploy.ps1（起動・更新）と standby.ps1（待機）で共通に使う関数（docs/deploy.md）。
# 各スクリプトの先頭で `. (Join-Path $PSScriptRoot 'common.ps1')` として読み込む。

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

# 値がない（まだ作っていない・待機モードで消している）ときは $null を返す。
# Windows PowerShell 5.1 は Stop のままネイティブコマンドの stderr を捨てると例外になるため、この関数の中だけ Continue にする
function Get-TfOutputOrNull([string]$Name) {
    $ErrorActionPreference = 'Continue'
    $value = terraform "-chdir=$tf" output -raw $Name 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $value) { return $null }
    return $value
}

# 動いている（または待機前に動いていた）API のイメージタグ。まだデプロイしていなければ $null
function Get-CurrentImageTag {
    $image = Get-TfOutputOrNull 'api_image'
    if (-not $image) { return $null }
    return $image.Split(':')[-1]
}

# RDS の状態（available / stopped / stopping / starting など）。まだ作っていなければ $null
function Get-DbStatus {
    $id = Get-TfOutputOrNull 'db_identifier'
    if (-not $id) { return $null }
    $ErrorActionPreference = 'Continue'
    $status = aws rds describe-db-instances --db-instance-identifier $id --query 'DBInstances[0].DBInstanceStatus' --output text 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return $status.Trim()
}

function Wait-DbStatus([string]$Expected, [int]$TimeoutMinutes = 20) {
    Write-Host "==> RDS が $Expected になるのを待つ（数分かかります）" -ForegroundColor Cyan
    $deadline = (Get-Date).AddMinutes($TimeoutMinutes)
    while ($true) {
        $status = Get-DbStatus
        if ($status -eq $Expected) { return }
        if ((Get-Date) -gt $deadline) { throw "RDS が $TimeoutMinutes 分以内に $Expected になりませんでした（現在: $status）" }
        Start-Sleep -Seconds 20
    }
}

# ゲストのパスワードが渡されていないと、タスク定義のゲストログインが無効になる（docs/deploy.md 3.3）
function Assert-GuestPassword {
    $tfvars = Join-Path $tf 'guest.auto.tfvars'
    if (-not $env:TF_VAR_guest_password -and -not (Test-Path $tfvars)) {
        Write-Host '注意: ゲストのパスワード（TF_VAR_guest_password または infra/terraform/guest.auto.tfvars）がないため、ゲストログインは無効になります' -ForegroundColor Yellow
    }
}

function Clear-WebCache {
    $distributionId = Get-TfOutput 'web_distribution_id'
    Invoke-Native 'CloudFront のキャッシュを削除' {
        aws cloudfront create-invalidation --distribution-id $distributionId --paths '/*' --query 'Invalidation.Id' --output text
    }
}
