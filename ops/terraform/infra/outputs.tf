output "workspace" {
  description = "Active Terraform workspace"
  value       = terraform.workspace
}

output "environment" {
  description = "Logical environment label"
  value       = var.environment
}

output "infra_profile" {
  description = "Resolved cost / HA profile applied by this run"
  value = {
    instance_type             = var.instance_type
    multi_az                  = var.multi_az
    use_fargate_spot          = var.use_fargate_spot
    enable_instance_scheduler = var.enable_instance_scheduler
    dedicated_fargate         = var.dedicated_fargate
    load_generator_tasks      = var.load_generator_tasks
  }
}

output "infra_profile_parameter_name" {
  description = "SSM parameter holding the serialized infra profile"
  value       = aws_ssm_parameter.infra_profile.name
}

output "plane_routing" {
  description = "Data plane / control plane hostnames, ports, and service names"
  value       = local.planes
}

output "plane_routing_parameter_name" {
  description = "SSM parameter holding serialized plane routing"
  value       = aws_ssm_parameter.plane_routing.name
}

output "data_plane_hostname" {
  description = "Tenant WMS public hostname"
  value       = var.data_plane_hostname
}

output "control_plane_hostname" {
  description = "Super Admin portal public hostname"
  value       = var.control_plane_hostname
}
