# Astronomical Data Processing Pipeline

An astronomical image calibration pipeline designed for the Roman Space Telescope mission, featuring
cloud-native data processing.

**📋 For HR/Hiring Managers**: See [EXECUTIVE-SUMMARY.md](EXECUTIVE-SUMMARY.md) for a concise overview of this portfolio
project.

---

## 🌟 Overview

This project implements an astronomical data processing system that:
- Processes raw telescope images (FITS files) through a calibration pipeline
- Does dark frame subtraction, flat field correction, and cosmic ray removal
- Generates astronomical object catalogs from processed images
- Runs on AWS cloud infrastructure with Kubernetes orchestration
- Uses Apache Airflow for workflow management

## 🏗️ Architecture

### Core Technologies
- **Backend**: Java Spring Boot microservices
- **Orchestration**: Apache Airflow with Kubernetes Executor  
- **Infrastructure**: AWS (EKS, RDS, S3) managed via Terraform
- **Data Storage**: PostgreSQL + S3 data lake architecture
- **CI/CD**: GitHub Actions with automated deployment

### Processing Pipeline
```
Raw FITS Files → Dark Subtraction → Flat Correction → Cosmic Ray Removal → Image Stacking → Catalog Generation
```

### 🆔 Processing ID System & Data Segregation

This system implements a comprehensive **processing ID architecture** that enables:

- **Production vs Experimental Data Segregation**
- **Database Partitioning for Efficient Querying**
- **S3 Organization by Processing Context**
- **Research Experiment Tracking and Lineage**

#### Processing ID Schema

All processing operations are assigned a unique processing ID with the format:

```
{type}-{timestamp}-{uuid}
```

**Examples:**

- `prod-20240928-14a7f2b3-8c45-4d12-9f3e-abc123def456` (Production)
- `exp-20240928-67d8e9f1-2a34-4b56-8c90-def456abc123` (Experimental)
- `test-20240928-89ab123c-4d56-7e89-0f12-345678901234` (Testing)

#### Processing Types

| Type             | Prefix | Description                 | Use Case                                   |
|------------------|--------|-----------------------------|--------------------------------------------|
| **Production**   | `prod` | Operational data processing | Standard telescope data reduction          |
| **Experimental** | `exp`  | Research processing         | Algorithm testing, parameter optimization  |
| **Test**         | `test` | Development testing         | CI/CD, integration testing                 |
| **Validation**   | `val`  | Quality validation          | Data quality assessment                    |
| **Reprocessing** | `repr` | Historical reprocessing     | Batch reprocessing with updated algorithms |

#### Data Segregation Architecture

**Database Partitioning:**

```sql
-- Partition key format: {type}_{YYYYMM}
partition_key VARCHAR(50) GENERATED ALWAYS AS (
    processing_type || '_' || to_char(created_at, 'YYYYMM')
) STORED

-- Examples: prod_202409, exp_202409, test_202409
```

**S3 Organization:**

```
s3://astro-processed-data/
├── production/{date}/{processing-id}/
│   ├── processed_image.fits
│   ├── catalog.csv
│   └── quality_metrics.json
├── experimental/{experiment-name}/{date}/{processing-id}/
│   ├── algorithm_test_results.fits
│   ├── comparison_metrics.json
│   └── research_notes.md
└── test/{date}/{processing-id}/
    └── validation_results/
```

#### Production Processing Context

For operational telescope data processing:

```json
{
  "processingType": "production",
  "productionContext": {
    "observationId": "OBS-2024-001",
    "instrumentId": "WFC3",
    "telescopeId": "HST",
    "programId": "GO-12345",
    "priority": 1,
    "dataReleaseVersion": "DR1"
  }
}
```

#### Experimental Processing Context

For research and algorithm development:

```json
{
  "processingType": "experimental",
  "experimentContext": {
    "experimentName": "Cosmic Ray Algorithm Comparison",
    "experimentDescription": "Comparing L.A.Cosmic vs median filter",
    "researcherId": "astronomer123",
    "researcherEmail": "astronomer@stsci.edu",
    "projectId": "PROJ-001",
    "hypothesis": "L.A.Cosmic v2 provides better star preservation"
  }
}
```

#### Data Lineage Tracking

The system tracks complete data lineage for derived processing:

```json
{
  "dataLineage": {
    "inputImagePath": "s3://raw-data/observation_001.fits",
    "inputImageChecksum": "sha256:abc123...",
    "previousProcessingId": "prod-20240928-parent-id",
    "rootProcessingId": "prod-20240928-root-id",
    "processingDepth": 2,
    "calibrationFrames": {
      "dark": "calibration/dark_master_20240928.fits",
      "flat": "calibration/flat_master_20240928.fits"
    }
  }
}
```

#### API Integration

**Creating Production Context:**

```bash
curl -X POST "/api/v1/processing/steps/bias-subtract" \
  -H "Content-Type: application/json" \
  -d '{
    "imagePath": "s3://raw-data/observation.fits",
    "sessionId": "production-session-001",
    "processingType": "production",
    "productionContext": {
      "observationId": "OBS-2024-001",
      "instrumentId": "WFC3",
      "telescopeId": "HST"
    }
  }'
```

**Creating Experimental Context:**

```bash
curl -X POST "/api/v1/processing/steps/cosmic-ray-remove" \
  -H "Content-Type: application/json" \
  -d '{
    "imagePath": "s3://test-data/sample.fits",
    "sessionId": "cosmic-ray-experiment-001",
    "processingType": "experimental",
    "algorithm": "lacosmic-v2",
    "parameters": {"sigclip": 4.5, "starPreservation": true},
    "experimentContext": {
      "experimentName": "L.A.Cosmic Parameter Optimization",
      "researcherId": "astronomer123",
      "projectId": "PROJ-001"
    }
  }'
```

#### Database Views for Data Access

**Production Data Only:**

```sql
SELECT * FROM production_astronomical_objects
WHERE observation_id = 'OBS-2024-001';
```

**Experimental Data by Researcher:**

```sql
SELECT * FROM experimental_astronomical_objects
WHERE processing_id IN (
  SELECT processing_id FROM processing_contexts
  WHERE researcher_id = 'astronomer123'
);
```

**Recent Processing Results:**

```sql
SELECT * FROM recent_processing_results
WHERE processing_type = 'experimental'
AND created_at >= CURRENT_DATE - INTERVAL '7 days';
```

#### Airflow Integration

The processing ID system is fully integrated with Airflow workflows:

```python
# Production processing
production_task = BiasSubtractionOperator(
    task_id='production_bias_subtract',
    image_path='{{ params.input_image }}',
    session_id='{{ ds }}-production',
    processing_type='production',
    observation_id='{{ params.observation_id }}',
    instrument_id='{{ params.instrument_id }}'
)

# Experimental processing
experiment_task = CosmicRayRemovalOperator(
    task_id='experiment_cosmic_rays',
    image_path='{{ params.test_image }}',
    session_id='{{ ds }}-cosmic-ray-exp',
    processing_type='experimental',
    experiment_name='L.A.Cosmic Comparison',
    researcher_id='{{ params.researcher_id }}',
    algorithm='lacosmic-v2'
)
```

### 🔄 Advanced Workflow Versioning & Management

Building on the processing ID foundation, the system implements **comprehensive workflow versioning** that enables:

#### Multi-Version Production Support

**Multiple Active Workflows:**

```bash
# List active workflows
curl -X GET "/api/v1/workflows/active?processingType=production"

# Example response showing A/B testing
[
  {
    "workflowName": "cosmic-ray-removal",
    "workflowVersion": "v1.1",
    "trafficSplitPercentage": 80.0,
    "isDefault": true
  },
  {
    "workflowName": "cosmic-ray-removal",
    "workflowVersion": "v1.2",
    "trafficSplitPercentage": 20.0,
    "isDefault": false
  }
]
```

#### Experimental → Production Promotion

**Seamless Workflow Promotion:**

```bash
# Promote experimental workflow to production
curl -X POST "/api/v1/workflows/experimental/cosmic-ray-v2.1/promote" \
  -H "Content-Type: application/json" \
  -d '{
    "newProductionVersion": "v1.3",
    "activatedBy": "astronomer123",
    "reason": "15% improvement in star preservation",
    "performanceMetrics": {
      "starPreservation": 0.97,
      "processingTime": 1800,
      "qualityScore": 92.0
    }
  }'
```

#### Intelligent Workflow Selection

**Traffic Splitting for A/B Testing:**

```python
# Airflow automatically selects workflow based on traffic split
cosmic_ray_task = CosmicRayRemovalOperator(
    task_id='remove_cosmic_rays',
    image_path='{{ params.input_image }}',
    session_id='{{ ds }}-production',
    use_active_workflow=True,  # Auto-selects based on traffic %
    processing_type='production'
)
```

**Session-Based Consistent Assignment:**

- Uses consistent hashing based on `sessionId`
- Same session always gets same workflow version
- Ensures reproducible results across processing steps

#### Workflow Activation Management

**Activate New Version:**

```bash
curl -X POST "/api/v1/workflows/cosmic-ray-removal/versions/v1.2/activate" \
  -H "Content-Type: application/json" \
  -d '{
    "activatedBy": "ops-team",
    "reason": "Improved algorithm performance",
    "trafficSplitPercentage": 100.0,
    "deactivateOthers": true
  }'
```

**Emergency Rollback:**

```bash
curl -X POST "/api/v1/workflows/cosmic-ray-removal/rollback" \
  -H "Content-Type: application/json" \
  -d '{
    "targetVersion": "v1.1",
    "performedBy": "ops-team",
    "reason": "Critical bug in v1.2"
  }'
```

#### Enhanced Airflow Integration

**New Workflow-Aware Operators:**

```python
# Automatic active workflow selection
active_processing = ActiveWorkflowOperator(
    task_id='process_with_active_workflow',
    image_path='{{ params.input_image }}',
    workflow_type='cosmic-ray-removal',
    processing_type='production'
)

# Workflow performance comparison
comparison = WorkflowComparisonOperator(
    task_id='compare_algorithms',
    workflow_name='cosmic-ray-removal',
    baseline_version='v1.1',
    comparison_version='v1.2'
)

# Experimental to production promotion
promotion = WorkflowPromotionOperator(
    task_id='promote_to_production',
    experiment_name='cosmic-ray-v2.1',
    new_production_version='v1.3',
    promoted_by='{{ params.researcher_id }}'
)
```

#### Workflow Performance Analytics

**Comprehensive Metrics Collection:**

```sql
-- Query workflow performance trends
SELECT
    wv.workflow_name,
    wv.workflow_version,
    wv.performance_metrics->'avg_processing_time_ms' as avg_time,
    wv.quality_metrics->'star_preservation_rate' as quality,
    wv.usage_statistics->'usage_count' as usage_count
FROM workflow_versions wv
WHERE wv.is_active = true
ORDER BY wv.workflow_name, wv.activated_at DESC;
```

**Real-time Workflow Monitoring:**

```bash
# Get workflow activation history
curl -X GET "/api/v1/workflows/cosmic-ray-removal/history?limit=10"

# Monitor active workflow performance
curl -X GET "/api/v1/workflows/active" | jq '.[] | {name: .workflowName, version: .workflowVersion, traffic: .trafficSplitPercentage}'
```

#### Workflow Management CLI

**Example Operations:**

```bash
# List all active workflows
astro-cli workflows list --active

# Activate new version with 20% traffic
astro-cli workflows activate cosmic-ray-removal v1.2 \
  --traffic-split 20 \
  --reason "Gradual rollout of improved algorithm"

# Compare workflow performance
astro-cli workflows compare cosmic-ray-removal v1.1 v1.2 \
  --metrics processing_time,quality_score,star_preservation

# Promote experimental workflow
astro-cli workflows promote experimental/cosmic-ray-v2.1 \
  --to-version v1.3 \
  --reason "ML-enhanced algorithm shows 15% improvement"

# Emergency rollback
astro-cli workflows rollback cosmic-ray-removal \
  --to-version v1.1 \
  --reason "Critical performance regression in v1.2"
```

#### Benefits of Processing ID + Workflow System

**For Operations:**

- ✅ **Clear Data Segregation** - Production and experimental data never mix
- ✅ **Efficient Querying** - Database partitioning enables fast data access
- ✅ **Organized Storage** - S3 hierarchy reflects processing context
- ✅ **Audit Trail** - Complete lineage tracking for all processing
- ✅ **Zero-Downtime Operations** - Seamless workflow version management
- ✅ **Risk-Free Deployments** - A/B testing and gradual rollouts
- ✅ **Emergency Procedures** - Quick rollback and incident response

**For Research:**

- ✅ **Experiment Tracking** - Every experiment has unique identification
- ✅ **Reproducibility** - Complete parameter and context preservation
- ✅ **Comparison Analysis** - Easy comparison between experimental runs
- ✅ **Collaboration** - Researcher-specific data organization
- ✅ **Production Pipeline** - Clear path from research to production
- ✅ **Performance Validation** - Automated A/B testing capabilities
- ✅ **Algorithm Evolution** - Track workflow improvements over time

**For System Management:**

- ✅ **Performance Optimization** - Partition pruning reduces query time
- ✅ **Storage Management** - Lifecycle policies per processing type
- ✅ **Resource Isolation** - Separate compute resources for different workloads
- ✅ **Data Governance** - Clear ownership and retention policies
- ✅ **Automated Operations** - API-driven workflow lifecycle management
- ✅ **Multi-Version Support** - Multiple production workflows with traffic splitting

## 🚀 Quick Start

### Prerequisites
- AWS CLI configured with appropriate permissions
- Docker and Docker Compose installed
- Terraform >= 1.0
- kubectl configured for EKS access
- Java 17+ and Gradle for local development

### Local Development

#### Standard Development Workflow
```bash
# Clone and setup
git clone <repository-url>
cd astro-data-pipeline

# Start local services
docker-compose up -d

# Build Java services
cd application
./gradlew build

# Run unit tests (H2 in-memory database)
./gradlew test

# Run integration tests (H2 in-memory database)
./gradlew integrationTest
```

#### Testing Against PostgreSQL (CI Environment Simulation)
For developers who want to test integration tests against the same PostgreSQL + PostGIS setup used in the CI pipeline:

```bash
# Start PostgreSQL container (identical to CI environment)
docker run -d --name astro-postgres-test \
  -e POSTGRES_DB=astro_test \
  -e POSTGRES_USER=test_user \
  -e POSTGRES_PASSWORD=test_password \
  -p 5432:5432 \
  postgis/postgis:15-3.3

# Wait for PostgreSQL to be ready
sleep 10

# Run integration tests against PostgreSQL (simulates CI pipeline)
cd application
SPRING_PROFILES_ACTIVE=ci-integration \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/astro_test \
SPRING_DATASOURCE_USERNAME=test_user \
SPRING_DATASOURCE_PASSWORD=test_password \
./gradlew integrationTest

# Clean up PostgreSQL container
docker stop astro-postgres-test && docker rm astro-postgres-test
```

**When to use PostgreSQL testing:**
- Before pushing changes that modify database entities or JPA configurations
- When troubleshooting CI pipeline failures related to database operations
- To validate that schema validation bypass settings work correctly with PostGIS
- When testing spatial queries or PostgreSQL-specific functionality

**Note:** The CI pipeline uses schema validation bypass (`allow_jdbc_metadata_access: false`) to prevent conflicts with PostGIS extensions while maintaining full DDL generation capability.

### AWS Deployment
```bash
# Deploy infrastructure
cd terraform
terraform init
terraform apply -var-file="production.tfvars"

# Deploy applications
kubectl apply -f kubernetes/base/
```

## 📁 Project Structure

```
├── terraform/           # AWS infrastructure (EKS, RDS, S3)
├── application/         # Java Spring Boot microservices  
├── airflow/            # Data processing workflows
├── kubernetes/         # Container orchestration configs
├── scripts/            # Automation and testing utilities
└── docs/              # Technical documentation
```

## 🔧 Key Features

- **High-Performance Processing**: Parallel FITS file processing with memory optimization
- **Scalable Architecture**: Kubernetes-based auto-scaling for variable workloads  
- **Scientific Accuracy**: Standards-compliant astronomical data reduction
- **Observability**: Comprehensive monitoring and performance metrics
- **Cost Optimization**: Intelligent resource management and storage tiering

## 📊 Performance Goals

- **Processing Speed**: 500+ FITS files per hour per node
- **Data Throughput**: 10GB/hour sustained processing rate
- **Scalability**: Auto-scales from 2-20 nodes based on queue depth
- **Availability**: 99.9% uptime with multi-AZ deployment

## 🔒 Security

- VPC with private subnets for compute resources
- IAM roles with least-privilege access
- Encryption at rest and in transit
- Security scanning integrated in CI/CD pipeline

## 🧪 Testing Strategy

This project implements a **multi-tier testing architecture** optimized for both speed and confidence:

### Testing Tiers

1. **Unit Tests (H2)** - Fast feedback loop
2. **Integration Tests (H2)** - Local development speed
3. **CI Integration Tests (PostgreSQL)** - Production validation

### Local Testing Commands

```bash
# Unit tests (fast, H2 in-memory database)
./gradlew test

# Integration tests (fast, H2 in-memory database)
./gradlew integrationTest

# Performance tests
cd scripts/performance-testing
python benchmark_pipeline.py
```

### Multi-Database Testing Architecture

#### Default Behavior (H2)
- **Local Development**: H2 in-memory database for instant feedback
- **Unit Tests**: H2 with PostgreSQL compatibility mode
- **Integration Tests**: H2 for fast test execution
- **Profile**: Automatically uses `test` profile

#### CI Environment (PostgreSQL + PostGIS)
- **GitHub Actions**: PostgreSQL with PostGIS spatial extensions
- **Profile**: Automatically activates `ci-integration` profile
- **Database**: Real PostgreSQL for production confidence
- **Schema Validation**: Disabled to prevent PostGIS conflicts

### Testing Different Database Backends

#### Option 1: Default H2 Testing (Recommended for Development)
```bash
# Fast local testing with H2 (default)
./gradlew integrationTest
```

#### Option 2: PostgreSQL Testing (CI Simulation)
```bash
# Start PostgreSQL container (matches CI environment exactly)
docker run -d --name astro-postgres-test \
  -e POSTGRES_DB=astro_test \
  -e POSTGRES_USER=test_user \
  -e POSTGRES_PASSWORD=test_password \
  -p 5432:5432 \
  postgis/postgis:15-3.3

# Wait for PostgreSQL to initialize
sleep 15

# Run integration tests against PostgreSQL
SPRING_PROFILES_ACTIVE=ci-integration \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/astro_test \
SPRING_DATASOURCE_USERNAME=test_user \
SPRING_DATASOURCE_PASSWORD=test_password \
./gradlew integrationTest

# Cleanup
docker stop astro-postgres-test && docker rm astro-postgres-test
```

### When to Use Each Testing Approach

#### Use H2 Testing (Default) For:
- ✅ Day-to-day development and testing
- ✅ Fast feedback during code changes
- ✅ TDD and rapid iteration
- ✅ CI/CD unit test stage (< 2 minutes)

#### Use PostgreSQL Testing For:
- ✅ Before pushing database schema changes
- ✅ Troubleshooting CI pipeline failures
- ✅ Testing spatial queries or PostGIS features
- ✅ Validating production database compatibility
- ✅ Final validation before release

### CI/CD Pipeline Architecture

The project uses a **dual-workflow strategy** that separates code validation from deployment:

#### 🔄 CI Workflow (Code Validation) - Runs on ALL branches
```
Dev Branch Push → Fast Validation Pipeline
┌─────────────────┐    ┌──────────────────────┐    ┌─────────────────┐
│   Unit Tests    │ -> │  Integration Tests   │ -> │Code Quality +   │
│   (H2 - Fast)   │    │ (PostgreSQL - Real)  │    │JAR Build Only   │
│   < 2 minutes   │    │    < 3 minutes       │    │  < 2 minutes    │
└─────────────────┘    └──────────────────────┘    └─────────────────┘
```

#### 🚀 CD Workflow (Build & Deploy) - Runs on MAIN branch only
```
Main Branch Merge → Full Build & Deployment Pipeline
┌─────────────────┐    ┌──────────────────────┐    ┌─────────────────┐
│  Docker Build   │ -> │  Security Scanning   │ -> │ Deploy Pipeline │
│ + ECR Push      │    │   (Trivy + Snyk)     │    │Stage → Prod     │
│  < 5 minutes    │    │    < 3 minutes       │    │  < 10 minutes   │
└─────────────────┘    └──────────────────────┘    └─────────────────┘
```

### Workflow Details

#### 🔄 CI Workflow: `ci-cd-pipeline.yml`
**Triggers:**
- Push to **any branch** (`feature/*`, `dev/*`, `bugfix/*`, etc.)
- Pull requests to `main` or `develop`

**Pipeline Steps:**
1. **Unit Tests** (H2) - Fast validation of business logic
2. **Integration Tests** (PostgreSQL + PostGIS) - Database integration validation
3. **Code Quality** - Checkstyle, SpotBugs, SonarCloud analysis
4. **Application Build** - JAR compilation and artifact creation
5. **Dependency Security** - Snyk vulnerability scanning

**Key Features:**
- ✅ **Fast feedback** for developers (~7 minutes total)
- ✅ **No Docker builds** - pure code validation
- ✅ **No deployments** - development-focused
- ✅ **Artifact upload** for potential reuse

#### 🚀 CD Workflow: `cd-deploy.yml`
**Triggers:**
- Push to `main` branch (after PR merge)
- Manual workflow dispatch with environment selection

**Pipeline Steps:**
1. **Application Build** - Fresh JAR compilation
2. **Docker Image Creation** - Multi-platform container builds
3. **ECR Publishing** - Push to Amazon Container Registry
4. **Container Security** - Trivy vulnerability scanning
5. **Staging Deployment** - Automated deployment with health checks
6. **Production Deployment** - Blue-green deployment after staging success

**Key Features:**
- ✅ **Production-ready images** with security scanning
- ✅ **Automated staging deployment** for validation
- ✅ **Blue-green production deployment** for zero downtime
- ✅ **Health checks and smoke tests** at each stage
- ✅ **Slack notifications** for deployment status

### Developer Workflow

#### 🔨 Development Cycle
```bash
# 1. Work on feature branch
git checkout -b feature/new-functionality
# ... make changes ...

# 2. Push to trigger CI validation (fast feedback)
git push origin feature/new-functionality
# ✅ CI runs: Tests → Quality → Build → Security

# 3. Create PR when ready
gh pr create --title "Add new functionality" --body "Description"
# ✅ CI runs again on PR

# 4. Merge to main triggers deployment
gh pr merge --squash
# ✅ CD runs: Build → Push → Deploy → Notify
```

#### ⚡ Quick Commands
```bash
# Run same tests as CI locally
./gradlew test integrationTest

# Simulate CI environment with PostgreSQL + PostGIS
docker run -d --name astro-postgres-test \
  -e POSTGRES_DB=astro_test \
  -e POSTGRES_USER=test_user \
  -e POSTGRES_PASSWORD=test_password \
  -p 5432:5432 postgis/postgis:15-3.3

# Wait for PostgreSQL to be ready
sleep 15

# Run integration tests (Flyway automatically creates schema)
SPRING_PROFILES_ACTIVE=ci-integration \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/astro_test \
SPRING_DATASOURCE_USERNAME=test_user \
SPRING_DATASOURCE_PASSWORD=test_password \
./gradlew integrationTest

# Cleanup
docker rm -f astro-postgres-test
```

### Test Configuration Details

The testing strategy uses **dynamic profile resolution** with Spring's `${SPRING_PROFILES_ACTIVE:test}` pattern:

- **Local Default**: `test` profile → H2 database + Hibernate DDL auto-generation
- **CI Environment**: `ci-integration` profile → PostgreSQL + PostGIS + Flyway schema management
- **Schema Management**: Flyway migrations provide consistent schema across all PostgreSQL environments
- **PostGIS Integration**: Uses `PostgisDialect` with spatial extension support
- **Zero Configuration**: No developer setup required

### Database Schema Management Strategy

#### Why Flyway is Used for PostGIS Development

**The Challenge**: PostGIS adds spatial extensions and custom data types that Hibernate's schema validator doesn't fully understand, causing validation conflicts even when the schema is correct. This is a common issue in the PostGIS + Hibernate community.

**The Solution**: Use Flyway for explicit schema management with PostgreSQL environments:

```yaml
# ci-integration profile configuration
spring:
  jpa:
    database-platform: org.hibernate.spatial.dialect.postgis.PostgisDialect
    hibernate:
      ddl-auto: validate  # Validate against Flyway-managed schema
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

**Migration Files** (Developer Editable):
```
application/image-processor/src/main/resources/db/migration/
├── V1__Create_processing_jobs_table.sql      # Main processing jobs table
├── V2__Create_processing_job_metadata_table.sql  # Key-value metadata storage
└── V3__Create_processing_job_steps_table.sql     # Processing step tracking
```

#### Alternative: Hibernate-Only Configuration (Without Flyway)

For developers who prefer pure Hibernate management, you can configure PostGIS with complete schema validation bypass:

```yaml
# Alternative configuration without Flyway
spring:
  jpa:
    database-platform: org.hibernate.spatial.dialect.postgis.PostgisDialect
    hibernate:
      ddl-auto: create-drop  # or update
    properties:
      hibernate:
        boot:
          allow_jdbc_metadata_access: false
        validator:
          apply_to_ddl: false
          autoregister_listeners: false
        jdbc:
          lob:
            non_contextual_creation: true
  flyway:
    enabled: false
```

**Trade-offs**:
- ✅ **Hibernate-Only**: Simpler configuration, no migration files needed
- ❌ **Schema Drift Risk**: Test and production schemas may diverge
- ❌ **PostGIS Conflicts**: May encounter spatial extension validation issues
- ❌ **Version Control**: Schema changes not tracked in git

#### Recommended Approach

**Use Flyway for PostgreSQL environments** because:
- ✅ **PostGIS Compatibility**: Avoids spatial extension validation conflicts
- ✅ **Production Ready**: Same schema management as production systems
- ✅ **Version Controlled**: Schema changes tracked and reviewable
- ✅ **Team Collaboration**: Explicit schema changes visible to all developers
- ✅ **Environment Consistency**: Eliminates schema drift between environments

**Keep Hibernate DDL for H2 testing** because:
- ✅ **Development Speed**: Fast schema generation for rapid iteration
- ✅ **Test Isolation**: Each test gets fresh schema
- ✅ **Entity Validation**: Ensures JPA mappings are correct

## 🎯 STScI Portfolio Showcase

This project demonstrates:
- **Cloud Architecture**: Enterprise AWS infrastructure with Terraform
- **Microservices Design**: Spring Boot services with proper separation of concerns  
- **Data Engineering**: High-volume astronomical data processing pipelines
- **DevOps Excellence**: Complete CI/CD with monitoring and optimization
- **Scientific Computing**: Domain expertise in astronomical data reduction
