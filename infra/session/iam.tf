data "aws_caller_identity" "current" {}

data "aws_secretsmanager_secret" "stripe_secret_key" {
  name = "storefront/stripe-secret-key"
}

data "aws_secretsmanager_secret" "stripe_webhook_secret" {
  name = "storefront/stripe-webhook-secret"
}

data "aws_iam_policy_document" "ecs_tasks_trust" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }

    # Confused-deputy guard: only ECS acting for this account may assume the roles.
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

# Execution role: what ECS itself needs to start the task (pull from ECR, write logs, fetch the three secrets).
resource "aws_iam_role" "execution" {
  name               = "${var.name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "read_secrets" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      aws_db_instance.main.master_user_secret[0].secret_arn,
      data.aws_secretsmanager_secret.stripe_secret_key.arn,
      data.aws_secretsmanager_secret.stripe_webhook_secret.arn,
    ]
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "read-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.read_secrets.json
}

# Task role of the API and the worker: what the application code may do in AWS. Only the worker sends mail, but both
# run the same image; the permission is limited to the one sending identity and the one From address.
resource "aws_iam_role" "task" {
  name               = "${var.name}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

data "aws_iam_policy_document" "send_mail" {
  statement {
    actions = ["ses:SendEmail"]
    # The domain identity, plus any recipient identity: in the SES sandbox the recipient must be verified, and SES also
    # authorizes the call against the recipient's identity ARN.
    resources = [
      "arn:aws:ses:${var.region}:${data.aws_caller_identity.current.account_id}:identity/*",
    ]
    condition {
      test     = "StringEquals"
      variable = "ses:FromAddress"
      values   = [var.mail_from]
    }
  }
}

resource "aws_iam_role_policy" "task_send_mail" {
  name   = "send-order-mail"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.send_mail.json
}

# The frontend needs nothing from AWS; its own role keeps it from inheriting the mail permission.
resource "aws_iam_role" "web_task" {
  name               = "${var.name}-ecs-web-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}
