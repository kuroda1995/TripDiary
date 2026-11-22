# CloudFront
#
# web: 画面（S3）と API（/api/*・/actuator/health → ALB）を同じドメインで配信する。
#      待機モード（var.standby）では API への経路を外し、すべてのページでお知らせページを返す。
#      ディストリビューションは消さないため、URL（*.cloudfront.net）は変わらない。
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
  count = local.running

  # 待機モードへの切り替えで、ディストリビューションから参照を外した「後」に消すため。
  # 既定の順番では先に消そうとして、使用中のため失敗する（409 CannotDeleteEntityWhileInUse）
  lifecycle {
    create_before_destroy = true
  }

  vpc_origin_endpoint_config {
    name                   = "${local.name}-api"
    arn                    = aws_lb.api[0].arn
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

# 待機モードの画面: すべてのページをお知らせページ（standby.html）に書き換え、API には 503 を返す
resource "aws_cloudfront_function" "standby" {
  name    = "${local.name}-standby"
  runtime = "cloudfront-js-2.0"
  comment = "Serve the standby notice while the API is stopped"
  publish = true
  code    = file("${path.module}/functions/standby.js")
}

# お知らせページは起動中も置いておく（deploy.ps1 の S3 同期では消さない）
resource "aws_s3_object" "standby_page" {
  bucket        = aws_s3_bucket.web.id
  key           = "standby.html"
  source        = "${path.module}/pages/standby.html"
  etag          = filemd5("${path.module}/pages/standby.html")
  content_type  = "text/html; charset=utf-8"
  cache_control = "no-cache"
}

locals {
  # 待機モードでは API への経路を作らない（/api/* も既定の動作に入り、standby.js が 503 を返す）
  api_paths = var.standby ? [] : ["/api/*", "/actuator/health"]
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

  dynamic "origin" {
    for_each = aws_lb.api
    content {
      origin_id   = "api"
      domain_name = origin.value.dns_name

      vpc_origin_config {
        vpc_origin_id = aws_cloudfront_vpc_origin.api[0].id
      }
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
      function_arn = var.standby ? aws_cloudfront_function.standby.arn : aws_cloudfront_function.spa_rewrite.arn
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
