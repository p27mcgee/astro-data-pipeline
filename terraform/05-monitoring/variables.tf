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

# Monitoring Configuration
variable "cloudwatch_log_retention_days" {
  description = "CloudWatch log retention period in days"
  type        = number
  default     = 7
}

variable "enable_container_insights" {
  description = "Enable CloudWatch Container Insights for EKS"
  type        = bool
  default     = false
}

variable "alarm_notification_emails" {
  description = "List of email addresses for alarm notifications"
  type        = list(string)
  default     = ["alerts@example.com"]
}

variable "additional_tags" {
  description = "Additional tags to apply to resources"
  type        = map(string)
  default     = {}
}