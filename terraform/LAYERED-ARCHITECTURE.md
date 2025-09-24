# Layered Terraform Architecture

This project implements a **5-layer infrastructure architecture** following enterprise best practices for infrastructure
management and deployment.

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     Layer 5: Monitoring                        │
│              CloudWatch, Alarms, Dashboards                    │
└─────────────────────────────────────────────────────────────────┘
                                ↑
┌─────────────────────────────────────────────────────────────────┐
│                     Layer 4: Compute                           │
│                  EKS Cluster, Node Groups                      │
└─────────────────────────────────────────────────────────────────┘
                                ↑
┌─────────────────────────────────────────────────────────────────┐
│                    Layer 3: Database                           │
│               RDS PostgreSQL, Parameter Groups                 │
└─────────────────────────────────────────────────────────────────┘
                                ↑
┌─────────────────────────────────────────────────────────────────┐
│                      Layer 2: Data                             │
│              S3 Buckets, Lambda, Event Processing              │
└─────────────────────────────────────────────────────────────────┘
                                ↑
┌─────────────────────────────────────────────────────────────────┐
│                   Layer 1: Foundation                          │
│              VPC, Subnets, Security Groups, NAT                │
└─────────────────────────────────────────────────────────────────┘
```

## 📁 Directory Structure

```bash
terraform/
├── 01-foundation/          # Layer 1: Network & Security Foundation
│   ├── providers.tf        # Provider configuration
│   ├── variables.tf        # Layer-specific variables
│   ├── main.tf            # VPC, subnets, NAT gateways
│   ├── security-groups.tf  # Security groups for all layers
│   └── outputs.tf          # Exports for dependent layers
├── 02-data/               # Layer 2: Data Lake & Event Processing
│   ├── providers.tf       # Provider configuration
│   ├── data.tf           # Remote state imports
│   ├── variables.tf      # S3 and Lambda configuration
│   ├── s3.tf             # S3 buckets and lifecycle
│   ├── lambda.tf         # Event processing functions
│   ├── lambda/           # Lambda function source code
│   │   └── s3_trigger.py # S3 event handler for FITS files
│   └── outputs.tf        # Data layer exports
├── 03-database/          # Layer 3: Database Services
│   ├── providers.tf      # Provider configuration
│   ├── data.tf          # Remote state imports
│   ├── variables.tf     # Database configuration
│   ├── rds.tf           # PostgreSQL with PostGIS
│   └── outputs.tf       # Database connection info
├── 04-compute/          # Layer 4: Kubernetes & Compute
│   ├── providers.tf     # Provider configuration
│   ├── data.tf         # Remote state imports
│   ├── variables.tf    # EKS configuration
│   ├── eks.tf          # EKS cluster and node groups
│   └── outputs.tf      # Cluster connection info
├── 05-monitoring/      # Layer 5: Observability & Alerts
│   ├── providers.tf    # Provider configuration
│   ├── data.tf        # Remote state imports
│   ├── variables.tf   # Monitoring configuration
│   ├── monitoring.tf  # CloudWatch, alarms
│   └── outputs.tf     # Monitoring endpoints
├── ci/                # CI/CD automation scripts
│   ├── deploy-layers.sh   # Main deployment & validation script
│   └── validate-layers.sh # Convenience wrapper for quick validation
├── archive/           # Archived monolithic configuration
│   └── monolithic-backup/ # Original single-file terraform config
├── staging.tfvars.example  # Staging environment config
├── prod.tfvars.example     # Production environment config
└── LAYERED-ARCHITECTURE.md # This documentation
```

## 🎯 Layer Definitions

### Layer 1: Foundation (VPC, Networking)

**Purpose**: Foundational network infrastructure
**Lifecycle**: Long-lived, rarely changes
**Components**:

- VPC with DNS support
- Public, Private, Database subnets
- Internet Gateway, NAT Gateways
- Route Tables and associations
- Security Groups for all tiers
- VPC Endpoints for S3

**Deployment Time**: ~3-5 minutes
**Dependencies**: None (base layer)

### Layer 2: Data (S3, Lambda)

**Purpose**: Data lake and event-driven processing
**Lifecycle**: Semi-persistent (data retention policies)
**Components**:

- S3 buckets with lifecycle management
- Lambda functions for event processing
- S3 event notifications
- IAM policies for data access

**Deployment Time**: ~2-3 minutes
**Dependencies**: Layer 1 (VPC for Lambda)

### Layer 3: Database (RDS)

**Purpose**: Persistent data storage
**Lifecycle**: Long-lived, contains stateful data
**Components**:

- RDS PostgreSQL with PostGIS
- Database subnet groups
- Parameter groups
- Secrets Manager integration

**Deployment Time**: ~8-12 minutes (RDS creation)
**Dependencies**: Layer 1 (subnets, security groups)

### Layer 4: Compute (EKS)

**Purpose**: Container orchestration platform
**Lifecycle**: Dynamic, frequently updated
**Components**:

- EKS cluster with managed control plane
- Node groups (general, compute, memory)
- EKS add-ons (VPC CNI, CoreDNS, etc.)
- OIDC provider for service accounts
- IAM roles and policies

**Deployment Time**: ~10-15 minutes (EKS creation)
**Dependencies**: Layer 1 (subnets, security groups), Layer 2 (S3 access)

### Layer 5: Monitoring (CloudWatch)

**Purpose**: Observability and alerting
**Lifecycle**: Operational, frequently tuned
**Components**:

- CloudWatch log groups
- Custom metrics and alarms
- Dashboards
- SNS topics for notifications

**Deployment Time**: ~2-3 minutes
**Dependencies**: All previous layers

## 🚀 Deployment Guide

### Max Layer Configuration

The deployment supports configurable layer limits for flexible infrastructure rollouts:

#### Deployment Options:

- **Manual GitHub Actions**: Select layers 1-5 via workflow_dispatch UI (recommended for production)
- **Local Development**: Use `./ci/deploy-layers.sh <tfvars> <action> [max_layer]` (recommended for development)

#### Layer-by-Layer Strategy:

- **Layer 1 only**: Just networking foundation
- **Layer 1-2**: Foundation + data lake (safe for most changes)
- **Layer 1-3**: Add database (staging/development)
- **Layer 1-4**: Add compute resources (full application stack)
- **Layer 1-5**: Full monitoring (production-ready)

### Quick Start

```bash
# 1. Copy example configuration
cp staging.tfvars.example staging.tfvars

# 2. Edit configuration (optional)
nano staging.tfvars

# 3. Deploy all layers
./ci/deploy-layers.sh staging.tfvars plan
./ci/deploy-layers.sh staging.tfvars apply
```

### Advanced Deployment

#### Deploy Single Layer

```bash
cd 01-foundation
terraform init
terraform plan -var-file="../staging.tfvars"
terraform apply -var-file="../staging.tfvars"
```

#### Deploy Specific Layers

```bash
# Deploy foundation and data only
for layer in 01-foundation 02-data; do
  cd $layer
  terraform init
  terraform apply -var-file="../staging.tfvars" -auto-approve
  cd ..
done
```

#### Validate Layers

```bash
# Quick validation (no tfvars required)
./ci/validate-layers.sh        # All layers
./ci/validate-layers.sh 2      # First 2 layers only

# Full validation with variables
./ci/deploy-layers.sh staging.tfvars validate
./ci/deploy-layers.sh staging.tfvars validate 3  # First 3 layers
```

## 🔄 State Management

### Local State (Default)

Each layer maintains its own `terraform.tfstate` file:

- `01-foundation/terraform.tfstate`
- `02-data/terraform.tfstate`
- etc.

### Remote State (Recommended for Production)

Configure S3 backend in each layer's `providers.tf`:

```hcl
terraform {
  backend "s3" {
    bucket         = "your-terraform-state-bucket"
    key            = "layers/01-foundation/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "terraform-state-locks"
    encrypt        = true
  }
}
```

### Cross-Layer References

Layers reference each other using `terraform_remote_state`:

```hcl
data "terraform_remote_state" "foundation" {
  backend = "local"  # or "s3" for remote
  config = {
    path = "../01-foundation/terraform.tfstate"
  }
}

# Use foundation outputs
resource "aws_db_subnet_group" "main" {
  subnet_ids = data.terraform_remote_state.foundation.outputs.database_subnet_ids
}
```

## 💥 Destruction (Reverse Order)

**CRITICAL**: Always destroy layers in reverse order to avoid dependency conflicts.

### Safe Destruction Script

```bash
#!/bin/bash
# destroy-layers.sh
LAYERS=("05-monitoring" "04-compute" "03-database" "02-data" "01-foundation")

for layer in "${LAYERS[@]}"; do
  echo "Destroying $layer..."
  cd $layer
  terraform destroy -var-file="../staging.tfvars" -auto-approve
  cd ..
done
```

### Manual Destruction

```bash
# Reverse order destruction
cd 05-monitoring && terraform destroy -var-file="../staging.tfvars"
cd ../04-compute && terraform destroy -var-file="../staging.tfvars"
cd ../03-database && terraform destroy -var-file="../staging.tfvars"
cd ../02-data && terraform destroy -var-file="../staging.tfvars"
cd ../01-foundation && terraform destroy -var-file="../staging.tfvars"
```

## 🎯 Benefits of This Architecture

### Risk Management

- **Blast Radius Containment**: EKS failure doesn't affect VPC
- **Data Protection**: S3/RDS survive compute layer changes
- **Partial Rollbacks**: Rollback only the problematic layer

### Team Collaboration

- **Separation of Duties**: Network team vs Application team
- **Independent Development**: Database changes don't block app deploys
- **Parallel Work**: Teams can work on different layers simultaneously

### Operational Flexibility

- **Environment Management**: Different layer combinations per environment
- **Cost Optimization**: Destroy expensive compute, keep cheap storage
- **Testing**: Spin up Layer 4-5 against existing Layer 1-3

### CI/CD Integration

- **Pipeline Stages**: Each layer becomes a pipeline stage
- **Conditional Deployment**: Deploy only changed layers
- **Environment Promotion**: Consistent layer deployment across environments

## 🛠️ Customization

### Adding New Layers

1. Create new directory (e.g., `06-applications/`)
2. Add `providers.tf`, `variables.tf`, `main.tf`, `outputs.tf`
3. Update `ci/deploy-layers.sh` with new layer
4. Document dependencies

### Environment-Specific Layers

```bash
# Different layer combinations per environment
staging: 01-foundation + 02-data + 04-compute
production: 01-foundation + 02-data + 03-database + 04-compute + 05-monitoring
development: 01-foundation + 02-data + 04-compute (minimal setup)
```

### Layer Dependencies

Update `data.tf` in each layer to import required remote states:

```hcl
# In 04-compute/data.tf
data "terraform_remote_state" "foundation" {
  backend = "local"
  config = { path = "../01-foundation/terraform.tfstate" }
}

data "terraform_remote_state" "data" {
  backend = "local"
  config = { path = "../02-data/terraform.tfstate" }
}
```

## 🎓 Best Practices

### Development Workflow

1. **Test layers individually** before full deployment
2. **Use separate state files** for isolation
3. **Plan before apply** to understand changes
4. **Tag resources** with layer information
5. **Document dependencies** between layers

### Production Considerations

1. **Use remote state** with locking (S3 + DynamoDB)
2. **Implement approval gates** between layers
3. **Automate validation** before deployment
4. **Monitor drift** in each layer independently
5. **Backup state files** regularly

### Troubleshooting

```bash
# Check layer state
cd 01-foundation && terraform show

# Validate layer configuration
cd 02-data && terraform validate

# Import existing resources
cd 03-database && terraform import aws_db_instance.main db-identifier

# Force unlock if needed
terraform force-unlock LOCK_ID
```

This layered architecture provides enterprise-grade infrastructure management with clear separation of concerns,
improved reliability, and operational flexibility.