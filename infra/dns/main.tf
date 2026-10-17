# __generated__ by Terraform
# Please review these resources and move them into your main configuration files.

# __generated__ by Terraform from "arn:aws:acm:us-east-1:724775511010:certificate/626bf738-88cf-480c-b68a-0b5cab50ece3"
resource "aws_acm_certificate" "main" {
  certificate_authority_arn = null
  certificate_body          = null
  certificate_chain         = null
  domain_name               = "hiromaki58.com"
  early_renewal_duration    = null
  key_algorithm             = "RSA_2048"
  private_key               = null # sensitive
  private_key_wo            = null
  private_key_wo_version    = null
  region                    = "us-east-1"
  subject_alternative_names = ["hiromaki58.com"]
  tags = {
    Name = "cross-border-payment-service"
  }
  tags_all = {
    Name = "cross-border-payment-service"
  }
  validation_method = "DNS"
  options {
    certificate_transparency_logging_preference = "ENABLED"
    export                                      = "DISABLED"
  }
}

# __generated__ by Terraform
resource "aws_route53_record" "cert_validation" {
  health_check_id                  = null
  name                             = "_f49308e680897902d1347a0c3f015008.hiromaki58.com"
  records                          = ["_3ebbfd73cb5b08bb2ed18caccc66acb1.wzccmgtwzk.acm-validations.aws."]
  ttl                              = 300
  type                             = "CNAME"
  zone_id                          = aws_route53_zone.main.zone_id
}

# __generated__ by Terraform from "Z0677376EIKXKR6ZPH3Z"
resource "aws_route53_zone" "main" {
  comment                     = "HostedZone created by Route53 Registrar"
  delegation_set_id           = null
  enable_accelerated_recovery = false
  force_destroy               = null
  name                        = "hiromaki58.com"
  tags                        = {}
  tags_all                    = {}
}
