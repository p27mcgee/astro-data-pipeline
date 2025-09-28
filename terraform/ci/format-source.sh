#!/bin/bash
# Convenience wrapper for terraform formatting
# Usage: ./format-source.sh [max_layer]

MAX_LAYER=${1:-5}  # Default to all layers

# Call the main deployment script with format action
exec "$(dirname "$0")/deploy-layers.sh" dummy format "$MAX_LAYER"