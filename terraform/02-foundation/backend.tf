# Remote state backend configuration for Layer 1: Foundation
# Stores terraform state in S3 with DynamoDB locking for safe concurrent access

terraform {
  backend "s3" {
    # S3 bucket for state storage
    bucket = "astro-data-pipeline-terraform-state-staging"

    # State file path for this layer
    key = "layers/01-foundation/terraform.tfstate"

    # AWS region
    region = "us-east-1"

    # DynamoDB table for state locking
    dynamodb_table = "astro-data-pipeline-terraform-state-lock-staging"

    # Enable state file encryption
    encrypt = true

    # Workspace key prefix for multi-environment support
    workspace_key_prefix = "workspaces"
  }
}