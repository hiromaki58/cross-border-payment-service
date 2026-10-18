# ===== DB サブネットグループ =====
resource "aws_db_subnet_group" "main" {
  name        = "default-vpc-bf12c8c2"
  description = "Created from the RDS Management Console"
  subnet_ids = [
    "subnet-061ec0eb17725b883", # 削除済みのサブネット（次の段階で入れ替える）
    "subnet-d8ee7af9",          # WebGamePrivate01 / us-east-1b
  ]
}

# ===== RDS for MySQL =====
resource "aws_db_instance" "main" {
  identifier     = "cross-border-payment-service-db"
  engine         = "mysql"
  engine_version = "8.4.11"
  instance_class = "db.t3.micro"
  username       = "admin"
  port           = 3306

  # ストレージ
  allocated_storage     = 20
  max_allocated_storage = 1000
  storage_type          = "gp2"
  storage_encrypted     = true
  kms_key_id            = "arn:aws:kms:us-east-1:724775511010:key/887fd8c9-6e85-4fdc-8442-ef1a3c77b6da"

  # ネットワーク
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = ["sg-091ec6525b03e7d08"] # WebGame-EKS-RDS-SG
  availability_zone      = "us-east-1b"
  multi_az               = false
  publicly_accessible    = false

  # パラメータグループとオプショングループ（AWS 標準のものを名前で参照）
  parameter_group_name = "default.mysql8.4"
  option_group_name    = "default:mysql-8-4"

  # バックアップとメンテナンス
  backup_retention_period    = 1
  backup_window              = "07:20-07:50"
  maintenance_window         = "mon:03:14-mon:03:44"
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true
  delete_automated_backups   = true

  # 削除時の設定（次の段階で安全な値に変更する）
  deletion_protection = false
  skip_final_snapshot = true

  ca_cert_identifier       = "rds-ca-rsa2048-g1"
  engine_lifecycle_support = "open-source-rds-extended-support-disabled"
  database_insights_mode   = "standard"

  tags = {
    group = "WebGame"
    name  = "WebGame"
  }

  lifecycle {
    prevent_destroy = true # Terraform からの削除を禁止する
  }
}
