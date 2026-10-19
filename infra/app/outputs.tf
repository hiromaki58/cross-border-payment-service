# CircleCI に登録する値
output "ECS_CLUSTER" {
  value = aws_ecs_cluster.main.name
}

output "ECS_BACKEND_SERVICE" {
  value = aws_ecs_service.backend.name
}

output "ECS_FRONTEND_SERVICE" {
  value = aws_ecs_service.frontend.name
}

# 動作確認用
output "alb_dns_name" {
  value = aws_lb.main.dns_name
}

output "app_url" {
  value = "https://${local.domain}"
}
