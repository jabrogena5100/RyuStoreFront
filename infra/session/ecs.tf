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

resource "aws_cloudwatch_log_group" "api" {
  name              = "/storefront/api"
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

    environment = [
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:5432/storefront?sslmode=require" },
      { name = "SPRING_DATASOURCE_USERNAME", value = "storefront" },
      { name = "SPRING_PROFILES_ACTIVE", value = var.app_profiles },
      { name = "STOREFRONT_SEED_PRODUCTS", value = tostring(var.seed_products) },
      { name = "STOREFRONT_COOKIE_SECURE", value = "true" },
      # The ALB ends TLS and talks plain HTTP to the task; trust its X-Forwarded-* headers.
      { name = "SERVER_FORWARD_HEADERS_STRATEGY", value = "native" },
      # No frontend yet: Stripe's return page lands on the API host (a 404 is expected there).
      { name = "STOREFRONT_PUBLIC_BASE_URL", value = "https://${var.api_domain}" },
    ]

    secrets = [
      # The RDS-generated secret is JSON; ECS picks one key with the ":key::" suffix.
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${aws_db_instance.main.master_user_secret[0].secret_arn}:password::" },
      { name = "STRIPE_SECRET_KEY", valueFrom = data.aws_secretsmanager_secret.stripe_secret_key.arn },
      { name = "STRIPE_WEBHOOK_SECRET", valueFrom = data.aws_secretsmanager_secret.stripe_webhook_secret.arn },
    ]

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
