# Product photos: a private S3 bucket served through CloudFront with an origin access control (the bucket is never
# public). Kept between sessions: about 45 MiB of variants, uploaded once with `scripts/images/upload.sh aws`.
# Uses the distribution's own *.cloudfront.net name, so no us-east-1 certificate is needed.

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "images" {
  bucket = "storefront-images-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "images" {
  bucket                  = aws_s3_bucket.images.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "images" {
  bucket = aws_s3_bucket.images.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "images" {
  bucket = aws_s3_bucket.images.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_cloudfront_origin_access_control" "images" {
  name                              = "storefront-images"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# AWS managed policy "Managed-CachingOptimized": long TTLs, compression, no cookies or query strings in the key.
data "aws_cloudfront_cache_policy" "optimized" {
  name = "Managed-CachingOptimized"
}

resource "aws_cloudfront_distribution" "images" {
  enabled         = true
  comment         = "storefront product images"
  price_class     = "PriceClass_100" # North America and Europe edges only: the cheapest class
  is_ipv6_enabled = true
  http_version    = "http2and3"

  origin {
    origin_id                = "images"
    domain_name              = aws_s3_bucket.images.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.images.id
  }

  default_cache_behavior {
    target_origin_id       = "images"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.optimized.id
    compress               = false # AVIF and WebP are already compressed
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

# Only this distribution may read the bucket.
data "aws_iam_policy_document" "images_bucket" {
  statement {
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.images.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.images.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "images" {
  bucket     = aws_s3_bucket.images.id
  policy     = data.aws_iam_policy_document.images_bucket.json
  depends_on = [aws_s3_bucket_public_access_block.images]
}

# The session stage reads this instead of the foundation's state: the API returns <base-url>/<key> for every photo.
resource "aws_ssm_parameter" "images_base_url" {
  name  = "/storefront/images-base-url"
  type  = "String"
  value = "https://${aws_cloudfront_distribution.images.domain_name}"
}

output "images_bucket" {
  value = aws_s3_bucket.images.bucket
}

output "images_base_url" {
  value = "https://${aws_cloudfront_distribution.images.domain_name}"
}
