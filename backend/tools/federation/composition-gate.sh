#!/usr/bin/env bash
# Composition gate: the three subgraphs must compose, and a broken subgraph must not.
#
# Usage: backend/tools/federation/composition-gate.sh [output-supergraph-path]
#
# 1. Composes the on-disk SDL of identity, catalog and booking — each with the shared @auth SDL
#    prepended, as the running services merge it from the classpath — at the pinned federation
#    version. Failure here fails the gate.
# 2. Composes three deliberately broken variants and requires each to FAIL, with the error code
#    the break should produce. A gate that is only ever run against correct schemas passes
#    identically whether or not it checks anything; these cases are what make its green mean
#    something.
#
# Needs `rover` on PATH and nothing running. The composition config is written to a temporary
# directory: no router or composition configuration is kept in this repository.
set -euo pipefail

FEDERATION_VERSION="=2.15.2"   # the same pin as docker-resources/apollo-router/ticketing
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
AUTH="$ROOT/shared-library/src/main/resources/graphql/auth.graphqls"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

command -v rover >/dev/null || { echo "rover is not on PATH" >&2; exit 2; }

prepare() {
  for service in identity catalog booking; do
    cat "$AUTH" "$ROOT/$service-service/src/main/resources/graphql/schema.graphqls" > "$WORK/$service.graphqls"
  done
  cat > "$WORK/supergraph.yaml" <<YAML
federation_version: $FEDERATION_VERSION
subgraphs:
  identity: {routing_url: "http://identity-service:8083/graphql", schema: {file: ./identity.graphqls}}
  catalog:  {routing_url: "http://catalog-service:8085/graphql",  schema: {file: ./catalog.graphqls}}
  booking:  {routing_url: "http://booking-service:8082/graphql",  schema: {file: ./booking.graphqls}}
YAML
}

compose() { # <output> <error-log>
  (cd "$WORK" && rover supergraph compose --config supergraph.yaml --elv2-license accept > "$1" 2> "$2")
}

# Applies a text substitution to one subgraph file and fails loudly if the anchor is gone,
# because a fixture whose anchor no longer matches would compose cleanly and be reported as a
# gate that did not catch it — or worse, silently skipped.
mutate() { # <service> <anchor> <replacement>
  python3 - "$WORK/$1.graphqls" "$2" "$3" <<'PY'
import sys
path, anchor, replacement = sys.argv[1:]
text = open(path).read()
if anchor not in text:
    sys.exit(f"fixture anchor not found in {path}: {anchor!r}")
open(path, "w").write(text.replace(anchor, replacement, 1))
PY
}

failures=0

prepare
if compose "$WORK/supergraph.graphql" "$WORK/real.err"; then
  echo "PASS  the three subgraphs compose"
  [ -n "${1:-}" ] && cp "$WORK/supergraph.graphql" "$1"
else
  echo "FAIL  the three subgraphs do not compose:" >&2
  grep -v '^HINT' "$WORK/real.err" >&2 || true
  exit 1
fi

expect_failure() { # <name> <expected-error-code> <service> <anchor> <replacement>
  prepare
  mutate "$3" "$4" "$5"
  if compose /dev/null "$WORK/case.err"; then
    echo "FAIL  $1: composed, and it must not" >&2
    failures=$((failures + 1))
  elif grep -q "$2" "$WORK/case.err"; then
    echo "PASS  $1 is refused ($2)"
  else
    echo "FAIL  $1: refused, but not with $2:" >&2
    grep -v '^HINT' "$WORK/case.err" >&2 || true
    failures=$((failures + 1))
  fi
}

expect_failure "a redeclared id in an extend type" "duplicate definitions for the \`id\` field" booking \
  'extend type User @key(fields: "id") {' \
  $'extend type User @key(fields: "id") {\n    id: ID!'

expect_failure "a shared field with a mismatched scalar" "FIELD_TYPE_MISMATCH" booking \
  'pageSize: Int! @shareable' \
  'pageSize: String! @shareable'

expect_failure "a field of a type another subgraph owns" "INVALID_FIELD_SHARING" catalog \
  'type Organization @key(fields: "id", resolvable: false) {' \
  $'type Organization @key(fields: "id") {\n    name: String!'

if [ "$failures" -gt 0 ]; then
  echo "$failures composition gate case(s) failed" >&2
  exit 1
fi
echo "Composition gate: green"
