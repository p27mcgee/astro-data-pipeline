# Max Layer Configuration Guide

## Overview

The Terraform Infrastructure workflow supports manual-only deployment with configurable layer limits for controlled
infrastructure rollouts.

## Configuration Methods

### 1. Manual Workflow Dispatch (Primary Method)

Use the GitHub Actions UI to select max_layer (1-5) when manually triggering the workflow.

**Steps:**

1. Go to GitHub Actions → Terraform Infrastructure
2. Click "Run workflow"
3. Select desired max_layer from dropdown (1-5)
4. Choose environment (staging/production)
5. Choose action (validate/plan)
6. Click "Run workflow"

### 2. Local Development

```bash
# Deploy specific layers locally
./ci/deploy-layers.sh staging.tfvars plan 3    # Layers 1-3
./ci/deploy-layers.sh staging.tfvars apply 1   # Layer 1 only
./ci/validate-layers.sh 2                      # Validate layers 1-2
```

## Layer Strategies

| Max Layer | Use Case                         | Resources Deployed             |
|-----------|----------------------------------|--------------------------------|
| 1         | Network foundation only          | VPC, subnets, security groups  |
| 2         | Foundation + Data (safe default) | + S3 buckets, Lambda functions |
| 3         | Add database                     | + RDS PostgreSQL               |
| 4         | Full application stack           | + EKS cluster, node groups     |
| 5         | Production ready                 | + CloudWatch monitoring        |

## Recommendations

- **Development/Testing**: Use max_layer 1-2 for faster iterations
- **Staging Environment**: Use max_layer 3-4 for application testing
- **Production Environment**: Use max_layer 5 for full monitoring
- **Manual Control**: Always review layer selection before deployment

## Manual Workflow Benefits

- **Controlled Deployment**: Prevents accidental infrastructure changes
- **Cost Management**: Deploy only needed layers to control AWS costs
- **Risk Mitigation**: Explicit approval required for each deployment
- **Layer Testing**: Test individual layers before full deployment