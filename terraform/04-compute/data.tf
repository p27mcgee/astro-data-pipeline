# Import foundation layer outputs for VPC and security group configuration
data "terraform_remote_state" "foundation" {
  backend   = "s3"
  workspace = terraform.workspace
  config = {
    bucket               = "astro-data-pipeline-terraform-state-staging"
    key                  = "layers/02-foundation/terraform.tfstate"
    workspace_key_prefix = "workspaces"
    region               = "us-east-1"
  }
}

# Retrieve TLS certificate for EKS OIDC provider configuration
data "tls_certificate" "eks" {
  url = aws_eks_cluster.main.identity[0].oidc[0].issuer
}