# Data sources to import previous layer outputs

data "terraform_remote_state" "data" {
  backend = "local"
  config = {
    path = "../02-data/terraform.tfstate"
  }
}

data "terraform_remote_state" "database" {
  backend = "local"
  config = {
    path = "../03-database/terraform.tfstate"
  }
}

data "terraform_remote_state" "compute" {
  backend = "local"
  config = {
    path = "../04-compute/terraform.tfstate"
  }
}

