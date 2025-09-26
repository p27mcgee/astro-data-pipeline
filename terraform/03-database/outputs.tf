# Database Layer Outputs
# These outputs are consumed by other layers

# RDS Instance Outputs
output "rds_endpoint" {
  description = "RDS instance endpoint"
  value       = aws_db_instance.main.endpoint
  sensitive   = true
}

output "rds_port" {
  description = "RDS instance port"
  value       = aws_db_instance.main.port
}

output "rds_database_name" {
  description = "Name of the database"
  value       = aws_db_instance.main.db_name
}

output "rds_username" {
  description = "RDS instance username"
  value       = aws_db_instance.main.username
  sensitive   = true
}

output "rds_instance_id" {
  description = "RDS instance ID"
  value       = aws_db_instance.main.id
}

output "rds_instance_arn" {
  description = "RDS instance ARN"
  value       = aws_db_instance.main.arn
}

# Database Connection String (for applications)
output "database_url" {
  description = "Database connection URL"
  value       = "postgresql://${aws_db_instance.main.username}:${random_password.rds_password.result}@${aws_db_instance.main.endpoint}:${aws_db_instance.main.port}/${aws_db_instance.main.db_name}"
  sensitive   = true
}

# Secrets Manager Outputs
output "rds_credentials_secret_arn" {
  description = "ARN of the RDS credentials secret in Secrets Manager"
  value       = var.enable_secrets_manager ? aws_secretsmanager_secret.rds_credentials[0].arn : null
  sensitive   = true
}

output "rds_credentials_secret_name" {
  description = "Name of the RDS credentials secret in Secrets Manager"
  value       = var.enable_secrets_manager ? aws_secretsmanager_secret.rds_credentials[0].name : null
}

# Database Subnet Group
output "db_subnet_group_name" {
  description = "Name of the database subnet group"
  value       = aws_db_subnet_group.main.name
}

output "db_subnet_group_arn" {
  description = "ARN of the database subnet group"
  value       = aws_db_subnet_group.main.arn
}

# Parameter Group
output "db_parameter_group_name" {
  description = "Name of the database parameter group"
  value       = aws_db_parameter_group.postgres.name
}

# PostGIS Extension Installation Instructions
output "postgis_installation_guide" {
  description = "PostGIS extension installation guide for application developers"
  value       = <<-EOT
    PostGIS Extension: APPLICATION-LEVEL INSTALLATION REQUIRED

    The PostgreSQL database is ready and PostGIS-compatible, but PostGIS extension
    installation is handled by the application layer for simplicity and reliability.

    DATABASE CREDENTIALS:
    - Secrets Manager: ${var.enable_secrets_manager ? aws_secretsmanager_secret.rds_credentials[0].name : "secrets-manager-disabled"}
    - RDS Endpoint: ${aws_db_instance.main.endpoint}
    - Database Name: ${aws_db_instance.main.db_name}

    APPLICATION IMPLEMENTATION:
    Add PostGIS installation to your application's database initialization code:

    Java (Spring Boot):
    @EventListener(ApplicationReadyEvent.class)
    public void initializeDatabase() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis;");
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis_topology;");
        log.info("PostGIS extension installed successfully");
    }

    Python (Django/Flask):
    def initialize_postgis():
        with connection.cursor() as cursor:
            cursor.execute("CREATE EXTENSION IF NOT EXISTS postgis;")
            cursor.execute("CREATE EXTENSION IF NOT EXISTS postgis_topology;")
        connection.commit()

    Node.js:
    async function initializePostGIS(client) {
        await client.query('CREATE EXTENSION IF NOT EXISTS postgis;');
        await client.query('CREATE EXTENSION IF NOT EXISTS postgis_topology;');
        console.log('PostGIS extension installed successfully');
    }

    BENEFITS:
    - Simple and reliable (follows AWS RDS documentation)
    - No additional infrastructure required
    - Idempotent (safe to run multiple times)
    - Application owns its dependencies

    REFERENCE: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.PostGIS.html
  EOT
}