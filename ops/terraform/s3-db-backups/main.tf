terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

variable "bucket_name" {
  type        = string
  description = "Dedicated, version-locked bucket for PostgreSQL dumps"
  default     = "archives-db-backups"
}

variable "tags" {
  type    = map(string)
  default = {
    Purpose = "postgres-disaster-recovery"
    System  = "invsys"
  }
}

resource "aws_s3_bucket" "archives_db_backups" {
  bucket = var.bucket_name
  tags   = var.tags
}

resource "aws_s3_bucket_versioning" "archives_db_backups" {
  bucket = aws_s3_bucket.archives_db_backups.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "archives_db_backups" {
  bucket                  = aws_s3_bucket.archives_db_backups.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "archives_db_backups" {
  bucket = aws_s3_bucket.archives_db_backups.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Transition dumps older than 30 days to Glacier; delete after 1 year.
resource "aws_s3_bucket_lifecycle_configuration" "archives_db_backups" {
  bucket = aws_s3_bucket.archives_db_backups.id

  rule {
    id     = "postgres-dump-glacier-then-expire"
    status = "Enabled"

    filter {
      prefix = "postgres/"
    }

    transition {
      days          = 30
      storage_class = "GLACIER"
    }

    expiration {
      days = 365
    }

    noncurrent_version_expiration {
      noncurrent_days = 365
    }
  }
}

output "bucket_arn" {
  value = aws_s3_bucket.archives_db_backups.arn
}

output "bucket_name" {
  value = aws_s3_bucket.archives_db_backups.bucket
}
