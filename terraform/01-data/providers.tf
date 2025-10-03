# Layer 2: Data - S3 Storage Infrastructure
# This layer provides the data lake and event-driven processing infrastructure

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.4"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5"
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
      Layer              = "02-data"
    }
  }
}