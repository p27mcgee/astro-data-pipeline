#!/bin/bash
# Layered Terraform Deployment Script
# Deploys infrastructure in the correct dependency order

set -e

# Configuration
TFVARS_FILE=${1:-"staging.tfvars"}
ACTION=${2:-"plan"}
LAST_LAYER=${3:-${LAST_LAYER:-1}}  # Default to layer 1, can be overridden by env var
ALL_LAYERS=("01-foundation" "02-data" "03-database" "04-compute" "05-monitoring")

# Determine layers to process based on LAST_LAYER and ACTION
LAYERS=()
if [[ "$ACTION" == "destroy" ]]; then
    # For destroy: LAST_LAYER is the lowest layer to destroy, destroy upwards
    for i in "${!ALL_LAYERS[@]}"; do
        layer_num=$((i + 1))
        if [[ $layer_num -ge $LAST_LAYER ]]; then
            LAYERS+=("${ALL_LAYERS[$i]}")
        fi
    done
else
    # For deploy/plan/validate: LAST_LAYER is the highest layer to process
    for i in "${!ALL_LAYERS[@]}"; do
        layer_num=$((i + 1))
        if [[ $layer_num -le $LAST_LAYER ]]; then
            LAYERS+=("${ALL_LAYERS[$i]}")
        fi
    done
fi

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if tfvars file exists (skip for validate-only action)
if [[ ! -f "$TFVARS_FILE" ]] && [[ "$ACTION" != "validate" ]]; then
    log_error "Terraform variables file '$TFVARS_FILE' not found!"
    log_info "Available files:"
    ls -la *.tfvars.example 2>/dev/null || echo "No example files found"
    log_info "For validation only, you can use: $0 dummy validate [last_layer]"
    exit 1
fi

# Validate action
if [[ ! "$ACTION" =~ ^(plan|apply|destroy|validate)$ ]]; then
    log_error "Invalid action '$ACTION'. Use: plan, apply, destroy, or validate"
    log_info "Usage: $0 <tfvars_file> <action> [last_layer]"
    log_info "  tfvars_file: Path to .tfvars file (e.g., staging.tfvars)"
    log_info "  action: plan, apply, destroy, or validate"
    log_info "  last_layer: For deploy/plan/validate: last layer to build (1-5)"
    log_info "             For destroy: last layer to destroy (1-5)"
    log_info "  Environment variable LAST_LAYER can also be used"
    log_info ""
    log_info "Examples:"
    log_info "  $0 staging.tfvars apply 3     # Deploy layers 1, 2, 3 (last built: 3)"
    log_info "  $0 staging.tfvars destroy 3   # Destroy layers 5, 4, 3 (last destroyed: 3)"
    exit 1
fi

# Validate last_layer
if [[ ! "$LAST_LAYER" =~ ^[1-5]$ ]]; then
    log_error "Invalid last_layer '$LAST_LAYER'. Must be between 1 and 5"
    log_info "Available layers:"
    for i in "${!ALL_LAYERS[@]}"; do
        layer_num=$((i + 1))
        echo "  $layer_num: ${ALL_LAYERS[$i]}"
    done
    exit 1
fi

log_info "Starting layered Terraform deployment..."
log_info "Variables file: $TFVARS_FILE"
log_info "Action: $ACTION"
log_info "Last layer: $LAST_LAYER"
log_info "Processing layers: ${LAYERS[*]}"
echo

# Set layer order (reverse for destroy)
if [[ "$ACTION" == "destroy" ]]; then
    # Reverse array for destruction (compatible with both macOS and Linux)
    PROCESSING_LAYERS=()
    for (( i=${#LAYERS[@]}-1; i>=0; i-- )); do
        PROCESSING_LAYERS+=("${LAYERS[i]}")
    done
    log_info "Using reverse order for destruction: ${PROCESSING_LAYERS[*]}"
else
    PROCESSING_LAYERS=("${LAYERS[@]}")
fi

# Process each layer
for layer in "${PROCESSING_LAYERS[@]}"; do
    log_info "Processing Layer: $layer"

    if [[ ! -d "$layer" ]]; then
        log_warning "Layer directory '$layer' not found, skipping..."
        continue
    fi

    cd "$layer"

    # Set tfvars file path (relative to layer directory)
    if [[ "$ACTION" != "validate" ]]; then
        TFVARS_PATH="../$TFVARS_FILE"
        if [[ ! -f "$TFVARS_PATH" ]]; then
            log_error "Terraform variables file '$TFVARS_FILE' not found at $TFVARS_PATH"
            exit 1
        fi
    fi

    # Initialize if needed
    if [[ ! -d ".terraform" ]]; then
        log_info "Initializing Terraform for $layer..."

        # Initialize with remote backend
        terraform init
    fi

    # Initialize workspace if needed
    if [[ "$ACTION" != "validate" ]]; then
        WORKSPACE=$(echo "$TFVARS_FILE" | sed 's/.tfvars//')
        log_info "Configuring workspace for environment: $WORKSPACE"

        # Robust workspace selection - avoid false error exits
        if terraform workspace list | grep -q "^\*\?\s\+$WORKSPACE\$"; then
            log_info "Switching to existing workspace: $WORKSPACE"
            terraform workspace select "$WORKSPACE"
        else
            log_info "Creating new workspace: $WORKSPACE"
            terraform workspace new "$WORKSPACE"
        fi

        log_success "Active workspace: $(terraform workspace show)"
    fi

    # Execute the action
    case $ACTION in
        "validate")
            log_info "Validating $layer..."
            terraform validate

            # Run TFLint if available
            if command -v tflint &> /dev/null; then
                log_info "Running TFLint for $layer..."
                tflint --init &> /dev/null || true  # Initialize if needed, suppress output
                tflint --format=compact
            else
                log_warning "TFLint not found - skipping lint checks (install with 'brew install tflint')"
            fi

            # Run Checkov security scan if available
            if command -v checkov &> /dev/null; then
                log_info "Running Checkov security scan for $layer..."

                # Check for project-level checkov configuration
                if [[ -f "../../.checkov.yml" ]]; then
                    checkov -d . --config-file ../../.checkov.yml --compact --quiet || true
                else
                    checkov -d . --framework terraform --compact --quiet --download-external-modules true || true
                fi
            else
                log_warning "Checkov not found - skipping security scan (install with 'pip install checkov')"
            fi
            ;;
        "plan")
            log_info "Planning $layer..."
            terraform plan -var-file="$TFVARS_PATH" -out="tfplan"
            ;;
        "apply")
            log_info "Applying $layer..."
            if [[ -f "tfplan" ]]; then
                terraform apply tfplan
            else
                terraform apply -var-file="$TFVARS_PATH" -auto-approve
            fi
            ;;
        "destroy")
            log_warning "Destroying $layer..."
            # In CI environment, skip confirmation
            if [[ -n "$TF_IN_AUTOMATION" ]]; then
                log_warning "CI environment detected - proceeding with destruction"
                terraform destroy -var-file="$TFVARS_PATH" -auto-approve
            else
                read -p "Are you sure you want to destroy $layer? (yes/no): " confirm
                if [[ "$confirm" == "yes" ]]; then
                    terraform destroy -var-file="$TFVARS_PATH" -auto-approve
                else
                    log_info "Skipping destruction of $layer"
                fi
            fi
            ;;
    esac

    # Check exit code
    if [[ $? -eq 0 ]]; then
        log_success "Layer $layer completed successfully"
    else
        log_error "Layer $layer failed!"
        exit 1
    fi

    cd ..
    echo
done

# Destroy behavior explanation
if [[ "$ACTION" == "destroy" ]]; then
    log_info "Destroy behavior: LAST_LAYER ($LAST_LAYER) was the last layer destroyed"
    log_info "Layers ${LAYERS[*]} were destroyed in reverse dependency order: ${PROCESSING_LAYERS[*]}"
    if [[ $LAST_LAYER -gt 1 ]]; then
        preserved_layers=()
        for i in "${!ALL_LAYERS[@]}"; do
            layer_num=$((i + 1))
            if [[ $layer_num -lt $LAST_LAYER ]]; then
                preserved_layers+=("${ALL_LAYERS[$i]}")
            fi
        done
        if [[ ${#preserved_layers[@]} -gt 0 ]]; then
            log_info "Preserved layers: ${preserved_layers[*]}"
        fi
    fi
fi

log_success "All layers processed successfully!"

# Summary
echo
log_info "=== Deployment Summary ==="
log_info "Action: $ACTION"
log_info "Last layer: $LAST_LAYER"
log_info "Layers processed: ${#LAYERS[@]} (${LAYERS[*]})"
log_info "Configuration: $TFVARS_FILE"

if [[ "$ACTION" == "plan" ]]; then
    log_info "Next step: Run './ci/deploy-layers.sh $TFVARS_FILE apply $LAST_LAYER' to deploy"
fi