# ===== ECS のコンテナを動かす EC2 インスタンス =====

resource "aws_launch_template" "ecs" {
  name_prefix   = "${local.name}-ecs-"
  image_id      = data.aws_ssm_parameter.ecs_ami.insecure_value
  instance_type = "t3.small" # 2 vCPU / 2 GiB。バックエンドとフロントエンドを 1 台に載せる

  iam_instance_profile {
    arn = aws_iam_instance_profile.ecs_instance.arn
  }

  # パブリックサブネットに置き、外へ出る通信（ECR、SES など）用にパブリック IP を付ける。
  # 入ってくる通信は SG で ALB からだけに絞っている
  network_interfaces {
    associate_public_ip_address = true
    security_groups             = [data.terraform_remote_state.network.outputs.ecs_instances_security_group_id]
  }

  # IMDSv2 を必須にし、hop limit を 1 にしてコンテナからインスタンスのロールを使えないようにする
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  user_data = base64encode(<<-EOT
    #!/bin/bash
    echo "ECS_CLUSTER=${local.cluster_name}" >> /etc/ecs/ecs.config
  EOT
  )

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name      = "${local.name}-ecs"
      Project   = "cross-border-payment-service"
      ManagedBy = "terraform"
    }
  }
}

resource "aws_autoscaling_group" "ecs" {
  name                = "${local.name}-ecs"
  min_size            = 1
  max_size            = 2 # デプロイ中に足りなくなったときだけ 2 台目が起動する
  desired_capacity    = 1
  vpc_zone_identifier = data.terraform_remote_state.network.outputs.public_subnet_ids
  health_check_type   = "EC2"

  launch_template {
    id      = aws_launch_template.ecs.id
    version = "$Latest"
  }

  # ECS のキャパシティプロバイダーが管理していることを示すタグ
  tag {
    key                 = "AmazonECSManaged"
    value               = "true"
    propagate_at_launch = true
  }

  lifecycle {
    # 台数は ECS（マネージドスケーリング）が調整するので Terraform では触らない
    ignore_changes = [desired_capacity]
  }
}

resource "aws_ecs_capacity_provider" "ec2" {
  name = "${local.name}-ec2"

  auto_scaling_group_provider {
    auto_scaling_group_arn         = aws_autoscaling_group.ecs.arn
    managed_termination_protection = "DISABLED"

    managed_scaling {
      status          = "ENABLED"
      target_capacity = 100
    }
  }
}

# ===== ECS クラスター =====
resource "aws_ecs_cluster" "main" {
  name = local.cluster_name
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name       = aws_ecs_cluster.main.name
  capacity_providers = [aws_ecs_capacity_provider.ec2.name]

  default_capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.ec2.name
    weight            = 1
  }
}
