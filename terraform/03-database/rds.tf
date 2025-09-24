# DB Subnet Group
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet-group"
  subnet_ids = data.terraform_remote_state.foundation.outputs.database_subnet_ids

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-db-subnet-group"
  })
}

# DB Parameter Group
resource "aws_db_parameter_group" "postgres" {
  family = "postgres15"
  name   = "${var.project_name}-postgres-params"

  # PostgreSQL configuration optimized for astronomical data
  parameter {
    name  = "shared_preload_libraries"
    value = "pg_stat_statements,postgis"
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
    Name = "${var.project_name}-postgres-params"
  })
}

# Random password for RDS master user
resource "random_password" "rds_password" {
  length  = 16
  special = true
}

# Main RDS Instance
resource "aws_db_instance" "main" {
  identifier = "${var.project_name}-postgres"

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
  multi_az               = var.rds_multi_az
  backup_retention_period = var.rds_backup_retention_period
  backup_window          = "03:00-04:00"
  maintenance_window     = "sun:04:00-sun:05:00"

  # Parameter and option groups
  parameter_group_name = aws_db_parameter_group.postgres.name

  # Monitoring
  performance_insights_enabled = var.rds_performance_insights_enabled
  monitoring_interval          = var.rds_monitoring_interval
  monitoring_role_arn          = var.rds_monitoring_interval > 0 ? aws_iam_role.rds_enhanced_monitoring[0].arn : null

  # Deletion protection (disabled for staging)
  deletion_protection     = var.environment == "prod"
  delete_automated_backups = true
  skip_final_snapshot     = var.environment != "prod"
  final_snapshot_identifier = var.environment == "prod" ? "${var.project_name}-final-snapshot-${formatdate("YYYY-MM-DD-hhmm", timestamp())}" : null

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-postgres"
  })

  depends_on = [
    aws_cloudwatch_log_group.rds
  ]
}

# CloudWatch Log Group for RDS
resource "aws_cloudwatch_log_group" "rds" {
  name              = "/aws/rds/instance/${var.project_name}-postgres/postgresql"
  retention_in_days = 7

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-rds-logs"
  })
}

# IAM role for RDS enhanced monitoring
resource "aws_iam_role" "rds_enhanced_monitoring" {
  count = var.rds_monitoring_interval > 0 ? 1 : 0

  name = "${var.project_name}-rds-enhanced-monitoring"

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
    Name = "${var.project_name}-rds-enhanced-monitoring-role"
  })
}

resource "aws_iam_role_policy_attachment" "rds_enhanced_monitoring" {
  count = var.rds_monitoring_interval > 0 ? 1 : 0

  role       = aws_iam_role.rds_enhanced_monitoring[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# Secrets Manager secret for RDS credentials
resource "aws_secretsmanager_secret" "rds_credentials" {
  count = var.enable_secrets_manager ? 1 : 0

  name        = "${var.project_name}-rds-credentials"
  description = "RDS credentials for ${var.project_name} PostgreSQL database"

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-rds-credentials"
  })
}

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