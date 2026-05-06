terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket = "translator-tf-state-prod"
    key    = "prod/terraform.tfstate"
    region = "us-east-1"
  }
}

provider "aws" {
  region = "us-east-1"
}

locals {
  project_name = "translator"
  environment  = "prod"
}

module "ecr" {
  source       = "../../modules/ecr"
  project_name = local.project_name
  environment  = local.environment
}

module "vpc" {
  source               = "../../modules/vpc"
  project_name         = local.project_name
  environment          = local.environment
  vpc_cidr             = "10.1.0.0/16"
  availability_zones   = ["us-east-1a", "us-east-1b", "us-east-1c"]
  public_subnet_cidrs  = ["10.1.1.0/24", "10.1.2.0/24", "10.1.3.0/24"]
  private_subnet_cidrs = ["10.1.10.0/24", "10.1.11.0/24", "10.1.12.0/24"]
}

module "alb" {
  source            = "../../modules/alb"
  project_name      = local.project_name
  environment       = local.environment
  vpc_id            = module.vpc.vpc_id
  public_subnet_ids = module.vpc.public_subnet_ids
}

module "ecs" {
  source               = "../../modules/ecs"
  project_name         = local.project_name
  environment          = local.environment
  ecr_image_uri        = "${module.ecr.repository_url}:${var.image_tag}"
  vpc_id               = module.vpc.vpc_id
  private_subnet_ids   = module.vpc.private_subnet_ids
  alb_target_group_arn = module.alb.target_group_arn
  desired_count        = 2         # Minimum 2 for HA
  cpu                  = 1024
  memory               = 2048
}

module "rds" {
  source         = "../../modules/rds"
  project_name   = local.project_name
  environment    = local.environment
  vpc_id         = module.vpc.vpc_id
  subnet_ids     = module.vpc.private_subnet_ids
  app_sg_id      = module.ecs.app_sg_id
  db_username    = "translator_user"
  db_password    = var.db_password
  instance_class = "db.r6g.large"  # RDS-optimized for prod
  multi_az       = true
}

module "elasticache" {
  source          = "../../modules/elasticache"
  project_name    = local.project_name
  environment     = local.environment
  vpc_id          = module.vpc.vpc_id
  subnet_ids      = module.vpc.private_subnet_ids
  app_sg_id       = module.ecs.app_sg_id
  node_type       = "cache.r6g.large"
  num_cache_nodes = 2
}

module "sqs" {
  source       = "../../modules/sqs"
  project_name = local.project_name
  environment  = local.environment
  queue_name   = "bulk-translations-queue-prod"
}

module "s3" {
  source       = "../../modules/s3"
  project_name = local.project_name
  environment  = local.environment
  bucket_name  = "translator-exports-prod"
}

variable "db_password" {
  description = "RDS master password"
  type        = string
  sensitive   = true
}

variable "image_tag" {
  description = "Docker image tag to deploy"
  type        = string
}

output "alb_url"          { value = "http://${module.alb.alb_dns_name}" }
output "ecr_repository"   { value = module.ecr.repository_url }
output "rds_endpoint"     { value = module.rds.rds_endpoint }
output "redis_endpoint"   { value = module.elasticache.redis_endpoint }
