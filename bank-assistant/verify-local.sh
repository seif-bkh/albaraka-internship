#!/usr/bin/env bash
# ============================================================
# Al Baraka Bank Assistant — LOCAL verify (run on YOUR host, NOT CI)
#
# One command that:
#   1. starts the Docker compose verify environment (postgres),
#   2. runs the requested build/test checks in throwaway containers,
#   3. writes each check's full log into verify/out/*.log,
#   4. writes a short status file (verify/out/STATUS.txt),
#   5. pushes those log/status files to the current git branch, and
#   6. leaves the compose environment RUNNING (postgres).
#
# Clean up afterwards with:
#     docker compose -f docker-compose.verify.yml down
#
# Usage:
#     ./verify-local.sh                     # backend + frontend
#     ./verify-local.sh backend             # Maven build/test only
#     ./verify-local.sh frontend            # Angular production build only
#     ./verify-local.sh all --no-push       # run checks, do NOT push logs
#
# Requires: Docker Engine + the Compose plugin (`docker compose version`)
#           or the legacy `docker-compose` binary.
# ============================================================
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

COMPOSE_FILE="docker-compose.verify.yml"
OUT="$ROOT/verify/out"
mkdir -p "$OUT"

# ---- locate the compose command (v2 plugin, else legacy v1) -----------------
# Store it as an ARRAY so the words are passed separately (a single quoted
# string like "docker compose -f x.yml" would be treated as one command name).
COMPOSE_CMD=()
if docker compose version >/dev/null 2>&1; then
  COMPOSE_CMD=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE_CMD=(docker-compose)
else
  echo "ERROR: could not find 'docker compose' or 'docker-compose'." >&2
  echo "Install Docker Engine with the Compose plugin, then re-run." >&2
  exit 127
fi
COMPOSE_CMD+=(-f "$COMPOSE_FILE")
DCC_DISP="docker compose -f ${COMPOSE_FILE}"

# ---- args ------------------------------------------------------------------
PART="all"
PUSH=1
usage() {
  cat <<'EOF'
Usage:
    ./verify-local.sh                     # backend + frontend
    ./verify-local.sh backend             # Maven build/test only
    ./verify-local.sh frontend            # Angular production build only
    ./verify-local.sh all --no-push       # run checks, do NOT push logs
EOF
}
for a in "$@"; do
  case "$a" in
    backend|frontend|all) PART="$a" ;;
    --no-push) PUSH=0 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $a"; usage; exit 2 ;;
  esac
done

BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
SHA="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
STAMP="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

STATUS_FILE="$OUT/STATUS.txt"
: > "$STATUS_FILE"
{
  echo "verify-local run @ $STAMP"
  echo "branch: $BRANCH   commit: $SHA"
  echo "requested: $PART"
  echo "------------------------------"
} >> "$STATUS_FILE"

# ---- helpers ----------------------------------------------------------------
run_part() {  # run_part <label> <compose_service> <logfile>
  local label="$1" svc="$2" logfile="$3" rc
  local log="$OUT/$logfile"
  echo ""
  echo "==> [$label] starting ($svc) — log: verify/out/$logfile"
  # NOTE: no `set -e` is active in this script, so a failing pipeline won't
  # abort the run; capture its real exit code from PIPESTATUS below.
  "${COMPOSE_CMD[@]}" run --rm "$svc" 2>&1 | tee "$log"
  rc=${PIPESTATUS[0]}
  echo "==> [$label] finished with exit code $rc"
  echo "[$label] exit=$rc" >> "$STATUS_FILE"
  return "$rc"
}

echo ""
echo "============================================================"
echo " Al Baraka — local verify   (branch=$BRANCH @ $SHA)"
echo " Using: $DCC_DISP"
echo "============================================================"

# ---- 1. bring up the persistent environment (left running) ------------------
echo ""
echo "==> starting postgres (left running; stop with \`$DCC_DISP down\`)"
"${COMPOSE_CMD[@]}" up -d postgres

# ---- 2. refresh the check images, then run the requested checks -------------
ANY_FAIL=0

if [[ "$PART" == "all" || "$PART" == "backend" ]]; then
  run_part "backend" "backend-verify" "backend.log"
  [ $? -eq 0 ] || ANY_FAIL=1
fi

if [[ "$PART" == "all" || "$PART" == "frontend" ]]; then
  run_part "frontend" "frontend-verify" "frontend.log"
  [ $? -eq 0 ] || ANY_FAIL=1
fi

# ---- 3. status summary ------------------------------------------------------
{
  echo "------------------------------"
  echo "result: $([ "$ANY_FAIL" -eq 0 ] && echo ALL_GREEN || echo FAILURES)"
  echo "logs:   verify/out/backend.log  verify/out/frontend.log"
} >> "$STATUS_FILE"

echo ""
echo "============================================================"
echo " result: $([ "$ANY_FAIL" -eq 0 ] && echo ALL_GREEN || echo FAILURES)"
echo " status file: verify/out/STATUS.txt"
echo " compose environment is STILL RUNNING"
echo " cleanup:  docker compose -f $COMPOSE_FILE down"
echo "============================================================"

# ---- 4. push the logs back to the repo (same command) ----------------------
if [ "$PUSH" -eq 1 ]; then
  echo ""
  echo "==> pushing verify logs to origin/$BRANCH ..."
  git add verify/out
  if git diff --cached --quiet; then
    echo "==> nothing changed to commit (logs identical to last run)."
  else
    git commit -m "verify: update build/test logs (${PART}; result=$([ "$ANY_FAIL" -eq 0 ] && echo GREEN || echo FAIL)) @ ${SHA}" >/dev/null \
      && git push origin "$BRANCH" \
      && echo "==> pushed OK." \
      || echo "==> WARNING: commit/push failed — commit it manually: git add verify/out && git commit && git push"
  fi
else
  echo ""
  echo "==> --no-push given: logs left in verify/out/ (commit & push them when ready)."
fi

exit "$ANY_FAIL"
