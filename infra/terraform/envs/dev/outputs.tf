# Safe: uses the local computed with try(one(resource[*]), one(data[*]))
output "ecr_repo_url" {
  value = local.ecr_repo_url
}

output "alb_dns_name" {
  value = aws_lb.app.dns_name
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}
