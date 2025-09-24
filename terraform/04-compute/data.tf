# Data sources to import foundation and data layer outputs
data "terraform_remote_state" "foundation" {
  backend = "local"
  config = {
    path = "../01-foundation/terraform.tfstate"
  }
}

# Data source for TLS certificate from EKS OIDC issuer
data "tls_certificate" "eks" {
  url = aws_eks_cluster.main.identity[0].oidc[0].issuer
}