# Layer 1: Foundation - VPC and Networking Infrastructure
# This layer provides the foundational network infrastructure for all other layers

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project            = "astro-data-pipeline"
      Environment        = var.environment
      Owner              = "pmcgee"
      ManagedBy          = "terraform"
      TerraformWorkspace = terraform.workspace
      Version            = "0.1"
      Layer              = "01-foundation"
    }
  }
}