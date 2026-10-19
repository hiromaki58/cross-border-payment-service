locals {
  domain = "hiromaki58.com"
}

# dns レイヤーで管理しているホストゾーンを名前で探す
data "aws_route53_zone" "main" {
  name = local.domain
}

# ===== ドメインの認証（既存を import） =====
resource "aws_sesv2_email_identity" "domain" {
  email_identity = local.domain
}

# ===== DKIM 用の CNAME レコード（3 つ） =====
# これを登録すると、SES のドメイン認証が PENDING から SUCCESS に変わる
resource "aws_route53_record" "ses_dkim" {
  count   = 3
  zone_id = data.aws_route53_zone.main.zone_id
  name    = "${aws_sesv2_email_identity.domain.dkim_signing_attributes[0].tokens[count.index]}._domainkey.${local.domain}"
  type    = "CNAME"
  ttl     = 1800
  records = ["${aws_sesv2_email_identity.domain.dkim_signing_attributes[0].tokens[count.index]}.dkim.amazonses.com"]
}

# ===== DMARC レコード =====
# まずは p=none（監視のみ）で始める。運用が安定したら quarantine などに強める
resource "aws_route53_record" "dmarc" {
  zone_id = data.aws_route53_zone.main.zone_id
  name    = "_dmarc.${local.domain}"
  type    = "TXT"
  ttl     = 1800
  records = ["v=DMARC1; p=none;"]
}

# ===== サンドボックス中のテスト用の宛先 =====
# 作成すると確認メールが届くので、24 時間以内にリンクを押す
# サンドボックス解除後は不要なので削除してよい
resource "aws_sesv2_email_identity" "test_recipient" {
  email_identity = "hiromaki58@gmail.com"
}

# ===== SMTP 用の IAM ユーザー =====
resource "aws_iam_user" "ses_smtp" {
  name = "cross-border-payment-ses-smtp"
}

resource "aws_iam_user_policy" "ses_smtp_send" {
  name = "ses-send-from-domain"
  user = aws_iam_user.ses_smtp.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "ses:SendRawEmail"
      Resource = "*"
      # 送信元が自分のドメインのアドレスのときだけ許可する
      Condition = {
        StringLike = { "ses:FromAddress" = "*@${local.domain}" }
      }
    }]
  })
}

resource "aws_iam_access_key" "ses_smtp" {
  user = aws_iam_user.ses_smtp.name
}

# ===== SMTP 認証情報を Secrets Manager に保存 =====
resource "aws_secretsmanager_secret" "smtp" {
  name        = "cross-border-payment-service/smtp"
  description = "SES の SMTP 認証情報"
}

resource "aws_secretsmanager_secret_version" "smtp" {
  secret_id = aws_secretsmanager_secret.smtp.id
  secret_string = jsonencode({
    host     = "email-smtp.us-east-1.amazonaws.com"
    port     = 587
    username = aws_iam_access_key.ses_smtp.id
    password = aws_iam_access_key.ses_smtp.ses_smtp_password_v4
  })
}

# バウンス（宛先不明）と苦情があったアドレスへの送信を自動で止める
resource "aws_sesv2_account_suppression_attributes" "main" {
  suppressed_reasons = ["BOUNCE", "COMPLAINT"]
}
