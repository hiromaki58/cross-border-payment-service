output "repository_urls" {
  value = { for k, r in aws_ecr_repository.app : k => r.repository_url }
}

output "app_secret_arn" {
  value = aws_secretsmanager_secret.app.arn
}

output "aws_account_id" {
  description = "CircleCI の AWS_ACCOUNT_ID"
  value       = data.aws_caller_identity.current.account_id
}

output "circleci_access_key_id" {
  description = "CircleCI の AWS_ACCESS_KEY_ID"
  value       = aws_iam_access_key.circleci.id
}

output "circleci_secret_access_key" {
  description = "CircleCI の AWS_SECRET_ACCESS_KEY（terraform output -raw circleci_secret_access_key で表示）"
  value       = aws_iam_access_key.circleci.secret
  sensitive   = true
}
