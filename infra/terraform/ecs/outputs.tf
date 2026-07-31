output "alb_dns_name" {
  description = "Public DNS name of the Application Load Balancer."
  value       = aws_lb.app.dns_name
}

output "route53_record_name" {
  description = "Route 53 record name when domain_name and route53_zone_id are set."
  value       = local.create_route53_records ? aws_route53_record.app[0].fqdn : null
}

output "backend_ecr_repository_url" {
  description = "Push the backend image here before a real ECS deployment."
  value       = aws_ecr_repository.backend.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.this.name
}

output "backend_ecs_service_name" {
  description = "Backend ECS service name."
  value       = aws_ecs_service.backend.name
}

output "rds_endpoint" {
  description = "RDS endpoint. Password is stored in Secrets Manager."
  value       = aws_db_instance.postgres.address
}

output "kms_key_alias" {
  description = "KMS key alias used by Secrets Manager, RDS, and CloudWatch Logs."
  value       = aws_kms_alias.app.name
}
