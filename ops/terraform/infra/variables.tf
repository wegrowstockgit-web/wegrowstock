variable "environment" {
  type        = string
  description = "Logical environment label (test | prod | stress)"
  validation {
    condition     = contains(["test", "prod", "stress"], var.environment)
    error_message = "environment must be test, prod, or stress."
  }
}

variable "instance_type" {
  type        = string
  description = "RDS / compute instance class for the primary database engines"
  default     = "db.t4g.micro"
}

variable "multi_az" {
  type        = bool
  description = "Enable Multi-AZ for production-grade database HA"
  default     = false
}

variable "use_fargate_spot" {
  type        = bool
  description = "Prefer Fargate Spot capacity providers (cost savings)"
  default     = true
}

variable "enable_instance_scheduler" {
  type        = bool
  description = "Schedule non-prod compute to resting hours"
  default     = true
}

variable "dedicated_fargate" {
  type        = bool
  description = "Use dedicated (on-demand) Fargate tasks instead of Spot"
  default     = false
}

variable "load_generator_tasks" {
  type        = number
  description = "Number of load-generator ECS tasks (stress widget)"
  default     = 0
  validation {
    condition     = var.load_generator_tasks >= 0 && var.load_generator_tasks <= 10
    error_message = "load_generator_tasks must be between 0 and 10."
  }
}

variable "project_name" {
  type        = string
  description = "Short project prefix for naming"
  default     = "invsys"
}

# ---------------------------------------------------------------------------
# Control plane / data plane routing (GitOps profile for gateway + DNS)
# ---------------------------------------------------------------------------

variable "data_plane_hostname" {
  type        = string
  description = "Public hostname for tenant WMS (data plane UI + API)"
  default     = "app.invsys.com"
}

variable "control_plane_hostname" {
  type        = string
  description = "Public hostname for Super Admin portal (control plane)"
  default     = "admin.invsys.com"
}

variable "data_plane_api_port" {
  type        = number
  description = "Edge port / listener for data-plane API gateway"
  default     = 8080
}

variable "control_plane_api_port" {
  type        = number
  description = "Edge port / listener for control-plane API gateway"
  default     = 8081
}

variable "control_plane_cidr_allowlist" {
  type        = list(string)
  description = "CIDRs allowed to reach the control-plane edge (empty = allow-all; tighten in prod)"
  default     = []
}

variable "wms_service_name" {
  type        = string
  description = "Logical service name for the data-plane API runner"
  default     = "invsys-app"
}

variable "admin_service_name" {
  type        = string
  description = "Logical service name for the control-plane API runner"
  default     = "invsys-admin-api"
}

variable "wms_frontend_package" {
  type        = string
  description = "Monorepo package / image name for tenant WMS UI"
  default     = "frontend_wms"
}

variable "admin_frontend_package" {
  type        = string
  description = "Monorepo package / image name for Super Admin UI"
  default     = "frontend_admin"
}
