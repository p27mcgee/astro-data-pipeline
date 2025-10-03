variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (staging, prod)"
  type        = string
  default     = "staging"

  validation {
    condition     = contains(["staging", "prod"], var.environment)
    error_message = "Environment must be one of: staging, prod."
  }
}

variable "project_name" {
  description = "Name of the project"
  type        = string
  default     = "astro-data-pipeline"
}

variable "infrastructure_version" {
  description = "Infrastructure deployment version for tracking and tagging"
  type        = string
  default     = "1.0.1"
}

# VPC Configuration
variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets"
  type        = list(string)
  default     = ["10.0.1.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets"
  type        = list(string)
  default     = ["10.0.10.0/24"]
}

variable "database_subnet_cidrs" {
  description = "CIDR blocks for database subnets"
  type        = list(string)
  default     = ["10.0.100.0/24", "10.0.101.0/24"]
}

# NAT Gateway Configuration
variable "single_nat_gateway_for_prototyping" {
  description = "Use a single NAT Gateway for all private subnets (cost optimization for prototyping/staging only)"
  type        = bool
  default     = false

  validation {
    condition     = var.single_nat_gateway_for_prototyping == false || var.environment != "prod"
    error_message = "single_nat_gateway_for_prototyping cannot be enabled in production environment."
  }
}

# Additional Tags
variable "additional_tags" {
  description = "Additional tags to apply to resources"
  type        = map(string)
  default     = {}
}