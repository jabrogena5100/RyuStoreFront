variable "api_domain" {
  type    = string
  default = "api.ryustorefront.com"
}

# Required, no default: pass the commit SHA that scripts/ecr-push.sh pushed (tags are immutable).
variable "image_tag" {
  type = string
}

# 0 creates the service without tasks; set 1 once the Stripe secrets hold values (a task cannot start without them).
variable "desired_count" {
  type    = number
  default = 0
}

# The worker (outbox: e-mail, cart clearing, refunds; reservation expiry) and the frontend. 0 or 1 each.
variable "worker_desired_count" {
  type    = number
  default = 0
}

variable "web_desired_count" {
  type    = number
  default = 0
}

# The frontend image (scripts/ecr-push.sh web). Required, like image_tag: the commit SHA it printed.
variable "web_image_tag" {
  type = string
}

variable "app_domain" {
  type    = string
  default = "ryustorefront.com"
}

# Sender of order e-mail; the domain is the SES identity in ../foundation/email.tf.
variable "mail_from" {
  type    = string
  default = "orders@api.ryustorefront.com"
}

# Off by default: the first start needs a writable /tmp (Tomcat, JVM). Try it as a hardening step once the app is up.
variable "read_only_root" {
  type    = bool
  default = false
}

# The seed profile fills an EMPTY database on startup and skips otherwise. Fine for a demo; not for production.
variable "app_profiles" {
  type    = string
  default = "seed"
}

variable "seed_products" {
  type    = number
  default = 100
}

data "aws_ecr_repository" "app" {
  name = "storefront"
}

data "aws_ecr_repository" "web" {
  name = "storefront-web"
}

# Written by ../foundation/images.tf: the CloudFront URL in front of the images bucket.
data "aws_ssm_parameter" "images_base_url" {
  name = "/storefront/images-base-url"
}

locals {
  # API and worker run the same image with the same database and Stripe settings; only the profile and mail differ.
  app_environment = [
    { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:5432/storefront?sslmode=require" },
    { name = "SPRING_DATASOURCE_USERNAME", value = "storefront" },
    { name = "STOREFRONT_COOKIE_SECURE", value = "true" },
    # Stripe returns shoppers to the frontend: /orders/<id>/confirmation on success, /cart on cancel.
    { name = "STOREFRONT_PUBLIC_BASE_URL", value = "https://${var.app_domain}" },
    { name = "STOREFRONT_IMAGES_BASE_URL", value = data.aws_ssm_parameter.images_base_url.value },
  ]
  app_secrets = [
    # The RDS-generated secret is JSON; ECS picks one key with the ":key::" suffix.
    { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${aws_db_instance.main.master_user_secret[0].secret_arn}:password::" },
    { name = "STRIPE_SECRET_KEY", valueFrom = data.aws_secretsmanager_secret.stripe_secret_key.arn },
    { name = "STRIPE_WEBHOOK_SECRET", valueFrom = data.aws_secretsmanager_secret.stripe_webhook_secret.arn },
  ]
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/storefront/api"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "worker" {
  name              = "/storefront/worker"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "web" {
  name              = "/storefront/web"
  retention_in_days = 7
}

resource "aws_ecs_cluster" "main" {
  name = var.name
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${var.name}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512  # 0.5 vCPU, a starting point to measure, not a tuned value
  memory                   = 1024 # MB
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64" # the image is linux/amd64
  }

  container_definitions = jsonencode([{
    name                   = "api"
    image                  = "${data.aws_ecr_repository.app.repository_url}:${var.image_tag}"
    essential              = true
    readonlyRootFilesystem = var.read_only_root

    portMappings = [{ containerPort = 8080, protocol = "tcp" }]

    environment = concat(local.app_environment, [
      { name = "SPRING_PROFILES_ACTIVE", value = var.app_profiles },
      { name = "STOREFRONT_SEED_PRODUCTS", value = tostring(var.seed_products) },
      # The ALB ends TLS and talks plain HTTP to the task; trust its X-Forwarded-* headers.
      { name = "SERVER_FORWARD_HEADERS_STRATEGY", value = "native" },
    ])

    secrets = local.app_secrets

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.api.name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = "api"
      }
    }
  }])
}

resource "aws_ecs_service" "api" {
  name            = "api"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  # A deployment whose tasks never become healthy rolls back to the last working task definition instead of
  # leaving the live site down.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # Startup was 22 s on the dev VM with no CPU limit; a 0.5 vCPU task will be slower. 180 s is a guess to be
  # replaced by the measured Fargate start time (docs/verification.md).
  health_check_grace_period_seconds = 180

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = true # no NAT gateway; the security group is the barrier (docs/decisions.md)
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.https]
}

# ---- worker: same image, profile "worker", no load balancer ----
# It polls the outbox every second and sweeps expired reservations; it keeps an HTTP port (8080) only for its own health.
resource "aws_ecs_task_definition" "worker" {
  family                   = "${var.name}-worker"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256 # 0.25 vCPU: background polling only; a starting point to measure
  memory                   = 1024
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name                   = "worker"
    image                  = "${data.aws_ecr_repository.app.repository_url}:${var.image_tag}"
    essential              = true
    readonlyRootFilesystem = var.read_only_root

    environment = concat(local.app_environment, [
      # No "seed": only the API seeds, so two processes never race to fill an empty database.
      { name = "SPRING_PROFILES_ACTIVE", value = "worker" },
      { name = "STOREFRONT_MAIL_TRANSPORT", value = "ses" },
      { name = "STOREFRONT_MAIL_FROM", value = var.mail_from },
      { name = "AWS_REGION", value = var.region },
    ])

    secrets = local.app_secrets

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.worker.name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = "worker"
      }
    }
  }])
}

resource "aws_ecs_service" "worker" {
  name            = "worker"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.worker.arn
  desired_count   = var.worker_desired_count
  launch_type     = "FARGATE"

  # A deployment whose tasks never become healthy rolls back to the last working task definition instead of
  # leaving the live site down.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = true
  }

  # Flyway runs in whichever process starts first; start the worker after the API service exists.
  depends_on = [aws_ecs_service.api]
}

# ---- web: Next.js frontend on the apex, behind the same load balancer (host-header rule) ----
resource "aws_ecs_task_definition" "web" {
  family                   = "${var.name}-web"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256 # 0.25 vCPU / 512 MB: a starting point to measure
  memory                   = 512
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.web_task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = "web"
    image     = "${data.aws_ecr_repository.web.repository_url}:${var.web_image_tag}"
    essential = true

    portMappings = [{ containerPort = 3000, protocol = "tcp" }]

    environment = [
      # Must match the value the image was built with (the /api rewrite is fixed at build time; scripts/ecr-push.sh web).
      { name = "API_ORIGIN", value = "https://${var.api_domain}" },
      # Next's standalone server binds to $HOSTNAME; the runtime may set it to the task's hostname, so pin all interfaces.
      { name = "HOSTNAME", value = "0.0.0.0" },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.web.name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = "web"
      }
    }
  }])
}

resource "aws_ecs_service" "web" {
  name            = "web"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.web.arn
  desired_count   = var.web_desired_count
  launch_type     = "FARGATE"

  # A deployment whose tasks never become healthy rolls back to the last working task definition instead of
  # leaving the live site down.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  health_check_grace_period_seconds = 60 # the Node server started in under a second locally (not measured on Fargate)

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.web.arn
    container_name   = "web"
    container_port   = 3000
  }

  depends_on = [aws_lb_listener_rule.web]
}
