output "alb_dns_name" {
  description = "Public URL of the Application Load Balancer"
  value       = aws_lb.app.dns_name
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

# Helpful to see what image was deployed in this run
output "image_uri" {
  value = var.image_uri
}
