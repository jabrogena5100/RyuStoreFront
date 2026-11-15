output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "security_group_ids" {
  value = {
    alb = aws_security_group.alb.id
    app = aws_security_group.app.id
    db  = aws_security_group.db.id
  }
}

output "db_endpoint" {
  value = aws_db_instance.main.address
}

output "db_secret_arn" {
  description = "Secrets Manager secret holding the generated master password (JSON keys: username, password)"
  value       = aws_db_instance.main.master_user_secret[0].secret_arn
}

output "api_url" {
  value = "https://${var.api_domain}"
}

output "alb_dns_name" {
  value = aws_lb.main.dns_name
}

output "ecs_cluster" {
  value = aws_ecs_cluster.main.name
}

output "log_group" {
  value = aws_cloudwatch_log_group.api.name
}
