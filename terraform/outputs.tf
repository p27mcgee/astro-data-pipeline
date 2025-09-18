# VPC Outputs
output "vpc_id" {
  description = "ID of the VPC"
  value       = aws_vpc.main.id
}

output "vpc_cidr_block" {
  description = "CIDR block of the VPC"
  value       = aws_vpc.main.cidr_block
}

output "public_subnet_ids" {
  description = "IDs of the public subnets"
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "IDs of the private subnets"
  value       = aws_subnet.private[*].id
}

output "database_subnet_ids" {
  description = "IDs of the database subnets"
  value       = aws_subnet.database[*].id
}

# EKS Outputs
output "eks_cluster_id" {
  description = "EKS cluster ID"
  value       = aws_eks_cluster.main.id
}

output "eks_cluster_arn" {
  description = "EKS cluster ARN"
  value       = aws_eks_cluster.main.arn
}

output "eks_cluster_endpoint" {
  description = "Endpoint for EKS control plane"
  value       = aws_eks_cluster.main.endpoint
}

output "eks_cluster_security_group_id" {
  description = "Security group ID attached to the EKS cluster"
  value       = aws_eks_cluster.main.vpc_config[0].cluster_security_group_id
}

output "eks_cluster_oidc_issuer_url" {
  description = "The URL on the EKS cluster OIDC Issuer"
  value       = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

output "eks_cluster_certificate_authority_data" {
  description = "Base64 encoded certificate data required to communicate with the cluster"
  value       = aws_eks_cluster.main.certificate_authority[0].data
}

output "eks_node_groups" {
  description = "EKS node groups"
  value = {
    for k, v in aws_eks_node_group.main : k => {
      arn            = v.arn
      status         = v.status
      capacity_type  = v.capacity_type
      instance_types = v.instance_types
    }
  }
}

# RDS Outputs
output "rds_endpoint" {
  description = "RDS instance endpoint"
  value       = aws_db_instance.main.endpoint
  sensitive   = true
}

output "rds_port" {
  description = "RDS instance port"
  value       = aws_db_instance.main.port
}

output "rds_database_name" {
  description = "RDS database name"
  value       = aws_db_instance.main.db_name
}

output "rds_username" {
  description = "RDS database username"
  value       = aws_db_instance.main.username
  sensitive   = true
}

output "rds_security_group_id" {
  description = "Security group ID for RDS"
  value       = aws_security_group.rds.id
}

output "rds_read_replica_endpoint" {
  description = "RDS read replica endpoint"
  value       = var.environment == "prod" ? aws_db_instance.read_replica[0].endpoint : null
  sensitive   = true
}

# S3 Outputs
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
output "s3_trigger_lambda_arn" {
  description = "ARN of the S3 trigger Lambda function"
  value       = aws_lambda_function.s3_trigger.arn
}

output "s3_trigger_lambda_function_name" {
  description = "Name of the S3 trigger Lambda function"
  value       = aws_lambda_function.s3_trigger.function_name
}

# Secrets Manager Outputs
output "rds_credentials_secret_arn" {
  description = "ARN of the RDS credentials secret in Secrets Manager"
  value       = var.enable_secrets_manager ? aws_secretsmanager_secret.rds_credentials[0].arn : null
}

# KMS Outputs
output "eks_kms_key_arn" {
  description = "ARN of the KMS key used for EKS encryption"
  value       = var.enable_kms_encryption ? aws_kms_key.eks[0].arn : null
}

output "rds_kms_key_arn" {
  description = "ARN of the KMS key used for RDS encryption"
  value       = var.enable_kms_encryption ? aws_kms_key.rds[0].arn : null
}

output "s3_kms_key_arn" {
  description = "ARN of the KMS key used for S3 encryption"
  value       = var.enable_kms_encryption ? aws_kms_key.s3[0].arn : null
}

# Monitoring Outputs
output "cloudwatch_log_groups" {
  description = "CloudWatch log groups created for the application"
  value = merge(
    {
      for k, v in aws_cloudwatch_log_group.application_logs : "application_${k}" => v.name
    },
    {
      for k, v in aws_cloudwatch_log_group.airflow_logs : "airflow_${k}" => v.name
    },
    {
      "eks_cluster" = aws_cloudwatch_log_group.eks_cluster.name
      "rds"         = aws_cloudwatch_log_group.rds.name
    }
  )
}

output "sns_topic_arn" {
  description = "ARN of the SNS topic for alerts"
  value       = aws_sns_topic.alerts.arn
}

output "cloudwatch_dashboards" {
  description = "CloudWatch dashboard names"
  value = {
    main     = aws_cloudwatch_dashboard.main.dashboard_name
    pipeline = aws_cloudwatch_dashboard.pipeline.dashboard_name
    s3       = aws_cloudwatch_dashboard.s3_dashboard.dashboard_name
  }
}

# IAM Outputs
output "eks_cluster_role_arn" {
  description = "ARN of the EKS cluster service role"
  value       = aws_iam_role.eks_cluster.arn
}

output "eks_node_role_arn" {
  description = "ARN of the EKS node group service role"
  value       = aws_iam_role.eks_nodes.arn
}

output "ebs_csi_driver_role_arn" {
  description = "ARN of the EBS CSI driver service role"
  value       = aws_iam_role.ebs_csi_driver.arn
}

# Terraform State Outputs (for production)
output "terraform_state_bucket_name" {
  description = "Name of the S3 bucket for Terraform state"
  value       = var.environment == "prod" ? aws_s3_bucket.terraform_state[0].id : null
}

output "terraform_state_lock_table_name" {
  description = "Name of the DynamoDB table for Terraform state locking"
  value       = var.environment == "prod" ? aws_dynamodb_table.terraform_state_lock[0].name : null
}

# Connection Information
output "kubectl_config_command" {
  description = "Command to configure kubectl for the EKS cluster"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${aws_eks_cluster.main.name}"
}

output "airflow_url" {
  description = "URL to access Airflow (after port-forward setup)"
  value       = "http://localhost:8080 (use: kubectl port-forward svc/airflow-webserver 8080:8080 -n ${var.airflow_namespace})"
}

# Resource Counts and Sizes
output "resource_summary" {
  description = "Summary of resources created"
  value = {
    vpc_subnets = {
      public   = length(aws_subnet.public)
      private  = length(aws_subnet.private)
      database = length(aws_subnet.database)
    }
    eks_node_groups    = length(aws_eks_node_group.main)
    s3_buckets         = length(aws_s3_bucket.data_buckets)
    cloudwatch_alarms  = length(aws_cloudwatch_metric_alarm.database_cpu) + length(aws_cloudwatch_metric_alarm.eks_node_cpu) + 1 # Approximate count
    rds_instance_class = aws_db_instance.main.instance_class
    environment        = var.environment
  }
}

# Quick Start Commands
output "quick_start_commands" {
  description = "Quick start commands for common operations"
  value = {
    configure_kubectl = "aws eks update-kubeconfig --region ${var.aws_region} --name ${aws_eks_cluster.main.name}"

    port_forward_airflow = "kubectl port-forward svc/airflow-webserver 8080:8080 -n ${var.airflow_namespace}"

    view_pods = "kubectl get pods --all-namespaces"

    view_logs = "kubectl logs -f deployment/image-processor -n default"

    connect_to_rds = "psql -h ${aws_db_instance.main.endpoint} -p ${aws_db_instance.main.port} -U ${aws_db_instance.main.username} -d ${aws_db_instance.main.db_name}"

    list_s3_buckets = "aws s3 ls | grep ${var.project_name}"

    view_cloudwatch_logs = "aws logs describe-log-groups --log-group-name-prefix '/aws/eks/${var.project_name}'"
  }
}