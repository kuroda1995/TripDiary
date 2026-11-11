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
  value = aws_ecs_service.api.name
}

output "log_group" {
  value = aws_cloudwatch_log_group.api.name
}
