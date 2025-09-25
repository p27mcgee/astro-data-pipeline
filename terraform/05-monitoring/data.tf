# Import data layer outputs for S3 and Lambda monitoring
data "terraform_remote_state" "data" {
  backend = "local"
  config = {
    path = "../02-data/terraform.tfstate"
  }
}

# Import database layer outputs for RDS monitoring
data "terraform_remote_state" "database" {
  backend = "local"
  config = {
    path = "../03-database/terraform.tfstate"
  }
}

# Import compute layer outputs for EKS monitoring
data "terraform_remote_state" "compute" {
  backend = "local"
  config = {
    path = "../04-compute/terraform.tfstate"
  }
}

