# Session stage: everything here is created for a working session and destroyed afterwards.
# Nothing in this stage may hold data worth keeping; the Route 53 zone and ECR live in ../foundation.
terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Same bucket as the foundation stage, its own state file: -backend-config="bucket=<name>"
  backend "s3" {
    key          = "storefront/session.tfstate"
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
      Stage   = "session"
    }
  }
}

variable "region" {
  type    = string
  default = "us-west-2"
}

variable "name" {
  type    = string
  default = "storefront"
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

# 18.7 (used locally) was not orderable on this class on 2026-11-15; see docs/decisions.md.
variable "db_engine_version" {
  type    = string
  default = "18.6"
}

data "aws_availability_zones" "available" {
  state = "available"

  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)
}
