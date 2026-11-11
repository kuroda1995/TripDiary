# CloudFront
#
# web: 画面（S3）と API（/api/*・/actuator/health → ALB）を同じドメインで配信する。
#      リフレッシュトークンの Cookie は SameSite=Strict のため、画面と API が同じサイトである必要がある。
#      ALB は内部向けのまま、CloudFront の VPC オリジンで接続する（ALB をインターネットに公開しない）。
# photos: 写真バケットの配信（PHOTO_BASE_URL）。
#
# 独自ドメインは使わず、CloudFront の既定のドメイン（*.cloudfront.net）と証明書で HTTPS にする。

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

# API はキャッシュしない。Authorization ヘッダーはキャッシュポリシーに含めないとオリジンへ転送されないため、
# キャッシュキーに含める（Spring Security が Cache-Control: no-store を返すので、実際には保存されない）
resource "aws_cloudfront_cache_policy" "api" {
  name        = "${local.name}-api-no-cache"
  min_ttl     = 0
  default_ttl = 0
  max_ttl     = 1

  parameters_in_cache_key_and_forwarded_to_origin {
    enable_accept_encoding_gzip   = false
    enable_accept_encoding_brotli = false

    headers_config {
      header_behavior = "whitelist"
      headers {
        items = ["Authorization"]
      }
    }
    cookies_config {
      cookie_behavior = "none"
    }
    query_strings_config {
      query_string_behavior = "all"
    }
  }
}

resource "aws_cloudfront_origin_access_control" "s3" {
  name                              = "${local.name}-s3"
  description                       = "TripDiary S3 buckets"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_vpc_origin" "api" {
  vpc_origin_endpoint_config {
    name                   = "${local.name}-api"
    arn                    = aws_lb.api.arn
    http_port              = 80
    https_port             = 443
    origin_protocol_policy = "http-only"

    origin_ssl_protocols {
      items    = ["TLSv1.2"]
      quantity = 1
    }
  }
}

resource "aws_cloudfront_function" "spa_rewrite" {
  name    = "${local.name}-spa-rewrite"
  runtime = "cloudfront-js-2.0"
  comment = "Rewrite SPA routes to /index.html"
  publish = true
  code    = file("${path.module}/functions/spa-rewrite.js")
}

locals {
  api_paths = ["/api/*", "/actuator/health"]
}

resource "aws_cloudfront_distribution" "web" {
  enabled             = true
  comment             = "${local.name} web + api"
  default_root_object = "index.html"
  price_class         = "PriceClass_200"
  http_version        = "http2and3"

  origin {
    origin_id                = "web"
    domain_name              = aws_s3_bucket.web.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  origin {
    origin_id   = "api"
    domain_name = aws_lb.api.dns_name

    vpc_origin_config {
      vpc_origin_id = aws_cloudfront_vpc_origin.api.id
    }
  }

  default_cache_behavior {
    target_origin_id       = "web"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_rewrite.arn
    }
  }

  dynamic "ordered_cache_behavior" {
    for_each = local.api_paths
    content {
      path_pattern             = ordered_cache_behavior.value
      target_origin_id         = "api"
      viewer_protocol_policy   = "redirect-to-https"
      allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
      cached_methods           = ["GET", "HEAD"]
      compress                 = true
      cache_policy_id          = aws_cloudfront_cache_policy.api.id
      origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
    }
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

resource "aws_cloudfront_distribution" "photos" {
  enabled      = true
  comment      = "${local.name} photos"
  price_class  = "PriceClass_200"
  http_version = "http2and3"

  origin {
    origin_id                = "photos"
    domain_name              = aws_s3_bucket.photos.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  default_cache_behavior {
    target_origin_id       = "photos"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id
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
