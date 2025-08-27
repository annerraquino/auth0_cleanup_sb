############################################
# ECS Fargate + ALB + VPC (public subnets)
# Uses a stable random suffix to avoid name collisions
# ECR creation is optional to avoid "RepositoryAlreadyExists" errors
############################################

# Stable random suffix (changes only if service_name changes)
resource "random_id" "suffix" {
  byte_length = 2
  keepers = {
    service_name = var.service_name
  }
}

locals {
  suffix  = random_id.suffix.hex
  resname = "${var.service_name}-${local.suffix}"
}

# Who am I (for ARNs)
data "aws_caller_identity" "me" {}

# -------------------------
# Networking (VPC + Subnets)
# -------------------------
resource "aws_vpc" "this" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${local.resname}-vpc"
  }
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = "${local.resname}-igw"
  }
}

resource "aws_subnet" "public_a" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = "10.42.0.0/24"
  availability_zone       = "${var.aws_region}a"
  map_public_ip_on_launch = true

  tags = {
    Name = "${local.resname}-pub-a"
  }
}

resource "aws_subnet" "public_b" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = "10.42.1.0/24"
  availability_zone       = "${var.aws_region}b"
  map_public_ip_on_launch = true

  tags = {
    Name = "${local.resname}-pub-b"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }

  tags = {
    Name = "${local.resname}-rtb-public"
  }
}

resource "aws_route_table_association" "a" {
  subnet_id      = aws_subnet.public_a.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "b" {
  subnet_id      = aws_subnet.public_b.id
  route_table_id = aws_route_table.public.id
}

# -------------------------
# Security Groups
# -------------------------
resource "aws_security_group" "alb" {
  name   = "${local.resname}-alb-sg"
  vpc_id = aws_vpc.this.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.resname}-alb-sg"
  }
}

resource "aws_security_group" "task" {
  name   = "${local.resname}-task-sg"
  vpc_id = aws_vpc.this.id

  ingress {
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.resname}-task-sg"
  }
}

# -------------------------
# ALB + Target Group + Listener
# -------------------------
resource "aws_lb" "app" {
  name               = "${local.resname}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [aws_subnet.public_a.id, aws_subnet.public_b.id]

  tags = {
    Name = "${local.resname}-alb"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aw
