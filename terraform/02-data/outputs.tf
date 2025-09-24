# Data Layer Outputs
# These outputs are consumed by other layers

# S3 Bucket Outputs
output "s3_bucket_names" {
  description = "Names of the S3 buckets"
  value = {
    for k, v in aws_s3_bucket.data_buckets : k => v.id
  }
}

output "s3_bucket_arns" {
  description = "ARNs of the S3 buckets"
  value = {
    for k, v in aws_s3_bucket.data_buckets : k => v.arn
  }
}

output "s3_bucket_domain_names" {
  description = "Domain names of the S3 buckets"
  value = {
    for k, v in aws_s3_bucket.data_buckets : k => v.bucket_domain_name
  }
}

# Lambda Outputs
output "lambda_function_arn" {
  description = "ARN of the S3 trigger Lambda function"
  value       = aws_lambda_function.s3_trigger.arn
}

output "lambda_function_name" {
  description = "Name of the S3 trigger Lambda function"
  value       = aws_lambda_function.s3_trigger.function_name
}

# KMS Outputs
output "s3_kms_key_arn" {
  description = "ARN of the S3 KMS key"
  value       = var.enable_kms_encryption ? aws_kms_key.s3[0].arn : null
}

output "s3_kms_key_id" {
  description = "ID of the S3 KMS key"
  value       = var.enable_kms_encryption ? aws_kms_key.s3[0].key_id : null
}