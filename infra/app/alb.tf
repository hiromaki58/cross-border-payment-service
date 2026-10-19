# ===== ALB =====
resource "aws_lb" "main" {
  name                       = local.name
  load_balancer_type         = "application"
  internal                   = false
  security_groups            = [data.terraform_remote_state.network.outputs.alb_security_group_id]
  subnets                    = data.terraform_remote_state.network.outputs.public_subnet_ids
  drop_invalid_header_fields = true
}

# ===== ターゲットグループ =====
# EC2 の bridge モードなので target_type は instance（ホストのポートは ECS が自動で登録する）
resource "aws_lb_target_group" "backend" {
  name                 = "${local.name}-backend"
  port                 = 8080
  protocol             = "HTTP"
  target_type          = "instance"
  vpc_id               = data.terraform_remote_state.network.outputs.vpc_id
  deregistration_delay = 30

  health_check {
    path                = "/api/auth/csrf"
    matcher             = "200"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_target_group" "frontend" {
  name                 = "${local.name}-frontend"
  port                 = 8080
  protocol             = "HTTP"
  target_type          = "instance"
  vpc_id               = data.terraform_remote_state.network.outputs.vpc_id
  deregistration_delay = 30

  health_check {
    path                = "/"
    matcher             = "200"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

# ===== リスナー =====
# HTTP は HTTPS へリダイレクトする
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# HTTPS：通常はフロントエンドへ転送する
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = data.aws_acm_certificate.main.arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend.arn
  }
}

# /api で始まるパスだけバックエンドへ転送する（frontend/src/cmn/Constant.ts と対応）
resource "aws_lb_listener_rule" "api" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 10

  condition {
    path_pattern {
      values = ["/api", "/api/*"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}

# ===== DNS：hiromaki58.com を ALB に向ける =====
resource "aws_route53_record" "app" {
  zone_id = data.aws_route53_zone.main.zone_id
  name    = local.domain
  type    = "A"

  alias {
    name                   = aws_lb.main.dns_name
    zone_id                = aws_lb.main.zone_id
    evaluate_target_health = true
  }
}
