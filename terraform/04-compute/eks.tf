# Kubernetes cluster for running astronomical data processing workloads
resource "aws_eks_cluster" "main" {
  name     = "${var.project_name}-${var.environment}-eks"
  role_arn = aws_iam_role.eks_cluster.arn
  version  = var.eks_cluster_version

  vpc_config {
    subnet_ids              = concat(data.terraform_remote_state.foundation.outputs.private_subnet_ids, data.terraform_remote_state.foundation.outputs.public_subnet_ids)
    endpoint_private_access = true
    endpoint_public_access  = true
    public_access_cidrs     = var.eks_public_access_cidrs
    security_group_ids      = [data.terraform_remote_state.foundation.outputs.eks_cluster_security_group_id]
  }

  dynamic "encryption_config" {
    for_each = var.enable_kms_encryption ? [1] : []
    content {
      resources = ["secrets"]
      provider {
        key_arn = aws_kms_key.eks[0].arn
      }
    }
  }

  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]

  depends_on = [
    aws_iam_role_policy_attachment.eks_cluster_policy,
    aws_iam_role_policy_attachment.eks_vpc_resource_controller,
    aws_cloudwatch_log_group.eks_cluster,
  ]

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-eks-cluster"
  })
}

# IAM service role allowing EKS to manage cluster resources
resource "aws_iam_role" "eks_cluster" {
  name = "${var.project_name}-${var.environment}-eks-cluster-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "eks.amazonaws.com"
        }
      }
    ]
  })

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-eks-cluster-role"
  })
}

# Attach AWS managed policy for EKS cluster management
resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks_cluster.name
}

# Attach policy for EKS to manage VPC networking resources
resource "aws_iam_role_policy_attachment" "eks_vpc_resource_controller" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSVPCResourceController"
  role       = aws_iam_role.eks_cluster.name
}

# Managed worker node groups for running containerized applications
resource "aws_eks_node_group" "main" {
  for_each = var.eks_node_groups

  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.project_name}-${var.environment}-${each.key}"
  node_role_arn   = aws_iam_role.eks_nodes.arn
  subnet_ids      = data.terraform_remote_state.foundation.outputs.private_subnet_ids

  capacity_type  = each.value.capacity_type
  instance_types = each.value.instance_types
  ami_type       = each.value.ami_type
  disk_size      = each.value.disk_size

  scaling_config {
    desired_size = each.value.scaling_config.desired_size
    max_size     = each.value.scaling_config.max_size
    min_size     = each.value.scaling_config.min_size
  }

  update_config {
    max_unavailable_percentage = 25
  }

  labels = each.value.labels

  dynamic "taint" {
    for_each = each.value.taints
    content {
      key    = taint.value.key
      value  = taint.value.value
      effect = taint.value.effect
    }
  }

  # Ensure proper ordering of resource creation
  depends_on = [
    aws_iam_role_policy_attachment.eks_worker_node_policy,
    aws_iam_role_policy_attachment.eks_cni_policy,
    aws_iam_role_policy_attachment.eks_container_registry_policy,
  ]

  tags = merge(var.additional_tags, {
    Name        = "${var.project_name}-${var.environment}-${each.key}-node-group"
    Environment = var.environment
    NodeGroup   = each.key
  })
}

# IAM role for EKS worker nodes to join cluster and access AWS services
resource "aws_iam_role" "eks_nodes" {
  name = "${var.project_name}-${var.environment}-eks-nodes-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-eks-nodes-role"
  })
}

# Attach AWS managed policy for EKS worker node permissions
resource "aws_iam_role_policy_attachment" "eks_worker_node_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
  role       = aws_iam_role.eks_nodes.name
}

# Attach policy for Kubernetes pod networking (CNI)
resource "aws_iam_role_policy_attachment" "eks_cni_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
  role       = aws_iam_role.eks_nodes.name
}

# Attach policy for pulling container images from ECR
resource "aws_iam_role_policy_attachment" "eks_container_registry_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
  role       = aws_iam_role.eks_nodes.name
}

# Custom IAM policy granting EKS nodes access to data lake S3 buckets
resource "aws_iam_policy" "eks_s3_access" {
  name        = "${var.project_name}-${var.environment}-eks-s3-access"
  description = "IAM policy for EKS nodes to access S3 buckets"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          "arn:aws:s3:::${var.project_name}-${var.environment}-*",
          "arn:aws:s3:::${var.project_name}-${var.environment}-*/*"
        ]
      }
    ]
  })
}

# Attach custom S3 access policy to worker nodes
resource "aws_iam_role_policy_attachment" "eks_s3_access" {
  policy_arn = aws_iam_policy.eks_s3_access.arn
  role       = aws_iam_role.eks_nodes.name
}

# CloudWatch log group for EKS control plane logs with KMS encryption
resource "aws_cloudwatch_log_group" "eks_cluster" {
  name              = "/aws/eks/${var.project_name}-${var.environment}-eks/cluster"
  retention_in_days = var.cloudwatch_log_retention_days
  kms_key_id        = var.enable_kms_encryption ? aws_kms_key.eks[0].arn : null

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-eks-cluster-logs"
  })
}

# Customer-managed KMS key for EKS secrets and CloudWatch logs encryption
resource "aws_kms_key" "eks" {
  count = var.enable_kms_encryption ? 1 : 0

  description             = "KMS key for EKS cluster and CloudWatch logs encryption"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  # KMS key policy allowing EKS and CloudWatch services
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "Enable IAM User Permissions"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        }
        Action   = "kms:*"
        Resource = "*"
      },
      {
        Sid    = "Allow CloudWatch Logs service"
        Effect = "Allow"
        Principal = {
          Service = "logs.${data.aws_region.current.name}.amazonaws.com"
        }
        Action = [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:DescribeKey"
        ]
        Resource = "*"
        Condition = {
          ArnEquals = {
            "kms:EncryptionContext:aws:logs:arn" = "arn:aws:logs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:log-group:/aws/eks/${var.project_name}-${var.environment}-eks/cluster"
          }
        }
      }
    ]
  })

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-eks-kms-key"
  })
}

# Human-readable alias for the EKS encryption KMS key
resource "aws_kms_alias" "eks" {
  count = var.enable_kms_encryption ? 1 : 0

  name          = "alias/${var.project_name}-${var.environment}-eks"
  target_key_id = aws_kms_key.eks[0].key_id
}

# Data sources for KMS key policy
data "aws_caller_identity" "current" {}
data "aws_region" "current" {}