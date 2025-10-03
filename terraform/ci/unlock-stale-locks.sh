#!/bin/bash
# Terraform State Lock Management Script
# Safely detects and unlocks stale Terraform state locks

set -e

# Configuration
ENVIRONMENT=${1:-"staging"}
LAYER=${2:-""}  # Optional: specific layer, or empty for all layers
MAX_LOCK_AGE_MINUTES=${3:-30}  # Consider locks older than 30 minutes as stale
ALL_LAYERS=("01-foundation" "02-data" "03-database" "04-compute" "05-monitoring")

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Determine layers to check
if [[ -n "$LAYER" ]]; then
    LAYERS_TO_CHECK=("$LAYER")
else
    LAYERS_TO_CHECK=("${ALL_LAYERS[@]}")
fi

log_info "Checking for stale Terraform locks in $ENVIRONMENT environment..."
log_info "Layers to check: ${LAYERS_TO_CHECK[*]}"
log_info "Max lock age: $MAX_LOCK_AGE_MINUTES minutes"
echo

LOCKS_FOUND=0
LOCKS_RELEASED=0

for layer in "${LAYERS_TO_CHECK[@]}"; do
    if [[ ! -d "$layer" ]]; then
        log_warning "Layer directory '$layer' not found, skipping..."
        continue
    fi

    log_info "Checking layer: $layer"
    cd "$layer"

    # Try to init if needed (to access backend)
    if [[ ! -d ".terraform" ]]; then
        log_info "Initializing Terraform to access backend..."
        terraform init -input=false > /dev/null 2>&1 || {
            log_warning "Could not initialize $layer, skipping..."
            cd ..
            continue
        }
    fi

    # Select workspace
    terraform workspace select "$ENVIRONMENT" > /dev/null 2>&1 || {
        log_warning "Workspace $ENVIRONMENT does not exist in $layer, skipping..."
        cd ..
        continue
    }

    # Attempt to detect lock by trying a refresh with -lock-timeout=1s
    log_info "Checking for active locks in $layer/$ENVIRONMENT..."

    LOCK_OUTPUT=$(terraform plan -refresh-only -lock-timeout=1s 2>&1 || true)

    if echo "$LOCK_OUTPUT" | grep -q "Error acquiring the state lock"; then
        LOCKS_FOUND=$((LOCKS_FOUND + 1))
        log_warning "🔒 Lock detected in $layer/$ENVIRONMENT"

        # Extract lock ID from error message
        LOCK_ID=$(echo "$LOCK_OUTPUT" | grep -A1 "Lock Info:" | grep "ID:" | awk '{print $2}')
        LOCK_CREATED=$(echo "$LOCK_OUTPUT" | grep "Created:" | sed 's/.*Created:\s*//')
        LOCK_WHO=$(echo "$LOCK_OUTPUT" | grep "Who:" | sed 's/.*Who:\s*//')
        LOCK_OPERATION=$(echo "$LOCK_OUTPUT" | grep "Operation:" | sed 's/.*Operation:\s*//')

        echo
        log_info "Lock Details:"
        echo "  Lock ID: $LOCK_ID"
        echo "  Created: $LOCK_CREATED"
        echo "  Who: $LOCK_WHO"
        echo "  Operation: $LOCK_OPERATION"
        echo

        # Calculate lock age
        if [[ -n "$LOCK_CREATED" ]]; then
            LOCK_TS=$(date -j -f "%Y-%m-%d %H:%M:%S" "$(echo $LOCK_CREATED | cut -d'+' -f1 | xargs)" "+%s" 2>/dev/null || echo "0")
            NOW_TS=$(date "+%s")
            AGE_MINUTES=$(( (NOW_TS - LOCK_TS) / 60 ))

            log_info "Lock age: $AGE_MINUTES minutes"

            if [[ $AGE_MINUTES -gt $MAX_LOCK_AGE_MINUTES ]]; then
                log_warning "Lock is older than $MAX_LOCK_AGE_MINUTES minutes - considering it stale"

                # Safety check: Don't unlock if lock is from different user (unless forced)
                CURRENT_USER=$(whoami)
                if [[ "$LOCK_WHO" == *"$CURRENT_USER"* ]] || [[ "$FORCE_UNLOCK" == "true" ]]; then
                    log_warning "Attempting to force-unlock stale lock..."

                    if terraform force-unlock -force "$LOCK_ID" 2>&1; then
                        log_success "✅ Successfully unlocked $layer/$ENVIRONMENT"
                        LOCKS_RELEASED=$((LOCKS_RELEASED + 1))
                    else
                        log_error "❌ Failed to unlock $layer/$ENVIRONMENT"
                    fi
                else
                    log_error "Lock belongs to different user: $LOCK_WHO (current: $CURRENT_USER)"
                    log_info "Set FORCE_UNLOCK=true to override this safety check"
                fi
            else
                log_info "Lock is recent (< $MAX_LOCK_AGE_MINUTES minutes) - likely active, not unlocking"
            fi
        fi
    else
        log_success "✓ No locks detected in $layer/$ENVIRONMENT"
    fi

    cd ..
    echo
done

# Summary
echo
log_info "=== Lock Management Summary ==="
echo "Layers checked: ${#LAYERS_TO_CHECK[@]}"
echo "Locks found: $LOCKS_FOUND"
echo "Locks released: $LOCKS_RELEASED"

if [[ $LOCKS_FOUND -eq 0 ]]; then
    log_success "No locks detected - all clear!"
    exit 0
elif [[ $LOCKS_RELEASED -eq $LOCKS_FOUND ]]; then
    log_success "All detected locks were released successfully"
    exit 0
else
    log_warning "Some locks remain - manual intervention may be required"
    exit 1
fi