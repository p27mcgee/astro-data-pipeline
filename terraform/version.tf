# Astronomical Data Pipeline - Terraform Infrastructure Version
#
# This file contains the canonical version for the Terraform infrastructure.
# ALL infrastructure layers will reference this version for consistency.
#
# Version Format: MAJOR.MINOR.PATCH (Semantic Versioning)
# - MAJOR: Breaking infrastructure changes, incompatible deployments
# - MINOR: New infrastructure features, backward compatible
# - PATCH: Infrastructure bug fixes, configuration updates
#
# IMPORTANT: This version MUST be incremented for every infrastructure change PR merge to main.
# The CI/CD pipeline will enforce this requirement.

locals {
  # Infrastructure version - increment for every infrastructure change
  infrastructure_version = "1.0.1"

  # Project metadata
  project_metadata = {
    name         = "astro-data-pipeline"
    description  = "Astronomical Data Processing Pipeline Infrastructure"
    version      = local.infrastructure_version
    created_by   = "terraform"
    managed_by   = "github-actions"
    repository   = "astro-data-pipeline"
  }
}

# Output the infrastructure version for use in workflows and other modules
output "infrastructure_version" {
  description = "Current infrastructure version"
  value       = local.infrastructure_version
}

output "project_metadata" {
  description = "Project metadata including version"
  value       = local.project_metadata
}