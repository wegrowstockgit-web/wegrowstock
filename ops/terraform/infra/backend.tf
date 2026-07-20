# Remote state: shared S3 bucket + DynamoDB lock table.
# Workspaces segregate environments (test / prod) under the same backend.
#
# Bootstrap (one-time, outside this module):
#   - S3 bucket:   invsys-terraform-state-storage  (versioning + encryption on)
#   - DynamoDB:    invsys-infra-lock-table         (partition key: LockID, String)
#
# Usage:
#   terraform init
#   terraform workspace select test || terraform workspace new test
#   terraform workspace select prod || terraform workspace new prod

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "invsys-terraform-state-storage"
    key            = "invsys/infra/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "invsys-infra-lock-table"
    encrypt        = true
  }
}
