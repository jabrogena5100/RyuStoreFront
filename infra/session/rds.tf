resource "aws_db_subnet_group" "main" {
  name       = var.name
  subnet_ids = aws_subnet.public[*].id
}

# Temporary database: single-AZ, no final snapshot, no automated backups. The backup/restore note is phase 6.
# Not publicly accessible: it has no public IP, and its security group admits only the app tasks.
resource "aws_db_instance" "main" {
  identifier = var.name

  engine                      = "postgres"
  engine_version              = var.db_engine_version
  auto_minor_version_upgrade  = false
  allow_major_version_upgrade = false
  instance_class              = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "storefront"
  username = "storefront"
  # RDS creates the password in Secrets Manager (a JSON secret with username and password); it never appears
  # in Terraform config. It is stored in the Terraform state as a reference to the secret, not the value.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period   = var.db_backup_retention_days
  skip_final_snapshot       = !var.db_final_snapshot
  final_snapshot_identifier = var.db_final_snapshot ? "${var.name}-final" : null
  deletion_protection       = var.db_deletion_protection
  apply_immediately         = true
}

# Session defaults keep a destroy quick and free. For an always-on demo, infra/session/live.tfvars.example turns on
# daily backups, a final snapshot and deletion protection (infra/README.md section 3L).
variable "db_backup_retention_days" {
  type    = number
  default = 0
}

variable "db_final_snapshot" {
  type    = bool
  default = false
}

variable "db_deletion_protection" {
  type    = bool
  default = false
}
