# ===== VPC =====
resource "aws_vpc" "main" {
  cidr_block           = "172.31.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "WebGameVpc"
    name = "WebGame"
  }

  lifecycle {
    prevent_destroy = true # RDS がいる VPC なので誤削除を防ぐ
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name  = "WebGameIgw"
    group = "WebGame"
  }
}

# ===== サブネット =====
resource "aws_subnet" "public_1a" {
  vpc_id                  = aws_vpc.main.id
  availability_zone       = "us-east-1a"
  cidr_block              = "172.31.16.0/20"
  map_public_ip_on_launch = true

  tags = {
    Name = "WebGamePub02"
    name = "WebGame"
  }
}

resource "aws_subnet" "public_1b" {
  vpc_id                  = aws_vpc.main.id
  availability_zone       = "us-east-1b"
  cidr_block              = "172.31.0.0/20"
  map_public_ip_on_launch = true

  tags = {
    Name = "WebGamePub01"
    name = "WebGame"
  }
}

resource "aws_subnet" "private_1b" {
  vpc_id                  = aws_vpc.main.id
  availability_zone       = "us-east-1b"
  cidr_block              = "172.31.80.0/20"
  map_public_ip_on_launch = false

  tags = {
    Name = "WebGamePrivate01"
    name = "WebGame"
  }
}

# ===== ルートテーブル =====
# メインルートテーブル（public サブネットが暗黙的に使用）
resource "aws_route_table" "main" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name  = "WebGemaRTPub"
    group = "WebGame"
  }
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  # 既存の NAT ゲートウェイへのルート（状態を確認後に整理する）
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = "nat-00fb14a8686c8fedb"
  }

  tags = {
    Name  = "WebGameRTPrivate"
    group = "WebGame"
  }
}

resource "aws_route_table_association" "private_1b" {
  subnet_id      = aws_subnet.private_1b.id
  route_table_id = aws_route_table.private.id
}

# ===== セキュリティグループ =====
# RDS に付いている SG（name と description は変えると作り直しになるので変更しない）
resource "aws_security_group" "rds" {
  name        = "WebGame-EKS-RDS-SG"
  description = "Allowing access to MySQL from EKS nodes"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port = 3306
    to_port   = 3306
    protocol  = "tcp"
    security_groups = [
      "sg-052217c72946c3577", # WebGameBastionSG
      "sg-076ea4e2a353bc30d", # eks-cluster-sg-webgame-cluster
    ]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "Cross-Border-Payment-Service-SG"
    name = "Cross-Border-Payment-Service"
  }

  lifecycle {
    prevent_destroy = true
  }
}
