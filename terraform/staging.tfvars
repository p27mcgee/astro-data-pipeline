# Staging Environment Configuration
# Copy this file to staging.tfvars and customize for your staging environment
#
# COST OPTIMIZATION APPROACH:
# This staging environment is optimized for cost (~$202/month) by using:
# - Single-AZ deployment (saves ~$35/month on NAT Gateway)
# - Minimal instance sizes (t3.small/t3.medium for balanced performance)
# - Aggressive S3 lifecycle policies (30-day expiration)
# - Reduced monitoring and logging retention
# - No KMS encryption (uses AWS managed keys)

# Basic Configuration
aws_region   = "us-east-1"
environment  = "staging"
project_name = "astro-data-pipeline"

# VPC Configuration - Single-AZ setup for cost optimization
vpc_cidr                = "10.0.0.0/16"
public_subnet_cidrs     = ["10.0.1.0/24"]                    # Single AZ (us-east-1a)
private_subnet_cidrs    = ["10.0.10.0/24"]                   # Single AZ (us-east-1a)
database_subnet_cidrs   = ["10.0.100.0/24", "10.0.101.0/24"] # RDS requires minimum 2 AZs

# EKS Configuration - Staging setup
eks_cluster_version = "1.28"
eks_node_groups = {
  general = {
    instance_types = ["t3.medium"]
    scaling_config = {
      desired_size = 1
      max_size     = 3
      min_size     = 1
    }
    capacity_type = "ON_DEMAND"
    ami_type     = "AL2_x86_64"
    disk_size    = 20
    labels = {
      "node.kubernetes.io/workload" = "general"
    }
    taints = []
  }
}

# RDS Configuration - Staging instance (Single-AZ for cost)
# Trade-off: No automatic failover, but sufficient for development/staging
# Upgraded to db.t3.small for better PostgreSQL performance (2GB RAM vs 1GB)
rds_instance_class           = "db.t3.small"
rds_allocated_storage        = 20
rds_max_allocated_storage    = 100
rds_storage_type            = "gp3"
rds_engine_version          = "15.4"
rds_database_name           = "astro_catalog"
rds_username                = "astro_user"
rds_backup_retention_period = 1
rds_multi_az               = false
rds_storage_encrypted      = true
rds_performance_insights_enabled = false
rds_monitoring_interval    = 0

# S3 Configuration - Staging lifecycle
s3_buckets = {
  raw-data = {
    versioning_enabled = false
    lifecycle_rules = [
      {
        id     = "staging_cleanup"
        status = "Enabled"
        transitions = [
          {
            days          = 7
            storage_class = "STANDARD_IA"
          }
        ]
        expiration = {
          days = 30
        }
      }
    ]
  }
  processed-data = {
    versioning_enabled = false
    lifecycle_rules = [
      {
        id     = "staging_cleanup"
        status = "Enabled"
        transitions = [
          {
            days          = 7
            storage_class = "STANDARD_IA"
          }
        ]
        expiration = {
          days = 30
        }
      }
    ]
  }
  archive = {
    versioning_enabled = false
    lifecycle_rules = [
      {
        id     = "staging_cleanup"
        status = "Enabled"
        transitions = [
          {
            days          = 1
            storage_class = "GLACIER"
          }
        ]
        expiration = {
          days = 30
        }
      }
    ]
  }
}

# Monitoring Configuration - Cost-optimized for staging
cloudwatch_log_retention_days = 7      # Short retention (vs 90 days in prod)
enable_container_insights    = false   # Disabled to save ~$20/month

# Airflow Configuration
airflow_namespace    = "airflow"
airflow_chart_version = "1.11.0"

# Auto Scaling Configuration
enable_cluster_autoscaler         = true
enable_horizontal_pod_autoscaler = true

# Security Configuration - Balanced security and cost
enable_secrets_manager = true     # Essential for credential management
enable_kms_encryption = false    # Uses AWS managed keys (saves ~$3/month)

# Cost Optimization
enable_spot_instances     = true
enable_scheduled_scaling = false

# Additional Tags
additional_tags = {
  Environment = "staging"
  Purpose     = "astronomical-data-processing"
  CostCenter  = "research-and-development"
  Owner       = "stsci-demo"
}