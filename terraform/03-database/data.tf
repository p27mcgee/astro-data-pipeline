# Data sources to import foundation layer outputs
data "terraform_remote_state" "foundation" {
  backend = "local"
  config = {
    path = "../01-foundation/terraform.tfstate"
  }
}

