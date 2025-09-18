# Performance Testing and Optimization Framework

This directory contains a comprehensive performance testing and optimization framework specifically designed for astronomical data processing pipelines. The framework provides advanced testing capabilities, system optimization analysis, and actionable recommendations for improving performance at scale.

## 🚀 Overview

The performance testing framework consists of multiple specialized tools:

| Tool | Purpose | Scope | Duration |
|------|---------|-------|----------|
| `db_performance_test.py` | Database performance analysis | PostgreSQL + PostGIS | 10-60 min |
| `app_performance_test.py` | Application performance testing | REST APIs + Services | 15-45 min |
| `system_optimizer.py` | System optimization analysis | Full stack | 5-15 min |
| `locust_load_test.py` | Advanced load testing | End-to-end workflows | 30-300 min |

## 🏗️ Framework Architecture

### Performance Testing Layers

```mermaid
graph TD
    A[Load Testing Layer] --> B[Application Testing Layer]
    B --> C[Database Testing Layer] 
    C --> D[System Analysis Layer]
    D --> E[Optimization Recommendations]
    
    A --> F[Locust Load Testing]
    B --> G[API Performance Testing]
    C --> H[PostgreSQL Performance Testing]
    D --> I[Kubernetes Analysis]
    D --> J[Cost Optimization]
    
    E --> K[Implementation Plan]
    E --> L[Performance Report]
```

### Testing Methodology

**1. Astronomical Workload Simulation**
- Realistic FITS file processing patterns
- Authentic astronomical coordinate queries
- Scientific data analysis workflows
- Multi-user concurrent access patterns

**2. Scalability Assessment**
- Horizontal scaling limits
- Resource utilization efficiency
- Database connection pool optimization
- Memory and CPU bottleneck identification

**3. Performance Optimization**
- Query optimization recommendations
- Infrastructure right-sizing
- Cost optimization opportunities
- Security and compliance alignment

## 🔬 Database Performance Testing

### Purpose
Comprehensive PostgreSQL performance analysis with PostGIS spatial optimization for astronomical catalog operations.

### Key Features
- **Spatial Query Performance**: Cone search optimization for astronomical coordinates
- **Bulk Operations**: Large-scale catalog ingestion performance
- **Complex Analytics**: Multi-table joins and aggregation performance
- **Concurrent Load**: Multi-user database access patterns

### Usage
```bash
# Quick database performance test
python db_performance_test.py --quick --host localhost --port 5432 --database astro_catalog

# Comprehensive analysis with custom parameters
python db_performance_test.py \
  --host db.astro-pipeline.internal \
  --port 5432 \
  --database astro_catalog \
  --user astro_user \
  --password $DB_PASSWORD \
  --output db_performance_detailed.json

# Connect via kubectl port-forward
kubectl port-forward service/postgresql 5432:5432 -n astro-pipeline &
python db_performance_test.py --host localhost --port 5432
```

### Test Categories

**Cone Search Performance**
- Spatial index utilization
- Query response time distribution
- Geographic query pattern optimization
- PostGIS performance tuning

```sql
-- Example optimized cone search
SELECT object_id, ra, dec, magnitude_v
FROM astronomical_objects 
WHERE ST_DWithin(
    position::geography,
    ST_SetSRID(ST_Point(%s, %s), 4326)::geography,
    %s * 111000  -- Convert degrees to meters
)
ORDER BY ST_Distance(position::geography, ST_SetSRID(ST_Point(%s, %s), 4326)::geography)
LIMIT 1000;
```

**Bulk Insert Performance**
- Batch size optimization
- Index maintenance during inserts
- Parallel loading strategies
- Data validation overhead

**Complex Query Analysis**
- Multi-table join performance
- Aggregation query optimization
- Temporal query patterns
- Cross-matching algorithms

### Performance Benchmarks

| Operation | Target Performance | Excellent | Good | Needs Improvement |
|-----------|-------------------|-----------|------|-------------------|
| Cone Search | < 50ms avg | < 25ms | 25-100ms | > 100ms |
| Bulk Insert | > 5000 rec/sec | > 10000/sec | 1000-5000/sec | < 1000/sec |
| Complex Query | < 500ms avg | < 200ms | 200-1000ms | > 1000ms |
| Concurrent Load | > 95% success | > 99% | 95-99% | < 95% |

## 📊 Application Performance Testing

### Purpose
Comprehensive testing of REST API performance, FITS processing throughput, and service reliability under load.

### Key Features
- **Asynchronous Testing**: High-performance async HTTP testing with aiohttp
- **Realistic Workloads**: Astronomical data processing simulation
- **Service Integration**: End-to-end workflow testing
- **Performance Profiling**: Detailed latency and throughput analysis

### Usage
```bash
# Basic application performance test
python app_performance_test.py --url http://localhost:8080 --quick

# Comprehensive load testing
python app_performance_test.py \
  --url http://image-processor-service.astro-pipeline.svc.cluster.local:8080 \
  --timeout 60 \
  --output app_performance_full.json

# Test against production (via kubectl proxy)
kubectl proxy --port=8001 &
python app_performance_test.py --url http://localhost:8001/api/v1/namespaces/astro-pipeline/services/image-processor-service:8080/proxy
```

### Test Scenarios

**Health Check Performance**
```python
# High-frequency health check testing
async def test_health_check_performance(iterations=1000, concurrent=20):
    # Simulates monitoring system load
    # Tests basic service responsiveness
```

**FITS Processing Performance**
```python
# Realistic FITS file processing simulation
async def test_fits_processing_performance(iterations=100, concurrent=10):
    # Tests astronomical image calibration pipeline
    # Monitors job submission and completion
    # Measures end-to-end processing time
```

**Catalog Query Performance**
```python
# Astronomical catalog query patterns
async def test_catalog_query_performance(iterations=500, concurrent=25):
    # Cone searches around interesting sky regions
    # Magnitude-based object filtering
    # Cross-matching with external catalogs
```

**Mixed Workload Simulation**
```python
# Realistic user behavior patterns
async def test_concurrent_mixed_workload(duration=300, users=50):
    # Simulates real-world usage patterns
    # Combines processing, queries, and monitoring
    # Tests system behavior under realistic load
```

## 🎯 Advanced Load Testing (Locust)

### Purpose
Sophisticated load testing with realistic astronomical user behavior patterns and advanced workload simulation.

### Key Features
- **Astronomical User Personas**: Researcher, Student, Automated Pipeline
- **Realistic Data Generation**: Authentic astronomical coordinates and parameters  
- **Advanced Task Distribution**: Weighted task selection based on real usage
- **Custom Metrics**: Astronomical-specific performance indicators

### Usage
```bash
# Basic load test
locust -f locust_load_test.py --host http://localhost:8080

# Distributed load testing
# Start master
locust -f locust_load_test.py --master --host http://your-app-url

# Start workers (on different machines)
locust -f locust_load_test.py --worker --master-host=master-ip

# Headless load testing with custom parameters
locust -f locust_load_test.py \
  --host http://your-app-url \
  --users 100 \
  --spawn-rate 10 \
  --run-time 300s \
  --headless \
  --html load_test_report.html

# Specific test scenarios
python locust_load_test.py stress    # High-intensity stress test
python locust_load_test.py endurance # Long-duration stability test
python locust_load_test.py spike     # Sudden load spike simulation
```

### User Behavior Patterns

**Astronomical Researcher** (Weight: 3)
```python
class AstronomicalResearcher(HttpUser):
    """Heavy catalog query usage pattern"""
    wait_time = between(2, 10)  # Thoughtful analysis time
    tasks = [CatalogQueryTasks]  # Focus on data exploration
```

**Automated Pipeline** (Weight: 1) 
```python
class AutomatedPipeline(HttpUser):
    """High-frequency processing requests"""
    wait_time = between(0.5, 2)  # Fast automated requests
    tasks = [ImageProcessingTasks]  # Batch processing focus
```

**Casual User** (Weight: 2)
```python
class CasualUser(HttpUser):
    """Mixed light usage pattern"""
    wait_time = between(5, 15)  # Exploratory usage
    tasks = {CatalogQueryTasks: 70, ImageProcessingTasks: 20, SystemMonitoringTasks: 10}
```

### Astronomical Workload Simulation

**Realistic Coordinate Generation**
```python
# Favor astronomically interesting regions
regions = [
    {'ra_range': (80, 100), 'dec_range': (-70, -60)},    # Large Magellanic Cloud
    {'ra_range': (10, 30), 'dec_range': (-75, -65)},     # Small Magellanic Cloud  
    {'ra_range': (265, 275), 'dec_range': (-30, -25)},   # Galactic center
    {'ra_range': (83, 87), 'dec_range': (21, 23)},       # Pleiades cluster
]
```

**Authentic FITS File Patterns**
```python
def generate_fits_filename():
    telescope = random.choice(['HST', 'JWST', 'VLT', 'Gemini', 'Keck'])
    instrument = random.choice(['WFC3', 'MIRI', 'FORS2', 'NIRI', 'DEIMOS'])
    filter_name = random.choice(['F606W', 'F814W', 'F160W', 'F475W', 'F850LP'])
    return f"{telescope}_{instrument}_{filter_name}_{timestamp}_{sequence:03d}.fits"
```

## 🔧 System Optimization Analysis

### Purpose
Comprehensive system analysis providing actionable optimization recommendations for performance, cost, and reliability improvements.

### Key Features
- **Kubernetes Resource Analysis**: Pod, deployment, and service optimization
- **Cost Optimization**: Resource right-sizing and usage optimization
- **Database Performance**: Query and configuration optimization
- **Application Metrics**: JVM tuning and application-specific recommendations

### Usage
```bash
# Basic system optimization analysis
python system_optimizer.py --namespace astro-pipeline

# Comprehensive analysis with database and applications
python system_optimizer.py \
  --namespace astro-pipeline \
  --db-url postgresql://user:pass@host:5432/dbname \
  --app-urls http://service1:8080 http://service2:8080 \
  --output comprehensive_optimization_report.json

# Analysis with custom kubeconfig
python system_optimizer.py \
  --kubeconfig ~/.kube/config-prod \
  --namespace astro-pipeline
```

### Analysis Categories

**Kubernetes Resource Optimization**
- Resource request/limit analysis
- Pod restart pattern identification  
- Health check configuration review
- Scaling configuration assessment

**Cost Optimization Analysis**
- Over-provisioned resource identification
- Unused persistent volume detection
- Compute resource right-sizing
- Storage optimization opportunities

**Database Performance Analysis**  
- Connection pool utilization
- Slow query identification
- Index usage effectiveness
- Table maintenance recommendations

**Application Performance Analysis**
- JVM memory usage patterns
- Garbage collection optimization
- Response time analysis
- Custom metric evaluation

### Optimization Recommendations

**Priority Levels**
- **HIGH**: Immediate performance or reliability impact
- **MEDIUM**: Significant improvement opportunity
- **LOW**: Minor optimization or cost saving

**Implementation Effort**
- **LOW**: Simple configuration change (< 1 hour)
- **MEDIUM**: Code or infrastructure modification (1-8 hours)  
- **HIGH**: Major architectural change (> 8 hours)

**Example Recommendations**
```json
{
  "component": "deployment/image-processor",
  "priority": "HIGH", 
  "category": "PERFORMANCE",
  "issue": "Missing resource limits can cause resource contention",
  "recommendation": "Set appropriate CPU and memory limits based on profiling",
  "expected_impact": "Improved stability and resource utilization",
  "implementation_effort": "LOW",
  "estimated_cost_savings": null
}
```

## 📊 Performance Monitoring Integration

### Metrics Collection
The framework integrates with existing monitoring infrastructure:

**Prometheus Integration**
- Custom metrics for astronomical operations
- Performance test result ingestion
- Alert rule generation based on test results

**Grafana Dashboards**
- Performance test result visualization
- Historical performance trend analysis  
- Optimization recommendation tracking

**Custom Metrics Examples**
```python
# Database performance metrics
cone_search_duration_seconds = Histogram('cone_search_duration_seconds', 'Cone search query time')
bulk_insert_rate = Gauge('bulk_insert_rate_per_second', 'Bulk insert throughput')

# Application performance metrics  
fits_processing_duration = Histogram('fits_processing_duration_seconds', 'FITS processing time')
catalog_query_rate = Counter('catalog_queries_total', 'Total catalog queries')
```

## 🎨 Test Data Management

### Realistic Test Data Generation
The framework generates authentic astronomical test data:

**Coordinate Distributions**
- Galactic coordinate system awareness
- Realistic sky region targeting
- Proper motion simulation
- Parallax and distance modeling

**Magnitude Distributions**
- Logarithmic magnitude distribution
- Filter-specific magnitude ranges
- Variable star amplitude simulation
- Photometric error modeling

**Object Type Distributions**
```python
object_types = random.choices(
    ['STAR', 'GALAXY', 'QUASAR', 'NEBULA', 'ASTEROID', 'VARIABLE_STAR'],
    weights=[70, 20, 3, 4, 2, 1]  # Realistic astronomical proportions
)
```

### Test Environment Management

**Database Setup**
```sql
-- Create test schema with spatial indexing
CREATE INDEX CONCURRENTLY idx_astro_objects_position 
ON astronomical_objects USING GIST (position);

CREATE INDEX CONCURRENTLY idx_astro_objects_magnitude 
ON astronomical_objects (magnitude_v);

-- Enable PostGIS extensions
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
```

**Kubernetes Test Environment**
```yaml
# Test namespace with resource quotas
apiVersion: v1
kind: ResourceQuota
metadata:
  name: performance-test-quota
spec:
  hard:
    requests.cpu: "10"
    requests.memory: 20Gi
    limits.cpu: "20"
    limits.memory: 40Gi
```

## 📋 Best Practices

### Performance Testing Best Practices

**Test Environment Isolation**
- Dedicated test namespace
- Resource quota enforcement
- Network policy isolation
- Data cleanup automation

**Realistic Load Patterns**
- Gradual ramp-up phases
- Realistic think times
- Authentic data patterns
- Multi-user behavior simulation

**Baseline Establishment**
- Performance baseline recording
- Regression detection
- Continuous performance monitoring
- Historical trend analysis

### Optimization Implementation

**Gradual Implementation**
- Start with HIGH priority, LOW effort items
- Monitor impact of each change
- Document performance improvements
- Validate optimization effectiveness

**Risk Management**
- Test optimizations in staging first
- Implement rollback procedures
- Monitor key metrics during deployment
- Maintain performance baselines

## 🚀 Advanced Usage Scenarios

### CI/CD Integration
```yaml
# GitHub Actions performance testing
- name: Run Performance Tests
  run: |
    python db_performance_test.py --quick --output db_results.json
    python app_performance_test.py --quick --output app_results.json
    python system_optimizer.py --output optimization_report.json

- name: Performance Gate Check
  run: |
    python check_performance_gates.py --results db_results.json app_results.json
```

### Multi-Environment Testing
```bash
# Production-like performance validation
kubectl create namespace performance-test
kubectl apply -f ../kubernetes/base/ -n performance-test

# Scale for performance testing
kubectl scale deployment image-processor --replicas=3 -n performance-test
kubectl scale deployment catalog-service --replicas=2 -n performance-test

# Run comprehensive test suite
python run_performance_suite.py --namespace performance-test --comprehensive
```

### Automated Optimization
```python
# Automated optimization implementation
def implement_optimization_recommendations(report_file):
    with open(report_file) as f:
        report = json.load(f)
    
    immediate_actions = report['optimization_report']['implementation_plan']['immediate_actions']
    
    for action in immediate_actions:
        if action['implementation_effort'] == 'LOW':
            implement_recommendation(action)
            validate_improvement(action)
```

## 📈 Performance Reporting

### Report Generation
The framework generates comprehensive performance reports:

**Executive Summary**
- Key performance indicators
- Test pass/fail status
- Critical issues identified
- Optimization opportunities

**Technical Details**
- Detailed metrics analysis
- Performance trend analysis
- Resource utilization patterns
- Bottleneck identification

**Action Items**
- Prioritized recommendation list
- Implementation time estimates
- Expected impact assessment
- Cost-benefit analysis

This performance testing and optimization framework provides enterprise-grade capabilities specifically designed for astronomical data processing workloads, ensuring optimal performance, cost efficiency, and scalability for scientific computing environments.