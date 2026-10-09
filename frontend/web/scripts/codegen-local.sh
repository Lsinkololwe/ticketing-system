#!/usr/bin/env bash
# Regenerate libs/shared/src/types/graphql/index.ts from the CURRENT backend subgraph SDL,
# without touching docker-resources. Composes the three schema.graphqls (with the shared @auth
# SDL prepended, as the services merge it at runtime) into a temp supergraph using rover and
# points graphql-codegen at it via GRAPHQL_SCHEMA. Rerunnable; writes nothing outside the temp dir
# except the generated types file.
set -euo pipefail
WEB="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="${BACKEND_DIR:-$WEB/../../backend}"
FED="${FEDERATION_VERSION:-=2.15.2}"
AUTH="$BACKEND/shared-library/src/main/resources/graphql/auth.graphqls"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

command -v rover >/dev/null || { echo "rover not found (install Apollo Rover)" >&2; exit 1; }
[ -f "$AUTH" ] || { echo "Missing shared auth SDL: $AUTH" >&2; exit 1; }

{
  echo "federation_version: $FED"
  echo "subgraphs:"
  for pair in identity:identity-service:8083 catalog:catalog-service:8085 booking:booking-service:8082; do
    IFS=: read -r name dir port <<<"$pair"
    src="$BACKEND/$dir/src/main/resources/graphql/schema.graphqls"
    [ -f "$src" ] || { echo "Missing service SDL: $src" >&2; exit 1; }
    cat "$AUTH" "$src" >"$TMP/$name.graphqls"
    printf '  %s:\n    routing_url: http://localhost:%s/graphql\n    schema:\n      file: %s\n' "$name" "$port" "$TMP/$name.graphqls"
  done
} >"$TMP/supergraph.yaml"

node "$WEB/scripts/harmonize-enums.mjs" "$TMP"/identity.graphqls "$TMP"/catalog.graphqls "$TMP"/booking.graphqls
APOLLO_ELV2_LICENSE=accept rover supergraph compose --config "$TMP/supergraph.yaml" >"$TMP/supergraph.graphql"
mkdir -p "$WEB/node_modules/.cache"
cp "$TMP/supergraph.graphql" "$WEB/node_modules/.cache/pml-supergraph.graphql"
cd "$WEB"
GRAPHQL_SCHEMA="$WEB/node_modules/.cache/pml-supergraph.graphql" npx graphql-codegen --config codegen.ts
