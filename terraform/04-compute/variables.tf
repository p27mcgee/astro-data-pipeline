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

# EKS Configuration
variable "eks_cluster_version" {
  description = "Kubernetes version for EKS cluster"
  type        = string
  default     = "1.28"
}

variable "eks_public_access_cidrs" {
  description = "List of CIDR blocks that can access the EKS cluster public endpoint"
  type        = list(string)
  default     = ["0.0.0.0/0"] # Default to open access - override in tfvars for security
}

variable "eks_node_groups" {
  description = "Configuration for EKS node groups"
  type = map(object({
    instance_types = list(string)
    scaling_config = object({
      desired_size = number
      max_size     = number
      min_size     = number
    })
    capacity_type = string
    ami_type      = string
    disk_size     = number
    labels        = map(string)
    taints = list(object({
      key    = string
      value  = string
      effect = string
    }))
  }))
  default = {
    general = {
      instance_types = ["t3.medium"]
      scaling_config = {
        desired_size = 1
        max_size     = 3
        min_size     = 1
      }
      capacity_type = "ON_DEMAND"
      ami_type      = "AL2_x86_64"
      disk_size     = 50  # Increased for container images and ephemeral processing storage
      labels = {
        "node.kubernetes.io/workload" = "general"
      }
      taints = []
    }
  }
}

variable "cloudwatch_log_retention_days" {
  description = "CloudWatch log retention period in days"
  type        = number
  default     = 7
}

variable "enable_kms_encryption" {
  description = "Enable KMS encryption for EKS secrets"
  type        = bool
  default     = false
}

variable "additional_tags" {
  description = "Additional tags to apply to resources"
  type        = map(string)
  default     = {}
}