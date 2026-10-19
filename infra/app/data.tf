locals {
  name         = "cross-border-payment"
  cluster_name = "cross-border-payment" # shared レイヤーの CircleCI 用ポリシーと同じ名前
  domain       = "hiromaki58.com"
  region       = "us-east-1"
}

# ===== 他のレイヤーで作ったリソースを読み込む =====

# network レイヤー（outputs.tf があるので state から読む）
data "terraform_remote_state" "network" {
  backend = "s3"
  config = {
    bucket = "cross-border-payment-service-724775511010"
    key    = "dev/network/terraform.tfstate"
    region = "us-east-1"
  }
}

# dns レイヤー：ホストゾーンと ACM 証明書（名前で探す）
data "aws_route53_zone" "main" {
  name = local.domain
}

data "aws_acm_certificate" "main" {
  domain      = local.domain
  statuses    = ["ISSUED"]
  most_recent = true
}

# database レイヤー：RDS のエンドポイント
data "aws_db_instance" "main" {
  db_instance_identifier = "cross-border-payment-service-db"
}

# mail レイヤー：SMTP 認証情報のシークレット
data "aws_secretsmanager_secret" "smtp" {
  name = "cross-border-payment-service/smtp"
}

# shared レイヤー：アプリ用シークレットと ECR
data "aws_secretsmanager_secret" "app" {
  name = "cross-border-payment-service/app"
}

data "aws_ecr_repository" "backend" {
  name = "cross-border-payment-backend"
}

data "aws_ecr_repository" "frontend" {
  name = "cross-border-payment-frontend"
}

# ECS 用に最適化された最新の Amazon Linux 2023 の AMI
data "aws_ssm_parameter" "ecs_ami" {
  name = "/aws/service/ecs/optimized-ami/amazon-linux-2023/recommended/image_id"
}
