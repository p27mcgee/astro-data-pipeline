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

# RDS Configuration
variable "rds_instance_class" {
  description = "Instance class for RDS"
  type        = string
  default     = "db.t3.small"
}

variable "rds_allocated_storage" {
  description = "Allocated storage for RDS instance"
  type        = number
  default     = 20
}

variable "rds_max_allocated_storage" {
  description = "Maximum allocated storage for RDS instance"
  type        = number
  default     = 100
}

variable "rds_storage_type" {
  description = "Storage type for RDS instance"
  type        = string
  default     = "gp3"
}

variable "rds_engine_version" {
  description = "PostgreSQL engine version"
  type        = string
  default     = "15.4"
}

variable "rds_database_name" {
  description = "Name of the database to create"
  type        = string
  default     = "astro_catalog"
}

variable "rds_username" {
  description = "Username for the RDS instance"
  type        = string
  default     = "astro_user"
}

variable "rds_backup_retention_period" {
  description = "Backup retention period in days"
  type        = number
  default     = 1
}

variable "rds_multi_az" {
  description = "Enable Multi-AZ deployment"
  type        = bool
  default     = false
}

variable "rds_storage_encrypted" {
  description = "Enable storage encryption"
  type        = bool
  default     = true
}

variable "rds_performance_insights_enabled" {
  description = "Enable Performance Insights"
  type        = bool
  default     = false
}

variable "rds_monitoring_interval" {
  description = "Enhanced monitoring interval in seconds (0, 1, 5, 10, 15, 30, 60)"
  type        = number
  default     = 0
}

variable "enable_secrets_manager" {
  description = "Enable AWS Secrets Manager for sensitive data"
  type        = bool
  default     = true
}

variable "additional_tags" {
  description = "Additional tags to apply to resources"
  type        = map(string)
  default     = {}
}