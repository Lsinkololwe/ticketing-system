#!/bin/bash
# =============================================================================
# Download GraphQL Schema from Apollo Router
# =============================================================================
#
# Downloads the federated supergraph schema using rover CLI
#
# PREREQUISITES:
#   Install rover: curl -sSL https://rover.apollo.dev/nix/latest | sh
#
# USAGE:
#   ./scripts/download_schema.sh              # Uses default localhost:4000
#   ./scripts/download_schema.sh http://api.example.com/graphql
#
# =============================================================================

set -e

ENDPOINT="${1:-http://localhost:4001/}"
OUTPUT_FILE="lib/graphql/schema.graphql"

echo "📡 Downloading schema from: $ENDPOINT"

# Check if rover is installed
if ! command -v rover &> /dev/null; then
    echo "❌ rover CLI not found"
    echo ""
    echo "Install with:"
    echo "  curl -sSL https://rover.apollo.dev/nix/latest | sh"
    echo ""
    echo "Or on macOS:"
    echo "  brew install rover"
    exit 1
fi

# Create output directory
mkdir -p "$(dirname "$OUTPUT_FILE")"

# Download schema using rover introspection
echo "⏳ Introspecting schema..."
rover graph introspect "$ENDPOINT" > "$OUTPUT_FILE"

echo "✅ Schema saved to: $OUTPUT_FILE"
echo ""
echo "Next step: Run code generation"
echo "  dart run build_runner build --delete-conflicting-outputs"
