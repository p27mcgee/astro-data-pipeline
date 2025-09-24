# Terraform Infrastructure

This directory contains the Terraform configuration for the Astronomical Data Processing Pipeline AWS infrastructure.

## Architecture Overview

The infrastructure consists of:
- **VPC**: Multi-AZ VPC with public, private, and database subnets
- **EKS**: Kubernetes cluster with multiple node groups for different workloads
- **RDS**: PostgreSQL database with performance optimization for astronomical data
- **S3**: Data lake with intelligent lifecycle management
- **Lambda**: Event-driven processing triggers
- **Monitoring**: Comprehensive CloudWatch monitoring and alerting

## Prerequisites

1. **AWS CLI**: Configure with appropriate permissions
2. **Terraform**: Version >= 1.0
3. **kubectl**: For EKS cluster management
4. **TFLint**: For Terraform code linting and validation (recommended)
5. **Checkov**: For security scanning and compliance checks (recommended)

```bash
# Install Terraform
brew install terraform  # macOS
# or download from https://terraform.io

# Install AWS CLI
brew install awscli     # macOS
# or download from https://aws.amazon.com/cli/

# Install kubectl
brew install kubectl    # macOS
# or download from https://kubernetes.io/docs/tasks/tools/

# Install TFLint (recommended for code quality)
brew install tflint     # macOS
# or download from https://github.com/terraform-linters/tflint

# Install Checkov (recommended for security scanning)
pip install checkov     # Python package
# or brew install checkov  # macOS with Homebrew
```

## AWS Permissions Required

The AWS user/role needs the following permissions:
- EC2: Full access for VPC, security groups, etc.
- EKS: Full access for cluster and node group management
- RDS: Full access for database management
- S3: Full access for bucket management
- Lambda: Full access for function management
- IAM: Full access for role and policy management
- CloudWatch: Full access for monitoring and logging
- KMS: Full access for encryption key management
- Secrets Manager: Full access for credential management

## Quick Start

### 1. Initialize Terraform

```bash
cd terraform
terraform init
```

### 2. Create Variable File

Copy the appropriate example file and customize:

```bash
# For staging
cp staging.tfvars.example staging.tfvars
# Edit staging.tfvars with your specific configuration

# For production
cp prod.tfvars.example prod.tfvars
# Edit prod.tfvars with your specific configuration
```

### 3. Plan the Deployment

```bash
# Staging environment
terraform plan -var-file="staging.tfvars"

# Production environment
terraform plan -var-file="prod.tfvars"
```

### 4. Deploy Infrastructure

```bash
# Staging environment
terraform apply -var-file="staging.tfvars"

# Production environment
terraform apply -var-file="prod.tfvars"
```

### 5. Configure kubectl

After deployment, configure kubectl to access the EKS cluster:

```bash
aws eks update-kubeconfig --region us-east-1 --name astro-data-pipeline-eks
```

## File Structure

```
terraform/
├── main.tf              # Core infrastructure (VPC, networking)
├── variables.tf         # Variable definitions
├── outputs.tf           # Output values
├── eks.tf              # EKS cluster configuration
├── rds.tf              # PostgreSQL database configuration
├── s3.tf               # S3 data lake configuration
├── monitoring.tf       # CloudWatch monitoring and alerting
├── lambda/             # Lambda function code
│   └── s3_trigger.py   # S3 event trigger function
├── staging.tfvars.example  # Staging environment variables
├── prod.tfvars.example # Production environment variables
└── README.md          # This file
```

## Infrastructure Components

### VPC and Networking
- Multi-AZ VPC with proper subnet segmentation
- NAT Gateways for private subnet internet access
- VPC Endpoints for S3 access optimization
- Security groups with least-privilege access

### EKS Cluster
- Managed Kubernetes cluster with multiple node groups
- Optimized for different workload types (general, compute, memory)
- Auto-scaling enabled
- Container Insights for monitoring
- EBS CSI driver for persistent volumes

### RDS PostgreSQL
- Performance-optimized for astronomical data workloads
- Multi-AZ deployment for production
- Automated backups and point-in-time recovery
- Enhanced monitoring with Performance Insights
- Read replicas for production environments

### S3 Data Lake
- Separate buckets for raw, processed, and archived data
- Intelligent lifecycle management
- Server-side encryption
- Event notifications for processing triggers

### Monitoring and Alerting
- CloudWatch dashboards for operational visibility
- Custom metrics for processing pipelines
- Alerts for system health and performance
- Log aggregation and analysis

## Environment Configurations

### Development
- Smaller, cost-optimized resources
- Single AZ deployment where possible
- Reduced backup retention periods
- Spot instances for cost savings

### Production
- High availability with Multi-AZ deployment
- Enhanced monitoring and alerting
- Extended backup retention
- Full encryption at rest and in transit

## Post-Deployment Steps

### 1. Verify Cluster Access
```bash
kubectl get nodes
kubectl get pods --all-namespaces
```

### 2. Deploy Applications
```bash
cd ../kubernetes
kubectl apply -f base/
```

### 3. Install Airflow
```bash
helm repo add apache-airflow https://airflow.apache.org
helm install airflow apache-airflow/airflow -n airflow --create-namespace
```

### 4. Configure Database
```bash
# Get RDS credentials from Secrets Manager or Terraform output
terraform output rds_endpoint
```

## Monitoring

### CloudWatch Dashboards
- **Main Dashboard**: Overall system health and resource utilization
- **Pipeline Dashboard**: Processing pipeline metrics and logs
- **S3 Dashboard**: Storage usage and access patterns

### Accessing Dashboards
```bash
# Open AWS Console and navigate to CloudWatch Dashboards
# Or use AWS CLI
aws cloudwatch list-dashboards --region us-east-1
```

### Key Metrics to Monitor
- EKS node CPU and memory utilization
- RDS connection count and performance metrics
- S3 storage usage and request patterns
- Processing pipeline success rates and duration

## Troubleshooting

### Common Issues

1. **EKS Node Group Creation Fails**
   - Check IAM permissions for EKS service roles
   - Verify subnet configurations and availability zones

2. **RDS Connection Issues**
   - Verify security group rules
   - Check VPC and subnet configurations

3. **S3 Access Issues**
   - Verify bucket policies and IAM roles
   - Check VPC endpoint configurations

### Debug Commands
```bash
# Check Terraform state
terraform state list
terraform state show <resource_name>

# Verify AWS resources
aws eks describe-cluster --name astro-data-pipeline-eks
aws rds describe-db-instances
aws s3 ls

# Check EKS cluster status
kubectl cluster-info
kubectl get events --all-namespaces
```

## Cost Optimization

### Development Environment
- Use smaller instance types
- Enable spot instances for non-critical workloads
- Shorter backup retention periods
- Lifecycle policies for aggressive archiving

### Production Environment
- Use Reserved Instances for predictable workloads
- Enable Auto Scaling for variable workloads
- Monitor with cost budgets and alerts
- Regular cost optimization reviews

## Security Best Practices

- All data encrypted at rest and in transit
- Least-privilege IAM policies
- Network segmentation with private subnets
- Security group rules audited regularly
- Secrets stored in AWS Secrets Manager

## Backup and Disaster Recovery

### RDS Backups
- Automated daily backups
- Point-in-time recovery enabled
- Cross-region backup replication for production

### S3 Data Protection
- Versioning enabled
- Cross-region replication for critical data
- MFA delete protection for production buckets

## Cleanup

To destroy the infrastructure:

```bash
# WARNING: This will delete all resources and data
terraform destroy -var-file="staging.tfvars"  # or prod.tfvars
```

## Environment Comparison & Cost Analysis

This project demonstrates different architectural approaches for staging vs production:

### Staging Environment (~$202/month)

**Optimized for cost and development efficiency:**

- **Single-AZ deployment** - Saves ~$35/month on NAT Gateway costs
- **Balanced instance sizes** - db.t3.small/t3.medium for performance vs cost balance
- **Aggressive S3 lifecycle** - 30-day expiration to minimize storage costs
- **Reduced monitoring** - 7-day log retention, Container Insights disabled
- **AWS managed encryption** - No additional KMS key costs

### Production Environment (~$1,200/month - DISABLED)

**Optimized for high availability and reliability:**

- **Multi-AZ deployment** - Full fault tolerance across 3 availability zones
- **Production instance sizes** - m5.large, c5.2xlarge, r5.xlarge instances
- **Long-term storage** - Multi-year retention with intelligent tiering
- **Comprehensive monitoring** - 90-day retention, full observability stack
- **Customer-managed encryption** - Enhanced security with KMS keys

### Key Trade-offs Demonstrated

| **Aspect**       | **Staging**             | **Production**     |
|------------------|-------------------------|--------------------|
| **Availability** | Single point of failure | 99.99% uptime      |
| **Cost**         | $202/month              | $1,200/month       |
| **Recovery**     | Manual intervention     | Automatic failover |
| **Monitoring**   | Basic                   | Comprehensive      |
| **Scaling**      | Manual                  | Automated          |

This approach demonstrates enterprise-level understanding of cost optimization vs reliability trade-offs.

## Support

For issues with this infrastructure:
1. Check the troubleshooting section above
2. Review CloudWatch logs and metrics
3. Consult AWS documentation for service-specific issues
4. Open an issue in the project repository