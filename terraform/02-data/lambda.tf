# Package Lambda function code for S3 event-driven processing
data "archive_file" "s3_trigger_lambda" {
  type        = "zip"
  output_path = "/tmp/s3_trigger_lambda.zip"

  source {
    content = templatefile("${path.module}/lambda/s3_trigger.py", {
      airflow_namespace = var.airflow_namespace
      airflow_endpoint  = "http://airflow-webserver.${var.airflow_namespace}:8080"
    })
    filename = "index.py"
  }
}

# IAM execution role for Lambda with AWS service trust policy
resource "aws_iam_role" "s3_trigger_lambda" {
  name = "${var.project_name}-${var.environment}-s3-trigger-lambda-role"

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

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-s3-trigger-lambda-role"
  })
}

# Attach AWS managed policy for CloudWatch logging
resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.s3_trigger_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# Custom IAM policy allowing S3 access to project buckets only
resource "aws_iam_policy" "lambda_s3_policy" {
  name        = "${var.project_name}-${var.environment}-lambda-s3-policy"
  description = "IAM policy for Lambda to access S3 buckets"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:ListBucket"
        ]
        Resource = [
          "arn:aws:s3:::${var.project_name}-${var.environment}-*",
          "arn:aws:s3:::${var.project_name}-${var.environment}-*/*"
        ]
      }
    ]
  })
}

# Attach custom S3 policy to Lambda execution role
resource "aws_iam_role_policy_attachment" "lambda_s3_policy" {
  role       = aws_iam_role.s3_trigger_lambda.name
  policy_arn = aws_iam_policy.lambda_s3_policy.arn
}

# Lambda function to trigger Airflow DAGs when FITS files arrive
resource "aws_lambda_function" "s3_trigger" {
  filename      = data.archive_file.s3_trigger_lambda.output_path
  function_name = "${var.project_name}-${var.environment}-s3-trigger"
  role          = aws_iam_role.s3_trigger_lambda.arn
  handler       = "index.lambda_handler"
  runtime       = "python3.9"
  timeout       = 60

  source_code_hash = data.archive_file.s3_trigger_lambda.output_base64sha256

  environment {
    variables = {
      AIRFLOW_NAMESPACE = var.airflow_namespace
      PROJECT_NAME      = var.project_name
    }
  }

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-s3-trigger"
  })
}

# Grant S3 service permission to invoke the Lambda function
resource "aws_lambda_permission" "s3_invoke" {
  statement_id  = "AllowS3Invoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.s3_trigger.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.data_buckets["raw-data"].arn
}