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
