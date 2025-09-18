# Monitoring and Observability Stack

This directory contains a comprehensive monitoring and observability stack for the Astronomical Data Processing Pipeline, designed specifically for production deployment and operational excellence.

## 🎯 Overview

The monitoring stack provides complete visibility into:
- **Application Performance**: FITS processing metrics, throughput, latency
- **Infrastructure Health**: CPU, memory, disk, network utilization
- **Service Reliability**: Uptime, error rates, response times
- **Astronomical Processing**: Domain-specific metrics for image calibration
- **Alert Management**: Proactive alerting for operational issues

## 🏗️ Architecture Components

### Core Monitoring Services
- **Prometheus**: Metrics collection and storage with 15-day retention
- **Grafana**: Visualization dashboards with astronomical-specific views
- **AlertManager**: Intelligent alert routing and notification management
- **Node Exporter**: Infrastructure metrics collection via DaemonSet

### Service Discovery
- **ServiceMonitors**: Automatic Prometheus target discovery
- **PrometheusRules**: Pre-configured alerting rules for common issues
- **Custom Metrics**: Astronomical processing and domain-specific metrics

## 📊 Metrics Categories

### Application Metrics
```promql
# FITS processing performance
fits_processing_total                    # Total files processed
fits_processing_duration_seconds         # Processing time distribution
fits_processing_errors_total             # Processing failures
astronomical_objects_detected_total      # Objects found in images

# Service health
http_server_requests_seconds            # API response times
jvm_memory_used_bytes                   # Java memory usage
hikaricp_connections_active             # Database connections
```

### Infrastructure Metrics
```promql
# Node-level metrics
node_cpu_seconds_total                  # CPU utilization
node_memory_MemAvailable_bytes          # Available memory
node_filesystem_avail_bytes             # Disk space
node_network_receive_bytes_total        # Network traffic

# Kubernetes metrics
kube_pod_status_phase                   # Pod health status
kube_deployment_status_replicas         # Replica counts
kube_job_status_failed                  # Batch job failures
```

### Custom Astronomical Metrics
```promql
# Processing pipeline metrics
dark_subtraction_operations_total       # Dark frame processing
flat_correction_operations_total        # Flat field corrections
cosmic_ray_detection_operations_total   # Cosmic ray removal
catalog_objects_ingested_total          # Catalog ingestion rate

# Quality metrics
image_quality_score                     # Image quality assessment
processing_artifacts_detected           # Data quality issues
calibration_accuracy_score              # Calibration effectiveness
```

## 🚀 Deployment Instructions

### Prerequisites
```bash
# Ensure namespace and RBAC are deployed
kubectl apply -f ../base/namespace.yaml
kubectl apply -f ../base/rbac.yaml

# Verify Prometheus Operator is installed (optional but recommended)
kubectl get crd prometheuses.monitoring.coreos.com
```

### Deploy Core Monitoring Stack
```bash
# Deploy in order for proper dependency management
kubectl apply -f prometheus-deployment.yaml
kubectl apply -f grafana-deployment.yaml
kubectl apply -f alertmanager-deployment.yaml
kubectl apply -f node-exporter-daemonset.yaml
kubectl apply -f service-monitors.yaml
```

### Verify Deployment
```bash
# Check all monitoring pods are running
kubectl get pods -n astro-pipeline -l component=monitoring

# Verify services are available
kubectl get services -n astro-pipeline -l component=monitoring

# Check ServiceMonitor discovery
kubectl get servicemonitors -n astro-pipeline
```

### Access Monitoring Services
```bash
# Grafana Dashboard (admin/astro-grafana-2023!)
kubectl port-forward svc/grafana 3000:3000 -n astro-pipeline
# Open http://localhost:3000

# Prometheus Web UI
kubectl port-forward svc/prometheus 9090:9090 -n astro-pipeline
# Open http://localhost:9090

# AlertManager Web UI
kubectl port-forward svc/alertmanager 9093:9093 -n astro-pipeline
# Open http://localhost:9093
```

## 📈 Grafana Dashboards

### Astronomical Pipeline Overview
- **Processing Rate**: Real-time FITS file processing throughput
- **Success Rate**: Processing success percentage with SLA tracking
- **Active Jobs**: Current batch processing job status
- **Catalog Ingestion**: Objects detected and stored per hour
- **Resource Utilization**: CPU/memory usage across services

### Performance Monitoring
- **Latency Distribution**: Processing time percentiles and heatmaps
- **Throughput Analysis**: Historical processing trends
- **JVM Performance**: Memory pools, garbage collection metrics
- **Database Performance**: Query times, connection pools
- **Storage I/O**: S3 transfer rates and error rates

### Infrastructure Dashboard
- **Node Health**: CPU, memory, disk usage across cluster
- **Network Performance**: Bandwidth utilization and errors
- **Pod Status**: Deployment health and replica counts
- **Job Monitoring**: Batch job completion and failure tracking

## 🚨 Alert Configuration

### Critical Alerts (Immediate Response)
- **ProcessingFailureRate > 10%**: High processing error rate
- **BatchJobFailure**: Any batch job failure
- **HighMemoryUsage > 90%**: Memory exhaustion risk
- **DatabaseConnections > 80%**: Database connection saturation

### Warning Alerts (Investigation Required)
- **HighProcessingLatency > 5min**: Slow processing performance
- **SlowDatabaseQueries > 1s**: Database performance degradation
- **S3ErrorRate > 5%**: Storage connectivity issues
- **DiskSpaceUsage > 85%**: Storage capacity concerns

### Alert Routing
```yaml
# Slack channels for different teams
- Critical alerts → #astro-pipeline-critical
- Processing issues → #processing-team
- Database issues → #database-team
- Batch job alerts → #batch-processing

# Email notifications for critical alerts
- oncall@astro-pipeline.local
```

## 🔧 Configuration Customization

### Adding Custom Metrics
```yaml
# In your Spring Boot application
@Timed(name = "custom_processing", description = "Custom processing timer")
@Counter(name = "custom_events", description = "Custom event counter")
public void customProcessingMethod() {
    // Your code here
}
```

### Custom Alert Rules
```yaml
# Add to prometheus-rules ConfigMap
- alert: CustomMetricThreshold
  expr: custom_metric > 100
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Custom metric exceeded threshold"
    description: "Custom metric is {{ $value }}"
```

### Dashboard Modifications
1. Access Grafana UI
2. Navigate to dashboard
3. Click "Edit" panel
4. Modify queries or visualization
5. Save dashboard

## 📊 Operational Runbooks

### High Processing Latency
```bash
# 1. Check current processing load
kubectl top pods -n astro-pipeline

# 2. Examine processing service logs
kubectl logs -f deployment/image-processor -n astro-pipeline

# 3. Check for resource constraints
kubectl describe pod $(kubectl get pods -n astro-pipeline -l app=image-processor -o name | head -1)

# 4. Scale if necessary
kubectl scale deployment image-processor --replicas=5 -n astro-pipeline
```

### Database Performance Issues
```bash
# 1. Check database connections
kubectl exec -it $(kubectl get pods -n astro-pipeline -l app=catalog-service -o name | head -1) -- \
  curl http://localhost:8081/actuator/metrics/hikaricp.connections.active

# 2. Examine slow queries
kubectl logs -f deployment/catalog-service -n astro-pipeline | grep "slow query"

# 3. Check database resource usage
kubectl top pods -n astro-pipeline -l tier=data
```

### Batch Job Failures
```bash
# 1. List failed jobs
kubectl get jobs -n astro-pipeline --field-selector status.successful=0

# 2. Examine job logs
kubectl logs job/fits-processing-batch -n astro-pipeline

# 3. Check job description for issues
kubectl describe job fits-processing-batch -n astro-pipeline

# 4. Restart failed job (if appropriate)
kubectl delete job fits-processing-batch -n astro-pipeline
kubectl apply -f ../batch-jobs/fits-processing-job-template.yaml
```

## 🔍 Troubleshooting

### Prometheus Not Scraping Targets
```bash
# Check service discovery
kubectl get servicemonitors -n astro-pipeline

# Verify service labels match ServiceMonitor selectors
kubectl get services -n astro-pipeline --show-labels

# Check Prometheus configuration
kubectl exec -it prometheus-0 -n astro-pipeline -- \
  wget -qO- http://localhost:9090/api/v1/targets
```

### Grafana Dashboard Issues
```bash
# Check Grafana data source
kubectl exec -it $(kubectl get pods -n astro-pipeline -l app=grafana -o name) -- \
  curl http://localhost:3000/api/datasources

# Verify Prometheus connectivity
kubectl exec -it $(kubectl get pods -n astro-pipeline -l app=grafana -o name) -- \
  curl http://prometheus:9090/api/v1/query?query=up
```

### AlertManager Not Sending Alerts
```bash
# Check AlertManager configuration
kubectl get configmap alertmanager-config -n astro-pipeline -o yaml

# Verify AlertManager is receiving alerts from Prometheus
kubectl exec -it $(kubectl get pods -n astro-pipeline -l app=alertmanager -o name) -- \
  wget -qO- http://localhost:9093/api/v1/alerts

# Test alert routing
kubectl exec -it $(kubectl get pods -n astro-pipeline -l app=alertmanager -o name) -- \
  amtool config routes --config.file=/etc/alertmanager/alertmanager.yml
```

## 📋 Maintenance Tasks

### Daily Operations
- Monitor dashboard for anomalies
- Review alert noise and tune thresholds
- Check storage utilization trends
- Verify backup and retention policies

### Weekly Operations
- Review alert patterns and false positives
- Update dashboard queries for new metrics
- Analyze performance trends
- Test alert notification channels

### Monthly Operations
- Review and update alerting rules
- Optimize metric retention policies
- Update monitoring stack components
- Conduct monitoring system health checks

## 🚀 Performance Tuning

### Prometheus Optimization
```yaml
# Adjust scrape intervals based on cardinality
scrape_interval: 15s        # Standard metrics
scrape_interval: 30s        # Infrastructure metrics
scrape_interval: 10s        # Critical application metrics

# Optimize storage retention
--storage.tsdb.retention.time=15d
--storage.tsdb.retention.size=10GB
```

### Grafana Optimization
```yaml
# Configure query timeout and concurrency
query_timeout: 60s
concurrent_query_limit: 20

# Enable dashboard caching
dashboard_cache_enabled: true
dashboard_cache_ttl: 300s
```

### Resource Scaling
```bash
# Scale Prometheus for higher load
kubectl patch deployment prometheus -n astro-pipeline -p \
  '{"spec":{"template":{"spec":{"containers":[{"name":"prometheus","resources":{"limits":{"memory":"8Gi","cpu":"4000m"}}}]}}}}'

# Scale Grafana for more users
kubectl scale deployment grafana --replicas=2 -n astro-pipeline
```

This monitoring stack provides enterprise-grade observability specifically designed for astronomical data processing workloads, ensuring reliable operation and proactive issue resolution for the STScI data pipeline.