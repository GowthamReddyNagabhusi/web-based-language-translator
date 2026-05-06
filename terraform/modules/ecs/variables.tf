variable "project_name"        { type = string }
variable "environment"          { type = string }
variable "ecr_image_uri"        { type = string }
variable "vpc_id"               { type = string }
variable "private_subnet_ids"   { type = list(string) }
variable "alb_target_group_arn" { type = string }
variable "desired_count"        { type = number; default = 1 }
variable "cpu"                  { type = number; default = 256 }
variable "memory"               { type = number; default = 512 }

variable "env_vars" {
  description = "Environment variables for the ECS task"
  type        = map(string)
  default     = {}
}
