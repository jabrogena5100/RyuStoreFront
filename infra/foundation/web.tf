# Frontend pieces that stay between sessions: its image registry and the certificate for the apex.

resource "aws_ecr_repository" "web" {
  name                 = "storefront-web"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "web" {
  repository = aws_ecr_repository.web.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 15 most recent images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 15 }
      action       = { type = "expire" }
    }]
  })
}

variable "app_domain" {
  type    = string
  default = "ryustorefront.com"
}

# The apex's DNS is at Cloudflare, not in Route 53, so Terraform cannot validate this certificate. After the first apply,
# copy the record from the output `app_certificate_validation` into Cloudflare by hand (CNAME, DNS only / grey cloud).
# The certificate shows PENDING_VALIDATION until then; the session stage looks it up only once it is ISSUED.
resource "aws_acm_certificate" "app" {
  domain_name       = var.app_domain
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

output "web_ecr_repository_url" {
  value = aws_ecr_repository.web.repository_url
}

output "app_certificate_validation" {
  description = "Add this CNAME in Cloudflare (DNS only), once"
  value = [for o in aws_acm_certificate.app.domain_validation_options : {
    name  = o.resource_record_name
    type  = o.resource_record_type
    value = o.resource_record_value
  }]
}
