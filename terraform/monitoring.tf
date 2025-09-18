# CloudWatch Log Groups
resource "aws_cloudwatch_log_group" "application_logs" {
  for_each = toset([
    "image-processor",
    "catalog-service",
    "data-simulator"
  ])

  name              = "/aws/eks/${var.project_name}/${each.value}"
  retention_in_days = var.cloudwatch_log_retention_days

  tags = {
    Name        = "${var.project_name}-${each.value}-logs"
    Application = each.value
  }
}

resource "aws_cloudwatch_log_group" "airflow_logs" {
  for_each = toset([
    "scheduler",
    "webserver",
    "worker",
    "triggerer"
  ])

  name              = "/aws/eks/${var.project_name}/airflow/${each.value}"
  retention_in_days = var.cloudwatch_log_retention_days

  tags = {
    Name        = "${var.project_name}-airflow-${each.value}-logs"
    Application = "airflow"
    Component   = each.value
  }
}

# SNS Topic for Alerts
resource "aws_sns_topic" "alerts" {
  name = "${var.project_name}-alerts"

  tags = {
    Name = "${var.project_name}-alerts"
  }
}

# CloudWatch Dashboard for Overall System Monitoring
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "${var.project_name}-overview"

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
            ["AWS/EKS", "cluster_node_count", "ClusterName", aws_eks_cluster.main.name],
            ["AWS/EKS", "cluster_failed_node_count", "ClusterName", aws_eks_cluster.main.name]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "EKS Cluster Health"
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
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", aws_db_instance.main.id],
            ["AWS/RDS", "DatabaseConnections", "DBInstanceIdentifier", aws_db_instance.main.id],
            ["AWS/RDS", "FreeableMemory", "DBInstanceIdentifier", aws_db_instance.main.id]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "RDS Performance"
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
            for bucket_name in keys(var.s3_buckets) : [
              "AWS/S3",
              "BucketSizeBytes",
              "BucketName",
              aws_s3_bucket.data_buckets[bucket_name].id,
              "StorageType",
              "StandardStorage"
            ]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "S3 Storage Usage"
          period  = 86400
        }
      },
      {
        type   = "log"
        x      = 0
        y      = 18
        width  = 24
        height = 6

        properties = {
          query  = "SOURCE '/aws/eks/${var.project_name}/image-processor' | fields @timestamp, @message\n| filter @message like /ERROR/\n| sort @timestamp desc\n| limit 100"
          region = var.aws_region
          title  = "Recent Application Errors"
          view   = "table"
        }
      }
    ]
  })
}

# CloudWatch Dashboard for Processing Pipeline Metrics
resource "aws_cloudwatch_dashboard" "pipeline" {
  dashboard_name = "${var.project_name}-pipeline"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 8
        height = 6

        properties = {
          metrics = [
            ["AWS/Lambda", "Invocations", "FunctionName", aws_lambda_function.s3_trigger.function_name],
            ["AWS/Lambda", "Errors", "FunctionName", aws_lambda_function.s3_trigger.function_name],
            ["AWS/Lambda", "Duration", "FunctionName", aws_lambda_function.s3_trigger.function_name]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "S3 Trigger Lambda"
          period  = 300
        }
      },
      {
        type   = "log"
        x      = 8
        y      = 0
        width  = 16
        height = 6

        properties = {
          query  = "SOURCE '/aws/lambda/${aws_lambda_function.s3_trigger.function_name}' | fields @timestamp, @message\n| filter @message like /Processing/\n| sort @timestamp desc\n| limit 50"
          region = var.aws_region
          title  = "S3 Processing Events"
          view   = "table"
        }
      },
      {
        type   = "log"
        x      = 0
        y      = 6
        width  = 24
        height = 6

        properties = {
          query  = "SOURCE '/aws/eks/${var.project_name}/airflow/scheduler' | fields @timestamp, @message\n| filter @message like /DAG/\n| sort @timestamp desc\n| limit 100"
          region = var.aws_region
          title  = "Airflow DAG Executions"
          view   = "table"
        }
      }
    ]
  })
}

# CloudWatch Alarms for EKS Cluster
resource "aws_cloudwatch_metric_alarm" "eks_node_cpu" {
  alarm_name          = "${var.project_name}-eks-node-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "node_cpu_utilization"
  namespace           = "ContainerInsights"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This metric monitors EKS node CPU utilization"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    ClusterName = aws_eks_cluster.main.name
  }

  tags = {
    Name = "${var.project_name}-eks-node-cpu-alarm"
  }
}

resource "aws_cloudwatch_metric_alarm" "eks_node_memory" {
  alarm_name          = "${var.project_name}-eks-node-memory-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "node_memory_utilization"
  namespace           = "ContainerInsights"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This metric monitors EKS node memory utilization"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    ClusterName = aws_eks_cluster.main.name
  }

  tags = {
    Name = "${var.project_name}-eks-node-memory-alarm"
  }
}

# CloudWatch Alarms for Lambda Functions
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

  dimensions = {
    FunctionName = aws_lambda_function.s3_trigger.function_name
  }

  tags = {
    Name = "${var.project_name}-lambda-errors-alarm"
  }
}

# CloudWatch Insight Queries for troubleshooting
resource "aws_cloudwatch_query_definition" "error_analysis" {
  name = "${var.project_name}-error-analysis"

  log_group_names = [
    aws_cloudwatch_log_group.application_logs["image-processor"].name,
    aws_cloudwatch_log_group.application_logs["catalog-service"].name
  ]

  query_string = <<EOF
fields @timestamp, @message
| filter @message like /ERROR/
| stats count() by bin(5m)
| sort @timestamp desc
EOF
}

resource "aws_cloudwatch_query_definition" "performance_analysis" {
  name = "${var.project_name}-performance-analysis"

  log_group_names = [
    aws_cloudwatch_log_group.application_logs["image-processor"].name
  ]

  query_string = <<EOF
fields @timestamp, @message
| filter @message like /processing_time/
| parse @message /processing_time: (?<duration>\d+)/
| stats avg(duration), max(duration), min(duration) by bin(5m)
| sort @timestamp desc
EOF
}

# Container Insights for EKS
resource "aws_eks_addon" "container_insights" {
  count = var.enable_container_insights ? 1 : 0

  cluster_name      = aws_eks_cluster.main.name
  addon_name        = "amazon-cloudwatch-observability"
  addon_version     = data.aws_eks_addon_version.container_insights[0].version
  resolve_conflicts = "OVERWRITE"
}

data "aws_eks_addon_version" "container_insights" {
  count = var.enable_container_insights ? 1 : 0

  addon_name         = "amazon-cloudwatch-observability"
  kubernetes_version = aws_eks_cluster.main.version
  most_recent        = true
}

# X-Ray Tracing for distributed tracing
resource "aws_iam_role" "xray_daemon" {
  name = "${var.project_name}-xray-daemon-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${var.project_name}-xray-daemon-role"
  }
}

resource "aws_iam_role_policy_attachment" "xray_daemon" {
  policy_arn = "arn:aws:iam::aws:policy/AWSXRayDaemonWriteAccess"
  role       = aws_iam_role.xray_daemon.name
}

# Custom CloudWatch Metrics for Application
resource "aws_cloudwatch_log_metric_filter" "processing_success" {
  name           = "${var.project_name}-processing-success"
  log_group_name = aws_cloudwatch_log_group.application_logs["image-processor"].name
  pattern        = "[timestamp, request_id, level=\"INFO\", message=\"Processing completed\", duration, ...]"

  metric_transformation {
    name      = "ProcessingSuccess"
    namespace = "${var.project_name}/Processing"
    value     = "1"
  }
}

resource "aws_cloudwatch_log_metric_filter" "processing_duration" {
  name           = "${var.project_name}-processing-duration"
  log_group_name = aws_cloudwatch_log_group.application_logs["image-processor"].name
  pattern        = "[timestamp, request_id, level=\"INFO\", message=\"Processing completed\", duration, ...]"

  metric_transformation {
    name      = "ProcessingDuration"
    namespace = "${var.project_name}/Processing"
    value     = "$duration"
    unit      = "Seconds"
  }
}

resource "aws_cloudwatch_log_metric_filter" "processing_errors" {
  name           = "${var.project_name}-processing-errors"
  log_group_name = aws_cloudwatch_log_group.application_logs["image-processor"].name
  pattern        = "[timestamp, request_id, level=\"ERROR\", ...]"

  metric_transformation {
    name      = "ProcessingErrors"
    namespace = "${var.project_name}/Processing"
    value     = "1"
  }
}

# CloudWatch Alarm for Processing Success Rate
resource "aws_cloudwatch_metric_alarm" "processing_success_rate" {
  alarm_name          = "${var.project_name}-processing-success-rate-low"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = "2"
  threshold           = "0.95"
  alarm_description   = "Processing success rate is below 95%"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  metric_query {
    id          = "success_rate"
    return_data = true

    metric {
      metric_name = "ProcessingSuccess"
      namespace   = "${var.project_name}/Processing"
      period      = 300
      stat        = "Sum"
    }
  }

  metric_query {
    id          = "total_requests"
    return_data = false

    expression = "success_rate + errors"
  }

  metric_query {
    id          = "errors"
    return_data = false

    metric {
      metric_name = "ProcessingErrors"
      namespace   = "${var.project_name}/Processing"
      period      = 300
      stat        = "Sum"
    }
  }

  tags = {
    Name = "${var.project_name}-processing-success-rate-alarm"
  }
}

# Cost monitoring
resource "aws_budgets_budget" "monthly_cost" {
  count = var.environment == "prod" ? 1 : 0

  name         = "${var.project_name}-monthly-budget"
  budget_type  = "COST"
  limit_amount = "500"
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  cost_filters {
    tag {
      key    = "Project"
      values = [var.project_name]
    }
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = ["alerts@example.com"]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = ["alerts@example.com"]
  }
}