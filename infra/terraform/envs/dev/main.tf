provider "aws" {
  region = var.aws_region
}

# Unique 4-hex suffix for TG name to avoid name collisions
resource "random_id" "tg" {
  byte_length = 2
}

########################################
# Locals
########################################
locals {
  name = var.project_name
}

########################################
# VPC + Networking (2 public subnets)
########################################
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = { Name = "${local.name}-vpc" }
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${local.name}-igw" }
}

resource "aws_subnet" "public_a" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[0]
  map_public_ip_on_launch = true
  availability_zone       = "${var.aws_region}a"
  tags = { Name = "${local.name}-public-a" }
}

resource "aws_subnet" "public_b" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[1]
  map_public_ip_on_launch = true
  availability_zone       = "${var.aws_region}b"
  tags = { Name = "${local.name}-public-b" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${local.name}-public-rt" }
}

resource "aws_route" "public_default" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.igw.id
}

resource "aws_route_table_association" "a" {
  subnet_id      = aws_subnet.public_a.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "b" {
  subnet_id      = aws_subnet.public_b.id
  route_table_id = aws_route_table.public.id
}

########################################
# Security Groups
########################################
# ALB: allow HTTP from anywhere
resource "aws_security_group" "alb" {
  name        = "${local.name}-alb-sg"
  description = "ALB security group"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  tags = { Name = "${local.name}-alb-sg" }
}

# ECS service: allow 8080 from ALB only
resource "aws_security_group" "ecs_service" {
  name        = "${local.name}-svc-sg"
  description = "ECS service security group"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "From ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  tags = { Name = "${local.name}-svc-sg" }
}

########################################
# ALB + Target Group + Listener
########################################
resource "aws_lb" "app" {
  name               = "${local.name}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [aws_subnet.public_a.id, aws_subnet.public_b.id]
  enable_deletion_protection = false

  tags = { Name = "${local.name}-alb" }
}

resource "aws_lb_target_group" "app" {
  name        = "${local.name}-${random_id.tg.hex}-tg"  # was: "auth0-cleanup-sb-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 5
    interval            = 30
    timeout             = 5
    matcher             = "200-499"
    path                = var.health_check_path
    port                = "traffic-port"
    protocol            = "HTTP"
  }

  tags = { Name = "${local.name}-tg" }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

########################################
# CloudWatch Logs for ECS
########################################
resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${local.name}"
  retention_in_days = 14
}

########################################
# IAM: execution role (pull image, push logs)
########################################
resource "aws_iam_role" "task_execution_role" {
  name = "${local.name}-exec"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect = "Allow",
      Principal = { Service = "ecs-tasks.amazonaws.com" },
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "task_exec_attach" {
  role       = aws_iam_role.task_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

########################################
# IAM: task role (S3 + SSM + optional KMS)
########################################
resource "aws_iam_role" "task_role" {
  name = "${local.name}-task"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect = "Allow",
      Principal = { Service = "ecs-tasks.amazonaws.com" },
      Action = "sts:AssumeRole"
    }]
  })
}

data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "task_s3_ssm" {
  statement {
    sid     = "S3ObjectsIO"
    actions = ["s3:GetObject", "s3:GetObjectVersion", "s3:PutObject", "s3:HeadObject"]
    resources = [
      "arn:aws:s3:::${var.s3_bucket}/input/*",
      "arn:aws:s3:::${var.s3_bucket}/output/*",
    ]
  }

  statement {
    sid       = "S3ListLimited"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::${var.s3_bucket}"]
    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["input/*", "output/*"]
    }
  }

  statement {
    sid     = "SSMRead"
    actions = ["ssm:GetParametersByPath", "ssm:GetParameters", "ssm:GetParameter"]
    resources = [
      "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter${var.ssm_param_prefix}*"
    ]
  }

  dynamic "statement" {
    for_each = compact([var.ssm_kms_key_arn, var.s3_kms_key_arn])
    content {
      sid       = "KmsDecrypt${replace(statement.value, ":", "")}"
      actions   = ["kms:Decrypt"]
      resources = [statement.value]
    }
  }
}

resource "aws_iam_policy" "task_s3_ssm" {
  name   = "${local.name}-task-s3-ssm"
  policy = data.aws_iam_policy_document.task_s3_ssm.json
}

resource "aws_iam_role_policy_attachment" "task_s3_ssm_attach" {
  role       = aws_iam_role.task_role.name
  policy_arn = aws_iam_policy.task_s3_ssm.arn
}

########################################
# ECS Cluster
########################################
resource "aws_ecs_cluster" "this" {
  name = "${local.name}-cluster"
}

########################################
# Task Definition
########################################
resource "aws_ecs_task_definition" "app" {
  family                   = "${local.name}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.task_execution_role.arn
  task_role_arn            = aws_iam_role.task_role.arn

  container_definitions = jsonencode([
    {
      name      = local.name
      image     = var.image_uri
      essential = true
      portMappings = [
        { containerPort = 8080, hostPort = 8080, protocol = "tcp" }
      ]
      environment = [
        { name = "AWS_REGION",       value = var.aws_region },
        { name = "APP_PARAM_PREFIX", value = var.ssm_param_prefix }
      ]
      logConfiguration = {
        logDriver = "awslogs",
        options = {
          awslogs-group         = aws_cloudwatch_log_group.app.name,
          awslogs-region        = var.aws_region,
          awslogs-stream-prefix = local.name
        }
      }
    }
  ])
}

########################################
# ECS Service
########################################
resource "aws_ecs_service" "app" {
  name            = local.name
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.public_a.id, aws_subnet.public_b.id]
    security_groups  = [aws_security_group.ecs_service.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = local.name
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.http]
}

########################################
# Optional: Restrictive S3 bucket policy
########################################
data "aws_iam_policy_document" "bucket_policy" {
  statement {
    sid = "AllowTaskRoleObjectIO"
    principals {
      type        = "AWS"
      identifiers = [aws_iam_role.task_role.arn]
    }
    actions = ["s3:GetObject", "s3:GetObjectVersion", "s3:PutObject", "s3:HeadObject"]
    resources = [
      "arn:aws:s3:::${var.s3_bucket}/input/*",
      "arn:aws:s3:::${var.s3_bucket}/output/*",
    ]
  }

  statement {
    sid = "AllowTaskRoleListLimited"
    principals {
      type        = "AWS"
      identifiers = [aws_iam_role.task_role.arn]
    }
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::${var.s3_bucket}"]
    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["input/*", "output/*"]
    }
  }
}

resource "aws_s3_bucket_policy" "restrict_to_task_role" {
  count  = var.attach_bucket_policy ? 1 : 0
  bucket = var.s3_bucket
  policy = data.aws_iam_policy_document.bucket_policy.json
}


