variable "project_name"   { type = string }
variable "environment"     { type = string }
variable "vpc_id"          { type = string }
variable "subnet_ids"      { type = list(string) }
variable "node_type"       { type = string; default = "cache.t3.micro" }
variable "num_cache_nodes" { type = number; default = 1 }
variable "app_sg_id"       { type = string }
