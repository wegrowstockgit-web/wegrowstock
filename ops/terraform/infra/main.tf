# InventorySystem multi-environment infra root.
# Profiles are driven by tfvars + workspace (test | prod).
# Concrete AWS resources can be added under modules/; this root captures the
# cost / HA profile the GitOps pipelines inject on each apply.

locals {
  profile = {
    environment               = var.environment
    workspace                 = terraform.workspace
    instance_type             = var.instance_type
    multi_az                  = var.multi_az
    use_fargate_spot          = var.use_fargate_spot
    enable_instance_scheduler = var.enable_instance_scheduler
    dedicated_fargate         = var.dedicated_fargate
    load_generator_tasks      = var.load_generator_tasks
  }

  planes = {
    data_plane = {
      hostname         = var.data_plane_hostname
      api_port         = var.data_plane_api_port
      api_service      = var.wms_service_name
      frontend_package = var.wms_frontend_package
      # Control-plane API paths are blocked on this edge (see ops/api-gateway/nginx.conf).
      block_control_plane_api = true
    }
    control_plane = {
      hostname         = var.control_plane_hostname
      api_port         = var.control_plane_api_port
      api_service      = var.admin_service_name
      frontend_package = var.admin_frontend_package
      cidr_allowlist   = var.control_plane_cidr_allowlist
    }
  }

  name_prefix = "${var.project_name}-${var.environment}"
}

# Lightweight marker so plan/apply always has a safe, idempotent resource while
# larger modules (RDS, ECS, ALB) are wired in. Tags encode the active profile.
resource "aws_ssm_parameter" "infra_profile" {
  name        = "/${local.name_prefix}/infra/profile"
  description = "Active InventorySystem infra cost/HA profile (GitOps-managed)"
  type        = "String"
  overwrite   = true
  value = jsonencode({
    environment               = local.profile.environment
    workspace                 = local.profile.workspace
    instance_type             = local.profile.instance_type
    multi_az                  = local.profile.multi_az
    use_fargate_spot          = local.profile.use_fargate_spot
    enable_instance_scheduler = local.profile.enable_instance_scheduler
    dedicated_fargate         = local.profile.dedicated_fargate
    load_generator_tasks      = local.profile.load_generator_tasks
  })

  tags = {
    Name = "${local.name_prefix}-infra-profile"
  }
}

# Decoupled Control Plane / Data Plane routing contract for gateway, DNS, and
# future ECS/ALB modules. Consumed by GitOps and ops runbooks.
resource "aws_ssm_parameter" "plane_routing" {
  name        = "/${local.name_prefix}/infra/plane-routing"
  description = "InventorySystem data-plane vs control-plane hostnames, ports, and service names"
  type        = "String"
  overwrite   = true
  value       = jsonencode(local.planes)

  tags = {
    Name  = "${local.name_prefix}-plane-routing"
    Plane = "split"
  }
}
