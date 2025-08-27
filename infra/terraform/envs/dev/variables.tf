# variables.tf

variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "us-east-1"
}

variable "service_name" {
  description = "Name prefix for ECS/ALB/SG/etc."
  type        = string
  default     = "auth0-cleanup-sb"
}

variable "ecr_repo_name" {
  description = "ECR repository name for the app image"
  type        = string
  default     = "auth0-cleanup-sb"
}

variable "image_uri" {
  description = "Full image URI incl. tag, e.g. 123456789012.dkr.ecr.us-east-1.amazonaws.com/auth0-cleanup-sb:<tag>"
  type        = string
}

variable "cpu" {
  description = "Fargate task CPU units (e.g., 256, 512, 1024, 2048, 4096)"
  type        = number
  default     = 512
}

variable "memory" {
  description = "Fargate task memory (MB) matching the CPU setting (e.g., 1024, 2048)"
  type        = number
  default     = 1024
}

variable "container_port" {
  description = "Container port exposed by the Spring Boot app"
  type        = number
  default     = 8080
}

variable "param_prefix" {
  description = "SSM Parameter Store path prefix for app config"
  type        = string
  default     = "/auth0-cleanup-sb/"

  validation {
    condition     = can(regex("^/.+/$", var.param_prefix))
    error_message = "param_prefix must start and end with '/'. Example: '/auth0-cleanup-sb/'."
  }
}

variable "csv_bucket" {
  description = "S3 bucket used to store deleted_users.csv"
  type        = string
  default     = "auth0-deleted-users"
}

variable "csv_key" {
  description = "S3 object key for the CSV log"
  type        = string
  default     = "output/deleted_users.csv"
}

variable "desired_count" {
  description = "Number of ECS tasks to run"
  type        = number
  default     = 1
}
