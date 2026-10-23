# Running the whole stack with Docker Compose

Everything this repository owns — database, backend, frontend — starts with one command.
The assistant itself (RAGLab) is an external service: you point `RAGLAB_URL` at it.

---

## 1. Prerequisites

- Docker Engine 24+ with the Compose plugin (`docker compose version`)
- ~4 GB free RAM
- A **Google AI** API key and a **Groq** API key

## 2. Configure

```bash
cp .env.docker.example .env
```

Edit `.env` and set the two required values:

| Variable | What it is |
|---|---|
| `RAGLAB_URL` | the assistant, as `http://<ip>:<port>`. Empty starts the stack without an engine (every chat turn is refused honestly) |
| `JWT_SECRET` | signing key; generate with `openssl rand -base64 64` |

`RAGLAB_TOKEN` is only needed when that service requires a shared secret.

`JWT_SECRET` has a dev default so a bare `up` works; that default is public in this
repository, so set a real one for anything that is not your laptop (see `AUDIT.md` S1).

`.env` is gitignored. Never commit it.

## 3. Start

```bash
docker compose up --build
```

First run takes a few minutes (Maven and npm download dependencies). Later runs are cached.

| What | URL |
|---|---|
| **Application** | http://localhost:8080 |
| Public chat | http://localhost:8080/chat |
| Backend health | http://localhost:8081/actuator/health |
| PostgreSQL | `localhost:5433` (user/pass from `.env`) |

Stop with `Ctrl+C`, or:

```bash
docker compose down          # stop and remove containers
docker compose down -v       # ...and wipe the database volume
```

---

## 4. How the pieces fit

```
Browser ──► frontend (nginx :8080)
                 │  serves the Angular SPA
                 │  proxies /api ──► backend :8081
                 │                      │
                 │                      ├──► RAGLab  http://<ip>:<port>   (external, RAGLAB_URL)
                 │                      └──► postgres :5432
                 └────────────────────────────────┘
```

**The browser only ever talks to `localhost:8080`.** nginx forwards `/api` to the backend
inside the Docker network, so there are no cross-origin requests and no CORS preflight.
Only the frontend port needs to be public; the others are exposed purely for debugging.

### Service reference

| Service | Image / build | Internal port | Purpose |
|---|---|---|---|
| `postgres` | `pgvector/pgvector:pg16` | 5432 | Relational data + vector store |
| `backend` | `backend/Dockerfile` | 8081 | Spring Boot: auth, quotas, admin |
| `frontend` | `frontend/Dockerfile` | 80 | nginx serving the SPA + API proxy |

Startup order is handled by healthchecks: the backend waits for Postgres to report healthy
before starting. RAGLab is not a container here, so nothing waits on it — the console says
"non configurée" when the address is missing, and a chat turn is refused instead of invented.

---

## 5. Database schema

The **backend owns the schema** through Flyway (`backend/src/main/resources/db/migration/`):
`V1__baseline.sql` (captured from the original database), `V2__governance_facts.sql`,
`V3__taxonomy_catalog.sql`, `V4__refresh_tokens.sql`. Hibernate runs with `ddl-auto=validate` and never issues DDL.

`V1__baseline.sql` is **idempotent** (`IF NOT EXISTS` everywhere, constraint/identity
statements wrapped in tolerant `DO` blocks) and Flyway baselines at version **0**, so V1
always runs on a database without a `flyway_schema_history` table — whether it is empty,
pre-populated by `docker/initdb/01-init.sql`, created by an older Hibernate
`ddl-auto=update` run, or a database that still holds the `langchain_pg_*` tables an earlier
in-house agent created. Whatever exists is kept; whatever is missing is created.

To start over: `docker compose down -v && docker compose up --build`.

---

## 6. Loading documents

RAGLab owns its corpus. Publish files from **Assistant IA → Console RAGLab → Documents**
(upload, list, delete, and the index actions that service exposes), or hand them to RAGLab
out of band and record the fact in the console. This repository no longer has a document
drop zone of its own: it holds no index to feed. The `documents` table it still owns is the
historical record behind exchanges already stored, not an upload target.

## 7. Common tasks

```bash
docker compose logs -f backend      # follow one service
docker compose ps                   # health status of everything
docker compose up -d --build backend  # rebuild just one service
docker compose exec postgres psql -U postgres -d banque_assistant
docker compose restart backend
```

---

### Assistant IA

The admin section that connects and inspects the engine that answers the customers
(`/admin/agent/**`) has its own operating guide with copy-pasteable commands — connecting the
external service at `<ip>:<port>`, publishing documents through it, models & keys as it reports
them, and watching production: [docs/ASSISTANT_IA.md](docs/ASSISTANT_IA.md).

---

## 8. Developing without Docker

Every service keeps its old localhost defaults, so the pre-Docker workflow still works.
Run only the infrastructure in Docker:

```bash
docker compose up -d postgres

# Terminal 1 — backend
cd backend && ./mvnw spring-boot:run

# Terminal 3 — frontend (proxies /api to :8081 via proxy.conf.json)
cd frontend && npm start
```

The frontend dev server uses `proxy.conf.json`, mirroring what nginx does in Docker, so
`environment.apiUrl` stays `/api` in both setups and no host is hardcoded anywhere.

---

## 9. Troubleshooting

**`JWT_SECRET is required`** — you skipped step 2. Set it in `.env`.

**Agent restarts / "Failed to create vector extension"** — Postgres wasn't ready or the
image lacks pgvector. Confirm `docker compose ps` shows `postgres` healthy and that the
image is `pgvector/pgvector:pg16`.

**Assistant replies that it found nothing** — RAGLab's index is empty, or the address is not
configured. Check **Assistant IA → Santé & diagnostic** first. See §6.

**Port already in use** — change `FRONTEND_PORT`, `BACKEND_PORT` or `POSTGRES_PORT` in
`.env`; nothing else needs editing.

**Backend can't reach RAGLab** — `RAGLAB_URL` must be reachable *from inside the backend
container*. `localhost` there is the container itself; a service on the host is
`http://host.docker.internal:8000` (the compose file already maps that name).

**Changes not showing** — these are production images, not dev watchers. Rebuild with
`docker compose up -d --build <service>`, or use §8 for hot reload.

---

## 10. Local vs production

This compose file is the **local development** stack: no TLS, debug ports on loopback,
Production hardening (TLS at the edge, managed Postgres with PITR, secrets from a manager,
`SPRING_PROFILES_ACTIVE=prod`, RAGLab only on the internal network) is specified in
`docs/DEPLOYMENT.md`; the incident procedures are in `docs/RUNBOOK.md`.
