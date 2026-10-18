# ===== network レイヤーの outputs を読み込む =====
data "terraform_remote_state" "network" {
  backend = "s3"
  config = {
    bucket = "cross-border-payment-service-724775511010"
    key    = "dev/network/terraform.tfstate"
    region = "us-east-1"
  }
}

# ===== DB サブネットグループ =====
resource "aws_db_subnet_group" "main" {
  name        = "default-vpc-bf12c8c2"
  description = "Created from the RDS Management Console"

  # 削除済みのサブネットを外し、1a と 1b のプライベートサブネットにする
  subnet_ids = data.terraform_remote_state.network.outputs.private_subnet_ids
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
  vpc_security_group_ids = [data.terraform_remote_state.network.outputs.rds_security_group_id]
  availability_zone      = "us-east-1b"
  multi_az               = false
  publicly_accessible    = false

  # パラメータグループとオプショングループ（AWS 標準のものを名前で参照）
  parameter_group_name = "default.mysql8.4"
  option_group_name    = "default:mysql-8-4"

  # バックアップとメンテナンス
  backup_retention_period    = 7 # 1 日 → 7 日
  backup_window              = "07:20-07:50"
  maintenance_window         = "mon:03:14-mon:03:44"
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true
  delete_automated_backups   = true

  # 削除時の設定
  deletion_protection       = true  # コンソールなどからの誤削除を防ぐ
  skip_final_snapshot       = false # 削除時に最終スナップショットを残す
  final_snapshot_identifier = "cross-border-payment-service-db-final"

  # 今回の変更をメンテナンスウィンドウを待たずに反映する（停止は発生しない）
  apply_immediately = false

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
