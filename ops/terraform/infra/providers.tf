variable "aws_region" {
  type        = string
  description = "AWS region for provider + state operations"
  default     = "us-east-1"
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      System      = "invsys"
      ManagedBy   = "terraform"
      Environment = terraform.workspace
    }
  }
}
