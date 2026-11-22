<#
.SYNOPSIS
  TripDiary のデモ環境を待機モードにする（docs/deploy.md 4章）。

.DESCRIPTION
  URL（CloudFront）とデータ（RDS・S3）を残したまま、費用のかかるものを止める。

  1. Terraform を standby=true で apply する
     - 画面をお知らせページに切り替え、ALB・VPC オリジン・ECS サービスを消す
     - RDS を1時間ごとに止める予約（EventBridge Scheduler）を作る（RDS は停止しても7日で自動起動するため）
  2. RDS を停止し、停止するまで待つ
  3. CloudFront のキャッシュを消す

  起動するときは deploy.ps1 を使う（-SkipBackend -SkipFrontend で、今のイメージ・画面のまま起動できる）。

.EXAMPLE
  ./infra/standby.ps1
  ./infra/standby.ps1 -AutoApprove
#>
[CmdletBinding()]
param(
    [switch]$AutoApprove
)

. (Join-Path $PSScriptRoot 'common.ps1')

$approve = @()
if ($AutoApprove) { $approve = @('-auto-approve') }

Assert-GuestPassword
Invoke-Native 'terraform init' { terraform "-chdir=$tf" init -input=false }

# 起動時に同じイメージで ECS を作り直せるよう、今のタスク定義のイメージをそのまま使う
$imageTag = Get-CurrentImageTag
if (-not $imageTag) { throw 'デモ環境がまだありません。先に deploy.ps1 で作成してください' }

# CloudFront を更新して VPC オリジンへの参照を外してから、VPC オリジン・ALB を消す
# （順番は cdn.tf の create_before_destroy で決めている）
Invoke-Native '待機モードに切り替え（お知らせページに切り替え、ALB・ECS を削除、RDS を止める予約を作成）' {
    terraform "-chdir=$tf" apply -input=false @approve '-var=standby=true' "-var=image_tag=$imageTag"
}

$dbId = Get-TfOutput 'db_identifier'
$dbStatus = Get-DbStatus
if ($dbStatus -eq 'available') {
    Invoke-Native 'RDS を停止' { aws rds stop-db-instance --db-instance-identifier $dbId --query 'DBInstance.DBInstanceStatus' --output text }
    $dbStatus = 'stopping'
}
if ($dbStatus -eq 'stopping') {
    Wait-DbStatus 'stopped'
}
elseif ($dbStatus -ne 'stopped') {
    # starting などのときは、1時間ごとの予約が止める
    Write-Host "注意: RDS の状態が $dbStatus のため、今は停止できませんでした。1時間以内に予約で停止されます" -ForegroundColor Yellow
}

Clear-WebCache

Write-Host ''
Write-Host "待機モードにしました: $(Get-TfOutput 'web_url')（お知らせページを表示します）" -ForegroundColor Green
Write-Host '起動するとき: powershell -ExecutionPolicy Bypass -File infra/deploy.ps1 -SkipBackend -SkipFrontend'
