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
