variable "project_name" {
  description = "Prefix used for AWS resource names."
  type        = string
  default     = "mapoker"
}

variable "environment" {
  description = "Environment name used in tags and resource names."
  type        = string
  default     = "learn"
}

variable "aws_region" {
  description = "AWS region to model the infrastructure in."
  type        = string
  default     = "ap-northeast-1"
}

variable "domain_name" {
  description = "Application domain name for Route 53 records. Leave empty to skip DNS records."
  type        = string
  default     = ""
}

variable "route53_zone_id" {
  description = "Existing Route 53 hosted zone ID. Leave empty to skip DNS records."
  type        = string
  default     = ""
}

variable "alb_certificate_arn" {
  description = "ACM certificate ARN for HTTPS on ALB. Leave empty to create only HTTP listener."
  type        = string
  default     = ""
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "availability_zones" {
  description = "Two availability zones used by public and private subnets."
  type        = list(string)
  default     = ["ap-northeast-1a", "ap-northeast-1c"]
}

variable "container_image_tag" {
  description = "Image tag expected in the ECR repositories."
  type        = string
  default     = "latest"
}

variable "enable_nat_gateway" {
  description = "Enable outbound internet from private ECS tasks. Needed for external APIs such as Google OAuth, but adds fixed cost."
  type        = bool
  default     = false
}

variable "backend_container_port" {
  description = "Spring Boot backend container port."
  type        = number
  default     = 8080
}

variable "backend_cpu" {
  description = "Backend Fargate CPU units. 512 = 0.5 vCPU."
  type        = number
  default     = 512
}

variable "backend_memory" {
  description = "Backend Fargate memory in MiB. Must be valid for backend_cpu."
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "Initial number of backend ECS tasks."
  type        = number
  default     = 1
}

variable "autoscaling_min_capacity" {
  description = "Minimum backend ECS task count for Application Auto Scaling."
  type        = number
  default     = 1
}

variable "autoscaling_max_capacity" {
  description = "Maximum backend ECS task count for Application Auto Scaling."
  type        = number
  default     = 3
}

variable "autoscaling_cpu_target" {
  description = "Target average CPU utilization percentage for backend ECS autoscaling."
  type        = number
  default     = 70
}

variable "database_name" {
  description = "PostgreSQL database name."
  type        = string
  default     = "mapoker"
}

variable "database_username" {
  description = "PostgreSQL master username."
  type        = string
  default     = "mapoker"
}

variable "database_instance_class" {
  description = "Small RDS instance class for learning."
  type        = string
  default     = "db.t4g.micro"
}

variable "database_allocated_storage" {
  description = "RDS storage size in GiB."
  type        = number
  default     = 20
}

variable "application_url" {
  description = "Public application URL. Replace after adding a real domain."
  type        = string
  default     = "https://example.com"
}

variable "cors_allowed_origin_patterns" {
  description = "CORS allowed origin patterns for the backend."
  type        = string
  default     = "https://example.com"
}

variable "google_client_id" {
  description = "Google OAuth client ID. Leave empty if OAuth is not configured yet."
  type        = string
  default     = ""
}

variable "google_client_secret" {
  description = "Google OAuth client secret. For real use, prefer importing an existing secret instead of storing this in Terraform state."
  type        = string
  default     = ""
  sensitive   = true
}

variable "enable_deletion_protection" {
  description = "Set true for real environments. False keeps destroy easy while learning."
  type        = bool
  default     = false
}
