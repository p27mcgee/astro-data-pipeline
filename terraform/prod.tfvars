# Production Environment Configuration
# Copy this file to prod.tfvars and customize for your production environment
#
# HIGH AVAILABILITY APPROACH:
# This production environment prioritizes availability and reliability (~$1,200/month):
# - Multi-AZ deployment across 3 availability zones
# - Multi-AZ RDS with read replicas for high availability
# - Full encryption with customer-managed KMS keys
# - Comprehensive monitoring and extended log retention
# - Production-grade instance sizes and scaling

# Basic Configuration
aws_region   = "us-east-1"
environment  = "prod"
project_name = "astro-data-pipeline"

# VPC Configuration - Multi-AZ for high availability
vpc_cidr                           = "10.0.0.0/16"
public_subnet_cidrs                = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
private_subnet_cidrs               = ["10.0.10.0/24", "10.0.20.0/24", "10.0.30.0/24"]
database_subnet_cidrs              = ["10.0.100.0/24", "10.0.101.0/24", "10.0.102.0/24"]
single_nat_gateway_for_prototyping = false # Production: One NAT Gateway per AZ for full redundancy

# EKS Configuration - Production-ready with multiple node groups
eks_cluster_version = "1.28"

# EKS Security Configuration - Restrict public endpoint access
# PRODUCTION: Define specific IP ranges for authorized access
eks_public_access_cidrs = [
  # "YOUR_CORPORATE_NETWORK/24",    # Office network
  # "YOUR_VPN_GATEWAY/32",          # VPN gateway
  # "YOUR_CI_CD_RUNNER_IP/32"       # GitHub Actions runners (if static)
  "0.0.0.0/0" # FIXME: Replace with your authorized IP ranges
]
eks_node_groups = {
  general = {
    instance_types = ["m5.large", "m5.xlarge"]
    scaling_config = {
      desired_size = 3
      max_size     = 10
      min_size     = 2
    }
    capacity_type = "ON_DEMAND"
    ami_type      = "AL2_x86_64"
    disk_size     = 50
    labels = {
      "node.kubernetes.io/workload" = "general"
    }
    taints = []
  }
  compute_optimized = {
    instance_types = ["c5.2xlarge", "c5.4xlarge"]
    scaling_config = {
      desired_size = 2
      max_size     = 20
      min_size     = 0
    }
    capacity_type = "SPOT"
    ami_type      = "AL2_x86_64"
    disk_size     = 100
    labels = {
      "node.kubernetes.io/workload" = "compute"
    }
    taints = [
      {
        key    = "compute-workload"
        value  = "true"
        effect = "NO_SCHEDULE"
      }
    ]
  }
  memory_optimized = {
    instance_types = ["r5.xlarge", "r5.2xlarge"]
    scaling_config = {
      desired_size = 1
      max_size     = 5
      min_size     = 0
    }
    capacity_type = "ON_DEMAND"
    ami_type      = "AL2_x86_64"
    disk_size     = 100
    labels = {
      "node.kubernetes.io/workload" = "memory-intensive"
    }
    taints = [
      {
        key    = "memory-workload"
        value  = "true"
        effect = "NO_SCHEDULE"
      }
    ]
  }
}

# RDS Configuration - Production-ready with high availability
rds_instance_class               = "db.r5.xlarge"
rds_allocated_storage            = 500
rds_max_allocated_storage        = 2000
rds_storage_type                 = "gp3"
rds_engine_version               = "15.13"
rds_database_name                = "astro_catalog"
rds_username                     = "astro_user"
rds_backup_retention_period      = 30
rds_multi_az                     = true
rds_storage_encrypted            = true
rds_performance_insights_enabled = true
rds_monitoring_interval          = 60

# S3 Configuration - Full lifecycle management for production
s3_buckets = {
  raw-data = {
    versioning_enabled = true
    lifecycle_rules = [
      {
        id     = "raw_data_lifecycle"
        status = "Enabled"
        transitions = [
          {
            days          = 30
            storage_class = "STANDARD_IA"
          },
          {
            days          = 90
            storage_class = "GLACIER"
          },
          {
            days          = 365
            storage_class = "DEEP_ARCHIVE"
          }
        ]
        expiration = {
          days = 2555 # 7 years
        }
      }
    ]
  }
  processed-data = {
    versioning_enabled = true
    lifecycle_rules = [
      {
        id     = "processed_data_lifecycle"
        status = "Enabled"
        transitions = [
          {
            days          = 60
            storage_class = "STANDARD_IA"
          },
          {
            days          = 180
            storage_class = "GLACIER"
          },
          {
            days          = 730
            storage_class = "DEEP_ARCHIVE"
          }
        ]
        expiration = {
          days = 3650 # 10 years
        }
      }
    ]
  }
  archive = {
    versioning_enabled = true
    lifecycle_rules = [
      {
        id     = "archive_lifecycle"
        status = "Enabled"
        transitions = [
          {
            days          = 1
            storage_class = "GLACIER"
          },
          {
            days          = 365
            storage_class = "DEEP_ARCHIVE"
          }
        ]
        expiration = {
          days = 10950 # 30 years
        }
      }
    ]
  }
}

# Monitoring Configuration - Full observability
cloudwatch_log_retention_days = 90
enable_container_insights     = true

# Airflow Configuration
airflow_namespace     = "airflow"
airflow_chart_version = "1.11.0"

# Auto Scaling Configuration
enable_cluster_autoscaler        = true
enable_horizontal_pod_autoscaler = true

# Security Configuration - Full encryption
enable_secrets_manager = true
enable_kms_encryption  = true

# Cost Optimization
enable_spot_instances    = true
enable_scheduled_scaling = true

# Additional Tags
additional_tags = {
}