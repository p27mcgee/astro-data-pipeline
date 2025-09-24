# Security Groups for different tiers
# These are foundational and used by other layers

# EKS Cluster Security Group
resource "aws_security_group" "eks_cluster" {
  name_prefix = "${var.project_name}-eks-cluster-"
  description = "Security group for EKS cluster control plane communication"
  vpc_id      = aws_vpc.main.id

  # EKS cluster control plane requires permissive egress for:
  # - API server communication with worker nodes
  # - AWS API calls for cluster management
  # - Container registry access for system pods
  egress {
    description = "Allow all outbound traffic for EKS cluster communication"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-eks-cluster-sg"
  })
}

# EKS Node Security Group
resource "aws_security_group" "eks_nodes" {
  name_prefix = "${var.project_name}-eks-nodes-"
  description = "Security group for EKS worker nodes and pod-to-pod communication"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "Allow all TCP traffic between EKS nodes for pod-to-pod communication"
    from_port = 0
    to_port   = 65535
    protocol  = "tcp"
    self      = true
  }

  # EKS worker nodes require permissive egress for:
  # - Container image pulls from ECR and public registries
  # - AWS API calls for node registration and operations
  # - Internet access for application dependencies
  # - Communication with cluster control plane
  egress {
    description = "Allow all outbound traffic for EKS nodes"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-eks-nodes-sg"
  })
}

# RDS Security Group
resource "aws_security_group" "rds" {
  name_prefix = "${var.project_name}-rds-"
  description = "Security group for PostgreSQL RDS database access from EKS nodes"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Allow PostgreSQL access from EKS nodes"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }

  # Note: RDS instances typically don't need outbound internet access
  # This rule is kept permissive to avoid RDS subnet routing issues
  # In production, consider removing this rule entirely
  egress {
    description = "Allow outbound traffic (consider restricting in production)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-rds-sg"
  })
}