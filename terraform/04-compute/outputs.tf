# Compute Layer Outputs
# These outputs are consumed by other layers and applications

# EKS Cluster Outputs
output "eks_cluster_id" {
  description = "EKS cluster ID"
  value       = aws_eks_cluster.main.id
}

output "eks_cluster_arn" {
  description = "EKS cluster ARN"
  value       = aws_eks_cluster.main.arn
}

output "eks_cluster_name" {
  description = "EKS cluster name"
  value       = aws_eks_cluster.main.name
}

output "eks_cluster_endpoint" {
  description = "EKS cluster API server endpoint"
  value       = aws_eks_cluster.main.endpoint
}

output "eks_cluster_version" {
  description = "EKS cluster Kubernetes version"
  value       = aws_eks_cluster.main.version
}

output "eks_cluster_platform_version" {
  description = "EKS cluster platform version"
  value       = aws_eks_cluster.main.platform_version
}

output "eks_cluster_status" {
  description = "EKS cluster status"
  value       = aws_eks_cluster.main.status
}

# Node Group Outputs
output "eks_node_groups" {
  description = "EKS node group information"
  value = {
    for k, v in aws_eks_node_group.main : k => {
      arn            = v.arn
      status         = v.status
      capacity_type  = v.capacity_type
      instance_types = v.instance_types
      scaling_config = v.scaling_config
    }
  }
}

# IAM Role Outputs
output "eks_cluster_role_arn" {
  description = "IAM role ARN of the EKS cluster"
  value       = aws_iam_role.eks_cluster.arn
}

output "eks_node_group_role_arn" {
  description = "IAM role ARN of the EKS node group"
  value       = aws_iam_role.eks_nodes.arn
}

# Certificate Authority
output "eks_cluster_certificate_authority_data" {
  description = "Base64 encoded certificate data required to communicate with the cluster"
  value       = aws_eks_cluster.main.certificate_authority[0].data
}

# Connection Information
output "kubectl_config_command" {
  description = "Command to configure kubectl for the EKS cluster"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${aws_eks_cluster.main.name}"
}

# CloudWatch Log Group
output "eks_cloudwatch_log_group_name" {
  description = "Name of the EKS CloudWatch log group"
  value       = aws_cloudwatch_log_group.eks_cluster.name
}

# KMS Key (if enabled)
output "eks_kms_key_arn" {
  description = "ARN of the KMS key used for EKS encryption"
  value       = var.enable_kms_encryption ? aws_kms_key.eks[0].arn : null
}

output "eks_kms_key_id" {
  description = "ID of the KMS key used for EKS encryption"
  value       = var.enable_kms_encryption ? aws_kms_key.eks[0].key_id : null
}