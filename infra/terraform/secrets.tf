# JWT の署名鍵（Base64・48バイト）。値は Secrets Manager から ECS タスクに環境変数 JWT_SECRET として渡す。
# ※ 値は terraform.tfstate にも残るため、状態ファイルはコミットしない

resource "random_bytes" "jwt_secret" {
  length = 48
}

resource "aws_secretsmanager_secret" "jwt" {
  name        = "${local.name}/jwt-secret"
  description = "TripDiary API: JWT signing key (Base64)"
  # destroy 後にすぐ同じ名前で作り直せるよう、削除の猶予期間を設けない
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = random_bytes.jwt_secret.base64
}
