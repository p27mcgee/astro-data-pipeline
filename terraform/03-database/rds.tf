# Database subnet group using isolated database subnets from foundation layer
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-${var.environment}-db-subnet-group"
  subnet_ids = data.terraform_remote_state.foundation.outputs.database_subnet_ids

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-db-subnet-group"
  })
}

# PostgreSQL parameter group optimized for astronomical data processing
resource "aws_db_parameter_group" "postgres" {
  family = "postgres15"
  name   = "${var.project_name}-${var.environment}-postgres-params"

  # PostgreSQL configuration optimized for astronomical data
  parameter {
    name = "shared_preload_libraries"
    # Note postgis is not a valid shared_preload_libraries
    # After database creation run SQL: CREATE EXTENSION postgis; to install
    value = "pg_stat_statements"
  }

  parameter {
    name  = "log_statement"
    value = "mod" # Log data-modifying statements
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000" # Log queries taking more than 1 second
  }

  parameter {
    name  = "log_checkpoints"
    value = "1"
  }

  parameter {
    name  = "log_lock_waits"
    value = "1"
  }

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-postgres-params"
  })
}

# Generate secure random password for database master user
resource "random_password" "rds_password" {
  length  = 16
  special = true
}

# PostGIS Extension Installation Strategy
# =====================================
#
# PROBLEM: PostgreSQL RDS requires PostGIS spatial extension for astronomical data processing.
# Initial attempts used complex infrastructure approaches:
# - Lambda functions (failed: psycopg2 import errors, VPC networking complexity)
# - ECS Fargate tasks (failed: container image compatibility, AWS CLI vs PostgreSQL client conflicts)
#
# SOLUTION: Application-level installation during database initialization.
# AWS RDS PostgreSQL supports PostGIS via simple SQL: CREATE EXTENSION IF NOT EXISTS postgis;
#
# IMPLEMENTATION: Applications should execute PostGIS installation on startup:
# - Spring Boot: @EventListener(ApplicationReadyEvent.class) with jdbcTemplate.execute()
# - Python: psycopg2 connection with cursor.execute()
# - Node.js: pg client with await client.query()
#
# BENEFITS:
# - Simple and reliable (follows AWS documentation best practices)
# - No additional infrastructure required (zero cost, zero maintenance)
# - Idempotent (safe to run multiple times)
# - Application owns its dependencies (proper separation of concerns)
#
# REFERENCE: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.PostGIS.html

# PostgreSQL RDS instance for astronomical catalog and metadata storage
resource "aws_db_instance" "main" {
  identifier = "${var.project_name}-${var.environment}-postgres"

  # Engine configuration
  engine         = "postgres"
  engine_version = var.rds_engine_version
  instance_class = var.rds_instance_class

  # Storage configuration
  allocated_storage     = var.rds_allocated_storage
  max_allocated_storage = var.rds_max_allocated_storage
  storage_type          = var.rds_storage_type
  storage_encrypted     = var.rds_storage_encrypted

  # Database configuration
  db_name  = var.rds_database_name
  username = var.rds_username
  password = random_password.rds_password.result

  # Network configuration
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [data.terraform_remote_state.foundation.outputs.rds_security_group_id]
  publicly_accessible    = false

  # Availability and backup configuration
  multi_az                = var.rds_multi_az
  backup_retention_period = var.rds_backup_retention_period
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:00-sun:05:00"

  # Parameter and option groups
  parameter_group_name = aws_db_parameter_group.postgres.name

  # Monitoring
  performance_insights_enabled = var.rds_performance_insights_enabled
  monitoring_interval          = var.rds_monitoring_interval
  monitoring_role_arn          = var.rds_monitoring_interval > 0 ? aws_iam_role.rds_enhanced_monitoring[0].arn : null

  # Deletion protection (disabled for staging)
  deletion_protection       = var.environment == "prod"
  delete_automated_backups  = true
  skip_final_snapshot       = var.environment != "prod"
  final_snapshot_identifier = var.environment == "prod" ? "${var.project_name}-${var.environment}-final-snapshot-${formatdate("YYYY-MM-DD-hhmm", timestamp())}" : null

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-postgres"
  })

  depends_on = [
    aws_cloudwatch_log_group.rds
  ]
}

# PostGIS extension will be available in RDS PostgreSQL
# Note: PostGIS extension can be installed manually after deployment using:
# CREATE EXTENSION IF NOT EXISTS postgis;
#
# For automated installation, the application should handle this on first startup
# since the database is in private subnets and not accessible from local terraform

# Lambda function to install PostGIS extension via AWS RDS Data API (future enhancement)
# This would require enabling RDS Data API which has additional costs
# CloudWatch log group for PostgreSQL database logs with KMS encryption
resource "aws_cloudwatch_log_group" "rds" {
  name              = "/aws/rds/instance/${var.project_name}-${var.environment}-postgres/postgresql"
  retention_in_days = 7
  kms_key_id        = var.enable_kms_encryption ? aws_kms_key.cloudwatch_logs[0].arn : null

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-rds-logs"
  })
}

# IAM role allowing RDS service to publish enhanced monitoring metrics
resource "aws_iam_role" "rds_enhanced_monitoring" {
  count = var.rds_monitoring_interval > 0 ? 1 : 0

  name = "${var.project_name}-${var.environment}-rds-enhanced-monitoring"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "monitoring.rds.amazonaws.com"
        }
      }
    ]
  })

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-rds-enhanced-monitoring-role"
  })
}

# Attach AWS managed policy for RDS enhanced monitoring permissions
resource "aws_iam_role_policy_attachment" "rds_enhanced_monitoring" {
  count = var.rds_monitoring_interval > 0 ? 1 : 0

  role       = aws_iam_role.rds_enhanced_monitoring[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# Secrets Manager secret for secure database credential storage
resource "aws_secretsmanager_secret" "rds_credentials" {
  count = var.enable_secrets_manager ? 1 : 0

  name        = "${var.project_name}-${var.environment}-rds-credentials"
  description = "RDS credentials for ${var.project_name} PostgreSQL database"

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-rds-credentials"
  })
}

# Store database connection details in Secrets Manager for application access
resource "aws_secretsmanager_secret_version" "rds_credentials" {
  count = var.enable_secrets_manager ? 1 : 0

  secret_id = aws_secretsmanager_secret.rds_credentials[0].id
  secret_string = jsonencode({
    username = aws_db_instance.main.username
    password = random_password.rds_password.result
    endpoint = aws_db_instance.main.endpoint
    port     = aws_db_instance.main.port
    dbname   = aws_db_instance.main.db_name
  })
}

# Customer-managed KMS key for CloudWatch logs encryption
resource "aws_kms_key" "cloudwatch_logs" {
  count = var.enable_kms_encryption ? 1 : 0

  description             = "KMS key for CloudWatch logs encryption"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  # KMS key policy allowing CloudWatch Logs service
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "Enable IAM User Permissions"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        }
        Action   = "kms:*"
        Resource = "*"
      },
      {
        Sid    = "Allow CloudWatch Logs service"
        Effect = "Allow"
        Principal = {
          Service = "logs.${data.aws_region.current.name}.amazonaws.com"
        }
        Action = [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:DescribeKey"
        ]
        Resource = "*"
        Condition = {
          ArnEquals = {
            "kms:EncryptionContext:aws:logs:arn" = "arn:aws:logs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:log-group:/aws/rds/instance/${var.project_name}-${var.environment}-postgres/postgresql"
          }
        }
      }
    ]
  })

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-cloudwatch-logs-kms-key"
  })
}

# Human-readable alias for the CloudWatch logs encryption KMS key
resource "aws_kms_alias" "cloudwatch_logs" {
  count = var.enable_kms_encryption ? 1 : 0

  name          = "alias/${var.project_name}-${var.environment}-cloudwatch-logs"
  target_key_id = aws_kms_key.cloudwatch_logs[0].key_id
}

# Data sources for KMS key policy
data "aws_caller_identity" "current" {}
data "aws_region" "current" {}