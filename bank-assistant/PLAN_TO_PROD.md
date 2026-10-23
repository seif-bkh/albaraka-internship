# Al Baraka Bank Assistant — Plan to Production

**Owner:** the maintainer + AI agent (session branch `arena/01a06850-bank-assistant`, previously `arena/01a06454-bank-assistant`)
**Status:** live working document — phases are executed in order, each slice pushed and CI-verified before the next.
**Companion docs:** `AUDIT.md` (takeover audit of the legacy prototype), `AGENTS.md` (working contract), `INTEGRATION.md`/`DOCKER.md` (legacy integration notes).

> Read this with the same lens as `AGENTS.md`: it is the blueprint for turning the
> legacy prototype into a production banking assistant. **The tech stack stays** —
> Angular 21 SPA · Spring Boot (Java) · FastAPI + LangGraph (Python) · PostgreSQL/pgvector · Redis.
> Everything else is being rebuilt cleanly as a green-field agent core (`albaraka/`)
> alongside the legacy code, migrated phase by phase.

---

## 0. Product definition (locked)

A trilingual (FR / AR / EN) **customer-facing assistant** for Al Baraka Bank, answering
customers' and prospects' questions by **grounding every answer in the bank's own documents
and governed business data**. It ships with an **internal admin back-office** that controls
what the assistant knows (documents, business figures, answer structure) and lets staff
regulate and audit it.

**North-star properties (in order of importance):**
1. **Grounded & auditable** — every answer traceable to a source; nothing fabricated about rates/terms.
2. **Compliant** — PII scrubbed on the way out; no auto-"learning" from customer chats.
3. **Quota-controlled & secure** — public channel metered; agent unreachable from outside.
4. **Regulable by staff** — admin edits the taxonomy, templates, governed facts, and documents.
5. **Multilingual** with Arabic RTL a first-class citizen.

---

## 1. Architecture (locked decisions)

```
Browser ─► Angular SPA (public chat + admin, one app, two lazy modules)
             │  /api proxy  (no host hardcoding; environment.apiUrl)
             ▼
        Spring Boot (Java)  ─── the ONLY public edge
           • auth (JWT, roles), quotas (two-phase metering)
           • OWNS the relational schema (Flyway, ddl-auto=validate)
           • persists chat after the agent returns (agent stays stateless)
           • passes last-N turns + language in the /invoke payload
             │  internal network, shared service token
             ▼
        FastAPI + LangGraph (Python)  ─── "agent", stateless inference worker
           • f(x) pipeline: normalize → route → scoped RAG → structured answer → gates
           • owns the pgvector collections + Redis ingestion queue
           • returns text + citations + usage; NEVER writes business tables
             │
             ▼
        PostgreSQL (pgvector) · Redis
```

**Key separation-by-domain:** the *business schema* (users, sessions, messages, documents,
quota) belongs to **Java + Flyway**. The *vector index* belongs to the **Python agent**.
No shared-DB write coupling.

**Locked sub-decisions**
- Green-field agent core `albaraka/` built/tested independently, then migrated in place.
- **Java persists; agent returns data** (conversation context is Java-passed last-N turns).
- Classification = **hybrid**: embedding similarity (confident path) + optional LLM fallback (ambiguity band only).
- Frozen category catalog, **rebuilt offline on ingest**, versioned between.
- Business figures in a **governed store** (DB/config), never in prompt strings.
- Streaming: agent streams up through Java to the browser; Java persists once on completion (`turnId` idempotency).
- "I don't know" is **structured** (`not_offered` / `knowledge_gap` / `hand_to_human`).

---

## 2. Execution strategy & guardrails

- Work in **independently-testable slices**. Each slice: pure/import-safe code where possible → tests → lint → commit (`feat:`/`fix:`/`ci:` etc.) → push → **watch CI to green**.
- The `albaraka/` package never imports `agent.py` / `api.py` / `config.py` and has **no side effects at import**.
- Do not weaken CI. Do not commit secrets. Keep the tree clean.
- Legacy services stay untouched until a slice can replace them without regression.
- Anything requiring a live key/DB/runner that the sandbox can't reach is implemented, unit-tested with stubs, and flagged as **needing live validation** rather than silently claimed done.

---

## 3. Phase-by-phase plan

### PHASE 0 — Foundations ✅ (mostly done)
- [x] AGENTS.md: agent pushes CI/workflow changes automatically.
- [x] `pyproject.toml`: pytest `pythonpath=["."]` so the bare `pytest` CI command resolves `albaraka` (fixed a CI import failure).
- [x] Slice #1 core: taxonomy enums, `RequestX`, seed catalog, hybrid classifier, deterministic router — 23 tests, ruff-clean, CI green.
- [x] Root `README.md` summarizing architecture + how to run.

### PHASE 1 — Green-field agent core (Python) — *current phase*
Goal: the whole `f(x)` pipeline implemented as pure, testable modules with no legacy coupling.

- **1.1 request normalization** ✅ — `albaraka/core/{enums,schema,catalog,classifier,router}.py`.
- **1.2 config & providers** — `albaraka/config.py` reading env (reuse `.env.example` keys); a **lazy embedder/LLM factory** so importing never opens a connection. Real adapters for Google embedding (already chosen) + Groq LLM behind small protocols; unit-testable with a stub.
- **1.3 pipeline facade** — `albaraka/pipeline.py::normalize(text, language, history) -> Routing(RequestX)` wiring classifier → router; plus `clarify` signal when slots are missing.
- **1.4 scoped RAG** — collection-per-domain or per-domain metadata filter on pgvector; parent/child retrieval; deterministic rerank/compression as *flagged options*. All behind small retrieval protocol (unit-testable without a live DB).
- **1.5 answer build** — per-`Route` handlers; **templated structured answer** (topic, facts, citations, required-section checklist); **internal structure + natural reformulation** for the customer; governed facts injected at build time (never from a prompt).
- **1.6 output gates** — hallucination verifier (facts must be supported by cited sources), **output-side PII scrub**, language/direction handling. 
- **1.7 `albaraka/server/`** — FastAPI app exposing a single authenticated `/invoke` (and later `/ingest`). Strict CORS, shared service token, streaming via SSE, `turnId` idempotency. Import-safe (lazy app factory).

**Acceptance:** `pytest` green; ruff clean; importing `albaraka` opens no DB/LLM; a stub-driven integration test exercises normalize→route→(stub rag)→answer→gates end to end.

### PHASE 2 — Seed real content & eval
- **2.1 domain catalog** — replace tiny seed with the real product catalog derived from the corpus + admin screen data model (domains, topics, exemplars in FR/AR/EN).
- **2.2 eval suite** — labeled Q&A set across FR/AR/EN + every domain; a script computes accuracy/coverage and a confidence-threshold report. This is the number that proves "product-ready".
- **2.3 exemplar bootstrapping** — LLM-draft candidate exemplars per category, human spot-check (maintainer/admins), then frozen into a versioned catalog.

### PHASE 3 — Backend (Spring Boot) refactor + new contracts
> ✅ CORE COMPLETE (verified on real Postgres). Remaining niceties: 3.6
> refresh-token revocation ADR, 3.7 admin CRUD APIs, 3.5 streaming + last-N/`turnId`
> (tie to stateless `albaraka` agent adoption).

- **3.1 POM/health/CI baseline** ✅
- **3.6 security hardening** ✅ (slf4j filter, refresh 401, hardened uploads, env JWT/JJWT0.12/typ/CORS allowlist, refresh returns id+roles, **server-side refresh-token rotation/revocation** — ADR in §4).
- **3.2 schema ownership** ✅ — Flyway + `ddl-auto=validate`, Java single owner (validated boots healthy on real Postgres; `V1__baseline.sql`).
- **3.3 service layer / error contract** ✅ — `@ControllerAdvice` returns consistent JSON (400 validation / 404 / 413 / 400 / 500).
- **3.4 two-phase metering** ✅ — pre-call gate + post-call settle, per-session concurrency guard (single-instance scope noted for scale-out).
- **3.5 forward to agent / persistence** — partial. ✅ Java persists exchange after `/invoke` (single writer, validated 1 row); [ ] streaming + last-N + `turnId` (stateless agent adoption).
- **3.7 admin APIs** — ✅ **A/B/C implemented, unit-tested (agent), awaiting host verify (backend)** (decision 2026-09-03: write access = SUPER_ADMIN + ADMIN_DOCUMENTS; all staff roles read).
  ✅ **Slice A — governed facts**: Flyway `V2__governance_facts` (`business_facts` +
  append-only `business_facts_audit`), `GovernedFactsService` (validated domain/factType
  vocab mirroring the agent enums, no hard delete, reason mandatory on change),
  `/api/admin/facts` CRUD + `/history` + `/vocabulary`; agent `factory.py` now reads
  the table via `DbFacts` (active rows only, DB hiccup ⇒ no facts, never a 500).
  Seeded only with figures published on the bank's site (Sayarat coverage %, 7-year
  term, documents). **The Mourabaha profit margin is NOT seeded** — the public site
  does not publish it and the legacy prompt contradicted itself; an admin must enter
  it through the API. ▶ needs `./verify-local.sh backend` (Flyway V2 + `validate`).
  ✅ **Slice B — taxonomy + releases**: Flyway `V3__taxonomy_catalog` (`catalog_category`,
  working-copy `catalog_exemplar`, immutable `catalog_release` + frozen
  `catalog_release_exemplar`; seeded with the 18 code exemplars sealed as `2026.09.1`).
  `TaxonomyService` CRUD (domain/intent vocab mirrors agent enums, soft-deactivate),
  `seal()` freezes active exemplars one version up (same bump rule as
  `albaraka.release.bump_version`) and `CatalogRegistryPublisher` writes
  `agent:catalog:active`; `/api/admin/taxonomy/{exemplars,releases,vocabulary}` +
  `POST releases/{version}/publish` for rollback. Agent side: `albaraka/catalog_store.py`
  loads the *frozen* copy for the registry version at boot (`factory._resolve_catalog`),
  falling back to `SEED_CATALOG`. New config `app.catalog.active-key` (`CATALOG_ACTIVE_KEY`).
  ▶ needs `./verify-local.sh backend`; note the agent must be restarted to pick up a
  new release (classifier pre-embeds the catalog at boot — hot reload is a follow-up).
  ✅ **Slice C — operations**: `POST /api/admin/documents/{id}/reingest` (re-enqueues on
  the 4.1c Redis queue; only VALIDE, not already EN_COURS), `GET /api/admin/ingestion/status`
  (+ `/{documentId}`) journal feed for the 5.4 UI, `POST /api/admin/cache/invalidate`
  (writes a fresh generation UUID to `agent:cache:generation`, `CACHE_GENERATION_KEY`).
  Agent: `cache.InvalidationWatcher` applies a new generation once (idempotent) from
  the worker's new `on_idle` hook; `RedisCorpusRegistry.cache_generation()` reads it.
  ▶ the watcher is inert until `factory.build_cache_store()` returns a real backend
  (the 4.3 "pgvector semantic_cache" item) — wiring is in place, eviction is not live.
  **Deferred from 3.7:** answer templates (required sections stay code-defined in
  `answer.py`); a template DSL needs a design pass before admins can edit it safely.
> **DECISION (2026-09-03): Option A chosen** — Java + Flyway become the single
> owner; 3.2/3.3/3.4/3.5 are implemented as one cohesive change. Verification is
> via `./verify-local.sh backend` on the maintainer's host (no sandbox Java toolchain), in
> batches, each confirmed from the pushed `verify/out/backend.log`.
>
> **The persistence-handover decision (unblocks 3.2, 3.4, 3.5, and 3.7's data model):**
> today the Python agent writes `messages`/`conversations` and Java only reads them via
> `@Immutable` entities. Phases 3.2/3.4/3.5/3.7 assume **Java becomes the single owner**
> and persists each turn after the agent returns. Two options:
>  - **A (full handover, matches plan):** Java + Flyway own the chat schema; add write
>    entities/repos; agent becomes stateless. Bigger, single-schema, correct end-state.
>  - **B (keep Python writer for now):** do only additive security/service-layer cleanups
>    (3.6 remainder, 3.3) and defer the handover + agent recontracting until the new
>    stateless agent fully replaces the legacy pipeline.
> Option A is the intended end-state; choosing it changes the order so 3.2/3.3/3.4/3.5
> are implemented as one cohesive change (write-persistence + forward + meter), not as
> isolated slices that would silently break `/history` and metering if applied alone.

### PHASE 4 — Ingestion & cache invalidation
> ✅ **COMPLETE (mechanism + live wiring) + verified on the stack.** 4.1 durable
> queue/worker/producer/reconciliation live-validated; 4.2 catalog version/pin and
> 4.3 provenance cache-invalidation implemented, tested, and their shared registry
> (`agent:corpus:editions`, `agent:catalog:active`) is written live by the worker.
> ✅ **Serving adoption started**: retrieval now reads the shared corpus collection
> (`config.COLLECTION` = the worker's index, not legacy `guide_interne_baraka`) and
> the server classifies under the live active catalog version from the registry.
> ✅ **Java client** (`AgentClient` + `PublicChatController`): the backend now calls the
> greenfield stateless `/invoke` sending last-N history + a fresh `turn_id` and the
> shared `X-Service-Token`, and parses `{text, structured{intent,topic,citations},
> grounded}`; Java persists sources/intent from that shape. Config `app.agent.token`
> (`AGENT_SERVICE_TOKEN`) + compose wiring added.
> Remaining serving adoption: cache-write provenance at answer time (4.3), the agent
> container flip to `albaraka.server` (deploy greenfield), and the admin-UI status
> surfacing (Phase 5.4).
> ✅ **Agent container flip**: `Dockerfile.agent` now runs `docker/agent_entrypoint.sh`,
> which backgrounds the ingestion worker and serves the greenfield `albaraka.server.main:app`
> (stateless /invoke) in the foreground. Public chat now goes end-to-end through the
> greenfield agent.
> ✅ **Greenfield /invoke live-validated** (direct): a financement question classified
> `financement/describe` and returned an honest structured `knowledge_gap` (grounded,
> no fabrication) because the live corpus has no financement-domain doc yet — correct
> grounding behaviour; a real answer only needs a financement-domain document ingested.
> ✅ **FULL SWITCHOVER COMPLETE + live-validated** — public chat now runs
> Java `PublicChatController` → `AgentClient` (service token + last-N history) →
> greenfield stateless `/invoke`, with Java metering/persisting the reply. A live curl
> through `POST /api/chat/message` returned the structured reply, metered it
> (`tokensConsommes:21`), and persisted the exchange. Slices: Java client ✅ · retrieval
> reconciliation ✅ · agent-container flip ✅ · persistence/metering ✅.
> ✅ **4.3 answer-time provenance**: grounded greenfield answers now carry the cited
> `(source, document_version)` editions (`structured.provenance`); `cache.record_answer`
> writes them as provenance-aware cache entries (blank/duplicate/governed handled),
> wired through `create_app` behind an optional `cache_store` (never fails a turn).
> Adopting a real pgvector cache backend with hit-lookup + eviction remains the large
> cache item.
> Remaining: the admin-UI status surfacing (Phase 5.4).
- **4.1 ingestion worker** — ✅ **4.1a chunk core** (`albaraka/ingest.py`): pure extract→parent/child
  split→domain/version-tagged chunks (+ `InMemoryVectorIndexer` reference + thin live
  pgvector factory), matching `retrieval.py`'s metadata contract. Import-safe, tested.
  ✅ **4.1b queue+worker** (`albaraka/ingestion.py`): `JobQueue` protocol + `RedisJobQueue`
  (LPUSH/BRPOP) + in-memory fake; `process_one`/`process_queued`/`run_worker` drive
  extract→`build_chunks`→indexer per job; each outcome carries version + chunk count, a
  bad job never stalls the queue. `config.UPLOAD_DIR` added to `albaraka/config.py`.
  ✅ **4.1c backend producer** (`IngestionQueueProducer` + `DocumentController` switch +
  Boot 4 `spring-boot-starter-data-redis` + `spring.data.redis.*` / `app.ingestion.queue-key`
  config + Compose `REDIS_HOST=redis`): validating a doc now LPUSHes a durable job onto the
  Redis list (LPUSH/BRPOP FIFO) instead of the fire-and-forget `new Thread` POST; a failed
  enqueue returns 500 so the admin retries rather than waits on `EN_COURS`.
  ✅ **worker CLI** (`albaraka/ingestion_cli.py`): standalone `python -m albaraka.ingestion_cli`
  runner wiring `RedisJobQueue` + pgvector indexer from env to drain the queue in the agent
  container (shares its Redis, uploads mount, DB and embedder key).
  ✅ **4.1c reconciliation** (worker→backend, Java stays single owner): worker reports each
  outcome to internal `POST /api/internal/ingestion/result` (guarded by shared
  `X-Ingestion-Token`); `IngestionResultService` looks the doc up by stored/legacy filename,
  sets `INDEXE`/`ERREUR` + version and writes a `JournalIngestion` row.
  ✅ **4.1 LIVE-VALIDATED on the stack** — worker booted in the agent container; a real
  document drained from Redis → 75 chunks embedded under a fresh `document_version`;
  backend reported it and wrote `journal_ingestion` = `SUCCES`, 75 chunks.
- **4.2 catalog rebuild** — ✅ **version/pin mechanism** (`albaraka/release.py`):
  `CatalogReleaseStore` seals a new `CategorySchemaVersion` one version above the
  current one (atomic active-swap) while keeping every old release retrievable, so a
  session can pin to the catalog version it started under. Import-safe, tested.
  ▶ real exemplar *refresh* still needs the Phase 2.3 corpus + human check; wiring the
  seal into the worker after a successful ingest is the live adoption step.
  ✅ **live registry wiring** (`albaraka/corpus.py` + `ingestion_cli`): after each successful
  drain the worker records the edition in Redis (`agent:corpus:editions`) and bumps the
  active catalog version (`agent:catalog:active`); 4.2 pinning + 4.3 eviction read this.
- **4.3 semantic-cache invalidation** — ✅ **provenance model + eviction**
  (`albaraka/cache.py`): cache entries carry the `(source, document_version)` editions
  they cite; `invalidate(store, active)` evicts grounded answers whose edition was
  re-versioned or withdrawn, while conversational (ungrounded) entries are untouched.
  Import-safe, tested. ✅ active-editions registry (above) now feeds the eviction.
  ▶ the runtime pgvector `semantic_cache` must record this
  provenance at cache-write time before invalidation can act on it (live adoption).
- **4.4 status/audit** — `JournalIngestion` rows are already written by the 4.1c
  reconciliation (`SUCCES`/`ERREUR` + chunk count). Remaining: surface ingestion status
  to the admin UI (part of Phase 5.4 frontend).

### PHASE 5 — Frontend (Angular) — public chat + admin regulation
- **5.1 single app, two lazy modules** — public chat (JWT-free, `/api`, streaming UI, RTL) and admin (role-gated).
- **5.2 use `environment.apiUrl`** everywhere; delete hardcoded hosts; self-host the Inter font (kill the build-time Google Fonts fetch) and fix the CSS budget.
- **5.3 i18n + RTL** — ✅ **public chat** (customer-facing multilingual surface) via a
  dependency-free `TranslationService` + `TranslatePipe` (FR/AR/EN) with RTL `[dir]`.
  **Admin module intentionally stays French** (internal staff back-office, role-gated);
  re-translating it into AR/EN adds little value. Any customer-visible admin-adjacent
  strings can reuse the same pipe later.
- **5.4 admin control room** — ✅ **governance screens shipped** (French, role-aware:
  write actions only for SUPER_ADMIN/ADMIN_DOCUMENTS): `/facts` (governed facts CRUD,
  audit history, deactivate/reactivate with mandatory reason), `/taxonomy` (exemplar
  CRUD with RTL for Arabic text, catalog releases: seal / publish-rollback, unsealed-
  changes warning), `/ingestion` upgraded (document state + worker journal feed,
  re-ingest button, semantic-cache invalidation). `GovernanceService` + 4 specs.
  Build within budgets. Remaining: conversation review + quality flags (needs a
  backend "flag" model), templates (deferred with 3.7).
- **5.5 streaming + error UX** — ✅ **agent side (additive)**: `GroqCompletion.stream` +
  `stream_completion` fallback for non-streaming models; `LlmTextGenerator.render_stream`;
  `run_pipeline_stream` yields SSE events (meta / delta / done) and runs the deterministic
  grounding gates (coverage + citations) BEFORE streaming; new `/invoke/stream` SSE endpoint
  (token-auth) with PII-scrub per chunk and provenance cache-record on grounded done.
  `/invoke` is unchanged. Java relay added (`/api/chat/message/stream`): it mirrors the
  `/message` gate/metering/persistence under the same per-session lock and relays the
  agent's deltas as SSE `data:` frames, writing a `done` frame with counters on finish.
  Angular: `PublicChatComponent.sendMessage` now streams from `/chat/message/stream`
  (fetch + ReadableStream SSE parse, since the endpoint is POST so native EventSource
  won't work), accumulating deltas into a single assistant bubble with a blinking caret,
  finalizing counters + bubble from the `done` frame, and falling back to the classic
  POST `/message` when the stream endpoint is missing or the network dies before the
  first frame. Stop button aborts the stream. **Slice complete + verified GREEN**
  (agent `6639d3a`, backend `2288de0`, frontend `51c2d5f`).
  ✅ **LIVE-VALIDATED end-to-end (2026-09-03)**: `POST /api/chat/message/stream` on the
  host stack relayed the agent's `delta` frame and closed with a `done` frame carrying
  metering (`tokensConsommes:10`, `nombreMessages:1`, `grounded:true`). Root cause of the
  earlier `Agent stream returned 422`: `java.net.http.HttpClient` defaults to HTTP/2 and
  sends an `Upgrade: h2c` handshake that uvicorn rejects; the client is now pinned to
  HTTP/1.1 (`AgentClient.invokeStream`). Remaining: a substantive grounded route once a
  financement-domain document is ingested (content, not code).

### PHASE 6 — Observability, security & non-functional
- **6.1 logs/metrics** — ✅ **shipped**: `turnId` minted once in `AgentClient`, sent
  in the body and as `X-Turn-Id`, bound to MDC (Java) and a `contextvar` (Python) so one
  grep spans both services; `CorrelationFilter` adds `requestId`/`sessionId` and echoes
  `X-Request-Id`. Structured logs: backend `LOG_FORMAT=ecs` (Spring structured logging),
  agent `LOG_FORMAT=json` (`albaraka/observability.py`). Metrics: backend
  `/actuator/prometheus` (SUPER_ADMIN or `METRICS_PUBLIC=true`) with `chat_turns_total`,
  `chat_turn_duration`, `chat_tokens_estimated_total`; agent `/metrics` with
  `agent_turns_total`, `agent_turn_seconds`, `agent_answer_chars_total`,
  `agent_cache_events_total`. Remaining: OpenTelemetry traces (add the OTel starter +
  collector when a backend exists to receive them).
- **6.2 threat pass** — ✅ **done**, see `docs/THREAT_PASS_6.2.md` (20-point checklist). Fixed in the pass: removed the legacy unauthenticated `ChatController`/`AgentService` and the `azerty123` scratch files, constant-time token compares, browser hardening headers (nginx CSP + Spring). Residual: upload magic-byte sniff, Swagger in prod, Redis auth (8.2). `chat_history*.html` was removed on 2026-10-22 (it contained a live Groq key; revoke it at the provider, deleting the file does not purge Git history).
- **6.3 perf/scale** — ✅ **shipped**: backend on JDK 21 virtual threads
  (`spring.threads.virtual.enabled`, `VIRTUAL_THREADS`), session striping moved from
  `synchronized` to `ReentrantLock` so the 300 s agent call no longer pins a carrier;
  SSE streaming is the default chat path (`/message/stream`). Agent: `UVICORN_WORKERS`
  (default 2) per container, stateless so replicas scale horizontally; host port bound
  to loopback only. Ingestion: one sequential worker per agent container draining a
  durable Redis list (BRPOP) — bounded by construction; scale by adding containers.
  Remaining: replace the in-JVM striped lock with a DB optimistic lock on the session
  row before running >1 backend replica.

### PHASE 7 — Testing, regression & release candidate
- **7.1 coverage gates** — ✅ **gates live in CI** (all ratchet-only): agent `pytest --cov`
  with `fail_under = 82` on `albaraka` (85% measured; wiring/CLI omitted, see
  `pyproject [tool.coverage]`); backend JaCoCo `check` ≥ 80% lines on `GovernedFactsService`
  + `CorrelationFilter` (new `GovernedFactsServiceTests` on H2 exercise audit trail, identity,
  reason and vocabulary rules); frontend Vitest v8 coverage ≥ 50% on `src/app/services/**`
  (auth + governance at ~100%; legacy services next). Testcontainers Postgres still deferred
  by decision. Widen JaCoCo includes as services gain tests.
- **7.2 RAG regression suite** — ✅ **shipped**: `tests/regression/cases.json` (10 golden end-to-end cases: routing, governed-fact precedence, corpus citations, knowledge-gap and out-of-scope refusals, output PII scrub) run by `tests/regression/test_rag_regression.py` on every CI push through a deterministic embedder. It already caught one defect (fallback text repeated the same passage per section — fixed). Extend with AR/EN cases as the catalog grows.
- **7.3 contract/e2e** — ✅ **shipped**: `contracts/agent-invoke.v1.json` is the single
  JSON-Schema source for the edge↔agent wire; `tests/test_contract.py` validates real agent
  output (it caught the SSE `done` frame missing `turn_id` — fixed) and
  `AgentContractTests` validates `AgentClient`'s payload + parser against an embedded stub
  server. `docker-compose.e2e.yml` + `e2e/run.sh` run the browser path through nginx →
  Spring → agent (real server code with offline providers, `e2e/stub_agent.py`) →
  Postgres/Redis: security headers, correlation id, sync + SSE chat, refusal, admin login,
  facts CRUD + audit trail, Prometheus. New CI job `e2e`.
- **7.4 RC checklist** — ✅ assessed in §4 (honest status per gate). Tagging `v1.0.0-rc` is the owner's call once the branch is merged to `master`.

### PHASE 8 — Production rollout
- **8.1 containerize** — ✅ `docker-compose.yml` (5 services, healthchecks, non-root images, debug ports on loopback only) + `docker-compose.e2e.yml`. Secrets: compose reads env (local); staging/prod inject via platform secrets per `docs/DEPLOYMENT.md §3`. Helm chart not written (compose→k8s mapping documented).
- **8.2 environment split** — ✅ specified in `docs/DEPLOYMENT.md` (dev/staging/prod matrix, TLS/HSTS at the edge, PITR backups, private network). Code: `REDIS_PASSWORD` honoured by agent + backend + compose (`requirepass`), `application-prod.yml` (Swagger off, ECS logs, health details hidden), agent/redis host ports bound to 127.0.0.1.
- **8.3 migrations** — ✅ Flyway at backend boot (V1–V3), expand→migrate→contract rule and rollback path in `docs/DEPLOYMENT.md §7`.
- **8.4 runbooks & docs** — ✅ `README.md`, `docs/RUNBOOK.md` (incidents, ingestion, rotation, security incident, regulatory audit queries), `DOCKER.md` refreshed. Big decisions are recorded inline in this plan (3.7, 4.3, 5.3, 6.x). LICENSE: proprietary (owner confirmed; `LICENSE` file added). `HOST_TESTING.md` + `docker-compose.test.yml` give a one-command full-stack test on a workstation.
- **8.5 launch** — 📋 plan written (`docs/DEPLOYMENT.md §8–9`: dashboards, alert set, canary 5 % → 25 % → 100 %, flag/tag/catalog rollback). Execution is an ops action outside the repo.

---

## 4. Definition of "production-ready" (release gates)

A release may ship only when **all** hold. Status as of 2026-09-03 (branch
`arena/01a06850-bank-assistant`, all CI jobs incl. dockerized e2e green):

- [x] Every public answer is **grounded** (citation/source present) — enforced in
  `orchestrate.py` (grounded ⇒ citations; refusals otherwise) and pinned by
  `tests/regression/` on every push.
- [x] **No PII leak** on output — `PiiScrubber` is the last gate on both sync and SSE
  paths; regression case `pii-in-output-is-scrubbed`.
- [~] **No fabricated business figures** — rates/terms come only from `business_fact`
  or cited documents; the agent never invents a number. **Open (owner):** the Sayarat
  Mourabaha rate is *not seeded* because no authoritative source was found (4.99 % vs
  4.75 % unresolved) — **owner decision: leave as is**; until an admin enters it in Faits
  gouvernés the assistant refuses/omits the rate rather than guessing.
- [x] Auth: roles enforced (all admin controllers `@PreAuthorize`), JWT secret is an env
  secret absent from history. Refresh tokens are **server-side revocable** (3.6 ADR below):
  single-use rotation with family reuse detection, `POST /api/auth/logout`, and admin
  role/password/deactivation/delete cut every session of the user.
- [x] Quotas enforced pre- and post-call; agent authenticated (`X-Service-Token`,
  constant-time) and not published (loopback/private network only).
- [x] Uploads hardened; ingestion is a durable Redis queue with journal + re-ingest.
- [x] CI green on every push; coverage gates (agent 82 %, backend JaCoCo, frontend 50 %)
  and the RAG regression + contract tests are hard gates.
- [~] Observability: structured logs + correlation ids across both services, Prometheus
  metrics incl. token-spend proxy per turn. **Open:** OpenTelemetry traces (no collector
  yet) and per-session cost in the admin UI (data exists in `public_chat_session`).
- [~] Admin regulates taxonomy (sealed releases), governed facts, re-ingest and cache
  invalidation. **Open:** answer templates (deferred with 3.7) and conversation quality
  flags (5.4 remainder).
- [x] Arabic end-to-end incl. RTL (public chat i18n, RTL exemplar editing, AR routing
  exemplars in the seed catalog).
- [x] Docs/runbooks current (README, DEPLOYMENT, RUNBOOK, THREAT_PASS, DOCKER,
  HOST_TESTING). Decisions are recorded inline in this plan rather than as separate ADRs.
  Owner decisions: `LICENSE` = proprietary (file added); `chat_history*.html` was deleted from git on 2026-10-22.

**Verdict:** ready to tag `v1.0.0-rc` for a **staging soak**; no owner items remain open.
Non-blocking backlog: OTel traces, answer templates, conversation quality flags, Helm chart.

### ADR — 3.6 refresh-token revocation (implemented)
- Refresh JWTs carry a `jti`; each is persisted in `refresh_tokens` (V4) with a *family*
  id (one family per login), subject, expiry and `revoked_at/revoke_reason`.
- `/api/auth/refresh` is **single-use**: the presented jti is revoked (`rotated`) and a
  new one issued in the same family; the response carries the new `refreshToken` and the
  SPA stores it. Presenting an already-consumed jti is treated as theft: the **whole
  family is burned** (`reuse_detected`) and 401 returned.
- `POST /api/auth/logout {refreshToken}` revokes (`logout`, idempotent). Admin
  `PUT/DELETE /api/users/{id}` (role change, deactivation, password reset, delete) and
  `POST /api/users/{id}/revoke-sessions` revoke everything for the subject (`admin_revoked`).
- Access tokens stay stateless (≤ 1 h in prod); the revocation horizon is the access-token
  TTL, which compliance accepted over per-request DB lookups.
- Tokens issued before V4 have no `jti` → 401 → users log in once after deploy.
- Rows are kept after revocation for audit; a nightly purge of rows expired > 30 days is
  a follow-up (RUNBOOK).

---

## 5. Risk register

| Risk | Mitigation |
|---|---|
| Sandbox has no live model/DB keys | Code import-safe; every remote call behind a protocol + stub-tested; **flag items needing live validation** (real embedding quality, cost, latency) |
| LLM classification silent failures | Hybrid + deterministic router; confidence surfaced; ambiguity → clarify not guess |
| Over-routing / taxonomy churn | Coarse domains + few intents; schema **versioned**; rebuild only on ingest, frozen between |
| "Self-improvement" from chats | Forbidden for a bank: conversations are a *diagnostic*, fixes go through governed store + regression suite |
| Schema drift (two ORMs, one DB) | Single owner (Java+Flyway) for business tables; agent owns only pgvector collections |
| Prompt drift / magic numbers | Figures out of prompts; regression suite pins every prompt change |
| Legacy entanglement | Green-field `albaraka/` has zero imports of legacy modules; migration is additive |

---

## 6. Current progress tracker

- **PHASE 0**: 3 of 4 items done (README pending).
- **PHASE 1** — green-field agent core: **complete + live-validated**.
  - 1.1 request normalization ✅ · 1.2 config & lazy providers ✅ · 1.3 pipeline facade ✅
  - 1.4 scoped retrieval ✅ · 1.5 templated answers ✅ · 1.6 output gates ✅ · 1.7 `/invoke` server ✅
  - 58 tests, ruff clean, CI green. Runs with just Groq (LLM-only) or the full
    hybrid + retrieval path.
- **PHASE 2.2 — live eval measured (real Gemini embeddings + Groq LLM, via CI secrets).**
  - 16 FR/EN/AR cases · **domain-accuracy 100% · intent-accuracy 93.75%**.
  - The single miss is an intent-level confusion within `financement` (domain still
    correct); low-impact given the router folds related intents into shared flows.
    On 16 cases one miss ≈ 6%, so the number is noisy; growing the eval set (2.1)
    is what stabilizes it. No over-tuning against a small set.
- Remaining phases 2–8 are sequenced above; each slice pushed and CI-watched to green before the next begins.
- **PHASE 9 — Public site & client portal (branch `arena/01a06ef7-bank-assistant`)**:
  - 9.1 brand tokens from the official alBaraka logo (red `#E0122F`, orange `#F5881C`) ✅
  - 9.2 trilingual landing page at `/` (hero, products, assistant showcase, services, FAQ, footer) ✅
  - 9.3 floating "talk to the agent" widget on the landing page (same SSE endpoints, own session) ✅
  - 9.4 login split Client / Personnel (tabs, role-based redirect) + `/register` ✅
  - 9.5 client portal `/portal` (comptes / cartes / transactions / profil + ChatGPT-style assistant with the client's own history) ✅
  - 9.6 `V8__client_portal.sql`: client profile fields, `comptes`/`cartes`/`transactions` (seeded demo data), `conversations.client_id` ownership ✅
  - 9.7 `/api/client/me/**` endpoints (profile, products, conversations) — strictly self-scoped, staff rejected 403 ✅
  - 9.8 admin back-office moved under `/admin/**` (StaffGuard), re-skinned, untouched functionally ✅
  - Frontend 30/30 tests, coverage 73% (gate 50%); Java compile & e2e validated by CI (no JDK in sandbox).

---

## PHASE 10 (redo) — Assistant IA in the admin (branch `arena/01a0a056-bank-assistant`)

The first RAGLab integration attempt was reverted (see the tag `archive/raglab-attempt-1`): it was
built before the shape was agreed, invented its own UI and assumed container networking. The redo
starts from capabilities, not from the CLI's menu — `docs/ADMIN_AGENT_UX.md` (organisation) and
`docs/RAGLAB_13_ENTRIES.md` (what each operator function is).

- **Slice 0 ✅ — connection & health.** The RAG service is addressed like production
  (`http://<ip>:<port>`): deployed default `RAGLAB_URL`, overridable by a SUPER_ADMIN from
  `/admin/agent/health`, persisted in `agent_endpoint` (V11), every change in `agent_admin_audit`
  with its reason. The screen shows the effective address, where it comes from, a live `/health`
  probe with latency and the raw reply — and says "not configured" rather than pretending when
  nothing is wired. Read: any staff role; change the address: SUPER_ADMIN only.
- **Slice 1 ✅ — quality workbench.** `/admin/agent/quality` answers three questions: *what did it
  answer and which verbatim passage backs each claim* (`/answer`), *was the right passage even
  retrieved* (`/search`), and *does this quote exist in the corpus at all* (`/chunks/search`, free).
  Claims without a quote and sources no claim used are both flagged; the prose answer is shown but
  never badged as verified. Spending panels are role-tiered, the free lookup is open to all staff,
  and console calls that spend are journalled (the free ones are not, or they would bury the rest).
- **Slice 2a ✅ — engine switch.** `/admin/agent/engine`: which engine serves the customers, both
  engines' address and liveness, the consequences of the switch listed before the button, and the
  switch itself (SUPER_ADMIN, mandatory reason, journalled as `ENGINE_SET`, refused when RAGLab has
  no address). Effective on the next turn: `AgentRouter` resolves the engine per call, and every turn
  is attributed to the engine that answered it. Both engines sit behind one `ServingAgent` interface,
  so quotas, metering, persistence, SSE framing and monitoring are untouched; RAGLab's two gaps (no
  token stream, no pipeline trace) are handled explicitly — one delta then done, retrieval probe
  instead of a trace.
- **Slice 2b ✅ — monitoring selector.** The monitoring room observes either engine: the feed filters
  by engine, and a KPI strip compares both (turns, grounded, refused, grounding rate, share with
  sources, refusal reasons) over the last 200 exchanges. Attribution is read from the intention
  (`RAGLAB_*`), so it needs no new column and works for the exchanges already stored; rates are null
  rather than 0 when an engine served nothing. A RAGLab turn is shown for what it is — claims with
  their verbatim quotes, uncited claims and unused sources flagged — instead of a pipeline trace it
  does not have.
- **Slice 3a ✅ — corpus & index.** One drop zone, two chunk worlds, never blended: the shared file
  world, the in-house index (state + journal + a real rebuild that re-queues every published file
  through the existing durable ingestion path, SUPER_ADMIN, journalled), and the RAGLab index (only
  what that service reports about itself, facts absent rather than zeroed, with its out-of-band
  rebuild recorded — who, when, why — instead of a fake trigger).
- **Slice 3b + 5 ✅ — models & keys.** The in-house agent gained `GET/PUT /config` (FastAPI, behind the
  same service token as `/invoke`), so an admin replaces the chat model or a provider key from
  `/admin/agent/config` instead of editing compose on the host: SUPER_ADMIN, mandatory reason,
  journalled `CONFIG_SET` with the field names only. Overrides are stored where they survive a restart
  (named volume), a key is write-only (presence + fingerprint, never a value), and the screen states
  what takes effect when — the chat model and its key on the next turn, the embedding key after a
  restart (`restartPending`, with the exact command). Retrieval thresholds, embedding
  model/dimension, collection and history depth are shown read-only with what each one decides,
  because changing them changes what the existing index means (D6). RAGLab's own settings stay out of
  band: its card shows what it reports about itself, and says where it reports nothing.
- **Slice 4a ✅ — embedding sanity (CLI entry 6).** The silent failure mode is now checked explicitly:
  the agent reports how many chunks the collection holds, the dimension of the vectors *stored* in it
  (read from the vector column), and whether that matches the dimension this process embeds with —
  `ok` / `empty` / `dimension_mismatch` / `unknown`, with the sentence that says what to do. `unknown`
  is a real verdict (unreachable DB, unfamiliar vector store), never a guess. `POST /index/probe`
  embeds one fixed sentence through the running embedder — the only proof that the key and model work
  *now* — on the spend tier and journalled. A failed probe never hides the corpus read.
- **Slice 4c ✅ — the journal (C8).** `/admin/agent/journal`: every action of the section, filterable
  by action (values read from the journal itself, not hardcoded) and searchable over the window, with
  the *motive* as its own column, and the console's spending reads kept visually distinct from the
  writes. Filtering happens in SQL — "who rebuilt an index" must be answerable when the last hundred
  rows are something else.
- **Next slices** (agreed order): heavy diagnostics (evaluate, harness, chat replay) → RAGLab's own
  write surfaces once its contract is known → chunking knobs.
- Standing decisions: one drop zone for documents with two independent chunk worlds; the in-house
  agent gains configuration endpoints (models + keys); no env kill switch — the role is the guard.

---

## 2026-10-22 — RAGLab only: the in-house engine was removed

Owner decision: stop maintaining two engines. RAGLab serves every customer turn; the
repository no longer ships an inference service.

**Removed**
- `albaraka/` (5.6k lines), `agent.py`, `api.py`, `config.py`, `anonymizer.py`,
  `ingest_document.py`, `run_eval.py`, their tests (`tests/`), `contracts/`, `pyproject.toml`,
  `requirements*.txt`, `Dockerfile.agent`, `docker/agent_entrypoint.sh`,
  `docker/verify-agent.Dockerfile`, `e2e/stub_agent.py`.
- Backend: `AgentClient`, `AgentRegistry`, `AgentProviderService`, `EngineConfigService`,
  `DocumentController`, `AdminOperationsController`, `IngestionResultController`, the
  ingestion/queue/cache-publisher services, `JournalIngestion`, `CatalogRegistryPublisher`,
  `spring-boot-starter-data-redis`, and the `/engine`, `/engines`, `/config`,
  `/config/legacy`, `/index/*`, `/corpus/rebuild/legacy` endpoints.
- Infra: the `agent` and `redis` services and their volumes; `AGENT_URL`,
  `AGENT_SERVICE_TOKEN`, `AGENT_PROVIDER_DEFAULT`, `*_QUEUE_KEY`, `*_TEMPLATE_KEY` knobs.
- Frontend: the engine-switch, models-and-keys, corpus, documents and ingestion screens;
  the taxonomy page keeps sealing but loses rollback (no consumer to repoint).

**What replaced it**
- `AgentWire` holds the wire types (`Turn`, `AgentReply`, `StreamDone`, `StreamListener`) so
  the edge still speaks one contract; `AgentRouter` names RAGLab.
- Document publishing happens in RAGLab's own screen (`/admin/agent/raglab/documents`);
  the corpus read shows our file records next to what that service reports about itself.
- Monitoring keeps a bucket for turns stored under the old engine, labelled *historique*.

**Consequences to remember**
- No engine fallback: if RAGLab is down, chat refuses honestly. One address (`RAGLAB_URL`)
  is now a hard prerequisite for answering anything.
- `documents` / `journal_ingestion` remain as historical records (exchanges cite them);
  nothing writes to them any more (V14 and earlier stay valid — no migration needed).
- The e2e smoke needs a reachable RAGLab; its chat checks are meaningless without one.
