resource "random_password" "db" {
  length  = 24
  special = true
}

resource "aws_secretsmanager_secret" "app" {
  name                    = "${local.name}/app"
  kms_key_id              = aws_kms_key.app.arn
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id

  secret_string = jsonencode({
    database_password    = random_password.db.result
    google_client_secret = var.google_client_secret
  })
}

resource "aws_db_subnet_group" "this" {
  name       = local.name
  subnet_ids = values(aws_subnet.private)[*].id

  tags = {
    Name = local.name
  }
}

resource "aws_db_instance" "postgres" {
  identifier = "${local.name}-postgres"

  engine         = "postgres"
  engine_version = "16"
  instance_class = var.database_instance_class

  allocated_storage     = var.database_allocated_storage
  max_allocated_storage = max(var.database_allocated_storage, 100)
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = aws_kms_key.app.arn

  db_name  = var.database_name
  username = var.database_username
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false

  backup_retention_period = 1
  deletion_protection     = var.enable_deletion_protection
  skip_final_snapshot     = !var.enable_deletion_protection

  tags = {
    Name = "${local.name}-postgres"
  }
}
