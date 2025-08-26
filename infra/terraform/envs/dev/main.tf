# --- VPC (2 public subnets, IGW) ---
resource "aws_vpc" "this" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags = { Name = "${var.service_name}-vpc" }
}
resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.this.id
  tags = { Name = "${var.service_name}-igw" }
}
resource "aws_subnet" "public_a" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = "10.42.0.0/24"
  availability_zone       = "${var.aws_region}a"
  map_public_ip_on_launch = true
  tags = { Name = "${var.service_name}-pub-a" }
}
resource "aws_subnet" "public_b" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = "10.42.1.0/24"
  availability_zone       = "${var.aws_region}b"
  map_public_ip_on_launch = true
  tags = { Name = "${var.service_name}-pub-b" }
}
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  route { cidr_block = "0.0.0.0/0" gateway_id = aws_internet_gateway.igw.id }
  tags = { Name = "${var.service_name}-rtb-public" }
}
resource "aws_route_table_association" "a" { subnet_id = aws_subnet.public_a.id route_table_id = aws_route_table.public.id }
resource "aws_route_table_association" "b" { subnet_id = aws_subnet.public_b.id route_table_id = aws_route_table.public.id }

# --- Security Groups ---
resource "aws_security_group" "alb" {
  name   = "${var.service_name}-alb-sg"
  vpc_id = aws_vpc.this.id
  ingress { from_port = 80 to_port = 80 protocol = "tcp" cidr_blocks = ["0.0.0.0/0"] }
  egress  { from_port = 0 to_port = 0 protocol = "-1" cidr_blocks = ["0.0.0.0/0"] }
}
resource "aws_security_group" "task" {
  name   = "${var.service_name}-task-sg"
  vpc_id = aws_vpc.this.id
  ingress {
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id] # only ALB can hit tasks
  }
  egress { from_port = 0 to_port = 0 protocol = "-1" cidr_blocks = ["0.0.0.0/0"] }
}

# --- ALB ---
resource "aws_lb" "app" {
  name               = "${var.service_name}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [aws_subnet.public_a.id, aws_subnet.public_b.id]
}
resource "aws_lb_target_group" "app" {
  name        = "${var.service_name}-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = aws_vpc.this.id
  target_type = "ip"
  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 2
    interval            = 30
    timeout             = 5
    matcher             = "200-399"
  }
}
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"
  default_action { type = "forward" target_group_arn = aws_lb_target_group.app.arn }
}

# --- ECR (repo for images) ---
resource "aws_ecr_repository" "repo" {
  name = var.ecr_repo_name
  image_scanning_configuration { scan_on_push = true }
  force_delete = true
}

# --- IAM: Task execution role (pull image, push logs) ---
data "aws_iam_policy_document" "task_exec_trust" {
  statement {
    effect = "Allow"
    principals { type = "Service" identifiers = ["ecs-tasks.amazonaws.com"] }
    actions = ["sts:AssumeRole"]
  }
}
resource "aws_iam_role" "task_execution" {
  name               = "${var.service_name}-task-exec"
  assume_role_policy = data.aws_iam_policy_document.task_exec_trust.json
}
resource "aws_iam_role_policy_attachment" "task_exec_policy" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# --- IAM: Task role (app permissions: SSM, S3, optional KMS) ---
data "aws_caller_identity" "me" {}
resource "aws_iam_role" "task_role" {
  name               = "${var.service_name}-task-role"
  assume_role_policy = data.aws_iam_policy_document.task_exec_trust.json
}
resource "aws_iam_policy" "task_permissions" {
  name = "${var.service_name}-task-perms"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Sid: "ReadParams",
        Effect: "Allow",
        Action: ["ssm:GetParameters","ssm:GetParameter","ssm:GetParametersByPath"],
        Resource: "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.me.account_id}:parameter${var.param_prefix}*"
      },
      {
        Sid: "DecryptIfSecure",
        Effect: "Allow",
        Action: ["kms:Decrypt"],
        Resource: "*"
      },
      {
        Sid: "S3WriteCsv",
        Effect: "Allow",
        Action: ["s3:GetObject","s3:PutObject","s3:AbortMultipartUpload"],
        Resource: [
          "arn:aws:s3:::${var.csv_bucket}",
          "arn:aws:s3:::${var.csv_bucket}/*"
        ]
      }
    ]
  })
}
resource "aws_iam_role_policy_attachment" "task_role_attach" {
  role       = aws_iam_role.task_role.name
  policy_arn = aws_iam_policy.task_permissions.arn
}

# --- Logs ---
resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.service_name}"
  retention_in_days = 14
}

# --- Task Definition ---
resource "aws_ecs_task_definition" "app" {
  family                   = var.service_name
  requires_compatibilities = ["FARGATE"]
  cpu                      = tostring(var.cpu)
  memory                   = tostring(var.memory)
  network_mode             = "awsvpc"
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task_role.arn
  container_definitions = jsonencode([
    {
      name      = "app"
      image     = var.image_uri
      essential = true
      portMappings = [{ containerPort = var.container_port, hostPort = var.container_port, protocol = "tcp" }]
      environment = [
        { name = "APP_PARAM_PREFIX", value = var.param_prefix },
        { name = "APP_S3_BUCKET",    value = var.csv_bucket },
        { name = "APP_S3_KEY",       value = var.csv_key     }
      ]
      logConfiguration = {
        logDriver = "awslogs",
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.app.name,
          "awslogs-region"        = var.aws_region,
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])
}

# --- Cluster & Service ---
resource "aws_ecs_cluster" "this" {
  name = "${var.service_name}-cluster"
}

resource "aws_ecs_service" "app" {
  name            = var.service_name
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.public_a.id, aws_subnet.public_b.id]
    security_groups  = [aws_security_group.task.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "app"
    container_port   = var.container_port
  }

  depends_on = [aws_lb_listener.http]
}
