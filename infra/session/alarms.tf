# Health alarms for an always-on demo (infra/README.md section 3L). Off unless alarm_email is set, so a normal
# session plan is unchanged. The address goes in the git-ignored live.tfvars, never in a committed file.
# SNS sends a confirmation mail to the address; nothing is delivered until the link in it is clicked.

variable "alarm_email" {
  type    = string
  default = ""
}

locals {
  alarms_on = var.alarm_email != ""
  health_alarms = {
    api = aws_lb_target_group.api.arn_suffix
    web = aws_lb_target_group.web.arn_suffix
  }
}

resource "aws_sns_topic" "alarms" {
  count = local.alarms_on ? 1 : 0
  name  = "${var.name}-alarms"
}

resource "aws_sns_topic_subscription" "alarms_email" {
  count     = local.alarms_on ? 1 : 0
  topic_arn = aws_sns_topic.alarms[0].arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

# No healthy target for 5 minutes in a row: the API or the shop is down for visitors.
resource "aws_cloudwatch_metric_alarm" "no_healthy_target" {
  for_each = local.alarms_on ? local.health_alarms : {}

  alarm_name          = "${var.name}-${each.key}-no-healthy-target"
  alarm_description   = "No healthy ${each.key} task behind the load balancer for 5 minutes"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HealthyHostCount"
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 5
  comparison_operator = "LessThanThreshold"
  threshold           = 1
  treat_missing_data  = "breaching"

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = each.value
  }

  alarm_actions = [aws_sns_topic.alarms[0].arn]
  ok_actions    = [aws_sns_topic.alarms[0].arn]
}
