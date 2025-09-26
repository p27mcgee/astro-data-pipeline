# Monitoring Layer Outputs

# SNS Topic
output "sns_alerts_topic_arn" {
  description = "ARN of the SNS topic for alerts"
  value       = aws_sns_topic.alerts.arn
}

output "sns_alerts_topic_name" {
  description = "Name of the SNS topic for alerts"
  value       = aws_sns_topic.alerts.name
}

# CloudWatch Dashboard
output "cloudwatch_dashboard_url" {
  description = "URL to the CloudWatch dashboard"
  value       = "https://${var.aws_region}.console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.main.dashboard_name}"
}

output "cloudwatch_dashboard_name" {
  description = "Name of the CloudWatch dashboard"
  value       = aws_cloudwatch_dashboard.main.dashboard_name
}

# CloudWatch Alarms
output "cloudwatch_alarms" {
  description = "List of CloudWatch alarm names and ARNs"
  value = {
    eks_node_count = {
      name = aws_cloudwatch_metric_alarm.eks_node_count.alarm_name
      arn  = aws_cloudwatch_metric_alarm.eks_node_count.arn
    }
    rds_cpu = {
      name = aws_cloudwatch_metric_alarm.rds_cpu.alarm_name
      arn  = aws_cloudwatch_metric_alarm.rds_cpu.arn
    }
    rds_connections = {
      name = aws_cloudwatch_metric_alarm.rds_connections.alarm_name
      arn  = aws_cloudwatch_metric_alarm.rds_connections.arn
    }
    lambda_errors = {
      name = aws_cloudwatch_metric_alarm.lambda_errors.alarm_name
      arn  = aws_cloudwatch_metric_alarm.lambda_errors.arn
    }
  }
}

# Container Insights (if enabled)
output "container_insights_enabled" {
  description = "Whether Container Insights is enabled"
  value       = var.enable_container_insights
}

# Monitoring Summary
output "monitoring_summary" {
  description = "Summary of monitoring setup"
  value = {
    environment         = var.environment
    dashboard_name      = aws_cloudwatch_dashboard.main.dashboard_name
    alerts_topic        = aws_sns_topic.alerts.name
    log_retention_days  = var.cloudwatch_log_retention_days
    container_insights  = var.enable_container_insights
    total_alarms        = 4 + length(data.terraform_remote_state.data.outputs.s3_bucket_names)
    notification_emails = length(var.alarm_notification_emails)
  }
}