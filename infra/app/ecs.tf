# ===== ログ =====
resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/${local.name}/backend"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "frontend" {
  name              = "/ecs/${local.name}/frontend"
  retention_in_days = 14
}

# ===== タスク定義 =====
# イメージは :latest を使う。CircleCI は :latest を push して force-new-deployment するだけなので、
# タスク定義は Terraform だけが管理する（.circleci/config.yml の build-and-deploy を参照）

resource "aws_ecs_task_definition" "backend" {
  family                   = "${local.name}-backend"
  network_mode             = "bridge"
  requires_compatibilities = ["EC2"]
  execution_role_arn       = aws_iam_role.task_execution.arn

  container_definitions = jsonencode([{
    name      = "backend"
    image     = "${data.aws_ecr_repository.backend.repository_url}:latest"
    essential = true

    cpu               = 512 # 0.5 vCPU（1024 = 1 vCPU）
    memoryReservation = 512 # 配置の判断に使うメモリ
    memory            = 768 # 上限。JVM はこの 75% をヒープに使う（Dockerfile の MaxRAMPercentage）

    portMappings = [{
      containerPort = 8080
      hostPort      = 0 # ホスト側のポートは自動で割り当てる
      protocol      = "tcp"
    }]

    environment = [
      { name = "DB_URL", value = "jdbc:mysql://${data.aws_db_instance.main.address}:3306/crossborderpayment?sslMode=VERIFY_IDENTITY&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true" },
      { name = "SPRING_MAIL_HOST", value = "email-smtp.${local.region}.amazonaws.com" },
      { name = "SPRING_MAIL_PORT", value = "587" },
      { name = "APP_MAIL_FROM", value = "no-reply@${local.domain}" },
      { name = "APP_WEBAUTHN_RP_ID", value = local.domain },
      { name = "APP_ALLOWED_ORIGINS", value = "https://${local.domain}" },
    ]

    # シークレットは「ARN:JSON のキー::」の形で指定する
    secrets = [
      { name = "DB_USERNAME", valueFrom = "${data.aws_secretsmanager_secret.app.arn}:DB_USERNAME::" },
      { name = "DB_PASSWORD", valueFrom = "${data.aws_secretsmanager_secret.app.arn}:DB_PASSWORD::" },
      { name = "APP_OTP_HMAC_KEY", valueFrom = "${data.aws_secretsmanager_secret.app.arn}:APP_OTP_HMAC_KEY::" },
      { name = "SPRING_MAIL_USERNAME", valueFrom = "${data.aws_secretsmanager_secret.smtp.arn}:username::" },
      { name = "SPRING_MAIL_PASSWORD", valueFrom = "${data.aws_secretsmanager_secret.smtp.arn}:password::" },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.backend.name
        "awslogs-region"        = local.region
        "awslogs-stream-prefix" = "backend"
      }
    }
  }])
}

resource "aws_ecs_task_definition" "frontend" {
  family                   = "${local.name}-frontend"
  network_mode             = "bridge"
  requires_compatibilities = ["EC2"]
  execution_role_arn       = aws_iam_role.task_execution.arn

  container_definitions = jsonencode([{
    name      = "frontend"
    image     = "${data.aws_ecr_repository.frontend.repository_url}:latest"
    essential = true

    cpu               = 128
    memoryReservation = 64
    memory            = 128

    portMappings = [{
      containerPort = 8080
      hostPort      = 0
      protocol      = "tcp"
    }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.frontend.name
        "awslogs-region"        = local.region
        "awslogs-stream-prefix" = "frontend"
      }
    }
  }])
}

# ===== サービス =====
resource "aws_ecs_service" "backend" {
  name            = "${local.name}-backend" # CircleCI の ECS_BACKEND_SERVICE
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.backend_desired_count

  capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.ec2.name
    weight            = 1
  }

  # 新しいタスクが正常になってから古いタスクを止める
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  # 新しいタスクが起動に失敗し続けたら、デプロイを止めて前の状態に戻す
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # Spring Boot と Flyway の起動を待ってからヘルスチェックを始める
  health_check_grace_period_seconds = 180

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "backend"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener_rule.api, aws_ecs_cluster_capacity_providers.main]
}

resource "aws_ecs_service" "frontend" {
  name            = "${local.name}-frontend" # CircleCI の ECS_FRONTEND_SERVICE
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.frontend.arn
  desired_count   = 1

  capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.ec2.name
    weight            = 1
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  health_check_grace_period_seconds = 30

  load_balancer {
    target_group_arn = aws_lb_target_group.frontend.arn
    container_name   = "frontend"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.https, aws_ecs_cluster_capacity_providers.main]
}
