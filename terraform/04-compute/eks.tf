# EKS Cluster
resource "aws_eks_cluster" "main" {
  name     = "${var.project_name}-eks"
  role_arn = aws_iam_role.eks_cluster.arn
  version  = var.eks_cluster_version

  vpc_config {
    subnet_ids              = concat(data.terraform_remote_state.foundation.outputs.private_subnet_ids, data.terraform_remote_state.foundation.outputs.public_subnet_ids)
    endpoint_private_access = true
    endpoint_public_access  = true
    public_access_cidrs     = ["0.0.0.0/0"]
    security_group_ids      = [data.terraform_remote_state.foundation.outputs.eks_cluster_security_group_id]
  }

  encryption_config {
    resources = ["secrets"]
    provider {
      key_arn = var.enable_kms_encryption ? aws_kms_key.eks[0].arn : null
    }
  }

  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]

  depends_on = [
    aws_iam_role_policy_attachment.eks_cluster_policy,
    aws_iam_role_policy_attachment.eks_vpc_resource_controller,
    aws_cloudwatch_log_group.eks_cluster,
  ]

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-eks-cluster"
  })
}

# EKS Cluster IAM Role
resource "aws_iam_role" "eks_cluster" {
  name = "${var.project_name}-eks-cluster-role"

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
    Name = "${var.project_name}-eks-cluster-role"
  })
}

# EKS Cluster IAM Role Policy Attachments
resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks_cluster.name
}

resource "aws_iam_role_policy_attachment" "eks_vpc_resource_controller" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSVPCResourceController"
  role       = aws_iam_role.eks_cluster.name
}

# EKS Node Groups
resource "aws_eks_node_group" "main" {
  for_each = var.eks_node_groups

  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.project_name}-${each.key}"
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
    Name        = "${var.project_name}-${each.key}-node-group"
    Environment = var.environment
    NodeGroup   = each.key
  })
}

# EKS Node Group IAM Role
resource "aws_iam_role" "eks_nodes" {
  name = "${var.project_name}-eks-nodes-role"

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
    Name = "${var.project_name}-eks-nodes-role"
  })
}

# EKS Node Group IAM Role Policy Attachments
resource "aws_iam_role_policy_attachment" "eks_worker_node_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
  role       = aws_iam_role.eks_nodes.name
}

resource "aws_iam_role_policy_attachment" "eks_cni_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
  role       = aws_iam_role.eks_nodes.name
}

resource "aws_iam_role_policy_attachment" "eks_container_registry_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
  role       = aws_iam_role.eks_nodes.name
}

# Additional IAM policy for S3 access
resource "aws_iam_policy" "eks_s3_access" {
  name        = "${var.project_name}-eks-s3-access"
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
          "arn:aws:s3:::${var.project_name}-*",
          "arn:aws:s3:::${var.project_name}-*/*"
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "eks_s3_access" {
  policy_arn = aws_iam_policy.eks_s3_access.arn
  role       = aws_iam_role.eks_nodes.name
}

# OIDC Provider for EKS
resource "aws_iam_openid_connect_provider" "eks" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.main.identity[0].oidc[0].issuer

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-eks-oidc"
  })
}

# CloudWatch Log Group for EKS
resource "aws_cloudwatch_log_group" "eks_cluster" {
  name              = "/aws/eks/${var.project_name}-eks/cluster"
  retention_in_days = var.cloudwatch_log_retention_days

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-eks-cluster-logs"
  })
}

# KMS Key for EKS encryption
resource "aws_kms_key" "eks" {
  count = var.enable_kms_encryption ? 1 : 0

  description             = "KMS key for EKS cluster encryption"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-eks-kms-key"
  })
}

resource "aws_kms_alias" "eks" {
  count = var.enable_kms_encryption ? 1 : 0

  name          = "alias/${var.project_name}-eks"
  target_key_id = aws_kms_key.eks[0].key_id
}