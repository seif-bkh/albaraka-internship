#!/usr/bin/env bash
# ============================================================
# Promotion and identity for the Al Baraka assistant stack.
#
# Why this exists: CI builds and tests, it does not promote. Registry credentials and
# the target host belong to the operator, and a pipeline that ships what it just built
# without a human decision is how a bank deploys an unreviewed image. The gap that
# leaves is not "how do I build" but "what is actually running" — so every image built
# here carries the commit it came from:
#
#   backend   GET /actuator/info      → build.gitSha
#   frontend  GET /version.json       → sha
#   any image docker inspect          → org.opencontainers.image.revision
#
# Usage
#   ./deploy/promote.sh --build                     build images tagged with HEAD
#   REGISTRY=registry.example.tn/albaraka ./deploy/promote.sh --push     build + push
#   DEPLOY_HOST=ops@prod DEPLOY_DIR=/srv/bank-assistant ./deploy/promote.sh --deploy
#   ./deploy/promote.sh --verify http://localhost:8081 http://localhost:8080
#   ./deploy/promote.sh --status                      what HEAD is, and what is dirty
#
# Rules the script enforces, because they are the ones a human forgets at 18:00:
#   * it refuses to promote a dirty tree (an uncommitted fix is not a release);
#   * it tags with the commit SHA, never with "latest";
#   * --deploy only pulls the tag it just pushed and restarts; it never runs a build
#     on the target, so the image you tested is the image that serves.
#
# Needs: docker, git, curl, jq (all already assumed by ./verify-local.sh).
# ============================================================
set -euo pipefail

ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")/.." rev-parse --show-toplevel)"
cd "$ROOT"

SHA="$(git rev-parse --short HEAD)"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
TAG="${TAG:-$SHA}"
REGISTRY="${REGISTRY:-}"
SERVICES=(backend frontend)   # RAGLab is external — not built or shipped here

die() { echo "promote: $*" >&2; exit 1; }
note() { echo "promote: $*"; }

require_clean_tree() {
  if [ -n "$(git status --porcelain)" ] && [ "${FORCE:-0}" != "1" ]; then
    die "working tree is not clean — commit what you want shipped (or FORCE=1 to override)."
  fi
}

need_registry() {
  [ -n "$REGISTRY" ] || die "REGISTRY is not set (e.g. REGISTRY=registry.example.tn/albaraka)."
}

build() {
  require_clean_tree
  note "building ${SERVICES[*]} as ${REGISTRY:-bank-assistant}/<service>:$TAG from $BRANCH@$SHA"
  GIT_SHA="$SHA" GIT_BRANCH="$BRANCH" BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  REGISTRY="$REGISTRY" TAG="$TAG" \
    docker compose build "${SERVICES[@]}"
}

push() {
  need_registry
  build
  for s in "${SERVICES[@]}"; do
    note "pushing $REGISTRY/$s:$TAG"
    docker push "$REGISTRY/$s:$TAG"
  done
  note "pushed. On the target: TAG=$TAG docker compose pull && docker compose up -d"
}

deploy() {
  need_registry
  : "${DEPLOY_HOST:?DEPLOY_HOST is not set (e.g. ops@prod.example.tn)}"
  : "${DEPLOY_DIR:?DEPLOY_DIR is not set (e.g. /srv/bank-assistant)}"
  push
  note "deploying $REGISTRY at tag $TAG on $DEPLOY_HOST:$DEPLOY_DIR"
  # The checkout only has to know the tag: the images come from the registry, which is
  # what makes "never rebuild for prod" (docs/DEPLOYMENT.md §2) enforceable.
  ssh "$DEPLOY_HOST" "cd '$DEPLOY_DIR' && git fetch -q && git checkout -q '$SHA' \
    && REGISTRY='$REGISTRY' TAG='$TAG' docker compose pull ${SERVICES[*]} \
    && REGISTRY='$REGISTRY' TAG='$TAG' docker compose up -d ${SERVICES[*]}"
  note "restarted. Verify with: ./deploy/promote.sh --verify <backend-url> <frontend-url>"
}

# compare(origin, path, label) — one origin, one identity document.
compare() {
  local origin="$1" path="$2" label="$3" key="$4" live
  live="$(curl -fsS --max-time 8 "$origin$path" 2>/dev/null \
            | jq -r "$key // \"unknown\"" 2>/dev/null || true)"
  [ -n "$live" ] && [ "$live" != "null" ] || live="unknown"
  if [ "$live" = "$SHA" ]; then
    note "$label: $live — this is HEAD ($SHA)."
    return 0
  fi
  if [ "$live" = "unknown" ]; then
    note "$label: no identity served at $origin$path — the image predates identity, or"
    note "         the endpoint is not reachable from here. Redeploy once to close that."
    BEHIND=1
    return 0
  fi
  if git cat-file -e "$live" 2>/dev/null && git merge-base --is-ancestor "$live" HEAD; then
    note "$label: $live — BEHIND HEAD by $(git rev-list --count "$live"..HEAD) commit(s):"
    git log --oneline "$live"..HEAD | sed 's/^/            /'
  else
    note "$label: $live — not an ancestor of HEAD on this branch (another branch is deployed?)"
  fi
  BEHIND=1
}

verify() {
  local backend="${1:-http://localhost:8081}" frontend="${2:-$backend}"
  BEHIND=0
  note "asking what is live (want $SHA)"
  compare "$backend" "/actuator/info" "backend" ".build.gitSha"
  compare "$frontend" "/version.json" "frontend" ".sha"
  if [ "$BEHIND" = "0" ]; then
    note "in sync."
  else
    note "not in sync — that is why the screen still behaves the old way."
    note "promote it: REGISTRY=… TAG=$TAG ./deploy/promote.sh --push, then --deploy."
    return 1
  fi
}

case "${1:---status}" in
  --build)  build ;;
  --push)   push ;;
  --deploy) deploy ;;
  --verify) shift; verify "${1:-}" "${2:-}" ;;
  --status)
    note "HEAD     : $SHA on $BRANCH"
    note "tree     : $([ -n "$(git status --porcelain)" ] && echo dirty || echo clean)"
    note "registry : ${REGISTRY:-<none, local build>}"
    note "tag      : $TAG"
    ;;
  *) die "unknown flag '${1}' (try --build, --push, --deploy, --verify, --status)" ;;
esac
