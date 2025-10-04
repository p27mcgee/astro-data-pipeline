# System Architecture: Service Interactions & Dependencies

## Overview

This document provides a high-level overview of the interactions and dependencies between the core components of the
Astronomical Data Processing Pipeline: **Image Processor Service**, **Catalog Service**, **PostgreSQL/PostGIS Database
**, **Apache Airflow**, and **Amazon S3**.

---

## System Components

### 1. **Image Processor Service** (Java Spring Boot)

- **Purpose**: Process raw astronomical FITS images through calibration pipeline
- **Technology**: Java 17, Spring Boot 3.1.5, nom-tam-fits library
- **Deployment**: Kubernetes pods in EKS cluster
- **Endpoints**:
    - `POST /api/v1/processing/jobs` - Submit processing jobs
    - `GET /api/v1/processing/jobs/{jobId}` - Check job status
    - `POST /api/v1/processing/jobs/s3` - Process from S3 directly
    - `POST /api/v1/processing/steps/*` - Granular processing endpoints

### 2. **Catalog Service** (Java Spring Boot)

- **Purpose**: Manage astronomical object catalogs with spatial queries
- **Technology**: Java 17, Spring Boot 3.1.5, PostGIS extensions
- **Deployment**: Kubernetes pods in EKS cluster
- **Endpoints**:
    - `POST /api/v1/catalog/objects` - Ingest catalog objects
    - `POST /api/v1/catalog/search/cone` - Spatial cone searches
    - `GET /api/v1/catalog/objects/{id}` - Retrieve objects
    - `POST /api/v1/catalog/crossmatch` - Cross-match catalogs

### 3. **PostgreSQL with PostGIS** (RDS)

- **Purpose**: Persistent storage for catalog data with spatial indexing
- **Technology**: PostgreSQL 15, PostGIS 3.3
- **Deployment**: Amazon RDS Multi-AZ
- **Key Features**:
    - Spatial indexes (GIST) for coordinate queries
    - Astronomical object schema with proper motion, parallax
    - Time-series observation storage

### 4. **Apache Airflow** (Kubernetes Executor)

- **Purpose**: Workflow orchestration and job scheduling
- **Technology**: Apache Airflow 2.x with Kubernetes Executor
- **Deployment**: Kubernetes deployment in EKS
- **Key DAGs**:
    - `telescope_data_processing` - Real-time processing pipeline
    - `batch_processing_dag` - Historical batch processing
    - `data_quality_monitoring` - Quality checks
    - `research_processing_dag` - Research workflows

### 5. **Amazon S3** (Data Lake)

- **Purpose**: Object storage for FITS files and processed data
- **Technology**: AWS S3 with lifecycle policies
- **Buckets**:
    - `astro-raw-data` - Incoming raw FITS files
    - `astro-processed-data` - Calibrated science-ready images
    - `astro-archive` - Long-term archive with Glacier tiering
    - `astro-intermediate` - Temporary processing artifacts

---

## Interaction Flows

### 🔄 Primary Data Flow: Raw Image → Processed Data

```
[S3 Raw Bucket]
    ↓ (S3 Event)
[Lambda Trigger]
    ↓ (Trigger DAG)
[Airflow]
    ↓ (HTTP POST)
[Image Processor Service]
    ↓ (Read FITS)
[S3 Raw Bucket]
    ↓ (Download & Process)
[Image Processor Service]
    ↓ (Write Results)
[S3 Processed Bucket]
    ↓ (Extract Catalog)
[Image Processor Service]
    ↓ (HTTP POST)
[Catalog Service]
    ↓ (SQL INSERT)
[PostgreSQL/PostGIS]
```

### 📊 Detailed Interaction Matrix

| Source Component    | Target Component       | Interaction Type     | Purpose                                        | Frequency           |
|---------------------|------------------------|----------------------|------------------------------------------------|---------------------|
| **S3 Raw**          | **Lambda**             | Event Notification   | Trigger on new file upload                     | Per upload          |
| **Lambda**          | **Airflow**            | REST API Call        | Trigger processing DAG                         | Per file            |
| **Airflow**         | **Image Processor**    | HTTP POST            | Submit processing job                          | Per workflow task   |
| **Airflow**         | **Catalog Service**    | HTTP POST            | Query/validate catalog                         | Per workflow task   |
| **Airflow**         | **S3**                 | AWS SDK              | Check file status, list objects                | Per workflow task   |
| **Image Processor** | **S3 Raw**             | AWS SDK (Read)       | Download FITS files for processing             | Per job             |
| **Image Processor** | **S3 Processed**       | AWS SDK (Write)      | Upload calibrated images                       | Per job             |
| **Image Processor** | **S3 Intermediate**    | AWS SDK (Read/Write) | Store/retrieve intermediate processing results | Per granular step   |
| **Image Processor** | **Catalog Service**    | HTTP POST            | Send extracted object catalogs                 | Per processed image |
| **Catalog Service** | **PostgreSQL/PostGIS** | JDBC Connection Pool | Store/query astronomical objects               | Continuous          |
| **Catalog Service** | **S3 Archive**         | AWS SDK (Write)      | Backup catalog exports                         | Scheduled           |
| **PostgreSQL**      | **Catalog Service**    | JDBC ResultSet       | Return spatial query results                   | Per query           |
| **Airflow**         | **PostgreSQL**         | JDBC (Metadata)      | Track workflow state, XCom data                | Continuous          |

---

## Dependency Deep Dive

### 🖼️ Image Processor Service Dependencies

#### **Depends On:**

1. **S3 Raw Bucket** (Critical)
    - **Purpose**: Source of raw FITS files to process
    - **Failure Impact**: Cannot read input data
    - **Retry Strategy**: Exponential backoff with 3 retries

2. **S3 Processed Bucket** (Critical)
    - **Purpose**: Destination for calibrated images
    - **Failure Impact**: Processing completes but results cannot be persisted
    - **Retry Strategy**: Retry with exponential backoff

3. **S3 Intermediate Bucket** (Optional for granular processing)
    - **Purpose**: Store intermediate results between processing steps
    - **Failure Impact**: Granular workflows cannot chain steps
    - **Fallback**: In-memory processing without persistence

4. **Catalog Service** (Optional)
    - **Purpose**: Store extracted object catalogs
    - **Failure Impact**: Processing succeeds but catalog not updated
    - **Retry Strategy**: Queue for retry, log for manual intervention

#### **Configuration:**

- S3 endpoint URLs via environment variables
- Connection pooling: 50 max connections
- Timeout: 30s per S3 operation
- Memory allocation: 2-4 GB per pod

---

### 📚 Catalog Service Dependencies

#### **Depends On:**

1. **PostgreSQL/PostGIS Database** (Critical)
    - **Purpose**: Persistent storage for all catalog data
    - **Failure Impact**: Service cannot read/write catalog data
    - **Retry Strategy**: HikariCP connection pooling with auto-retry
    - **Connection Pool**: 20 connections max, 5 minimum idle

2. **S3 Archive Bucket** (Non-critical)
    - **Purpose**: Backup catalog exports for disaster recovery
    - **Failure Impact**: Catalog operations continue, backups fail
    - **Retry Strategy**: Asynchronous retry queue

#### **Database Schema Dependencies:**

- **PostGIS Extension**: Required for spatial indexing
    - `ST_Distance()` - Calculate angular separations
    - `ST_DWithin()` - Cone search queries
    - `GIST indexes` - Accelerate spatial queries
- **Flyway Migrations**: Automatic schema versioning

#### **Configuration:**

- Database connection string via Spring Boot properties
- Spatial SRID: 4326 (WGS84 for celestial coordinates)
- Query timeout: 60s for complex spatial queries

---

### 🌪️ Apache Airflow Dependencies

#### **Depends On:**

1. **Image Processor Service** (Critical for processing DAGs)
    - **Purpose**: Execute image calibration tasks
    - **Failure Impact**: DAG tasks fail, automatic retry per task policy
    - **Health Check**: HTTP GET `/actuator/health` every 30s

2. **Catalog Service** (Critical for catalog DAGs)
    - **Purpose**: Query and validate catalog data
    - **Failure Impact**: Catalog-dependent tasks fail
    - **Health Check**: HTTP GET `/actuator/health` every 30s

3. **S3 Buckets** (Critical)
    - **Purpose**: Discover files, check processing status
    - **Failure Impact**: Cannot list files or trigger processing
    - **Sensors**: `S3KeySensor` polls for file availability

4. **PostgreSQL Metadata DB** (Critical)
    - **Purpose**: Store DAG state, task instances, XCom data
    - **Failure Impact**: Airflow cannot schedule or track workflows
    - **Connection**: Separate metadata database (can be same RDS instance)

#### **Orchestration Patterns:**

- **Event-Driven**: S3 Lambda triggers initiate workflows
- **Scheduled**: Hourly batch processing for new files
- **Manual**: Research workflows triggered on-demand
- **Retry Logic**: 2 retries per task with 5-minute delay

#### **Configuration:**

- Kubernetes Executor: Each task runs in isolated pod
- Resource requests: 0.5 CPU, 512MB RAM per task pod
- Parallelism: Max 3 concurrent DAG runs

---

### 🗄️ PostgreSQL/PostGIS Dependencies

#### **Depends On:**

1. **Catalog Service** (Primary Client)
    - **Purpose**: Read/write astronomical object data
    - **Connection Type**: JDBC connection pool (HikariCP)
    - **Access Pattern**: Read-heavy with burst writes

2. **Airflow Metadata** (Secondary Client)
    - **Purpose**: Store workflow execution state
    - **Connection Type**: SQLAlchemy connection pool
    - **Access Pattern**: Write-heavy during workflow execution

#### **External Dependencies:**

- **S3 for Backups**: Automated RDS snapshots to S3
- **CloudWatch**: Database performance metrics
- **AWS Secrets Manager**: Database credentials rotation

#### **Performance Optimizations:**

- **Spatial Indexes**: GIST indexes on coordinate columns
  ```sql
  CREATE INDEX idx_objects_position ON astronomical_objects
  USING GIST (position);
  ```
- **Partitioning**: Time-based partitioning for observation tables
- **Connection Pooling**: 100 max connections, 20 per service
- **Read Replicas**: Optional for read scaling (not currently deployed)

---

### 💾 Amazon S3 Dependencies

#### **Depends On:**

1. **Lambda Functions** (Event Triggers)
    - **Purpose**: Process S3 events and trigger workflows
    - **Event Types**: `ObjectCreated:Put`, `ObjectCreated:CompleteMultipartUpload`
    - **Configuration**: Filter for `.fits` files only

2. **VPC Endpoints** (Network Optimization)
    - **Purpose**: Private connectivity from EKS to S3
    - **Benefit**: Reduced latency and data transfer costs
    - **Route**: Traffic stays within AWS network

#### **Consumed By:**

1. **Image Processor Service**
    - Access Pattern: Read-heavy (downloads), periodic writes (uploads)
    - Typical File Size: 10MB - 500MB per FITS file
    - Throughput: Up to 500 files/hour during batch processing

2. **Catalog Service**
    - Access Pattern: Write-only (periodic backups)
    - Frequency: Daily catalog exports

3. **Airflow**
    - Access Pattern: List operations, metadata queries
    - Frequency: Per DAG execution (hourly)

#### **Lifecycle Policies:**

- **Raw Data**: 90 days in Standard, then Glacier
- **Processed Data**: 30 days in Standard, then Intelligent-Tiering
- **Intermediate**: 7 days, then delete
- **Archive**: Immediate Glacier Deep Archive

---

## Failure Scenarios & Resilience

### Scenario 1: **PostgreSQL Database Unavailable**

**Impact:**

- ❌ Catalog Service: All endpoints return 503 Service Unavailable
- ⚠️ Image Processor: Processing continues, catalog ingestion queued
- ❌ Airflow: Metadata operations fail, DAGs cannot execute

**Mitigation:**

- Multi-AZ RDS deployment with automatic failover (~60s)
- Catalog Service retry queue for failed ingestions
- Image Processor continues with "catalog-disabled" mode

**Recovery Time:** ~2 minutes (RDS failover + DNS propagation)

---

### Scenario 2: **S3 Bucket Unavailable**

**Impact:**

- ❌ Image Processor: Cannot read input or write output
- ⚠️ Catalog Service: Backup exports fail (non-critical)
- ❌ Airflow: File discovery tasks fail

**Mitigation:**

- Exponential backoff retry (3 attempts over 15 minutes)
- S3 SLA: 99.99% availability (rare event)
- Cross-region replication for critical buckets (future enhancement)

**Recovery Time:** Self-healing via retries or manual intervention

---

### Scenario 3: **Image Processor Service Down**

**Impact:**

- ❌ Airflow: Processing tasks fail, automatic retry
- ✅ Catalog Service: Unaffected (independent service)
- ⚠️ User Impact: Processing queue builds up

**Mitigation:**

- Kubernetes auto-healing: Restart failed pods
- Horizontal Pod Autoscaler: Scale up under load
- Multiple replicas: 3 pods for high availability

**Recovery Time:** ~30 seconds (pod restart) to 5 minutes (if scaling needed)

---

### Scenario 4: **Airflow Scheduler Failure**

**Impact:**

- ❌ Workflow Orchestration: No new DAGs scheduled
- ✅ Services: Image Processor and Catalog Service continue independently
- ⚠️ Processing Queue: Files accumulate without processing

**Mitigation:**

- Kubernetes liveness/readiness probes: Auto-restart
- Catch-up on restart: Airflow processes missed schedules
- Manual job submission: Direct API calls to Image Processor

**Recovery Time:** ~1 minute (scheduler restart)

---

## Network & Security

### Service-to-Service Communication

| Source          | Target          | Protocol | Port | Authentication    | Encryption  |
|-----------------|-----------------|----------|------|-------------------|-------------|
| Airflow         | Image Processor | HTTP     | 8080 | None (internal)   | TLS future  |
| Airflow         | Catalog Service | HTTP     | 8080 | None (internal)   | TLS future  |
| Image Processor | Catalog Service | HTTP     | 8080 | None (internal)   | TLS future  |
| Services        | PostgreSQL      | TCP      | 5432 | Username/Password | TLS enabled |
| Services        | S3              | HTTPS    | 443  | IAM Role (IRSA)   | TLS 1.2+    |
| Lambda          | Airflow         | HTTPS    | 443  | API Key           | TLS 1.2+    |

### AWS IAM Roles (IRSA - IAM Roles for Service Accounts)

**Image Processor IAM Policy:**

```json
{
  "Effect": "Allow",
  "Action": [
    "s3:GetObject",
    "s3:PutObject",
    "s3:ListBucket"
  ],
  "Resource": [
    "arn:aws:s3:::astro-raw-data/*",
    "arn:aws:s3:::astro-processed-data/*",
    "arn:aws:s3:::astro-intermediate/*"
  ]
}
```

**Catalog Service IAM Policy:**

```json
{
  "Effect": "Allow",
  "Action": [
    "s3:PutObject"
  ],
  "Resource": [
    "arn:aws:s3:::astro-archive/*"
  ]
}
```

**Airflow IAM Policy:**

```json
{
  "Effect": "Allow",
  "Action": [
    "s3:ListBucket",
    "s3:GetObject"
  ],
  "Resource": [
    "arn:aws:s3:::astro-raw-data",
    "arn:aws:s3:::astro-raw-data/*"
  ]
}
```

---

## Performance & Scalability

### Throughput Targets

| Component       | Current Capacity           | Auto-Scaling Trigger | Max Capacity                       |
|-----------------|----------------------------|----------------------|------------------------------------|
| Image Processor | 500 files/hour             | CPU > 70%            | 10 pods                            |
| Catalog Service | 1000 queries/sec           | CPU > 70%            | 5 pods                             |
| PostgreSQL      | 100 concurrent connections | Manual resize        | 500 connections (instance upgrade) |
| S3              | Unlimited                  | N/A                  | Unlimited                          |
| Airflow         | 3 concurrent DAGs          | N/A (manual)         | 10 concurrent DAGs                 |

### Data Volume Estimates

- **Raw FITS Files**: 100 GB/day → 36 TB/year
- **Processed Images**: 80 GB/day → 29 TB/year
- **Catalog Database**: 10 million objects/year → ~50 GB/year
- **Airflow Metadata**: ~1 GB/year

---

## Monitoring & Observability

### Health Checks

| Service           | Endpoint            | Check Interval | Timeout |
|-------------------|---------------------|----------------|---------|
| Image Processor   | `/actuator/health`  | 30s            | 5s      |
| Catalog Service   | `/actuator/health`  | 30s            | 5s      |
| PostgreSQL        | TCP probe port 5432 | 10s            | 3s      |
| Airflow Scheduler | Process liveness    | 60s            | N/A     |

### Key Metrics

**Image Processor:**

- Processing jobs per minute
- Average processing time per FITS file
- S3 upload/download throughput
- Memory usage per pod

**Catalog Service:**

- Queries per second
- 95th percentile query latency
- Database connection pool utilization
- PostGIS spatial query performance

**PostgreSQL:**

- Active connections
- Query execution time (slow query log)
- Disk I/O utilization
- Replication lag (if read replicas enabled)

**Airflow:**

- DAG success/failure rate
- Task queue depth
- Executor slot utilization
- Scheduler heartbeat

**S3:**

- Request count by bucket
- 4xx/5xx error rate
- Data transfer volume
- Storage usage by bucket/tier

---

## Future Enhancements

### Planned Improvements

1. **Service Mesh (Istio)**
    - Mutual TLS between services
    - Advanced traffic management
    - Circuit breakers for resilience

2. **Event Streaming (Kafka)**
    - Replace Lambda triggers with Kafka topics
    - Event-driven architecture with better replay capability
    - Decouple S3 events from Airflow

3. **Caching Layer (Redis)**
    - Cache frequent catalog queries
    - Session management for Airflow
    - Processing job status cache

4. **Read Replicas for PostgreSQL**
    - Offload read-heavy catalog queries
    - Improve query performance under load
    - Geographic distribution for global access

5. **Cross-Region Replication**
    - S3 cross-region replication for disaster recovery
    - Multi-region RDS deployment
    - Global load balancing

---

## Summary

The Astronomical Data Processing Pipeline is a distributed system with clear separation of concerns:

- **S3** acts as the authoritative data lake for all FITS files
- **Image Processor** handles compute-intensive calibration workflows
- **Catalog Service** manages spatial astronomical data with PostGIS
- **PostgreSQL** provides ACID-compliant persistent storage
- **Airflow** orchestrates complex multi-step workflows

Each component is designed for independent scaling, fault tolerance, and operational simplicity. The architecture
follows cloud-native best practices with Kubernetes orchestration, AWS-managed services, and comprehensive monitoring.
