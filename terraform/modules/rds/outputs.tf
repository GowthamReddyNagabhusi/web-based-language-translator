output "rds_endpoint" {
  value       = aws_db_instance.postgres.endpoint
  description = "RDS instance endpoint (host:port)"
}

output "rds_db_name" {
  value       = aws_db_instance.postgres.db_name
  description = "Database name"
}
