# Testing the whole application on your machine

One compose file, everything built from this checkout: `docker-compose.test.yml`.

## 1. Prerequisites
Docker Desktop / Docker Engine 24+ with Compose v2 (`docker compose version`).
Ports 8080 (UI) and 8081, 5433 free (the last two bind to loopback only).
RAGLab itself runs elsewhere — this stack only needs its address to be reachable.

## 2. Configure
```bash
cp .env.docker.example .env
# edit .env — for real answers set:
#   RAGLAB_URL=http://<ip>:<port>   (the engine; empty = no answers, honest refusals)
#   RAGLAB_TOKEN=...                (only if that service requires one)
```
Everything else has a test default (`JWT_SECRET`, the service token).
Those defaults are for a **local test stack only** — never reuse them (see docs/DEPLOYMENT.md).

## 3. Run
```bash
docker compose -f docker-compose.test.yml up --build
```
First build takes a few minutes (Maven + npm). Wait until `backend` and
`frontend` report `healthy`, then open **http://localhost:8080**.

| Account | Email | Password | Role | Lands on |
|---|---|---|---|---|
| Admin (staff) | `admin@albaraka.tn` | `postgres` | SUPER_ADMIN | `/admin/dashboard` |
| Client (demo) | `client@albaraka.tn` | `client123` | UTILISATEUR | `/portal` |

Every account above comes from the development fixture, and so does the rest of the
population: `docker-compose.test.yml` sets `DEV_SEED=true` because it *is* a test stack.
Turning the flag off (`DEV_SEED=false`) leaves the database with no accounts at all —
that is the setting any shared or production environment must keep, and the reason a
production boot can never come up with a known password.

With the flag on you also get five staff accounts (one per role, plus two custom roles a
general manager would create) and three more customers with a running Mourabaha, a
professional Ijara, an investment deposit and one blocked card. The full list and the
passwords are in [docs/CUSTOMER_DATA_MODEL.md](docs/CUSTOMER_DATA_MODEL.md) §5.

(The two accounts in the table above are seeded by `BackendApplication`, the five staff and
three customers by `dev/DevDataSeeder`; both are gated by `app.dev-seed.enabled`. The demo
client comes with sample comptes / cartes / transactions — `V8__client_portal.sql` — so
the portal shows live data out of the box. Change every password for anything that is not
a laptop.)

## 4. Manual test plan (do it in this order)

Each step: **action → expected → what it proves**. Source files for the corpus are in
`documents/` (internal Islamic-banking guide `.docx` in French, BCT circular and Loi 2016-48
`.pdf` in Arabic) — publish them through RAGLab, which owns the corpus now.

1. **Empty corpus first.** Open http://localhost:8080/chat, ask *"C'est quoi le financement
   Mourabaha ?"* → a refusal, not an invented answer (`grounded:false` via
   `POST /api/chat/message`). Proves the grounding gate — check this BEFORE publishing.
2. **Corpus.** Login (admin), *Assistant IA → Console RAGLab → Documents*: upload the files
   from `documents/` there and run the index action the console offers. The service owns
   chunking, embedding and its own rate limits; progress and failures are what it reports
   about itself on that screen, never a local journal.
3. **Grounded answers.** Same question → real answer with citations. *"Où se trouve
   l'agence de Tunis ?"* → still refuses (no drift). *"Quel est le taux pour financer une
   voiture ?"* → must NOT state a number (rate intentionally unseeded).
4. **Governed facts.** *Facts → Nouveau*: `financement` / `sayarat.taux` /
   `4,99 % (indicatif)` / type `rate` / `fr` / reason. Re-ask → answers with exactly that
   value. Edit to `5,25 %` → new value at once (cache invalidation). Delete (reason) →
   refusal again. *History* shows the three audited changes. Delete the test fact afterwards.
5. **Languages (no barrier).** The document language is detected at indexation (the
   *Langue* column fills in after `INDEXE`), and the reply language follows the
   **question**, not the source: ask *"ما هو تمويل المرابحة؟"* → Arabic answer (RTL flips
   automatically) drawing on the French guide; ask in French about the BCT circular →
   French answer translated from the Arabic source; *"What is Mourabaha?"* → English.
   Product names and figures must stay unchanged across languages.
6. **Sessions (3.6).** Refresh once with the login's `refreshToken` → 200 + a new token;
   replay the old one → 401. Log out → token dead server-side. *Users → change a role* →
   that user is signed out everywhere. `client@albaraka.tn` must not see admin pages and
   gets 403 on `/api/admin/*`.
7. **Public site & client portal.** Open http://localhost:8080/ → the landing page
   (FR/AR/EN switcher, products, FAQ). Click the red bubble (bottom-right) → the
   floating assistant opens and answers from the corpus (streaming). Login with the
   demo client → `/portal`: solde total, comptes (masked IBAN + copy), cartes,
   transactions, profile edit; *Assistant* → ChatGPT-style thread whose history is the
   client's own conversations (ask something, reopen the portal, the conversation is
   listed). Login with `admin@albaraka.tn` on the *Espace Personnel* tab → `/admin/...`.
8. **Ops.** http://localhost:8081/actuator/prometheus → `chat_*` counters moved; each
   turn's `X-Request-Id`/`turnId` appears in the backend log and in the exchange row.
9. **Dynamic access control (2026-10-14).** Sign in as `admin@albaraka.tn`, open
   **Rôles & permissions**, and create separate roles for Documents Read, Documents Process,
   Ingestion History, RAGLab View, RAGLab Run (View + Run), and RAGLab Manage. Assign each to a
   temporary staff account under **Utilisateurs & rôles**. Confirm that the history-only account
   can read `/api/admin/ingestion/status` but cannot list `/api/documents`; the Documents Read
   account has the inverse access. The Documents Process account can use standalone document
   processing/cache controls but must receive HTTP 403 for RAGLab feed writes and
   `/api/admin/raglab/ingest`. RAGLab View can inspect status/profile, feed contents, and masked
   key status, but cannot write the feed or run indexing, ranked search/answer, or live embedding
   operations. Exact citation lookup remains read-only and available. Run may execute model-backed
   checks but still cannot change configuration or feed/index controls. Manage must see View and
   Run capabilities and perform the full RAGLab console actions, but must not gain access to
   **Rôles & permissions**. Verify that a temporary staff account assigned the seeded historical
   `ADMIN_DOCUMENTS` role retains its migrated feed CRUD/index actions without being able to change
   settings, keys, or engine/provider configuration, and without receiving Manage. Check both hidden
   UI actions and direct API requests (the expected denied response is HTTP 403). Finally, log in as `client@albaraka.tn` and confirm the `/portal`
   experience still works and `/admin` remains unavailable. Delete the temporary accounts after
   the test, then deactivate the temporary roles if they are no longer needed.

Deviations in 1/3/4 are answer-quality issues (report question + answer + `gate_reasons`);
deviations in 2/6/7 are bugs (report the log).

## 5. Automated end-to-end smoke
```bash
docker compose -f docker-compose.test.yml --profile smoke run --rm smoke
```
Runs `e2e/run.sh` inside the network: health, login, refresh rotation + replay
rejection, logout, governed facts CRUD, public chat, metrics. Exit code 0 = pass.

## 6. Without RAGLab
There is no offline engine any more: the in-house pipeline is gone. Start the stack with
`RAGLAB_URL` empty (or unreachable) and everything except answering still works — auth,
governance, the client portal, the console — while a chat turn is refused honestly.
The chat checks in `e2e/run.sh` need a real engine and will fail without one; that is the
expected result, not a regression.

## 7. Useful commands
```bash
docker compose -f docker-compose.test.yml logs -f backend   # follow logs
docker compose -f docker-compose.test.yml exec postgres psql -U postgres banque_assistant
docker compose -f docker-compose.test.yml down        # stop, keep data
docker compose -f docker-compose.test.yml down -v     # stop and wipe DB/vectors
```

## 8. A migration failed and the backend will not start

Postgres rolls a failed Flyway script back, so the schema is intact and the migration will
simply be retried — but only once the *image* carries the fixed script, because the
migration lives in the JAR:

```bash
git pull
docker compose -f docker-compose.test.yml up --build -d      # --build is the part that matters
docker compose -f docker-compose.test.yml logs -f backend
```

If Flyway refuses with `Detected failed migration to version 15` (it records a failed row
only when it cannot roll back), clear that row and retry:

```bash
docker compose -f docker-compose.test.yml exec postgres psql -U postgres banque_assistant \
  -c "select version, description, success from flyway_schema_history order by installed_rank desc limit 3;"
docker compose -f docker-compose.test.yml exec postgres psql -U postgres banque_assistant \
  -c "delete from flyway_schema_history where version = '15';"
docker compose -f docker-compose.test.yml up -d --force-recreate backend
```

A `Migration checksum mismatch` on a version that *did* succeed means the script was edited
after it ran: never do that in a shared environment — write a new migration. On a laptop
with throwaway data, `docker compose -f docker-compose.test.yml down -v` is faster than
reasoning about it.

## 9. Troubleshooting
| Symptom | Cause / fix |
|---|---|
| `backend` never healthy, log says `Flyway validate failed` / `Migration checksum mismatch` | Volume from an older build → `down -v` then `up`. |
| Chat answers "je ne peux pas répondre" to everything | No `RAGLAB_URL`, or that index is empty. Check *Assistant IA → Santé & diagnostic* first. |
| Console says "Aucune adresse configurée" | Set `RAGLAB_URL` or enter it from *Santé & diagnostic* (SUPER_ADMIN, with a reason). |
| `401` right after upgrade | Refresh tokens issued before 3.6 have no `jti`; log in again. |
| Port 8080 in use | `FRONTEND_PORT=9090 docker compose -f docker-compose.test.yml up` |
| `backend` exits with `Failed to execute script V15…` / `violates check constraint` | The image is older than the database. `git pull` then `docker compose -f docker-compose.test.yml up --build -d`. If it still refuses, see §8. |
