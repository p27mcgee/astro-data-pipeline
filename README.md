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
