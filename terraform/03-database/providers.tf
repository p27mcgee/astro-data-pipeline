# Layer 3: Database - RDS PostgreSQL Infrastructure
# This layer provides the persistent database services

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
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
      Project     = "astro-data-pipeline"
      Environment = var.environment
      ManagedBy   = "terraform"
      Layer       = "03-database"
      Owner       = "stsci-demo"
    }
  }
}