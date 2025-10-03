# Terraform State Lock Management

## Overview

This document describes the automated state lock management system implemented to prevent and recover from Terraform state locks that can occur during CI/CD pipeline failures.

## Problem Statement

Terraform uses state locking to prevent concurrent modifications. When a Terraform operation (plan/apply) is interrupted (e.g., CI timeout, manual cancellation, network failure), the lock may not be released properly, blocking all subsequent operations with errors like:

```
Error: Error acquiring the state lock
Lock Info:
  ID:        0d2cb69e-f9fd-7ed5-3571-09bf08c827e1
  Operation: OperationTypeApply
  Who:       pmcgee@dash-midnight.local
  Created:   2025-10-03 14:47:06.994532 +0000 UTC
```

## Solution: 3-Layer Defense

### 1. **Prevention** (Future Enhancement)

- Set appropriate timeouts in deploy-layers.sh
- Implement graceful shutdown handlers
- Use workflow timeout limits

### 2. **Detection** (Automated)

Checks for stale locks before every operation:

- **Pre-Plan**: Check for locks older than 30 minutes
- **Pre-Apply**: Check for locks older than 15 minutes (more aggressive)

### 3. **Recovery** (Automated)

Automatically unlocks stale locks with safety checks:

- **Post-Failure**: Force unlock immediately after failed operations
- **Safety validation**: Checks lock age and ownership before unlocking
- **Audit trail**: Logs all lock operations

## Implementation

### Script: `unlock-stale-locks.sh`

**Location**: `terraform/ci/unlock-stale-locks.sh`

**Usage**:

```bash
# Check all layers for locks older than 30 minutes
./ci/unlock-stale-locks.sh staging "" 30

# Check specific layer
./ci/unlock-stale-locks.sh staging 01-foundation 30

# Force unlock (bypasses safety checks)
FORCE_UNLOCK=true ./ci/unlock-stale-locks.sh staging "" 0
```

**Parameters**:

1. `ENVIRONMENT` - staging, prod, etc.
2. `LAYER` - Specific layer (empty for all layers)
3. `MAX_LOCK_AGE_MINUTES` - Consider locks older than this stale

**Safety Features**:

- ✅ Age-based validation (don't unlock recent locks)
- ✅ Ownership check (don't unlock other users' locks by default)
- ✅ Force option for CI automation (`FORCE_UNLOCK=true`)
- ✅ Detailed logging of all operations

### GitHub Actions Integration

**Workflow**: `.github/workflows/terraform-infrastructure.yml`

#### Added Steps:

**1. Pre-Plan Lock Check** (Line ~294):

```yaml
- name: Check and Clear Stale Locks (Pre-Plan)
  run: |
    cd terraform
    chmod +x ci/unlock-stale-locks.sh
    ./ci/unlock-stale-locks.sh ${ENVIRONMENT} "" 30 || {
      echo "⚠️ Warning: Could not check/clear all locks, proceeding anyway..."
    }
```

**2. Post-Plan Failure Cleanup** (Line ~331):

```yaml
- name: Cleanup Locks on Failure (Post-Plan)
  if: failure() && steps.tf-plan.conclusion == 'failure'
  run: |
    cd terraform
    echo "::warning::Terraform plan failed - attempting to cleanup any locks..."
    chmod +x ci/unlock-stale-locks.sh
    FORCE_UNLOCK=true ./ci/unlock-stale-locks.sh ${ENVIRONMENT} "" 0 || true
```

**3. Pre-Apply Lock Check** (Line ~436):

```yaml
- name: Check and Clear Stale Locks (Pre-Apply)
  run: |
    cd terraform
    chmod +x ci/unlock-stale-locks.sh
    # More aggressive - clear locks older than 15 minutes
    ./ci/unlock-stale-locks.sh ${ENVIRONMENT} "" 15 || {
      echo "⚠️ Warning: Could not check/clear all locks, proceeding anyway..."
    }
```

**4. Post-Apply Failure Cleanup** (Line ~458):

```yaml
- name: Cleanup Locks on Failure (Post-Apply)
  if: failure() && steps.tf-apply.conclusion == 'failure'
  run: |
    cd terraform
    echo "::error::Terraform apply failed - cleaning up locks..."
    chmod +x ci/unlock-stale-locks.sh
    FORCE_UNLOCK=true ./ci/unlock-stale-locks.sh ${ENVIRONMENT} "" 0 || true
```

## Workflow Behavior

### Normal Operation (No Locks)

```
Plan Phase:
  1. Check for stale locks → ✓ None found
  2. Run terraform plan → ✓ Success
  3. Continue workflow

Apply Phase:
  1. Check for stale locks → ✓ None found
  2. Run terraform apply → ✓ Success
  3. Complete workflow
```

### Recovery from Failed Run

```
Plan Phase (After Previous Failure):
  1. Check for stale locks → 🔒 Found lock from 45 minutes ago
  2. Unlock stale lock → ✓ Unlocked
  3. Run terraform plan → ✓ Success
  4. Continue workflow

Apply Phase:
  1. Check for stale locks → ✓ None found (cleaned in plan)
  2. Run terraform apply → ✓ Success
  3. Complete workflow
```

### Handling New Failure

```
Plan Phase:
  1. Check for stale locks → ✓ None found
  2. Run terraform plan → ❌ FAILED
  3. Cleanup locks → ✓ Force unlocked
  4. Workflow fails (but locks are cleaned)

Next Run:
  1. Check for stale locks → ✓ None found (cleaned by previous run)
  2. Run terraform plan → ✓ Can proceed
```

## Local Usage

### Check for locks manually:

```bash
cd terraform
./ci/unlock-stale-locks.sh staging
```

### Force unlock specific layer:

```bash
cd terraform
FORCE_UNLOCK=true ./ci/unlock-stale-locks.sh staging 04-compute 0
```

### Check single layer:

```bash
cd terraform
./ci/unlock-stale-locks.sh staging 01-foundation 30
```

## Manual Override (Emergency)

If automated unlock fails, you can manually unlock:

```bash
cd terraform/04-compute
terraform force-unlock <LOCK_ID>
```

Or use AWS CLI to delete from DynamoDB:

```bash
aws dynamodb delete-item \
  --table-name astro-data-pipeline-terraform-lock-staging \
  --key '{"LockID":{"S":"<state-file-path>-md5"}}'
```

## Lock Age Thresholds

| Context | Threshold | Rationale |
|---------|-----------|-----------|
| CI Pre-Plan | 30 minutes | Conservative - allows long-running plans |
| CI Pre-Apply | 15 minutes | Aggressive - applies are typically faster |
| CI Post-Failure | 0 minutes | Emergency - clean up immediately |
| Local Usage | 30 minutes | Default safe value for manual operations |

## Monitoring

The workflow will output warnings/errors in GitHub Actions:

- `⚠️ Warning: Could not check/clear all locks` - Non-fatal, continues
- `🔒 Lock detected in layer/environment` - Informational
- `✅ Successfully unlocked layer/environment` - Success
- `❌ Failed to unlock layer/environment` - Requires manual intervention

## Safety Considerations

1. **Owner validation**: Won't unlock other users' locks unless `FORCE_UNLOCK=true`
2. **Age validation**: Won't unlock recent locks (respects MAX_LOCK_AGE_MINUTES)
3. **CI context**: In CI, FORCE_UNLOCK is automatically set for post-failure cleanup
4. **Audit trail**: All lock operations are logged

## Future Enhancements

1. **Slack/Email notifications** when locks are auto-unlocked
2. **Metrics collection** - track lock frequency and duration
3. **Lock timeout in Terraform** - Set `-lock-timeout` parameter
4. **Workflow timeouts** - Add overall job timeouts
5. **Lock monitoring dashboard** - Visualize lock patterns

## Troubleshooting

### Locks still present after auto-cleanup

```bash
# Check lock details
cd terraform/04-compute
terraform plan -lock-timeout=1s 2>&1 | grep -A10 "Lock Info"

# Force unlock manually
FORCE_UNLOCK=true ./ci/unlock-stale-locks.sh staging 04-compute 0
```

### Script can't access backend

```bash
# Reinitialize Terraform
cd terraform/04-compute
terraform init
```

### Permission errors in CI

- Ensure the AWS role has DynamoDB permissions for the lock table
- Check that the workflow has `id-token: write` permissions

## References

- [Terraform State Locking](https://www.terraform.io/language/state/locking)
- [DynamoDB State Locking](https://www.terraform.io/language/settings/backends/s3#dynamodb-table-permissions)
- [GitHub Actions Conditional Execution](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#jobsjob_idif)
