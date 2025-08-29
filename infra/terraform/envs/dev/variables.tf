########################################
# Global / App
########################################
variable "project_name" {
  description = "Base name for resources"
  type        = string
  default     = "auth0-cleanup-sb"
}

variable "aws_region" {
  description = "AWS region to deploy to"
  type        = string
  default     = "us-east-1"
}

########################################
# Container image
########################################
variable "image_uri" {
  description = "Full image URI (e.g., <acct>.dkr.ecr.us-east-1.amazonaws.com/auth0-cleanup-sb:main)"
  type        = string
}

########################################
# Service sizing
########################################
variable "desired_count" {
  description = "Number of ECS tasks to run"
  type        = number
  default     = 1
}

variable "task_cpu" {
  description = "Task CPU (Fargate compatible: 256, 512, 1024, ...)"
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "Task memory in MB (Fargate compatible: 1024, 2048, ...)"
  type        = number
  default     = 1024
}

########################################
# App config (S3 + SSM)
########################################
variable "s3_bucket" {
  description = "Bucket with input/users_to_delete.csv and output/deleted_users.csv"
  type        = string
}

variable "ssm_param_prefix" {
  description = "Parameter Store prefix for app config (e.g., /auth0-cleanup-sb/)"
  type        = string
  default     = "/auth0-cleanup-sb/"
}

variable "attach_bucket_policy" {
  description = "If true, attach a restrictive bucket policy to allow only the ECS task role"
  type        = bool
  default     = false
}

# Optional CMKs (only if you use customer-managed KMS for SSM SecureString or S3 SSE-KMS)
variable "ssm_kms_key_arn" {
  description = "KMS key ARN for SSM SecureString (optional)"
  type        = string
  default     = null
}

variable "s3_kms_key_arn" {
  description = "KMS key ARN for S3 SSE-KMS (optional)"
  type        = string
  default     = null
}

########################################
# VPC / Networking
########################################
variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "Two public subnets in different AZs"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "health_check_path" {
  description = "ALB target group health check path"
  type        = string
  default     = "/"
}
