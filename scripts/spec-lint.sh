#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# spec-lint.sh — validates spec structure AND the construct rules from
#                specs/CONVENTIONS.md.
#
#   ./scripts/spec-lint.sh                          everything
#   ./scripts/spec-lint.sh specs/finance/001-x       one spec's structure
#   ./scripts/spec-lint.sh --reactive                CONVENTIONS §1
#   ./scripts/spec-lint.sh --persistence             CONVENTIONS §2
#   ./scripts/spec-lint.sh --events                  CONVENTIONS §3
#   ./scripts/spec-lint.sh --federation              CONVENTIONS §4
#   ./scripts/spec-lint.sh --errors                  CONVENTIONS §5
#   ./scripts/spec-lint.sh --security                CONVENTIONS §6
#   ./scripts/spec-lint.sh --money                   CONVENTIONS §7
#   ./scripts/spec-lint.sh --clock                   CONVENTIONS §8
#
# Exit 0 = clean. Any FAIL exits 1.
# ---------------------------------------------------------------------------
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

fail=0
red=$'\033[31m'; grn=$'\033[32m'; dim=$'\033[2m'; off=$'\033[0m'
err() { printf '  %sFAIL%s %s\n' "$red" "$off" "$1"; fail=1; }
ok()  { printf '  %s ok %s %s\n' "$grn" "$off" "$1"; }
hdr() { printf '\n%s── %s%s\n' "$dim" "$1" "$off"; }

JAVA=(backend --include='*.java')
SDL=(backend --include='*.graphqls')

# Production Java only: test sources and generated build output are excluded
# everywhere. A `target/` hit is a compiled copy of a source file that has
# already been reported, so counting it doubles every finding.
prod() {
  grep -rn "$@" "${JAVA[@]}" 2>/dev/null \
    | grep -v '/src/test/' | grep -v '/target/' || true
}

# waivers: one "pattern # reason" per line
WAIVERS=specs/_waivers.txt
waived() { [ -f "$WAIVERS" ] && grep -qF -- "$1" "$WAIVERS"; }

scan() { # scan <extended-regex> <message>
  local pat="$1" msg="$2" hits
  hits=$(prod -E "$pat")
  if [ -z "$hits" ]; then ok "$msg"; else
    err "$msg"; printf '%s\n' "$hits" | head -6 | sed 's/^/       /'
  fi
}

# ===========================================================================
# 1 · Spec structure
# ===========================================================================
lint_structure() {
  hdr "spec structure"
  local target="${1:-}" dirs
  if [ -n "$target" ]; then dirs="$target"
  else dirs=$(find specs -mindepth 2 -maxdepth 2 -type d -not -path 'specs/_templates*'); fi

  for dir in $dirs; do
    [ -d "$dir" ] || continue
    local id
    for f in spec.yaml spec.md; do
      [ -f "$dir/$f" ] || err "$dir missing $f"
    done
    [ -f "$dir/spec.yaml" ] || continue

    id=$(awk '/^id:/{print $2; exit}' "$dir/spec.yaml")
    [[ "$id" =~ ^ET-[A-Z]+-[0-9]{3}$ ]] || err "$dir bad id: '${id:-<none>}'"

    awk '/^status:/{print $2; exit}' "$dir/spec.yaml" \
      | grep -qE '^(draft|approved|in-progress|implemented|verified|deferred|withdrawn)$' \
      || err "$id invalid status"

    grep -q '^verify:' "$dir/spec.yaml" || err "$id spec.yaml has no verify: block"

    # the six required sections
    for sec in '## 1. Capability' '## 2. Design decisions' '## 3. Requirements' \
               '## 4. Model' '## 5. Tasks' '## 6. Out of scope'; do
      grep -qF "$sec" "$dir/spec.md" || err "$id spec.md missing section '$sec'"
    done

    grep -q 'THE SYSTEM SHALL' "$dir/spec.md" || err "$id no EARS requirement"
    grep -qE '^- \[[ x]\] \*\*T[0-9]' "$dir/spec.md" || err "$id §5 has no tasks"
    grep -qE '^- \[[ x]\] ' "$dir/spec.md" || err "$id no acceptance criteria"

    # Greenfield: no remediation language. Note "stub" alone is NOT a marker here —
    # "stub type" is core Apollo Federation vocabulary (ET-PLT-004) and appears
    # legitimately throughout. Only the brownfield senses are matched.
    if grep -qniE '\b(currently|today the code|at present|as it stands today|stub implementation|the existing code|delete the existing|replace the existing)\b' "$dir/spec.md"; then
      err "$id contains remediation language — specs are written greenfield"
      grep -niE '\b(currently|today the code|at present|as it stands today|stub implementation|the existing code|delete the existing|replace the existing)\b' "$dir/spec.md" \
        | head -3 | sed 's/^/       /'
    fi
  done

  local dupes
  dupes=$(grep -h '^id:' specs/*/*/spec.yaml 2>/dev/null | sort | uniq -d)
  [ -z "$dupes" ] && ok "no duplicate spec ids" || err "duplicate ids: $dupes"

  # blocked_by must resolve.
  # Fed by process substitution, not a pipe: a `| while` runs the loop in a
  # subshell, so err()'s `fail=1` lands in the child and is discarded — the FAIL
  # prints but the script still exits 0 and reports "clean".
  local known unresolved=0
  known=$(grep -h '^id:' specs/*/*/spec.yaml 2>/dev/null | awk '{print $2}')
  while read -r ref; do
    [ -z "$ref" ] && continue
    echo "$known" | grep -qx "$ref" \
      || { err "blocked_by references unknown spec $ref"; unresolved=1; }
  done < <(grep -h -A20 '^blocked_by:' specs/*/*/spec.yaml 2>/dev/null \
             | grep -oE 'ET-[A-Z]+-[0-9]{3}' | sort -u)
  [ "$unresolved" -eq 0 ] && ok "every blocked_by reference resolves"
}

# ===========================================================================
# 2 · CONVENTIONS §1 — reactivity
# ===========================================================================
lint_reactive() {
  hdr "reactivity (CONVENTIONS §1)"
  scan '\.block\(\)|\.blockFirst\(\)|\.blockLast\(\)|\.toFuture\(\)\.get\(\)' \
       'no blocking call in production code'
  scan '\.subscribe\(\)' \
       'no fire-and-forget .subscribe() — return the chain'

  grep -rq 'spring-boot-starter-web<' backend --include='pom.xml' 2>/dev/null \
    && err 'spring-boot-starter-web on a classpath — this platform is WebFlux only' \
    || ok 'no servlet stack on any classpath'

  grep -rq 'spring-cloud-starter-gateway<' backend --include='pom.xml' 2>/dev/null \
    && err 'deprecated spring-cloud-starter-gateway — use -server-webflux' \
    || ok 'gateway uses the WebFlux starter'

  grep -rqE '^\s+gateway:' backend/api-gateway/src/main/resources/*.yml 2>/dev/null \
    && ! grep -rq 'server:' backend/api-gateway/src/main/resources/*.yml 2>/dev/null \
    && err 'gateway config not under spring.cloud.gateway.server.webflux.*' \
    || ok 'gateway config namespace is the WebFlux one'
}

# ===========================================================================
# 3 · CONVENTIONS §2 — persistence
# ===========================================================================
lint_persistence() {
  hdr "persistence (CONVENTIONS §2)"

  scan '\b(private|protected|public)\s+(double|float|Double|Float)\s+\w*([Aa]mount|[Bb]alance|[Pp]rice|[Ff]ee|[Tt]otal|[Cc]ommission)' \
       'no floating-point money'
  scan '\bLocalDateTime\b' \
       'no LocalDateTime — timestamps are Instant'
  scan '\bjava\.util\.Date\b' \
       'no java.util.Date'

  # Business documents must not reach PostgreSQL. Modulith's own registry is the
  # only thing allowed there, and it is configured, not annotated.
  scan '@(Entity|Table)\b.*\n?' \
       'no JPA entities — PostgreSQL holds framework infrastructure only'

  # Every @Document is a row of the ET-PLT-002 registry.
  local reg=specs/_platform/002-persistence-baseline/spec.md
  if [ -f "$reg" ]; then
    local names unknown=0
    names=$(grep -oE '^\| `[a-z0-9_]+`' "$reg" | tr -d '|` ')
    while IFS= read -r c; do
      [ -z "$c" ] && continue
      printf '%s\n' "$names" | grep -qx "$c" \
        || { err "@Document(\"$c\") names no row of the ET-PLT-002 §4 collection registry"; unknown=1; }
    done < <(grep -rhoE '@Document\(\s*(collection\s*=\s*)?"[a-z0-9_]+"' "${JAVA[@]}" 2>/dev/null \
             | grep -v '/target/' | sed 's/.*"\(.*\)"/\1/' | sort -u)
    [ "$unknown" -eq 0 ] && ok 'every @Document names a registry row'
  fi

  # Balance-bearing documents need optimistic locking.
  local nover=0
  while IFS= read -r f; do
    case "$f" in */src/test/*|*/target/*) continue ;; esac
    grep -q '@Version' "$f" \
      || { err "$f: carries a balance but declares no @Version"; nover=1; }
  done < <(grep -rlE 'BigDecimal\s+(current)?[Bb]alance' "${JAVA[@]}" 2>/dev/null | grep -v '/target/')
  [ "$nover" -eq 0 ] && ok 'every balance-bearing document is version-locked'
}

# ===========================================================================
# 4 · CONVENTIONS §3 — events
# ===========================================================================
lint_events() {
  hdr "event contract (CONVENTIONS §3)"

  # The bus must never be reached from inside a transaction. Checking the 30
  # lines after @Transactional rather than the whole file: a class may legitimately
  # hold both a transactional method and a separate publisher method.
  local intx
  intx=$(prod -A30 '@Transactional' | grep -E 'streamBridge\.send|StreamBridge' || true)
  [ -z "$intx" ] && ok 'no bus publish inside a transaction' \
    || { err 'StreamBridge inside a @Transactional method — publish after commit'; \
         printf '%s\n' "$intx" | head -5 | sed 's/^/       /'; }

  scan '@EventListener\b' \
       'no bare @EventListener — use @ApplicationModuleListener'

  # A listener that throws holds the message and dead-letters a transient failure.
  local rethrow
  rethrow=$(prod -A25 '@ApplicationModuleListener' | grep -E 'throw new ' || true)
  [ -z "$rethrow" ] && ok 'no listener rethrows a delivery failure' \
    || { err 'listener throws — record the failure as an event instead'; \
         printf '%s\n' "$rethrow" | head -5 | sed 's/^/       /'; }
}

# ===========================================================================
# 5 · CONVENTIONS §4 — federation
# ===========================================================================
lint_federation() {
  hdr "federation (CONVENTIONS §4)"

  local redef
  redef=$(grep -rn -A12 '^extend type' "${SDL[@]}" 2>/dev/null \
          | grep -E '^\S+[-:][0-9]+[-:]\s*id:\s*ID!' || true)
  [ -z "$redef" ] && ok 'no id redeclared inside an extend block' \
    || { err "id redeclared in an extend block — 'tried to redefine field'"; \
         printf '%s\n' "$redef" | head -5 | sed 's/^/       /'; }

  # Federation version must be identical across subgraphs.
  local vers
  vers=$(grep -rhoE 'specs\.apollo\.dev/federation/v[0-9.]+' "${SDL[@]}" 2>/dev/null | sort -u | wc -l | tr -d ' ')
  [ "$vers" -le 1 ] && ok 'one federation version across all subgraphs' \
    || err "subgraphs link $vers different federation versions"

  # Shared scalars must agree.
  local scal
  scal=$(grep -rhoE '^scalar [A-Za-z]+' backend/*/src/main/resources/graphql/schema.graphqls 2>/dev/null \
         | sort | uniq -c | awk '$1>0{print $3}' | sort -u)
  [ -n "$scal" ] && ok "shared scalars declared: $(echo "$scal" | tr '\n' ' ')" \
                 || err 'no shared scalars declared'

  # Composition is the real gate.
  local compose=../docker-resources/apollo-router/ticketing/compose-supergraph.sh
  if [ -x "$compose" ]; then
    "$compose" --static >/dev/null 2>&1 \
      && ok 'supergraph composes from on-disk SDL' \
      || err 'supergraph composition FAILED — run compose-supergraph.sh --static'
  else
    err "composition script not found at $compose"
  fi
}

# ===========================================================================
# 6 · CONVENTIONS §5 — errors
# ===========================================================================
lint_errors() {
  hdr "error contract (CONVENTIONS §5)"

  local reg=specs/_platform/005-error-contract/spec.md
  if [ -f "$reg" ]; then
    local codes unknown=0
    codes=$(grep -oE '^\| `[A-Z][A-Z0-9_]+`' "$reg" | tr -d '|` ')
    while IFS= read -r c; do
      [ -z "$c" ] && continue
      printf '%s\n' "$codes" | grep -qx "$c" \
        || { err "errorCode \"$c\" is no row of the ET-PLT-005 §4 registry"; unknown=1; }
    done < <(prod -hoE '"errorCode",\s*"[A-Z0-9_]+"' | sed 's/.*"\([A-Z0-9_]*\)"$/\1/' | sort -u)
    [ "$unknown" -eq 0 ] && ok 'every errorCode names a registry row'
  fi

  # Every DomainRefusal subtype needs a handler, or it surfaces as INTERNAL.
  local nohandler=0
  while IFS= read -r f; do
    local cls
    cls=$(basename "$f" .java)
    prod -q "@DgsExceptionHandler($cls.class)" \
      || { err "$cls extends DomainRefusal but has no @DgsExceptionHandler"; nohandler=1; }
  done < <(grep -rl 'extends DomainRefusal' "${JAVA[@]}" 2>/dev/null | grep -v '/target/')
  [ "$nohandler" -eq 0 ] && ok 'every domain refusal has a typed handler'

  local noretry
  noretry=$(prod -A8 '@DgsExceptionHandler' | grep -c 'retryable' || true)
  [ "${noretry:-0}" -gt 0 ] && ok 'handlers carry extensions.retryable' \
                            || err 'no handler sets extensions.retryable'
}

# ===========================================================================
# 7 · CONVENTIONS §6 — security
# ===========================================================================
lint_security() {
  hdr "security (CONVENTIONS §6)"

  # One permission resolver, one implementation.
  local resolvers
  resolvers=$(grep -rl 'hasPermission' "${JAVA[@]}" 2>/dev/null \
              | grep -v '/src/test/' | grep -v '/target/' \
              | grep -vE 'PermissionResolver|PermissionEvaluator' || true)
  [ -z "$resolvers" ] && ok 'permission resolution lives in one implementation' \
    || { err 'permission resolution written outside the resolver'; \
         printf '%s\n' "$resolvers" | head -5 | sed 's/^/       /'; }

  # Internal endpoints must be scope-gated.
  local openinternal
  openinternal=$(prod -B4 '"/api/internal/\*\*"' | grep -E 'permitAll' || true)
  [ -z "$openinternal" ] && ok '/api/internal/** is scope-gated' \
    || err '/api/internal/** is permitAll — these endpoints bypass user authorization'

  scan 'keycloakUserId' \
       'no User.keycloakUserId — User.id IS the Keycloak user ID'
}

# ===========================================================================
# 8 · CONVENTIONS §7 — money
# ===========================================================================
lint_money() {
  hdr "money (CONVENTIONS §7)"

  # A balance is a projection of the ledger, never an assignment.
  local direct
  direct=$(prod -E 'set(Current)?Balance\(' \
           | grep -viE 'LedgerService|JournalService|BalanceProjection|Test' || true)
  [ -z "$direct" ] && ok 'no balance assigned outside the ledger' \
    || { err 'balance assigned directly — post a journal pair'; \
         printf '%s\n' "$direct" | head -6 | sed 's/^/       /'; }

  scan 'RoundingMode\.(HALF_DOWN|HALF_EVEN|DOWN|UP|FLOOR|CEILING)' \
       'money rounds HALF_UP everywhere'

  local nocur
  nocur=$(grep -rlE 'BigDecimal\s+(amount|grossAmount|netAmount)' "${JAVA[@]}" 2>/dev/null \
          | grep -v '/target/' | xargs grep -Ln 'currency' 2>/dev/null || true)
  [ -z "$nocur" ] && ok 'every monetary document carries its currency' \
    || { err 'monetary document without a currency field'; \
         printf '%s\n' "$nocur" | head -5 | sed 's/^/       /'; }
}

# ===========================================================================
# 9 · CONVENTIONS §8 — time
# ===========================================================================
lint_clock() {
  hdr "clock (CONVENTIONS §8)"
  scan '(Instant|LocalDateTime|LocalDate|ZonedDateTime|OffsetDateTime)\.now\(\)|System\.currentTimeMillis\(\)' \
       'no inline now() — inject the Clock bean'

  prod -q 'Clock clock' && ok 'a Clock bean is injected' \
                        || err 'no Clock injection found — CONVENTIONS §8 requires one'
}

# ===========================================================================
main() {
  case "${1:-}" in
    --reactive)    lint_reactive ;;
    --persistence) lint_persistence ;;
    --events)      lint_events ;;
    --federation)  lint_federation ;;
    --errors)      lint_errors ;;
    --security)    lint_security ;;
    --money)       lint_money ;;
    --clock)       lint_clock ;;
    --structure)   lint_structure "${2:-}" ;;
    specs/*)       lint_structure "$1" ;;
    "")            lint_structure; lint_reactive; lint_persistence; lint_events
                   lint_federation; lint_errors; lint_security; lint_money; lint_clock ;;
    *)             echo "unknown option: $1" >&2; exit 2 ;;
  esac
  printf '\n'
  [ "$fail" -eq 0 ] && printf '%sspec-lint: clean%s\n' "$grn" "$off" \
                    || printf '%sspec-lint: failures above%s\n' "$red" "$off"
  exit "$fail"
}
main "$@"
