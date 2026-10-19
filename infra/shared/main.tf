# app レイヤーより先に用意しておくもの
# - ECR リポジトリ（イメージを push してから ECS を作るため）
# - アプリ用シークレットの入れ物（値を入れてから ECS を作るため）
# - CircleCI がデプロイに使う IAM ユーザー

locals {
  name         = "cross-border-payment"
  cluster_name = "cross-border-payment"
  repositories = ["cross-border-payment-backend", "cross-border-payment-frontend"] # .circleci/config.yml と同じ名前
}

data "aws_caller_identity" "current" {}

# ===== ECR =====
resource "aws_ecr_repository" "app" {
  for_each = toset(local.repositories)

  name                 = each.value
  image_tag_mutability = "MUTABLE" # CircleCI が毎回 :latest を付け替えるため

  image_scanning_configuration {
    scan_on_push = true
  }
}

# 古いイメージを自動で削除する（最新 20 個だけ残す）
resource "aws_ecr_lifecycle_policy" "app" {
  for_each   = aws_ecr_repository.app
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the latest 20 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}

# ===== アプリ用シークレット（入れ物だけ。値は CLI で入れる） =====
# 中身は JSON: {"DB_USERNAME": "...", "DB_PASSWORD": "...", "APP_OTP_HMAC_KEY": "..."}
resource "aws_secretsmanager_secret" "app" {
  name        = "cross-border-payment-service/app"
  description = "バックエンドの DB 認証情報と OTP 用 HMAC 鍵"
}

# ===== CircleCI 用の IAM ユーザー =====
resource "aws_iam_user" "circleci" {
  name = "${local.name}-circleci-deploy"
}

resource "aws_iam_user_policy" "circleci" {
  name = "ecr-push-and-ecs-deploy"
  user = aws_iam_user.circleci.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "EcrLogin"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Sid    = "EcrPush"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:CompleteLayerUpload",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
        ]
        Resource = [for r in aws_ecr_repository.app : r.arn]
      },
      {
        Sid    = "EcsDeploy"
        Effect = "Allow"
        Action = [
          "ecs:UpdateService",
          "ecs:DescribeServices",
        ]
        Resource = "arn:aws:ecs:us-east-1:${data.aws_caller_identity.current.account_id}:service/${local.cluster_name}/*"
      },
    ]
  })
}

resource "aws_iam_access_key" "circleci" {
  user = aws_iam_user.circleci.name
}
