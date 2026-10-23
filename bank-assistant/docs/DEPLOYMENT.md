# Deployment guide (Phase 8)

Three environments, one image set. Everything environment-specific is an environment
variable or a secret; nothing is baked into images.

## 1. Topology

```
            Internet
               │ 443 (TLS terminated at the edge)
        ┌──────▼──────┐
        │  edge proxy  │  Traefik / nginx / cloud LB — TLS, HSTS, rate limit, WAF
        └──────┬──────┘
               │ 80 (private network)
        ┌──────▼──────┐        ┌──────────────┐        ┌──────────────┐
        │  frontend    │ /api ▶│   backend     │ ─────▶ │    RAGLab     │  never published
        │  nginx SPA   │       │  Spring Boot  │        │  (external)   │
        └─────────────┘        └───────┬──────┘        └───────────────┘
                                       │
                                 ┌─────▼──┐
                                 │Postgres│
                                 └────────┘
```

Only the edge proxy has a public address. `backend`, `postgres` and RAGLab live on a private
network / VPC subnet with no ingress from the Internet.

## 2. Environments

| | dev | staging | prod |
|---|---|---|---|
| How | `docker compose up` (this repo) | same images, real TLS, prod-like data volume | same images, HA data stores |
| `SPRING_PROFILES_ACTIVE` | (default) | `prod` | `prod` |
| `LOG_FORMAT` | plain / `json` | `ecs` | same |
| `METRICS_PUBLIC` | true | true (scrape network only) | true (scrape network only) |
| Swagger UI | on | off (prod profile) | off |
| RAGLab's model keys | its own deployment | its own deployment | its own deployment, alerting on spend |
| Data | throwaway | anonymised copy, refreshed monthly | live |

Promotion = the same image digests move dev → staging → prod. Never rebuild for prod.

## 3. Secrets

Secrets are injected by the platform (Docker/Swarm secrets, Kubernetes Secrets fed from
Vault / AWS Secrets Manager / GCP Secret Manager), **never** from a committed `.env`.
Required in staging/prod:

| Secret | Consumer | Notes |
|---|---|---|
| `JWT_SECRET` | backend | ≥ 256-bit, `openssl rand -base64 64`; rotating it logs everyone out |
| `POSTGRES_PASSWORD` | backend | least-privilege role; the backend is the only DB writer |
| `RAGLAB_TOKEN` | backend, RAGLab | shared secret on every call to the engine |

Rotation: JWT/tokens can be rotated by rolling restart (dual-value support is not
implemented — plan a short maintenance window). RAGLab's own provider keys rotate inside that
service, without downtime.

## 4. TLS and edge policy

- Terminate TLS at the edge (Let's Encrypt/ACM). Redirect 80 → 443. Send
  `Strict-Transport-Security: max-age=31536000; includeSubDomains`.
- Forward `X-Forwarded-For` / `X-Forwarded-Proto` (the backend's rate limit reads the
  client IP from `X-Forwarded-For`; only trust it from your edge).
- Keep the browser hardening headers set by `frontend/nginx.conf` (CSP, frame deny,
  nosniff); don't strip them at the edge.
- SSE: disable response buffering for `/api/chat/message/stream` at the edge
  (`proxy_buffering off` / equivalent) and allow ≥ 300 s idle.

## 5. Data stores

**Postgres (pgvector ≥ pg16)** — managed service where available.
- Backups: daily full + WAL archiving for point-in-time recovery; retention ≥ 30 days;
  restore drill quarterly (see RUNBOOK §6).
- Schema: Flyway runs on backend start (`spring.flyway.enabled=true`), see §7.
- Sizing: the backend's own tables are small; the vector index lives with RAGLab, so size
  that service (and its store) separately.

## 6. Deploy procedure (compose-based hosts)

```bash
# 1. pull the released digests
export TAG=v1.0.0          # git tag == image tag
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
# 2. migrate + restart the backend (Flyway runs at boot)
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d backend
docker compose logs backend | grep -E "Flyway|Migrating|Successfully"
# 3. then the frontend
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d frontend
# 4. smoke
E2E_BASE_URL=https://assistant.example.tn ./e2e/run.sh   # read-only checks only
```

`docker-compose.prod.yml` (per host, not committed) sets `SPRING_PROFILES_ACTIVE=prod`,
removes host port mappings for postgres/backend, and points at the secret files. Kubernetes
users translate one-to-one: each compose service is a Deployment + ClusterIP Service; the
frontend is the only Ingress backend; RAGLab is addressed over the private network with a
NetworkPolicy allowing ingress only from the backend.

### 6.1 Promotion is a script, not a hope

CI compiles, tests and builds — it deliberately does not deploy: registry credentials and the
target host belong to the operator, and a pipeline that ships what it just built without a human
decision is how a bank puts an unreviewed image in front of its clients. `deploy/promote.sh` is
the missing link, and it enforces the three things that get forgotten at 18:00:

- it refuses a **dirty tree** (an uncommitted fix is not a release);
- it tags with the **commit SHA**, never `latest`, and `--deploy` only pulls that tag — the
  target never builds, which is what makes "never rebuild for prod" (§2) checkable;
- it prints, at the end, the command that proves the promotion worked.

```bash
REGISTRY=registry.example.tn/albaraka ./deploy/promote.sh --push          # build + push by SHA
DEPLOY_HOST=ops@prod.example.tn DEPLOY_DIR=/srv/bank-assistant \
  ./deploy/promote.sh --deploy                                             # pull that tag, up -d
./deploy/promote.sh --verify https://assistant.example.tn                  # what is live?
```

`REGISTRY` and `TAG` are read by `docker-compose.yml` itself (`image:` names and build args), so a
plain `docker compose up --build` on a laptop keeps working with local names and no registry.

`./deploy/update-live.sh` is the same procedure for someone who should not have to remember it:
it pulls, rebuilds with the commit stamped in, restarts, runs the §6.3 cleanup while the backend
boots, switches the cleanup off again, checks that the running containers report the commit that was
deployed, and finishes with the plain-language "what to type to test it" block. `--report` counts
without changing anything; `--dry-run` prints every command. It refuses to run when the checkout
does not contain the fixes, which is the failure mode where an operator "updates" the wrong branch
and concludes the code did not work.

### 6.2 Which build is live? Ask before you debug an answer

Symptom-level debugging of a RAG answer cannot tell "the policy is wrong" from "the policy is
right and this container predates it" — the screen looks identical. So each image carries its
commit, and two endpoints answer the question without shell access:

| Surface | Where the identity is | Example |
|---|---|---|
| backend | `GET /actuator/info` → `.build.gitSha` | `curl -s localhost:8081/actuator/info \| jq .build` |
| frontend | `GET /version.json` → `.sha` | `curl -s localhost:8080/version.json` |
| any image | `org.opencontainers.image.revision` label | `docker inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' $(docker compose ps -q backend)` |

`./deploy/promote.sh --verify <backend-origin> [<frontend-origin>]` compares them against `HEAD`
and lists the commits a behind box is missing. `/actuator/info` is public in the current
`SecurityConfig` because it exposes nothing but a SHA and a build time; if your policy prefers
otherwise, restrict it there and keep the label route for operators.

### 6.3 One-off customer-history backfill

`messages.reponse` holds the answer **as the customer was served it** — the edge sanitises before
persisting, and provenance lives in `sources_utilisees` and `trace_json` for the reviewers. Rows
written before that rule may still hold the engine's own wording (a refusal plus the list of
documents it consulted). The history endpoints sanitise on read, so nothing leaks to a client; this
closes it **at rest**, in the database, in backups and in clones of it:

```bash
# 1. Look. Writes nothing, prints the count.
CHAT_HISTORY_BACKFILL_ENABLED=true docker compose up -d backend
docker compose logs backend | grep "Customer answer backfill"
# 2. Apply, then turn it off again.
CHAT_HISTORY_BACKFILL_ENABLED=true CHAT_HISTORY_BACKFILL_DRY_RUN=false docker compose up -d backend
docker compose logs backend | grep "Customer answer backfill"   # needs rewrite: 0
CHAT_HISTORY_BACKFILL_ENABLED=false docker compose up -d backend
```

It runs once at boot, in id-ordered batches, and is idempotent because the rule it applies
(`AnswerSanitizer.sanitizeStored`) is idempotent — re-running after it is clean changes nothing.
It does not touch `sources_utilisees` or `trace_json`: the admin journal keeps naming the documents
visited, and after the backfill the answer it shows is what a client actually read.

Before applying, take a plain copy — `pg_dump -t messages --data-only` is enough. What the rewrite
removes is the engine's refusal prose, which the current policy does not store for new turns
either, but a destructive one-off on a production table should still be undoable by hand rather
than by trust.

## 7. Migrations and zero-downtime

- Flyway migrations are **additive only** (add columns/tables/indexes; never drop or
  rename in the same release that stops using them). Removal happens one release later
  (expand → migrate → contract).
- Backend N+1 must boot against schema N+1 while backend N is still serving: keep old
  columns readable until the next release.
- Rollback = redeploy the previous image; because migrations are additive, the previous
  code runs fine on the newer schema. Never `flyway undo` in prod.
- Long-running index builds (`CREATE INDEX CONCURRENTLY`) go in their own migration with
  `flyway.outOfOrder=false` and are run during low traffic.

## 8. Observability wiring

- Metrics: scrape `backend:8081/actuator/prometheus` from the private network, plus
  RAGLab's own metrics endpoint. Dashboards: turns/min by outcome, p95
  `chat_turn_duration`, refused ratio, Postgres connection health.
- Logs: the backend emits JSON (`ecs`); ship it and index `turnId`, `sessionId`,
  `requestId`.
- Alerts (starting set): backend health ≠ UP for 2 min; refused ratio > 40 % over 15 min;
  p95 latency > 20 s over 10 min; RAGLab unreachable for 2 min; Postgres connection errors.

## 9. Launch plan (8.5)

1. **Staging soak** — 1 week, real corpus, admins use the control room daily; run the
   RAG regression + live eval on each catalog seal.
2. **Canary** — publish the assistant on the public site behind a feature flag for 5 %
   of visitors; watch the dashboards above for 48 h.
3. **Ramp** — 25 % → 100 % over a week; each step gated on: refused ratio stable,
   p95 < 12 s, no P1 incidents.
4. **Rollback** — flip the feature flag (seconds) or redeploy the previous tag (minutes);
   catalog rollback = republish the previous release from Taxonomie.
