#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# spec-status.sh — traceability roll-up: spec → code → test.
#
#   ./scripts/spec-status.sh              full table
#   ./scripts/spec-status.sh --orphans    only specs with no implementing code
#   ./scripts/spec-status.sh --drift      only specs claiming verified with open boxes
#   ./scripts/spec-status.sh --waves      counts per wave
#
# Flags:
#   ORPHAN    past draft, no implementing code
#   UNTESTED  code exists, no tagged test
#   DRIFT     claims verified while acceptance boxes remain unchecked
#   BLOCKED   a blocker has not reached `implemented`
# ---------------------------------------------------------------------------
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

mode="${1:-}"

# bash 3.2 on macOS has no associative arrays — use a flat "id status" index.
INDEX=$(for y in specs/*/*/spec.yaml; do
  [ -f "$y" ] || continue
  printf '%s %s\n' "$(awk '/^id:/{print $2; exit}' "$y")" \
                   "$(awk '/^status:/{print $2; exit}' "$y")"
done)
status_of() { echo "$INDEX" | awk -v k="$1" '$1==k{print $2; found=1} END{if(!found) print "unknown"}'; }

printf '%-14s %-4s %-12s %-42s %5s %5s %5s  %s\n' \
  ID WAVE STATUS TITLE SRC TEST OPEN FLAGS
printf '%s\n' "$(printf '─%.0s' {1..112})"

total=0; verified=0; orphans=0; drifts=0

for y in specs/*/*/spec.yaml; do
  [ -f "$y" ] || continue
  d=$(dirname "$y")
  id=$(awk '/^id:/{print $2; exit}' "$y")
  st=$(awk '/^status:/{print $2; exit}' "$y")
  wv=$(awk '/^wave:/{print $2; exit}' "$y")
  ti=$(awk -F': ' '/^title:/{print substr($0, index($0,": ")+2)}' "$y" | cut -c1-41)

  # grep -c exits 1 on a zero count, so never chain `|| echo 0` — it emits twice.
  # target/ is excluded: a compiled copy of an annotated source file is not a
  # second implementation, and counting it doubles every SRC number.
  hits=$(grep -rl "$id" backend --include='*.java' 2>/dev/null | grep -v '/target/')
  src=$(printf '%s\n' "$hits" | grep -v '^$' | grep -v '/src/test/' | wc -l | tr -d ' ')
  tst=$(printf '%s\n' "$hits" | grep -v '^$' | grep    '/src/test/' | wc -l | tr -d ' ')
  open=$(grep    '^- \[ \]' "$d/spec.md" 2>/dev/null | wc -l | tr -d ' ')

  flags=""
  [ "$src" -eq 0 ] && [ "$st" != draft ] && [ "$st" != deferred ] && [ "$st" != withdrawn ] \
    && { flags="$flags ORPHAN"; orphans=$((orphans+1)); }
  [ "$tst" -eq 0 ] && [ "$src" -gt 0 ] && flags="$flags UNTESTED"
  [ "$st" = verified ] && [ "$open" -gt 0 ] && { flags="$flags DRIFT"; drifts=$((drifts+1)); }

  # blockers must be implemented or later
  for b in $(awk '/^blocked_by:/{gsub(/[][,]/," ");for(i=2;i<=NF;i++)print $i}' "$y"); do
    bs=$(status_of "$b")
    case "$bs" in implemented|verified) ;; *) flags="$flags BLOCKED($b:$bs)" ;; esac
  done

  total=$((total+1)); [ "$st" = verified ] && verified=$((verified+1))

  case "$mode" in
    --orphans) [[ "$flags" == *ORPHAN* ]] || continue ;;
    --drift)   [[ "$flags" == *DRIFT*  ]] || continue ;;
  esac

  printf '%-14s %-4s %-12s %-42s %5s %5s %5s %s\n' \
    "$id" "${wv:--}" "$st" "$ti" "$src" "$tst" "$open" "$flags"
done

if [ "$mode" = "--waves" ]; then
  printf '\nBy wave:\n'
  for w in 0 1 2 3 4 5 6 7; do
    n=$(grep -l "^wave: $w$" specs/*/*/spec.yaml 2>/dev/null | wc -l | tr -d ' ')
    [ "$n" -gt 0 ] && printf '  wave %s  %s spec(s)\n' "$w" "$n"
  done
fi

printf '\n%s specs · %s verified · %s orphaned · %s drifting\n' \
  "$total" "$verified" "$orphans" "$drifts"
[ "$drifts" -gt 0 ] && exit 1 || exit 0
