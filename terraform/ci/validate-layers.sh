#!/bin/bash
# Convenience wrapper for quick validation
# Usage: ./validate-layers.sh [max_layer]

MAX_LAYER=${1:-5}  # Default to all layers

# Call the main deployment script with validate action
exec "$(dirname "$0")/deploy-layers.sh" dummy validate "$MAX_LAYER"