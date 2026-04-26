output "cluster_id"     { value = aws_ecs_cluster.main.id }
output "service_name"   { value = aws_ecs_service.app.name }
output "app_sg_id"      { value = aws_security_group.app.id }
output "task_family"    { value = aws_ecs_task_definition.app.family }
