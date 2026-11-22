output "web_url" {
  description = "アプリの URL（メンターさんに共有する URL）"
  value       = "https://${aws_cloudfront_distribution.web.domain_name}"
}

output "photos_url" {
  description = "写真の配信 URL（PHOTO_BASE_URL）"
  value       = "https://${aws_cloudfront_distribution.photos.domain_name}"
}

output "ecr_repository_url" {
  value = aws_ecr_repository.api.repository_url
}

output "web_bucket" {
  value = aws_s3_bucket.web.bucket
}

output "web_distribution_id" {
  value = aws_cloudfront_distribution.web.id
}

output "ecs_cluster" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service" {
  description = "API の ECS サービス名（待機モードでは空）"
  value       = one(aws_ecs_service.api[*].name)
}

output "api_image" {
  description = "タスク定義のイメージ（standby.ps1・deploy.ps1 -SkipBackend が同じイメージを使い続けるために読む）"
  # タスク定義はゲストのパスワード（sensitive）を含むが、イメージ名は秘密ではないため取り出して出力する
  value = nonsensitive(jsondecode(aws_ecs_task_definition.api.container_definitions)[0].image)
}

output "db_identifier" {
  value = aws_db_instance.main.identifier
}

output "standby" {
  description = "待機モードかどうか"
  value       = var.standby
}

output "log_group" {
  value = aws_cloudwatch_log_group.api.name
}
