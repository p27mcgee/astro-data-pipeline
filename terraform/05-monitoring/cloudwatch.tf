# SNS Topic for Alarms
resource "aws_sns_topic" "alerts" {
  name = "${var.project_name}-alerts"

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-alerts"
  })
}

# SNS Topic Subscription
resource "aws_sns_topic_subscription" "email_alerts" {
  count = length(var.alarm_notification_emails)

  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alarm_notification_emails[count.index]
}

# CloudWatch Dashboard
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "${var.project_name}-${var.environment}"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/EKS", "cluster_node_count", "ClusterName", data.terraform_remote_state.compute.outputs.eks_cluster_name],
            ["AWS/EKS", "cluster_failed_request_count", "ClusterName", data.terraform_remote_state.compute.outputs.eks_cluster_name]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "EKS Cluster Metrics"
          period  = 300
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", data.terraform_remote_state.database.outputs.rds_instance_id],
            ["AWS/RDS", "DatabaseConnections", "DBInstanceIdentifier", data.terraform_remote_state.database.outputs.rds_instance_id],
            ["AWS/RDS", "FreeableMemory", "DBInstanceIdentifier", data.terraform_remote_state.database.outputs.rds_instance_id]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "RDS Database Metrics"
          period  = 300
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 12
        width  = 12
        height = 6
        properties = {
          metrics = [
            for bucket_name, bucket_arn in data.terraform_remote_state.data.outputs.s3_bucket_names : [
              "AWS/S3", "BucketSizeBytes", "BucketName", bucket_name, "StorageType", "StandardStorage"
            ]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "S3 Storage Metrics"
          period  = 86400
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 18
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/Lambda", "Invocations", "FunctionName", data.terraform_remote_state.data.outputs.lambda_function_name],
            ["AWS/Lambda", "Errors", "FunctionName", data.terraform_remote_state.data.outputs.lambda_function_name],
            ["AWS/Lambda", "Duration", "FunctionName", data.terraform_remote_state.data.outputs.lambda_function_name]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Lambda Function Metrics"
          period  = 300
        }
      }
    ]
  })
}

# CloudWatch Alarms

# EKS Node Count Alarm
resource "aws_cloudwatch_metric_alarm" "eks_node_count" {
  alarm_name          = "${var.project_name}-eks-no-nodes"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "cluster_node_count"
  namespace           = "AWS/EKS"
  period              = "300"
  statistic           = "Average"
  threshold           = "1"
  alarm_description   = "This metric monitors EKS cluster node count"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    ClusterName = data.terraform_remote_state.compute.outputs.eks_cluster_name
  }

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-eks-node-count-alarm"
  })
}

# RDS CPU Utilization Alarm
resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  alarm_name          = "${var.project_name}-rds-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This metric monitors RDS CPU utilization"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    DBInstanceIdentifier = data.terraform_remote_state.database.outputs.rds_instance_id
  }

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-rds-cpu-alarm"
  })
}

# RDS Connection Count Alarm
resource "aws_cloudwatch_metric_alarm" "rds_connections" {
  alarm_name          = "${var.project_name}-rds-connections-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = "300"
  statistic           = "Average"
  threshold           = "50"
  alarm_description   = "This metric monitors RDS connection count"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    DBInstanceIdentifier = data.terraform_remote_state.database.outputs.rds_instance_id
  }

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-rds-connections-alarm"
  })
}

# Lambda Error Rate Alarm
resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  alarm_name          = "${var.project_name}-lambda-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = "300"
  statistic           = "Sum"
  threshold           = "5"
  alarm_description   = "This metric monitors Lambda function errors"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    FunctionName = data.terraform_remote_state.data.outputs.lambda_function_name
  }

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-lambda-errors-alarm"
  })
}

# S3 Monitoring (CloudWatch alarms for S3 monitoring)
resource "aws_cloudwatch_metric_alarm" "s3_4xx_errors" {
  for_each = data.terraform_remote_state.data.outputs.s3_bucket_names

  alarm_name          = "${var.project_name}-s3-${each.key}-4xx-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "4xxErrors"
  namespace           = "AWS/S3"
  period              = "300"
  statistic           = "Sum"
  threshold           = "10"
  alarm_description   = "S3 4xx errors for ${each.key} bucket"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    BucketName = each.value
  }

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-s3-${each.key}-4xx-errors"
  })
}

# Container Insights for EKS (optional)
resource "aws_eks_addon" "container_insights" {
  count = var.enable_container_insights ? 1 : 0

  cluster_name                = data.terraform_remote_state.compute.outputs.eks_cluster_name
  addon_name                  = "amazon-cloudwatch-observability"
  addon_version               = data.aws_eks_addon_version.container_insights[0].version
  resolve_conflicts_on_create = "OVERWRITE"
}

data "aws_eks_addon_version" "container_insights" {
  count = var.enable_container_insights ? 1 : 0

  addon_name         = "amazon-cloudwatch-observability"
  kubernetes_version = data.terraform_remote_state.compute.outputs.eks_cluster_version
  most_recent        = true
}