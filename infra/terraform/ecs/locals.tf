locals {
  name = "${var.project_name}-${var.environment}"

  public_subnets = {
    for index, az in var.availability_zones :
    az => cidrsubnet(var.vpc_cidr, 8, index)
  }

  private_subnets = {
    for index, az in var.availability_zones :
    az => cidrsubnet(var.vpc_cidr, 8, index + 10)
  }

  common_environment = [
    {
      name  = "SPRING_PROFILES_ACTIVE"
      value = "postgresql,production"
    },
    {
      name  = "SERVER_PORT"
      value = tostring(var.backend_container_port)
    },
    {
      name  = "CORS_ALLOWED_ORIGIN_PATTERNS"
      value = var.cors_allowed_origin_patterns
    },
    {
      name  = "GOOGLE_CLIENT_ID"
      value = var.google_client_id
    },
    {
      name  = "AVATAR_STORAGE_PATH"
      value = "/data/uploads/avatars"
    }
  ]
}
