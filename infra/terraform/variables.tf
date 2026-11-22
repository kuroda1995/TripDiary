variable "project" {
  description = "リソース名の接頭辞"
  type        = string
  default     = "tripdiary"
}

variable "region" {
  description = "AWS リージョン"
  type        = string
  default     = "ap-northeast-1"
}

variable "image_tag" {
  description = "ECS で動かす API イメージのタグ（deploy.ps1 が Git のコミット ID を渡す）"
  type        = string
  default     = "latest"
}

variable "api_desired_count" {
  description = "API のタスク数。費用を抑えるため既定は1台（設計書の本番構成は2台）"
  type        = number
  default     = 1
}

variable "api_cpu" {
  description = "API タスクの vCPU（1024 = 1 vCPU）"
  type        = number
  default     = 512
}

variable "api_memory" {
  description = "API タスクのメモリ（MiB）"
  type        = number
  default     = 1024
}

variable "db_instance_class" {
  description = "RDS のインスタンスクラス"
  type        = string
  default     = "db.t4g.micro"
}

variable "guest_email" {
  description = "ゲストログインに使うサンプルユーザーのメールアドレス（docs/features/01_auth.md 4.8）"
  type        = string
  default     = "haruka@example.com"
}

variable "guest_password" {
  description = "ゲストのパスワード（demo-seed.mjs の DEMO_PASSWORD と同じ値）。ログイン画面に表示される。空ならゲストログインは無効"
  type        = string
  default     = ""
  sensitive   = true
}

variable "standby" {
  description = <<-EOT
    待機モード（docs/deploy.md 4章）。true にすると、URL（CloudFront）とデータ（RDS・S3）を残したまま、
    費用のかかる ALB・ECS サービスを消し、画面をお知らせページに切り替える。RDS の停止は standby.ps1 が行う。
    切り替えは infra/standby.ps1（待機）/ infra/deploy.ps1（起動）で行い、直接 apply しない
  EOT
  type        = bool
  default     = false
}

variable "log_retention_days" {
  description = "CloudWatch Logs の保持日数（デモ環境のため短くする）"
  type        = number
  default     = 7
}
