# Order e-mail from the worker through the SES API. The sending domain is the delegated zone (api.ryustorefront.com),
# so Terraform can publish the DKIM records itself. The account starts in the SES sandbox: mail goes only to
# addresses verified in SES, which is fine for a demo (the owner verifies their own address with the AWS CLI;
# infra/README.md). Production access is a request to AWS, not a Terraform setting.

resource "aws_sesv2_email_identity" "orders" {
  email_identity = var.domain

  dkim_signing_attributes {
    next_signing_key_length = "RSA_2048_BIT"
  }
}

resource "aws_route53_record" "ses_dkim" {
  count   = 3 # Easy DKIM always issues three tokens
  zone_id = aws_route53_zone.main.zone_id
  name    = "${aws_sesv2_email_identity.orders.dkim_signing_attributes[0].tokens[count.index]}._domainkey.${var.domain}"
  type    = "CNAME"
  ttl     = 1800
  records = ["${aws_sesv2_email_identity.orders.dkim_signing_attributes[0].tokens[count.index]}.dkim.amazonses.com"]
}

# Monitoring only (p=none): receivers report, nothing is rejected.
resource "aws_route53_record" "dmarc" {
  zone_id = aws_route53_zone.main.zone_id
  name    = "_dmarc.${var.domain}"
  type    = "TXT"
  ttl     = 1800
  records = ["v=DMARC1; p=none"]
}

output "ses_identity" {
  value = aws_sesv2_email_identity.orders.email_identity
}
