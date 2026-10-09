#!/usr/bin/env bash
# Writes each subgraph's SDL as the running service serves it: the shared @auth SDL, which the
# services merge from shared-library's classpath, followed by the service's own schema.graphqls.
# Publishing or checking the bare schema.graphqls sends a schema that uses @auth without
# declaring it.
#
# Usage: backend/tools/federation/subgraph-sdl.sh <output-directory>
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${1:?output directory}"
mkdir -p "$OUT"
for service in identity catalog booking; do
  cat "$ROOT/shared-library/src/main/resources/graphql/auth.graphqls" \
      "$ROOT/$service-service/src/main/resources/graphql/schema.graphqls" > "$OUT/$service.graphqls"
done
