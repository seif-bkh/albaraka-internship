# Assistant IA — operating the console

Everything below is copy-pasteable. It covers the admin section that connects the bank to
**RAGLab**, the only engine, and what the console can honestly show about it.

> **Since 2026-10-22** the in-house Python agent is gone: no second engine, no engine switch,
> no local document drop zone, no ingestion queue, no model/key overrides stored in a container.
> RAGLab owns the corpus, the index and its own configuration. This page was rewritten to match;
> design history lives in [ADMIN_AGENT_UX.md](ADMIN_AGENT_UX.md) and [PLAN_TO_PROD.md](../PLAN_TO_PROD.md).

## 1. Bring the stack up

```bash
git clone https://github.com/seif-bkh/bank-assistant.git
cd bank-assistant

cp .env.docker.example .env
#   RAGLAB_URL=http://10.20.30.40:8000   (the assistant; empty = not wired yet — see §2)
#   RAGLAB_TOKEN=...                     (only if that service requires one)
#   JWT_SECRET=...                       (any long random string; the dev default is public)

docker compose up --build -d
docker compose ps                      # wait until backend is healthy
```

| What | URL |
|---|---|
| Application | http://localhost:8080 |
| Admin console → Assistant IA | http://localhost:8080/admin/agent/health |
| Backend health | http://localhost:8081/actuator/health |

Sign in as `admin@albaraka.tn` / `postgres` (seeded SUPER_ADMIN).

## 2. Connecting RAGLab

The console talks to RAGLab at a **real `http://<ip>:<port>`**, the way production does — never
`localhost` inside a container, never a container alias it cannot resolve.

If RAGLab runs on the same host as the stack, point the edge at the host gateway:

```bash
# .env
RAGLAB_URL=http://host.docker.internal:8000
docker compose up -d backend          # picks the value up
```

Or set the address from the console (SUPER_ADMIN, persisted in the database, journalled, and it
overrides the deployed value):

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@albaraka.tn","motDePasse":"postgres"}' | jq -r .token)
echo "${TOKEN:0:20}…"          # sanity check: a JWT was issued

curl -s -X PUT http://localhost:8081/api/admin/agent/endpoint \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"url":"http://10.20.30.40:8000","reason":"service RAGLab de recette"}' | jq
```

Then confirm the connection before anything else:

```bash
curl -s http://localhost:8081/api/admin/agent/diagnostics \
  -H "Authorization: Bearer $TOKEN" | jq '{reachable, httpStatus, latencyMs, error, errorReason}'
```

**Santé & diagnostic** (http://localhost:8080/admin/agent/health) shows the same, with the raw reply
and the journal of every change. If nothing is wired, it says *« Aucune adresse configurée »* — the
console never pretends a missing service is a healthy one.

## 3. Which engine answers

RAGLab, always. There is nothing to switch, so the engine screen and its `ENGINE_SET` journal
entries belong to history. What remains relevant is **liveness**: if RAGLab is down, every chat turn
is refused honestly (or answered with the service's own 5xx surfaced as "indisponible") and the
console says why.

## 4. Documents and the index

**Console RAGLab → Documents** (http://localhost:8080/admin/raglab/documents) is the drop zone:
upload, list, inspect, delete, and trigger whatever index action that service exposes. The service
reports its own state; the console shows exactly that, with absent facts left absent rather than
zeroed.

Rebuilding is the service's business and happens out of band. The console does not pretend to
trigger it — it records that it happened (who, when, why):

```bash
curl -s -X POST http://localhost:8081/api/admin/agent/corpus/rebuild/raglab \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"reason":"ingest CLI lancé à 14h05"}' | jq
```

The **corpus** read (`GET /api/admin/agent/corpus`) still lists the bank's document records —
the historical rows behind exchanges already stored — next to what RAGLab reports about its own
index. It is a read, not a queue: nothing is published from there any more.

## 5. Models and keys

RAGLab is configured by its own CLI. The console shows only what that service says about itself
(`GET /api/admin/agent/config`), and there is no form to change another process's model or key:
the old in-house agent's `PUT /config/legacy` is gone with the agent, and so is the `agentconfig`
volume. Rotating a provider key now means rotating it where RAGLab runs, then checking here that
the service reports the new model.

## 6. Watching production

**Monitoring du chat** (http://localhost:8080/admin/monitoring): the feed is the exchange log —
turns, grounded, refused, share with sources, refusal reasons. A RAGLab turn is shown for what it
is: each claim with its verbatim quote, uncited claims and unused sources flagged.

Provenance has a hard boundary (2026-09-21): **customers never see it**. The Spring Boot edge
strips it from the answer before it is sent *and* before it is stored as what was shown
(`AnswerSanitizer`: `[S1]`-style markers, `(Source : …)` in FR/AR/EN, `Sources:` blocks, document
file names, internal URLs), and neither `PublicChatResponse` nor the streamed `done` frame carries a
`sources` field at all. Which documents the answer rested on is recorded for reviewers — on the
exchange row (`sources_utilisees`) and in the live feed's `documents[]`.

**Qualité des réponses** (http://localhost:8080/admin/agent/quality) answers "is it any good?": ask a
question, see the claims and quotes, probe retrieval, check whether a quote exists in the corpus.

Every console action that spends or changes something is in the journal
(`GET /api/admin/agent/audit`, or the table on the journal page): `ENDPOINT_SET`,
`CORPUS_REBUILD_RAGLAB_DECLARED`, `CONSOLE_SEARCH`, `CONSOLE_ANSWER`.

## 7. Tests and troubleshooting

```bash
(cd frontend && npm ci && npm test -- --watch=false && npm run build)
(cd backend && mvn -B verify)                                          # JUnit + Spring context
```

| Symptom | What to look at |
|---|---|
| "Aucune adresse configurée" | §2 — set `RAGLAB_URL` or use the console address field |
| "Connexion à http://… impossible" | the IP/host and port, from inside the backend container: `docker compose exec backend getent hosts host.docker.internal` |
| Answers suddenly refuse everything | RAGLab's own index/health first (its documents screen), then this console's *Santé & diagnostic* |
| A refusal mentions documents | expected for a gap: the customer sees the bank's own sentence, not the engine's |
