output "redis_endpoint" {
  value       = aws_elasticache_cluster.redis.cache_nodes[0].address
  description = "Primary Redis endpoint"
}
output "redis_port" {
  value       = aws_elasticache_cluster.redis.port
  description = "Redis port"
}
