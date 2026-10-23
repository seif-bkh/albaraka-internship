#!/usr/bin/env bash
# ============================================================
# lcov-rebase.sh — make a frontend lcov report addressable from the repo root.
#
# The Angular unit-test builder writes `frontend/coverage/frontend/lcov.info` with
# paths relative to `frontend/` (SF:src/app/services/auth.service.ts). Sonar is run
# from the repository root, where the same file lives at frontend/src/..., so
# without this rewrite every JavaScript coverage line is silently ignored — the
# scan succeeds and reports 0% coverage, which is worse than an error because
# nobody notices.
#
# Usage:
#   scripts/lcov-rebase.sh [path/to/lcov.info]
#
# Idempotent: a report whose SF lines already start with frontend/ is left alone.
# ============================================================
set -euo pipefail

REPORT="${1:-frontend/coverage/frontend/lcov.info}"

if [ ! -s "$REPORT" ]; then
  echo "lcov-rebase: $REPORT not found or empty — run the frontend tests first" >&2
  exit 1
fi

if grep -q '^SF:frontend/' "$REPORT"; then
  echo "lcov-rebase: $REPORT already root-relative, nothing to do"
  exit 0
fi

before=$(grep -c '^SF:' "$REPORT")
sed -i.bak 's|^SF:src/|SF:frontend/src/|' "$REPORT"
rm -f "$REPORT.bak"
after=$(grep -c '^SF:frontend/src/' "$REPORT")

echo "lcov-rebase: $after/$before entries now root-relative"
[ "$after" -eq "$before" ] || {
  echo "lcov-rebase: some entries did not match the expected 'src/' prefix — inspect $REPORT" >&2
  exit 1
}
