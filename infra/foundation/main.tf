# Foundation stage: things that stay between sessions (DNS zone, image registry).
# Per-session resources (VPC, RDS, ECS, ALB) live in a separate stage and are destroyed after each session.
terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Bucket is passed at init time: -backend-config="bucket=<name>" (see infra/README.md).
  backend "s3" {
    key          = "storefront/foundation.tfstate"
    region       = "us-west-2"
    use_lockfile = true
    encrypt      = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project = "storefront"
      Stage   = "foundation"
    }
  }
}

variable "region" {
  type    = string
  default = "us-west-2"
}

variable "domain" {
  type    = string
  default = "api.ryustorefront.com"
}

# The public hosted zone already exists (created by hand and delegated from Cloudflare as the api subdomain).
# Import it before the first apply so Terraform adopts it instead of creating a second zone
# with different name servers: terraform import aws_route53_zone.main <zone-id>
resource "aws_route53_zone" "main" {
  name = var.domain

  lifecycle {
    prevent_destroy = true # deleting it would change the name servers and break the delegation
  }
}

resource "aws_ecr_repository" "app" {
  name                 = "storefront"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 15 most recent images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 15
      }
      action = { type = "expire" }
    }]
  })
}

output "name_servers" {
  value = aws_route53_zone.main.name_servers
}

output "zone_id" {
  value = aws_route53_zone.main.zone_id
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}
