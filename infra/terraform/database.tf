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

  # デモ環境は数日で `terraform destroy` する
  deletion_protection      = false
  skip_final_snapshot      = true
  delete_automated_backups = true
}
