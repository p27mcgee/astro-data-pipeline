# Data source for availability zones
data "aws_availability_zones" "available" {
  state = "available"

  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

# VPC Configuration
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(var.additional_tags, {
    Name                                            = "${var.project_name}-vpc"
    "kubernetes.io/cluster/${var.project_name}-eks" = "shared"
  })
}

# Internet Gateway
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-igw"
  })
}

# Public Subnets
resource "aws_subnet" "public" {
  count = length(var.public_subnet_cidrs)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = false  # Security best practice - explicitly assign public IPs when needed

  tags = merge(var.additional_tags, {
    Name                                            = "${var.project_name}-public-subnet-${count.index + 1}"
    Type                                            = "public"
    "kubernetes.io/cluster/${var.project_name}-eks" = "shared"
    "kubernetes.io/role/elb"                        = "1"
  })
}

# Private Subnets
resource "aws_subnet" "private" {
  count = length(var.private_subnet_cidrs)

  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = merge(var.additional_tags, {
    Name                                            = "${var.project_name}-private-subnet-${count.index + 1}"
    Type                                            = "private"
    "kubernetes.io/cluster/${var.project_name}-eks" = "owned"
    "kubernetes.io/role/internal-elb"               = "1"
  })
}

# Database Subnets
resource "aws_subnet" "database" {
  count = length(var.database_subnet_cidrs)

  vpc_id            = aws_vpc.main.id
  cidr_block        = var.database_subnet_cidrs[count.index]
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-db-subnet-${count.index + 1}"
    Type = "database"
  })
}

# Elastic IP for NAT Gateway
resource "aws_eip" "nat" {
  count = length(var.public_subnet_cidrs)

  domain     = "vpc"
  depends_on = [aws_internet_gateway.main]

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-nat-eip-${count.index + 1}"
  })
}

# NAT Gateways
resource "aws_nat_gateway" "main" {
  count = length(var.public_subnet_cidrs)

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  depends_on = [aws_internet_gateway.main]

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-nat-gateway-${count.index + 1}"
  })
}

# Route Tables
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-public-rt"
  })
}

resource "aws_route_table" "private" {
  count = length(var.private_subnet_cidrs)

  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main[count.index].id
  }

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-private-rt-${count.index + 1}"
  })
}

resource "aws_route_table" "database" {
  vpc_id = aws_vpc.main.id

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-database-rt"
  })
}

# Route Table Associations
resource "aws_route_table_association" "public" {
  count = length(var.public_subnet_cidrs)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  count = length(var.private_subnet_cidrs)

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

resource "aws_route_table_association" "database" {
  count = length(var.database_subnet_cidrs)

  subnet_id      = aws_subnet.database[count.index].id
  route_table_id = aws_route_table.database.id
}

# VPC Endpoints for S3
resource "aws_vpc_endpoint" "s3" {
  vpc_id       = aws_vpc.main.id
  service_name = "com.amazonaws.${var.aws_region}.s3"

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-s3-endpoint"
  })
}

# VPC Endpoint Route Table Associations
resource "aws_vpc_endpoint_route_table_association" "s3_private" {
  count = length(aws_route_table.private)

  vpc_endpoint_id = aws_vpc_endpoint.s3.id
  route_table_id  = aws_route_table.private[count.index].id
}