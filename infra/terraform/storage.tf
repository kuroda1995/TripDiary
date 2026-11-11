# S3 バケット
#
# - web:    フロントエンド（Vite のビルド成果物）。CloudFront（OAC）経由でのみ読める
# - photos: 投稿写真・アイコン。ブラウザから署名付き URL で直接 PUT し、CloudFront（OAC）経由で配信する
# どちらもパブリックアクセスをすべてブロックする（NFR-SEC-09）

resource "aws_s3_bucket" "web" {
  bucket        = "${local.name}-web-${local.account_id}"
  force_destroy = true
}

resource "aws_s3_bucket" "photos" {
  bucket        = "${local.name}-photos-${local.account_id}"
  force_destroy = true
}

locals {
  buckets = {
    web    = aws_s3_bucket.web
    photos = aws_s3_bucket.photos
  }
}

resource "aws_s3_bucket_public_access_block" "this" {
  for_each = local.buckets

  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "this" {
  for_each = local.buckets

  bucket = each.value.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  for_each = local.buckets

  bucket = each.value.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# ブラウザ（画面のドメイン）からの署名付き URL での PUT を許可する
resource "aws_s3_bucket_cors_configuration" "photos" {
  bucket = aws_s3_bucket.photos.id

  cors_rule {
    allowed_methods = ["PUT"]
    allowed_origins = ["https://${aws_cloudfront_distribution.web.domain_name}"]
    allowed_headers = ["*"]
    max_age_seconds = 3000
  }
}

# ---- CloudFront（OAC）からの読み取りだけを許可する ----

data "aws_iam_policy_document" "web_bucket" {
  statement {
    sid       = "AllowCloudFrontRead"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.web.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.web.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "web" {
  bucket = aws_s3_bucket.web.id
  policy = data.aws_iam_policy_document.web_bucket.json

  depends_on = [aws_s3_bucket_public_access_block.this]
}

data "aws_iam_policy_document" "photos_bucket" {
  statement {
    sid       = "AllowCloudFrontRead"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.photos.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.photos.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "photos" {
  bucket = aws_s3_bucket.photos.id
  policy = data.aws_iam_policy_document.photos_bucket.json

  depends_on = [aws_s3_bucket_public_access_block.this]
}
