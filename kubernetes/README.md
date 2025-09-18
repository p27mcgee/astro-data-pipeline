# Kubernetes Manifests for Astronomical Data Pipeline

This directory contains Kubernetes manifests for deploying the astronomical data processing pipeline on EKS.

## Directory Structure

```
kubernetes/
├── base/                     # Core infrastructure manifests
│   ├── namespace.yaml       # Namespace, quotas, and limits
│   ├── configmap.yaml       # Application configuration
│   ├── rbac.yaml           # Service accounts and permissions
│   ├── image-processor-deployment.yaml
│   └── catalog-service-deployment.yaml
├── batch-jobs/              # Batch processing job templates
│   └── fits-processing-job-template.yaml
├── monitoring/              # Observability stack
│   └── (monitoring manifests)
└── README.md
```

## Key Components

### Microservices
- **Image Processor**: Scalable FITS file processing service
- **Catalog Service**: Astronomical object catalog management
- **Auto-scaling**: HPA based on CPU, memory, and custom metrics

### Batch Processing
- **Processing Jobs**: Template for large-scale batch processing
- **Scheduled Jobs**: Nightly processing and maintenance tasks
- **Resource Management**: Dedicated compute nodes with taints/tolerations

### Security & RBAC
- **Service Accounts**: AWS IAM roles for service integration
- **Network Policies**: Secure inter-service communication
- **Resource Quotas**: Prevent resource exhaustion

### Configuration Management
- **ConfigMaps**: Application settings and monitoring configuration
- **Secrets**: Sensitive data (database credentials, API keys)
- **Environment-specific**: Separate configs for dev/staging/prod

## Deployment Instructions

### Prerequisites
```bash
# Configure kubectl for EKS cluster
aws eks update-kubeconfig --region us-east-1 --name astro-data-pipeline-eks

# Verify cluster access
kubectl cluster-info
```

### Deploy Core Infrastructure
```bash
# Create namespace and RBAC
kubectl apply -f base/namespace.yaml
kubectl apply -f base/rbac.yaml

# Deploy configuration
kubectl apply -f base/configmap.yaml

# Deploy microservices
kubectl apply -f base/image-processor-deployment.yaml
kubectl apply -f base/catalog-service-deployment.yaml
```

### Deploy Batch Processing
```bash
# Deploy batch job templates
kubectl apply -f batch-jobs/fits-processing-job-template.yaml
```

### Verify Deployment
```bash
# Check pod status
kubectl get pods -n astro-pipeline

# Check services
kubectl get services -n astro-pipeline

# Check HPA status
kubectl get hpa -n astro-pipeline

# View logs
kubectl logs -f deployment/image-processor -n astro-pipeline
```

## Scaling and Performance

### Horizontal Pod Autoscaler
- **CPU Target**: 70% utilization
- **Memory Target**: 80% utilization  
- **Custom Metrics**: Processing queue size
- **Scale Range**: 2-10 replicas for image-processor

### Node Affinity
- **General Workloads**: Standard compute nodes
- **Batch Processing**: Compute-optimized nodes with taints
- **Anti-affinity**: Spread pods across availability zones

### Resource Requests/Limits
- **Image Processor**: 1-4 GB memory, 0.5-2 CPU cores
- **Catalog Service**: 512MB-2GB memory, 0.25-1 CPU cores
- **Batch Jobs**: 2-8 GB memory, 1-4 CPU cores

## Monitoring Integration

### Prometheus Metrics
- Custom application metrics exposed on `/actuator/prometheus`
- Infrastructure metrics via node-exporter sidecars
- Service discovery via Kubernetes annotations

### Health Checks
- **Startup Probes**: Handle slow application startup
- **Readiness Probes**: Traffic routing decisions
- **Liveness Probes**: Container restart decisions

### Logging
- Structured JSON logging to stdout/stderr
- Log aggregation via EKS/CloudWatch integration
- Centralized log analysis and alerting

## Security Best Practices

### Pod Security
- Non-root user execution (UID 1000)
- Read-only root filesystem where possible
- Security contexts and capabilities restrictions
- Network policies for inter-pod communication

### Secrets Management
- AWS Secrets Manager integration via IAM roles
- No hardcoded secrets in manifests
- Automatic secret rotation support

### Network Security
- Private subnets for all worker nodes
- Security groups with least-privilege access
- VPC endpoints for AWS service communication

## Troubleshooting

### Common Issues
```bash
# Pod startup failures
kubectl describe pod <pod-name> -n astro-pipeline
kubectl logs <pod-name> -n astro-pipeline --previous

# Service connectivity
kubectl port-forward svc/image-processor-service 8080:8080 -n astro-pipeline

# Resource constraints
kubectl top pods -n astro-pipeline
kubectl describe hpa -n astro-pipeline

# Configuration issues
kubectl get configmap astro-pipeline-config -n astro-pipeline -o yaml
```

### Performance Tuning
- Adjust HPA thresholds based on workload patterns
- Tune JVM settings via environment variables
- Monitor resource utilization and adjust requests/limits
- Use pod disruption budgets for rolling updates

## Maintenance

### Updates
```bash
# Rolling update deployment
kubectl set image deployment/image-processor image-processor=astro-image-processor:1.1.0 -n astro-pipeline

# Check rollout status
kubectl rollout status deployment/image-processor -n astro-pipeline

# Rollback if needed
kubectl rollout undo deployment/image-processor -n astro-pipeline
```

### Cleanup
```bash
# Remove all resources
kubectl delete namespace astro-pipeline

# Remove batch jobs only
kubectl delete jobs --all -n astro-pipeline
kubectl delete cronjobs --all -n astro-pipeline
```

This Kubernetes setup provides a production-ready, scalable, and observable astronomical data processing pipeline optimized for EKS deployment.