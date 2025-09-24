variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (staging, prod)"
  type        = string
  default     = "staging"
}

variable "project_name" {
  description = "Name of the project"
  type        = string
  default     = "astro-data-pipeline"
}

# S3 Configuration
variable "s3_buckets" {
  description = "Configuration for S3 buckets"
  type = map(object({
    versioning_enabled = bool
    lifecycle_rules = list(object({
      id     = string
      status = string
      transitions = list(object({
        days          = number
        storage_class = string
      }))
      expiration = object({
        days = number
      })
    }))
  }))
  default = {
    raw-data = {
      versioning_enabled = false
      lifecycle_rules = [{
        id     = "staging_cleanup"
        status = "Enabled"
        transitions = [
          { days = 7, storage_class = "STANDARD_IA" }
        ]
        expiration = { days = 30 }
      }]
    }
    processed-data = {
      versioning_enabled = false
      lifecycle_rules = [{
        id     = "staging_cleanup"
        status = "Enabled"
        transitions = [
          { days = 7, storage_class = "STANDARD_IA" }
        ]
        expiration = { days = 30 }
      }]
    }
    archive = {
      versioning_enabled = false
      lifecycle_rules = [{
        id     = "staging_cleanup"
        status = "Enabled"
        transitions = [
          { days = 1, storage_class = "GLACIER" }
        ]
        expiration = { days = 30 }
      }]
    }
  }
}

variable "enable_kms_encryption" {
  description = "Enable KMS encryption for S3 buckets"
  type        = bool
  default     = false
}

variable "airflow_namespace" {
  description = "Kubernetes namespace for Airflow"
  type        = string
  default     = "airflow"
}

variable "additional_tags" {
  description = "Additional tags to apply to resources"
  type        = map(string)
  default     = {}
}