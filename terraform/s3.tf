# S3 Buckets for Data Lake Architecture
resource "aws_s3_bucket" "data_buckets" {
  for_each = var.s3_buckets

  bucket = "${var.project_name}-${each.key}-${var.environment}"

  tags = {
    Name        = "${var.project_name}-${each.key}"
    Environment = var.environment
    Purpose     = each.key
  }
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

      noncurrent_version_expiration {
        noncurrent_days = 30
      }

      abort_incomplete_multipart_upload {
        days_after_initiation = 7
      }
    }
  }

  depends_on = [aws_s3_bucket_versioning.data_buckets]
}

# S3 Bucket Notification for triggering Airflow workflows
resource "aws_s3_bucket_notification" "raw_data_notification" {
  bucket = aws_s3_bucket.data_buckets["raw-data"].id

  lambda_function {
    lambda_function_arn = aws_lambda_function.s3_trigger.arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "fits/"
    filter_suffix       = ".fits"
  }

  depends_on = [aws_lambda_permission.s3_trigger]
}

# Lambda function to trigger Airflow DAGs on S3 events
resource "aws_lambda_function" "s3_trigger" {
  filename         = data.archive_file.s3_trigger_lambda.output_path
  function_name    = "${var.project_name}-s3-trigger"
  role             = aws_iam_role.s3_trigger_lambda.arn
  handler          = "index.handler"
  source_code_hash = data.archive_file.s3_trigger_lambda.output_base64sha256
  runtime          = "python3.9"
  timeout          = 60

  environment {
    variables = {
      AIRFLOW_ENDPOINT = "http://airflow-webserver.${var.airflow_namespace}.svc.cluster.local:8080"
      DAG_ID           = "telescope_data_processing"
    }
  }

  tags = {
    Name = "${var.project_name}-s3-trigger"
  }
}

# Lambda function code
data "archive_file" "s3_trigger_lambda" {
  type        = "zip"
  output_path = "/tmp/s3_trigger_lambda.zip"

  source {
    content = templatefile("${path.module}/lambda/s3_trigger.py", {
      airflow_namespace = var.airflow_namespace
    })
    filename = "index.py"
  }
}

# IAM role for Lambda function
resource "aws_iam_role" "s3_trigger_lambda" {
  name = "${var.project_name}-s3-trigger-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${var.project_name}-s3-trigger-lambda-role"
  }
}

# IAM policy for Lambda function
resource "aws_iam_role_policy" "s3_trigger_lambda" {
  name = "${var.project_name}-s3-trigger-lambda-policy"
  role = aws_iam_role.s3_trigger_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject"
        ]
        Resource = "${aws_s3_bucket.data_buckets["raw-data"].arn}/*"
      }
    ]
  })
}

# Lambda permission for S3 to invoke the function
resource "aws_lambda_permission" "s3_trigger" {
  statement_id  = "AllowExecutionFromS3Bucket"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.s3_trigger.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.data_buckets["raw-data"].arn
}

# S3 Bucket for Terraform state (optional)
resource "aws_s3_bucket" "terraform_state" {
  count = var.environment == "prod" ? 1 : 0

  bucket = "${var.project_name}-terraform-state-${random_id.bucket_suffix.hex}"

  tags = {
    Name        = "${var.project_name}-terraform-state"
    Environment = var.environment
    Purpose     = "terraform-state"
  }
}

# S3 Bucket Versioning for Terraform state
resource "aws_s3_bucket_versioning" "terraform_state" {
  count = var.environment == "prod" ? 1 : 0

  bucket = aws_s3_bucket.terraform_state[0].id
  versioning_configuration {
    status = "Enabled"
  }
}

# S3 Bucket Server-Side Encryption for Terraform state
resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  count = var.environment == "prod" ? 1 : 0

  bucket = aws_s3_bucket.terraform_state[0].id

  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = var.enable_kms_encryption ? aws_kms_key.s3[0].arn : null
      sse_algorithm     = var.enable_kms_encryption ? "aws:kms" : "AES256"
    }
    bucket_key_enabled = var.enable_kms_encryption
  }
}

# S3 Bucket Public Access Block for Terraform state
resource "aws_s3_bucket_public_access_block" "terraform_state" {
  count = var.environment == "prod" ? 1 : 0

  bucket = aws_s3_bucket.terraform_state[0].id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# DynamoDB table for Terraform state locking
resource "aws_dynamodb_table" "terraform_state_lock" {
  count = var.environment == "prod" ? 1 : 0

  name         = "${var.project_name}-terraform-locks"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  tags = {
    Name        = "${var.project_name}-terraform-locks"
    Environment = var.environment
    Purpose     = "terraform-state-locking"
  }
}

# Random ID for bucket suffix to ensure uniqueness
resource "random_id" "bucket_suffix" {
  byte_length = 8
}

# KMS Key for S3 encryption
resource "aws_kms_key" "s3" {
  count = var.enable_kms_encryption ? 1 : 0

  description             = "KMS key for S3 bucket encryption"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = {
    Name = "${var.project_name}-s3-kms-key"
  }
}

resource "aws_kms_alias" "s3" {
  count = var.enable_kms_encryption ? 1 : 0

  name          = "alias/${var.project_name}-s3"
  target_key_id = aws_kms_key.s3[0].key_id
}

# CloudWatch dashboard for S3 metrics
resource "aws_cloudwatch_dashboard" "s3_dashboard" {
  dashboard_name = "${var.project_name}-s3-metrics"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6

        properties = {
          metrics = [
            for bucket_name in keys(var.s3_buckets) : [
              "AWS/S3",
              "BucketSizeBytes",
              "BucketName",
              aws_s3_bucket.data_buckets[bucket_name].id,
              "StorageType",
              "StandardStorage"
            ]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "S3 Bucket Sizes"
          period  = 86400
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6

        properties = {
          metrics = [
            for bucket_name in keys(var.s3_buckets) : [
              "AWS/S3",
              "NumberOfObjects",
              "BucketName",
              aws_s3_bucket.data_buckets[bucket_name].id,
              "StorageType",
              "AllStorageTypes"
            ]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "S3 Object Counts"
          period  = 86400
        }
      }
    ]
  })
}

# S3 Access Logging (optional)
resource "aws_s3_bucket" "access_logs" {
  count = var.environment == "prod" ? 1 : 0

  bucket = "${var.project_name}-access-logs-${random_id.bucket_suffix.hex}"

  tags = {
    Name        = "${var.project_name}-access-logs"
    Environment = var.environment
    Purpose     = "access-logging"
  }
}

resource "aws_s3_bucket_logging" "data_bucket_logging" {
  for_each = var.environment == "prod" ? var.s3_buckets : {}

  bucket = aws_s3_bucket.data_buckets[each.key].id

  target_bucket = aws_s3_bucket.access_logs[0].id
  target_prefix = "${each.key}/"
}

# CloudWatch alarms for S3 monitoring
resource "aws_cloudwatch_metric_alarm" "s3_4xx_errors" {
  for_each = var.s3_buckets

  alarm_name          = "${var.project_name}-s3-${each.key}-4xx-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "4xxErrors"
  namespace           = "AWS/S3"
  period              = "300"
  statistic           = "Sum"
  threshold           = "10"
  alarm_description   = "This metric monitors S3 4xx errors for ${each.key} bucket"
  alarm_actions       = [] # Add SNS topic ARN for notifications

  dimensions = {
    BucketName = aws_s3_bucket.data_buckets[each.key].id
  }

  tags = {
    Name = "${var.project_name}-s3-${each.key}-4xx-errors"
  }
}