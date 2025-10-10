# LocalStack Initialization Scripts

This directory contains initialization scripts that run automatically when LocalStack starts up.

## How It Works

The `docker-compose.yml` mounts this directory to `/etc/localstack/init/ready.d/` inside the LocalStack container:

```yaml
volumes:
  - ./scripts/localstack:/etc/localstack/init/ready.d
```

LocalStack automatically executes all shell scripts (`.sh`) in the `ready.d` directory after the service is fully initialized.

## Available Scripts

### init-aws.sh

**Purpose:** Automatically creates S3 buckets for the astronomical data pipeline.

**Created Buckets:**
- `s3://astro-raw-data` - Raw FITS files from telescopes
- `s3://astro-processed-data` - Calibrated/processed astronomical data
- `s3://astro-intermediate-data` - Intermediate processing files
- `s3://astro-archive` - Long-term archived data

**Execution:** Runs automatically when LocalStack starts

**Logs:** View with `docker compose logs localstack`

## Verification

After starting docker-compose, verify buckets were created:

```bash
# Check LocalStack logs for initialization output
docker compose logs localstack | grep "Initializing LocalStack"

# List S3 buckets
aws --endpoint-url=http://localhost:4566 s3 ls
```

Expected output:
```
astro-archive
astro-intermediate-data
astro-processed-data
astro-raw-data
```

## Adding New Init Scripts

1. Create a new `.sh` file in this directory
2. Make it executable: `chmod +x scripts/localstack/your-script.sh`
3. Restart LocalStack: `docker compose restart localstack`

Scripts run in alphabetical order. Use numeric prefixes for ordering:
- `01-init-aws.sh`
- `02-create-lambdas.sh`
- etc.

## Troubleshooting

### Buckets Not Created

```bash
# Check if script is mounted correctly
docker compose exec localstack ls -la /etc/localstack/init/ready.d/

# Check script execution logs
docker compose logs localstack | tail -50
```

### Script Permissions

Scripts must be executable:
```bash
chmod +x scripts/localstack/*.sh
```

### awslocal Command Not Found

The `awslocal` command is included in the LocalStack Docker image. If it's not available, use:
```bash
aws --endpoint-url=http://localhost:4566 [command]
```
