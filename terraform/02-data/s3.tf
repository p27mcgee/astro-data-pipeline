# S3 Buckets for Data Lake Architecture
resource "aws_s3_bucket" "data_buckets" {
  for_each = var.s3_buckets

  bucket = "${var.project_name}-${each.key}-${var.environment}"

  tags = merge(var.additional_tags, {
    Name        = "${var.project_name}-${each.key}"
    Environment = var.environment
    Purpose     = each.key
  })
}

# S3 Bucket Versioning
resource "aws_s3_bucket_versioning" "data_buckets" {
  for_each = var.s3_buckets

  bucket = aws_s3_bucket.data_buckets[each.key].id
  versioning_configuration {
    status = each.value.versioning_enabled ? "Enabled" : "Disabled"
  }
}

# S3 Bucket Server-Side Encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "data_buckets" {
  for_each = var.s3_buckets

  bucket = aws_s3_bucket.data_buckets[each.key].id

  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = var.enable_kms_encryption ? aws_kms_key.s3[0].arn : null
      sse_algorithm     = var.enable_kms_encryption ? "aws:kms" : "AES256"
    }
    bucket_key_enabled = var.enable_kms_encryption
  }
}

# S3 Bucket Public Access Block
resource "aws_s3_bucket_public_access_block" "data_buckets" {
  for_each = var.s3_buckets

  bucket = aws_s3_bucket.data_buckets[each.key].id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# S3 Bucket Lifecycle Configuration
resource "aws_s3_bucket_lifecycle_configuration" "data_buckets" {
  for_each = var.s3_buckets

  bucket = aws_s3_bucket.data_buckets[each.key].id

  dynamic "rule" {
    for_each = each.value.lifecycle_rules
    content {
      id     = rule.value.id
      status = rule.value.status

      dynamic "transition" {
        for_each = rule.value.transitions
        content {
          days          = transition.value.days
          storage_class = transition.value.storage_class
        }
      }

      expiration {
        days = rule.value.expiration.days
      }

      abort_incomplete_multipart_upload {
        days_after_initiation = 7
      }
    }
  }
}

# S3 Bucket Notification for Lambda triggers
resource "aws_s3_bucket_notification" "data_processing_trigger" {
  bucket = aws_s3_bucket.data_buckets["raw-data"].id

  lambda_function {
    lambda_function_arn = aws_lambda_function.s3_trigger.arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = ""
    filter_suffix       = ".fits"
  }

  depends_on = [aws_lambda_permission.s3_invoke]
}

# Random ID for unique bucket naming
resource "random_id" "bucket_suffix" {
  byte_length = 8
}

# KMS Key for S3 encryption (optional)
resource "aws_kms_key" "s3" {
  count = var.enable_kms_encryption ? 1 : 0

  description             = "KMS key for S3 bucket encryption"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-s3-kms-key"
  })
}

resource "aws_kms_alias" "s3" {
  count = var.enable_kms_encryption ? 1 : 0

  name          = "alias/${var.project_name}-s3"
  target_key_id = aws_kms_key.s3[0].key_id
}