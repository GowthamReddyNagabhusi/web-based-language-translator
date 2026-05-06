variable "project_name" { type = string }
variable "environment"   { type = string }
variable "vpc_id"        { type = string }
variable "subnet_ids"    { type = list(string) }
variable "db_name"       { type = string; default = "translator_db" }
variable "db_username"   { type = string }
variable "db_password"   { type = string; sensitive = true }
variable "instance_class" {
  type    = string
  default = "db.t3.micro"
}
variable "multi_az" {
  type    = bool
  default = false
}
variable "app_sg_id" { type = string }
