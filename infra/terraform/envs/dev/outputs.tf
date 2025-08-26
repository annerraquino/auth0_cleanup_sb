output "alb_dns_name" { value = aws_lb.app.dns_name }
output "ecr_repo_url" { value = aws_ecr_repository.repo.repository_url }
