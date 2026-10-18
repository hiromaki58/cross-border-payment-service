output "smtp_secret_arn" {
  description = "SMTP 認証情報のシークレット（ECS のタスク定義で使う）"
  value       = aws_secretsmanager_secret.smtp.arn
}

output "ses_identity_arn" {
  value = aws_sesv2_email_identity.domain.arn
}
