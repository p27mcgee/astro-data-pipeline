# Import foundation layer outputs for VPC and security group configuration
data "terraform_remote_state" "foundation" {
  backend = "local"
  config = {
    path = "../01-foundation/terraform.tfstate"
  }
}

# Retrieve TLS certificate for EKS OIDC provider configuration
data "tls_certificate" "eks" {
  url = aws_eks_cluster.main.identity[0].oidc[0].issuer
}