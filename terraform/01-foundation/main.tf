# Get available AZs that don't require opt-in for multi-AZ deployment
data "aws_availability_zones" "available" {
  state = "available"

  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

# Main VPC with DNS support for EKS cluster communication
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(var.additional_tags, {
    Name                                            = "${var.project_name}-${var.environment}-vpc"
    "kubernetes.io/cluster/${var.project_name}-${var.environment}-eks" = "shared"
  })
}

# Internet gateway for public subnet internet access
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-igw"
  })
}

# Public subnets for load balancers and NAT gateways
resource "aws_subnet" "public" {
  count = length(var.public_subnet_cidrs)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = false # Security best practice - explicitly assign public IPs when needed

  tags = merge(var.additional_tags, {
    Name                                            = "${var.project_name}-${var.environment}-public-subnet-${count.index + 1}"
    Type                                            = "public"
    "kubernetes.io/cluster/${var.project_name}-${var.environment}-eks" = "shared"
    "kubernetes.io/role/elb"                        = "1"
  })
}

# Private subnets for EKS nodes and application workloads
resource "aws_subnet" "private" {
  count = length(var.private_subnet_cidrs)

  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = merge(var.additional_tags, {
    Name                                            = "${var.project_name}-${var.environment}-private-subnet-${count.index + 1}"
    Type                                            = "private"
    "kubernetes.io/cluster/${var.project_name}-${var.environment}-eks" = "owned"
    "kubernetes.io/role/internal-elb"               = "1"
  })
}

# Isolated database subnets for RDS instances
resource "aws_subnet" "database" {
  count = length(var.database_subnet_cidrs)

  vpc_id            = aws_vpc.main.id
  cidr_block        = var.database_subnet_cidrs[count.index]
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-db-subnet-${count.index + 1}"
    Type = "database"
  })
}

# Static IP addresses for NAT gateways to ensure consistent outbound IPs
resource "aws_eip" "nat" {
  count = length(var.public_subnet_cidrs)

  domain     = "vpc"
  depends_on = [aws_internet_gateway.main]

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-nat-eip-${count.index + 1}"
  })
}

# NAT gateways to provide internet access for private subnets
resource "aws_nat_gateway" "main" {
  count = length(var.public_subnet_cidrs)

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  depends_on = [aws_internet_gateway.main]

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-nat-gateway-${count.index + 1}"
  })
}

# Public route table directing traffic to internet gateway
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-public-rt"
  })
}

# Private route tables directing traffic through NAT gateways
resource "aws_route_table" "private" {
  count = length(var.private_subnet_cidrs)

  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main[count.index].id
  }

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-private-rt-${count.index + 1}"
  })
}

# Database route table with no internet access for security
resource "aws_route_table" "database" {
  vpc_id = aws_vpc.main.id

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-database-rt"
  })
}

# Associate public subnets with public route table
resource "aws_route_table_association" "public" {
  count = length(var.public_subnet_cidrs)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Associate private subnets with their respective NAT gateway route tables
resource "aws_route_table_association" "private" {
  count = length(var.private_subnet_cidrs)

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

# Associate database subnets with isolated route table
resource "aws_route_table_association" "database" {
  count = length(var.database_subnet_cidrs)

  subnet_id      = aws_subnet.database[count.index].id
  route_table_id = aws_route_table.database.id
}

# VPC endpoint for S3 access without internet gateway
resource "aws_vpc_endpoint" "s3" {
  vpc_id       = aws_vpc.main.id
  service_name = "com.amazonaws.${var.aws_region}.s3"

  tags = merge(var.additional_tags, {
    Name = "${var.project_name}-${var.environment}-s3-endpoint"
  })
}

# Enable S3 VPC endpoint access from private subnets
resource "aws_vpc_endpoint_route_table_association" "s3_private" {
  count = length(aws_route_table.private)

  vpc_endpoint_id = aws_vpc_endpoint.s3.id
  route_table_id  = aws_route_table.private[count.index].id
}