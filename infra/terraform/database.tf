# RDS for PostgreSQL
#
# 費用を抑えるため Single-AZ・db.t4g.micro（設計書の本番構成は Multi-AZ）。
# マスターパスワードは RDS に Secrets Manager で管理させ、Terraform の変数やコードには出さない。

resource "aws_db_subnet_group" "main" {
  name       = "${local.name}-db"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "main" {
  identifier     = "${local.name}-db"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name                     = "tripdiary"
  username                    = "tripdiary"
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period    = 1
  auto_minor_version_upgrade = true
  apply_immediately          = true

  # デモ環境は使い終わったら `terraform destroy` する
  deletion_protection      = false
  skip_final_snapshot      = true
  delete_automated_backups = true
}

# ---- 待機モード: RDS を止めたままにする ----
# RDS は停止しても7日で自動的に起動する。待機中は1時間ごとに停止を試み、起動していても1時間以内に止める
# （すでに停止しているときの失敗は無視してよい）。起動中に止めてしまわないよう、待機モードのときだけ作る。

data "aws_iam_policy_document" "scheduler_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.account_id]
    }
  }
}

resource "aws_iam_role" "stop_db" {
  count              = var.standby ? 1 : 0
  name               = "${local.name}-stop-db"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume.json
}

resource "aws_iam_role_policy" "stop_db" {
  count = var.standby ? 1 : 0
  name  = "stop-db"
  role  = aws_iam_role.stop_db[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "rds:StopDBInstance"
      Resource = aws_db_instance.main.arn
    }]
  })
}

resource "aws_scheduler_schedule" "stop_db" {
  count               = var.standby ? 1 : 0
  name                = "${local.name}-stop-db"
  description         = "Keep the demo RDS instance stopped while in standby (RDS restarts itself after 7 days)"
  schedule_expression = "rate(1 hour)"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:rds:stopDBInstance"
    role_arn = aws_iam_role.stop_db[0].arn
    input    = jsonencode({ DbInstanceIdentifier = aws_db_instance.main.identifier })

    retry_policy {
      maximum_retry_attempts = 0
    }
  }

  depends_on = [aws_iam_role_policy.stop_db]
}
