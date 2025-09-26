output "terraform_state_bucket" {
  description = "Name of the S3 bucket for terraform state"
  value       = aws_s3_bucket.terraform_state.bucket
}

output "terraform_state_bucket_arn" {
  description = "ARN of the S3 bucket for terraform state"
  value       = aws_s3_bucket.terraform_state.arn
}

output "terraform_state_lock_table" {
  description = "Name of the DynamoDB table for terraform state locking"
  value       = aws_dynamodb_table.terraform_state_lock.name
}

output "terraform_state_lock_table_arn" {
  description = "ARN of the DynamoDB table for terraform state locking"
  value       = aws_dynamodb_table.terraform_state_lock.arn
}