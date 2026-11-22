# TripDiary のデモ環境（docs/deploy.md）
#
# - 状態ファイル（terraform.tfstate）は手元に置く（.gitignore 済み。秘密情報を含むためコミットしない）
# - 使わないときは待機モード（var.standby。infra/standby.ps1）にし、URL とデータを残して費用を抑える
# - 完全に消すときは `terraform destroy`。削除保護・最終スナップショットは付けない
terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.project
      ManagedBy = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name       = var.project
  account_id = data.aws_caller_identity.current.account_id
  azs        = slice(data.aws_availability_zones.available.names, 0, 2)
}
