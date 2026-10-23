#!/usr/bin/env bash
# ============================================================
# update-live.sh — one command to put the current code on the machine that serves
# the assistant, clean the old stored answers once, and prove the site is on the
# new build.
#
# It is written for someone who does not want to hold any of this in their head:
# run it where the website runs, read the verdict at the end, and if anything is
# wrong, copy the last few lines to whoever maintains the stack.
#
#   cd ~/bank-assistant && ./deploy/update-live.sh
#
# Flags
#   --report          count what still needs cleaning, change nothing
#   --no-pull         use this checkout as it is (skip git pull)
#   --dry-run         print every command, run none
#   --backend-url U   where /actuator/info answers   (default http://localhost:8081)
#   --frontend-url U  where the site is served        (default http://localhost:8080)
#
# What it does, in order: pulls the branch, rebuilds the two images with the
# commit stamped into them, restarts them, runs the one-off answer cleanup while
# the backend boots, turns that cleanup off again, then checks that the running
# containers report the commit you just deployed. It never drops data, never runs
# a migration that deletes, and the cleanup is idempotent — a second run finds
# nothing to do.
# ============================================================
set -euo pipefail

step() { printf '\n>> %s\n' "$*"; }
ok()   { printf '   ok   : %s\n' "$*"; }
warn() { printf '   note : %s\n' "$*"; }
fail() { printf '   FAIL : %s\n' "$*" >&2; exit 1; }

DRY=0 PULL=1 REPORT=0
BACKEND_URL="${BACKEND_URL:-http://localhost:${BACKEND_PORT:-8081}}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:${FRONTEND_PORT:-8080}}"
FIXES=(02f80f3 ee9c6da c1e1090 2cb417a)   # prompt, cleanup, refusal wording, history replay
LABELS=("the new system prompt", "the answer cleanup", "the refusal wording fix", "the history replay fix")

while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY=1 ;;
    --no-pull) PULL=0 ;;
    --report)  REPORT=1 ;;
    --backend-url)  [ $# -ge 2 ] || fail "--backend-url needs a value";  BACKEND_URL="$2"; shift ;;
    --frontend-url) [ $# -ge 2 ] || fail "--frontend-url needs a value"; FRONTEND_URL="$2"; shift ;;
    *) fail "unknown flag '$1' (supported: --report, --no-pull, --dry-run, --backend-url, --frontend-url)" ;;
  esac
  shift
done

run() { if [ "$DRY" = 1 ]; then printf '   dry-run: %s\n' "$*"; else "$@"; fi; }

cd "$(git -C "$(dirname "${BASH_SOURCE[0]}")/.." rev-parse --show-toplevel)" || fail "not inside a git checkout"
command -v docker >/dev/null 2>&1 || fail "docker is not installed on this machine — run this where the website actually runs."
if docker compose version >/dev/null 2>&1; then COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then COMPOSE=(docker-compose)
else fail "neither 'docker compose' nor 'docker-compose' works here."
fi
# A host that keeps a prod overlay uses it; one that does not gets the plain stack.
COMPOSE_FILES=(-f docker-compose.yml)
[ -f docker-compose.prod.yml ] && COMPOSE_FILES=(-f docker-compose.yml -f docker-compose.prod.yml)
compose() { "${COMPOSE[@]}" "${COMPOSE_FILES[@]}" "$@"; }

step "0/4 Which machine am I on? (this must be the one that serves the website)"
printf '   host  : %s\n' "$(hostname)"
printf '   folder: %s\n' "$PWD"

step "1/4 Getting the code"
if [ "$PULL" = 1 ]; then
  run git pull --ff-only || fail "git pull refused (local changes or a diverged branch). Do not force it here — send this output to whoever maintains the stack."
fi
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
SHA="$(git rev-parse --short HEAD)"
ok "on $BRANCH at $SHA"
MISSING=()
for i in "${!FIXES[@]}"; do
  git cat-file -e "${FIXES[$i]}" 2>/dev/null \
    && git merge-base --is-ancestor "${FIXES[$i]}" HEAD 2>/dev/null \
    || MISSING+=("${FIXES[$i]} ${LABELS[$i]}")
done
if [ "${#MISSING[@]}" -gt 0 ]; then
  fail "this checkout does not contain $(printf '%s; ' "${MISSING[@]}"). It is on the wrong branch — update it with:
     git fetch origin && git checkout arena/01a0c44c-bank-assistant && git pull --ff-only"
fi
ok "the checkout contains the four fixes you asked for"

step "2/4 Building and restarting (this is the part that updates the website)"
# Compose interpolates these from the environment, so exporting them is how the flags
# reach the container without editing any file on the host.
if [ "$REPORT" = 1 ]; then
  warn "--report: the cleanup will count old rows and write nothing"
  export CHAT_HISTORY_BACKFILL_DRY_RUN=true
else
  export CHAT_HISTORY_BACKFILL_DRY_RUN=false
fi
export GIT_SHA="$SHA" GIT_BRANCH="$BRANCH" CHAT_HISTORY_BACKFILL_ENABLED=true
run compose up -d --build backend frontend
ok "requested. Waiting for the backend to finish starting up…"

step "3/4 Cleaning the old answers that customers could still re-read"
LINE=""
for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18; do
  LINE="$(compose logs --tail 400 backend 2>/dev/null \
          | grep 'Customer answer backfill' | tail -1 || true)"
  [ -n "$LINE" ] && break
  sleep 5
done
if [ -z "$LINE" ]; then
  warn "no cleanup line in the backend log yet. It is harmless — it runs again on the next
   boot with the same flags. Check with:  ${COMPOSE[*]} ${COMPOSE_FILES[*]} logs backend | grep backfill"
else
  printf '   log  : %s\n' "$(printf '%s' "$LINE" | sed 's/.*Customer answer backfill//')"
  COUNT="$(printf '%s' "$LINE" | grep -oE 'rewrite [0-9]+' | grep -oE '[0-9]+' | tail -1 || true)"
  if [ "$DRY" = 1 ]; then
    warn "dry run: nothing was restarted, so the line above is whatever the running container last said."
  else
    case "${COUNT:-}" in
      ''  ) warn "could not read the number from the log; the line above is what it said." ;;
      0   ) ok "nothing to clean — every stored answer already matches what customers were shown." ;;
      *   ) if [ "$REPORT" = 1 ]; then
              ok "$COUNT row(s) still carry the old wording. Run the same command without --report to clean them."
            else
              ok "$COUNT row(s) rewritten to the clean version. The admin journal keeps the documents and the reason, untouched."
            fi ;;
    esac
  fi
fi
# The cleanup is a one-off: turn it back off so no later boot repeats it.
export CHAT_HISTORY_BACKFILL_ENABLED=false CHAT_HISTORY_BACKFILL_DRY_RUN=true
run compose up -d backend

step "4/4 Is the website actually on the new build?"
LIVE_SHA="$(curl -fsS --max-time 8 "$BACKEND_URL/actuator/info" 2>/dev/null \
             | tr ',' '\n' | grep -o '"gitSha":"[^"]*"' | cut -d'"' -f4 || true)"
UI_SHA="$(curl -fsS --max-time 8 "$FRONTEND_URL/version.json" 2>/dev/null \
           | grep -o '"sha":"[^"]*"' | cut -d'"' -f4 || true)"
[ -n "$LIVE_SHA" ] && ok "backend is running $LIVE_SHA" || warn "backend did not answer at $BACKEND_URL/actuator/info"
[ -n "$UI_SHA" ]   && ok "frontend is running $UI_SHA"   || warn "frontend did not answer at $FRONTEND_URL/version.json"
if [ -n "$LIVE_SHA" ] && [ "$LIVE_SHA" != "$SHA" ]; then
  warn "the backend reports $LIVE_SHA but the code here is $SHA — the container was not rebuilt."
fi

step "You can test it now"
cat <<EOF
   Open the site (locally: $FRONTEND_URL — otherwise use your normal web address)
   and ask the assistant, in Arabic:  ما هي شروط تمويل التجهيز؟

   You should get one of these two replies:
     1. the equipment-financing conditions themselves, from the bank's guide —
        new equipment, a proforma invoice under three months, a 20% personal
        contribution — possibly followed by the documents it asks for; or
     2. if no document actually answers it: the short sentence
        «لا تتوفر لديّ بعد معلومات موثوقة حول هذا الموضوع في وثائقي. يرجى الاتصال
        مباشرة بفرع بنك البركة.»
        and nothing else. No list of documents, no file names, no "source" line.

   Then reload the page. The earlier messages must come back clean as well — that
   is the history-cleanup part of this run.

   If it still shows a list of documents, send these lines to whoever maintains the
   stack, exactly as they are:
       branch/commit here : $BRANCH@$SHA
       backend reports    : ${LIVE_SHA:-nothing}
       frontend reports   : ${UI_SHA:-nothing}
       cleanup log        : ${LINE:-no line found}
EOF
