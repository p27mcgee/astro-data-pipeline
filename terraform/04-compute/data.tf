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