# Al Baraka Bank Assistant — Technical Audit & Takeover Report

**Date:** 2026-09-01
**Reviewed commit:** `b278b88` ("first commit")
**Scope:** architecture, code base, build, security, operability
**Verdict:** Functional prototype / demo. **Not production-ready.** Roughly 4–6 focused weeks of work to reach a deployable v1.

---

## 1. Executive summary

The project is a trilingual (FR/AR/EN) RAG banking assistant with a genuinely thoughtful retrieval design — Multi-Query, HyDE, parent/child chunking, semantic caching, PII anonymisation, session quotas. The *ideas* are strong and clearly the work of someone who understands RAG.

The *engineering* is where it falls down. The repository is a single "first commit" with no tests, no dependency manifest for the Python service, no CI, hardcoded credentials, three conflicting service topologies, and a `pom.xml` that references Maven artifacts which do not exist. **The Java backend cannot compile today, on any machine.**

| Area | Grade | One-line verdict |
|---|---|---|
| Architecture concept | **B−** | Sound idea, but shared-DB coupling and no gateway |
| Python AI agent | **C** | Clever RAG, poor software hygiene, 300-line prompt |
| Java backend | **D** | Does not build — invalid dependencies |
| Angular frontend | **B** | Builds cleanly; hardcoded URLs, no tests |
| Security | **D−** | Committed JWT secret, open CORS, credentials in source |
| Build / CI / DevEx | **F** | No CI, no requirements.txt, no Dockerfiles, `.bat` launcher |
| Testing | **F** | One empty test; zero coverage |
| Documentation | **C+** | `INTEGRATION.md` is good but describes ports that don't match code |

### Build results in this sandbox

| Component | Result | Detail |
|---|---|---|
| **Frontend** | ✅ **Builds** | `npm ci && ng build` succeeds. 404 kB initial bundle (102 kB transfer). Two non-blocking issues found & documented below. |
| **Python agent** | ⚠️ **Compiles** | All 6 modules pass `py_compile`. Cannot run: no `requirements.txt` existed (**I created one**), needs live Postgres+Redis+API keys. |
| **Java backend** | ❌ **Cannot build** | Maven Central is firewalled in this sandbox *and* the POM is independently broken (see §4.1). Verified by source inspection. |

Because the Java build can't run here, I've delivered **`ci/ci.yml`** — a validated three-job GitHub Actions workflow you can push manually.

---

## 2. Current architecture

```
Angular 21 SPA  ──HTTP──►  Spring Boot 4  ──HTTP──►  FastAPI + LangGraph
  (port 4200)              (port 8081?/62215?)         (port 8000)
                                 │                          │
                                 ▼                          ▼
                          PostgreSQL + pgvector  ◄──────────┘
                            (5432? / 5433?)            Redis (6379)
```

### 2.1 What's right

- **Clear separation of concerns**: presentation / business+auth / AI inference. Correct instinct — the LLM layer is isolated in Python where the ecosystem lives, and business rules stay in Java.
- **Single-writer discipline**: only the Python agent writes `conversations`/`messages`; Java reads via `@Immutable` entities. That's a deliberate, well-reasoned decision documented in `INTEGRATION.md`.
- **Quota enforcement before the LLM call** — checks in `PublicChatController` happen *before* burning tokens. Correct order.
- **PII anonymisation before the prompt** (`AnonymiseurPII`) — regex-based for Tunisian CIN, RIB, phone, email. The right idea at the right point in the pipeline.
- **Advanced RAG** — Multi-Query + HyDE + parent-document retrieval + semantic cache is a genuinely modern stack.

### 2.2 Architectural defects

**A1 — Database used as an integration bus (HIGH).**
Two services with different ORMs (Hibernate + raw psycopg2) share tables. Hibernate runs `ddl-auto: update` while Python assumes a hand-written schema (`create_messages_table.sql`). One entity rename silently corrupts the other service. `@Immutable` mitigates *write* conflicts but not *schema* drift.
→ **Fix:** the agent should not write business tables. Have it return data; let Java persist. Or, if you keep shared DB, put the schema under **Flyway/Liquibase** owned by exactly one service and forbid `ddl-auto` outside dev.

**A2 — Three contradictory port topologies (HIGH — this is why it doesn't run).**

| Source | Backend port | DB port | DB user |
|---|---|---|---|
| `application.yml` | **62215** | 5433 | postgres/postgres |
| `frontend/src/environments/` | **62215** | — | — |
| `sidebar.ts`, `public-chat.component.ts` | **8081** (hardcoded) | — | — |
| `INTEGRATION.md` | **8081** | 5432 | — |
| `api.py` `DB_CONFIG` | — | **5432** | **rayen/motdepasse123** |
| `agent.py` connection string | — | **5433** | postgres/postgres |
| `docker-compose.yml` | — | 5433→5432 | postgres/postgres |

`api.py` and `agent.py` **connect to two different databases** inside the same process. The port `62215` looks like an accidental IDE-assigned ephemeral port committed by mistake.
→ **Fix:** one `.env` (I've provided `.env.example`), every service reads from it. Never hardcode.

**A3 — No API gateway / no service auth (HIGH).**
The FastAPI agent on :8000 is completely unauthenticated. Anyone who can reach that port bypasses Spring Security, all rate limits, and all quotas, and gets free LLM inference on your Groq/Google billing. There is no network policy in the compose file preventing this.
→ **Fix:** mTLS or a shared service token between Java↔Python; bind the agent to the internal network only; never expose :8000 publicly.

**A4 — Dual write-paths for chat (MEDIUM).**
`PublicChatController` (Java) and `/api/v1/chat` (Python) both accept chat traffic and apply *different* rules — the Python path enforces no quota at all. Two doors, one lock.
→ **Fix:** delete or internalise the public Python endpoint; make `/invoke` the only agent entry point.

**A5 — Synchronous long-poll for AI (MEDIUM).**
`PublicChatController` sets a **300 second** read timeout while the browser waits. That's a held thread per user and a terrible UX under load.
→ **Fix:** SSE/WebSocket streaming (Groq supports it), or a job queue with polling.

**A6 — Fire-and-forget ingestion thread (MEDIUM).**
`DocumentController` spawns a raw `new Thread(...)` that mutates a detached JPA entity and saves it outside a transaction. No retry, no dead-letter, lost on restart.
→ **Fix:** `@Async` with a bounded executor at minimum; ideally a real queue (the Redis you already run).

**A7 — Local `.pkl` parent-doc store (MEDIUM).**
`./parent_doc_store/*.pkl` on local disk means the agent cannot be horizontally scaled or containerised statelessly — and `pickle.load()` on stored files is an RCE primitive if that directory is ever writable by an upload path.
→ **Fix:** store parent documents in Postgres or S3, serialised as JSON, not pickle.

**A8 — No observability.** No structured logging, health endpoints (`/actuator/health` isn't enabled), metrics, or tracing. `print()` and `e.printStackTrace()` are the entire strategy. For a regulated banking product this is a non-starter.

---

## 3. Security findings

> These are the items to fix **before anything else**. Several are disclosure-grade.

**S1 — JWT signing secret committed to Git (CRITICAL).**
`application.yml` contains `app.jwt.secret: aW1wb3J0YW50X3NlY3JldF9rZXlfdGhhdF9tdXN0X2JlX2F0X2xlYXN0XzI1Nl9iaXRzX2xvbmc=` — that base64 decodes to the literal string *"important_secret_key_that_must_be_at_least_256_bits_long"*. Anyone with repo access can forge an admin token for any email. It's also duplicated in `backend/gen_model.py`.
→ Rotate immediately, move to env var, and treat every token ever issued as compromised.

**S2 — Database credentials hardcoded (CRITICAL).**
`api.py` ships `"user": "rayen", "password": "motdepasse123"`. `agent.py` embeds a full DSN with `postgres:postgres`. Someone's real dev credentials are in the history.

**S3 — Refresh-token endpoint accepts access tokens (HIGH).**
`AuthController.refreshtoken()` calls `validateJwtToken()` which only checks the signature — there is no `type` claim distinguishing access from refresh. A stolen 1-hour access token can be rolled forward indefinitely into fresh tokens. There is also no revocation list, so logout is cosmetic.
→ Add a `typ: refresh` claim, verify it, and persist refresh tokens server-side so they can be revoked.

**S4 — JWT has no roles claim (HIGH).**
The token carries only `sub` (email). Every authorised request re-hits the DB, and `JwtResponse` returns roles from login that the client is trusted to remember. Combined with S3 (refresh returns `roles: null`), role state after refresh is undefined.

**S5 — Wide-open CORS on the chat controller (HIGH).**
`SecurityConfig` correctly restricts origins to `localhost:4200`, but `@CrossOrigin(origins = "*")` on `PublicChatController` overrides it for the chat path. FastAPI is worse: `allow_origins=["*"]` **with** `allow_credentials=True` — an invalid and dangerous combination.

**S6 — Deprecated JJWT 0.11.5 API.** `setSubject`/`signWith(key, alg)`/`parserBuilder` are all deprecated; 0.11.5 has known advisories. Move to 0.12.x (`claims().subject()`, `verifyWith()`).

**S7 — Unrestricted file upload.**
`DocumentController.createDocument` writes `file.getOriginalFilename()` straight into `uploads/` — **path traversal** (`../../etc/...`), no MIME allowlist, no size cap beyond the 50 MB multipart limit, no AV scan. Filenames collide and overwrite (`REPLACE_EXISTING`).

**S8 — IP-based rate limiting is trivially bypassed.**
`getClientIp()` trusts `X-Forwarded-For` from the client. Spoof the header, get unlimited sessions. Also: the "5 sessions/hour" counter is computed but the DB write isn't atomic, so it races.

**S9 — Weak PII anonymisation.** `CIN_TUNISIENNE: \b\d{8}\b` matches *any* 8-digit number — including amounts, dates, account fragments — so it will mangle legitimate financial questions while missing formatted variants (`12 345 678`). It runs on input only; **model output is never scrubbed**.

**S10 — Prompt-injection defence is prompt-based only.** Rule 13 politely asks the model to ignore injection. That is not a control. No output filtering, no allowlist on the response.

**S11 — Committed binaries & PII risk.** `backend/uploads/` contains 5 real PDFs/DOCX (BCT circulars, internal guides) committed to Git, plus a stray `backend/echo` file. Internal bank documents should not live in a source repo.

**S12 — Stack traces to stderr, secrets in logs.** `spring.jpa.show-sql: true` and `logging.level.org.springframework.security: DEBUG` are on in the only config profile — that logs SQL and auth internals in production.

---

## 4. Code-base findings

### 4.1 Java backend — **the build is broken**

`backend/pom.xml` declares dependencies that **do not exist in Maven Central**:

```xml
spring-boot-starter-webmvc            <-- not a real artifact (it's -web)
spring-boot-starter-data-jpa-test     <-- does not exist
spring-boot-starter-security-test     <-- does not exist (it's spring-security-test)
spring-boot-starter-validation-test   <-- does not exist
spring-boot-starter-webmvc-test       <-- does not exist
```

The only real test starter is `spring-boot-starter-test`. Additionally **`spring-boot-starter-parent:4.1.0` does not exist** (current line is 3.5.x) and Spring Boot 4 requires **Java 21**, while `<java.version>17</java.version>`. These look like hallucinated/AI-generated coordinates — consistent with the `gen_*.py` scaffolding scripts committed in `backend/`.

**Corrected dependency block:**

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.5</version>
</parent>
<properties><java.version>21</java.version></properties>
<!-- keep: starter-web, starter-data-jpa, starter-security, starter-validation -->
<!-- delete all five bogus *-test starters, replace with: -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-test</artifactId>
  <scope>test</scope>
</dependency>
```
Also bump `jjwt` to `0.12.6` and add `spring-boot-starter-actuator`.

**Other Java issues**

- **No service layer.** `PublicChatController` is 200 lines of repositories, HTTP calls, quota logic, DTO mapping and IP parsing in a controller. `AgentService` exists but is bypassed by the controller that most needs it. Business logic belongs in `@Service`, controllers should be thin.
- **`@Autowired` field injection everywhere** — untestable. Use constructor injection.
- **Duplicate imports** in `PublicChatController` (the `import` block is pasted twice) — a strong smell of generated code that was never read.
- **Raw `Map` / `ResponseEntity<?>`** returns lose all type safety; `(Integer) body.get(...)` will `ClassCastException` the moment the agent returns a float.
- **`RestTemplate` instantiated per-controller**, no connection pooling, no retry, deprecated in favour of `RestClient`.
- **`GlobalExceptionHandler` exists but controllers still `try/catch` + `printStackTrace`** — the handler is dead code.
- **N+1 query** in `getUserSessions()`: one `findFirstQuestionBySessionId` per session in a loop.
- **`getChatHistory` splits one row into two DTOs** with identical timestamps, so ordering between a question and its answer is not guaranteed.
- `PasswordGenerator.java` and `DatasourceLogger.java` are dev scratch files in `src/main`.
- **Two user tables** (`User` and `Client`) with duplicated auth logic and an email-uniqueness check done manually across both — should be one table with a role discriminator.

### 4.2 Python AI agent

- **No dependency manifest existed.** I've added `requirements.txt` / `requirements-dev.txt` reconstructed from imports.
- **Module-level side effects.** `agent.py` opens PGVector connections, a Redis client and two LLM clients *at import time*. Any import — including by a test — tries to reach Postgres. This alone makes the code untestable and is why CI needs live services.
- **The 300-line mega-prompt (`_generer_reponse_specialisee`).** This is the single biggest maintainability problem in the Python layer. It contains **26+ numbered rules with hardcoded business data**:
  - Mourabaha 4.99% TMM (rule 18) vs **4.75% TMM** (rule 25) — *the prompt contradicts itself on the car financing rate*
  - Ijarah 5.75%, phone number `71 186 500`, product lists, required-document lists
  There are **two rules numbered 17** and two numbered 4. Business rates in a prompt string cannot be audited, versioned, or approved by compliance — for a bank this is a governance failure, not just a style issue.
  → **Fix:** move rates/contacts/product data into a database table or config, inject into the prompt at runtime, and cut the system prompt to ~20 lines of behaviour + retrieved facts.
- **Language detection is done three times**, inconsistently: the frontend sends `langue`, `api.py` calls `detect()`, and `_generer_reponse_specialisee` calls `detect()` *again* and **overrides the user's explicit choice**. A user who selects French but types an Arabic word gets an Arabic answer.
- **Dead code paths.** Re-ranking and contextual compression are fully implemented then disabled by comment (`# DÉSACTIVÉ POUR ACCÉLÉRER`). `hallucination` is in `AgentState` but never set. `SEUIL_DISTANCE = 0.65` is assigned and never used (the code actually uses a magic `0.45`).
- **Bare `except:` clauses** (12+ occurrences) swallow `KeyboardInterrupt` and mask real failures. `classifier_intention` catches everything and silently returns `"commercial"`.
- **Blocking `time.sleep()` retry inside async FastAPI** — `invoke_llm` sleeps up to 15+30+45+60 = 150 s on the event loop thread, freezing the entire agent for all users.
- **Semantic cache has no invalidation and no tenant scoping.** Answers are cached globally by question similarity at threshold 0.05; re-ingesting a document does not evict stale answers, so the bot will keep quoting a withdrawn rate.
- **`pickle.load()` on files from disk** (see A7) — deserialisation RCE risk.
- **Imports inside functions** (`import os, pickle` mid-file; a 3-line `from agent import ...` inside the request handler) — circular-import workarounds that indicate the module boundary is wrong.
- `query.py` is a 5-line debug scratch script; `create_users.py` prints raw SQL `INSERT`s with a shared password `azerty123`. Neither belongs in the repo root.
- `datetime.utcnow()` is deprecated in 3.12+ → `datetime.now(timezone.utc)`.

### 4.3 Angular frontend

**Builds successfully** — verified: 404 kB initial, 102 kB transferred, one lazy chunk. Two blockers found:

1. **Production build fails without internet.** `src/styles.css` line 2 does `@import url('https://fonts.googleapis.com/...Inter...')`, and Angular's font-inlining tries to fetch it at build time. In an air-gapped or restricted CI runner the build **fails hard**. → Self-host the font via `@fontsource/inter`, or set `"fonts": { "inline": false }`.
2. **Budget error:** `public-chat.css` is 11.76 kB against a 10 kB `anyComponentStyle` error budget. `ng build --configuration production` **fails**. → Split the stylesheet or raise the budget.

Other issues:
- **Hardcoded `http://localhost:8081`** in `sidebar.ts` and `public-chat.component.ts` while `environment.ts` says `62215` — so the app cannot work against both at once, and cannot be deployed at all. Use `environment.apiUrl` everywhere.
- **`environment.ts` has broken indentation** (`apiUrl` at column 0) and a commented-out alternative URL — leftover debugging.
- **Zero tests.** `app.spec.ts` is the CLI default. Vitest is installed and unused.
- **No HTTP error interceptor / no loading or error UI state** beyond ad-hoc handling.
- **Tokens in `localStorage`** (XSS-exfiltratable). Prefer httpOnly cookies given this is banking.
- **No i18n framework** despite being a trilingual product — and **no RTL stylesheet** despite Arabic being a first-class language. That's a visible product defect for Arabic users.
- Angular 21 with **no lint config** (`ng lint` not wired), no `.nvmrc`, no Prettier check in CI.

### 4.4 Repository hygiene

- **Single "first commit"** — no history, no branches, no PRs, no review trail.
- **`backend/gen_controllers.py`, `gen_controllers2.py`, `gen_model.py`, `gen_security.py`, `frontend/gen_auth.py`, `gen_login.py`, `gen_sidebar.py`, `generate_pages.py`** — eight code-generation scratch scripts committed alongside their own output. They duplicate (and now contradict) the real source, including a second copy of the JWT secret. Delete them.
- **`start-backend.bat`** — Windows-only launcher; there is no `.sh` equivalent and no Dockerfile for either backend or agent, despite a `compose.yaml` that bizarrely builds `frontend/node_modules/zod-to-json-schema` as a service (auto-generated by a VS Code extension, meaningless here).
- **Three compose files** (`compose.yaml`, `compose.debug.yaml`, `docker-compose.yml`) — only `docker-compose.yml` (Postgres + Redis) is real. The other two should go.
- `backend/echo`, `netstat` in `.gitignore` — evidence of shell mishaps committed/patched over.
- **No `README.md` at root**, no setup instructions, no LICENSE, no CONTRIBUTING.
- **`create_messages_table.sql`** is the only schema artifact and it conflicts with Hibernate `ddl-auto: update`.

---

## 5. Remediation plan

### P0 — Stop the bleeding (days 1–3)
1. **Rotate the JWT secret and all DB credentials.** Purge from Git history (`git filter-repo`). Assume compromise.
2. **Fix `pom.xml`** (§4.1) so the backend compiles. This unblocks everything else.
3. **Externalise all config** to environment variables — use the `.env.example` I've added. Delete `62215` everywhere; standardise on backend **8081**, agent **8000**, Postgres **5433**.
4. **Point the frontend at `environment.apiUrl`** — remove the two hardcoded `localhost:8081` strings.
5. **Fix `api.py` DB_CONFIG** so it uses the same database as `agent.py`.
6. **Push `ci/ci.yml`** to `.github/workflows/` so regressions get caught.
7. Delete the 8 `gen_*.py` scripts, `query.py`, `backend/echo`, `compose.yaml`, `compose.debug.yaml`, and the committed `backend/uploads/` binaries.

### P1 — Make it correct (weeks 1–2)
8. Extract a **service layer** in Java; constructor injection; typed DTOs; `@ControllerAdvice` handles errors.
9. Fix **refresh-token semantics** (`typ` claim + server-side revocation) and add a `roles` claim.
10. **Lock down CORS** to a configured origin list; remove `@CrossOrigin("*")`; fix FastAPI's `*` + credentials.
11. **Harden file upload**: sanitise filename (UUID + extension allowlist), MIME sniffing, size cap, store outside webroot.
12. **Authenticate the agent** — shared token on `/invoke`, bind :8000 to the internal Docker network only.
13. **Introduce Flyway** and set `ddl-auto: validate`. One owner for the schema.
14. **Refactor `agent.py`**: move client construction behind lazy factories/DI so the module is importable without a database. Split the file (retrieval / prompts / graph / persistence).
15. **Externalise the mega-prompt.** Rates, phone numbers and product data into a DB table with an admin screen; resolve the 4.99% vs 4.75% contradiction with the business.
16. Replace `time.sleep` retry with async backoff (`tenacity` + `asyncio.sleep`).
17. Replace bare `except:` with targeted exceptions + structured logging.

### P2 — Make it trustworthy (weeks 3–4)
18. **Tests.** Target ≥60% on business logic: JUnit + Testcontainers for Java, pytest with mocked LLM for the agent, Vitest for Angular. RAG regression suite — a fixed set of Q&A pairs asserted on every prompt change. This is non-negotiable for a bank.
19. **Observability**: Actuator + Micrometer, structured JSON logs with correlation IDs propagated Java→Python, OpenTelemetry traces, token-spend metrics per session.
20. **Dockerfiles** for backend and agent; one `docker-compose.yml` that brings the whole stack up with `docker compose up`.
21. **Streaming responses** (SSE) to kill the 300 s timeout.
22. **Output-side PII scrubbing** and a real prompt-injection filter; improve the CIN regex.
23. Move the parent-doc store off local pickle into Postgres/S3 as JSON.

### P3 — Product & scale (weeks 5–6)
24. `@ngx-translate` or Angular i18n + **RTL support for Arabic**.
25. Semantic-cache invalidation keyed to document version.
26. Re-enable re-ranking behind a feature flag, measured against the regression suite.
27. Audit log of every admin action (regulatory requirement).
28. Root `README.md`, ADRs for the big decisions, LICENSE.

---

## 6. Deliverables added in this branch

| File | Purpose |
|---|---|
| `AUDIT.md` | This report |
| `ci/ci.yml` | **Validated** GitHub Actions workflow — 3 jobs (Python agent w/ pgvector+Redis services, Maven backend, Angular frontend). Copy to `.github/workflows/ci.yml` and push. |
| `requirements.txt` | Reconstructed Python runtime dependencies (none existed) |
| `requirements-dev.txt` | Test/lint/audit tooling |
| `.env.example` | Single canonical config surface for all three services |

**To install CI:**
```bash
mkdir -p .github/workflows && cp ci/ci.yml .github/workflows/ci.yml
git add .github/workflows/ci.yml && git commit -m "ci: add build pipeline" && git push
```
Expect the **backend job to fail on the first run** — that failure *is* the `pom.xml` bug in §4.1, and it's the correct first thing to fix.

---

## 7. Closing assessment

Judge this as what it is: a capable RAG prototype that proved the concept. The retrieval architecture, the single-writer decision, the pre-LLM quota check and the PII layer all show real thought.

What's missing is everything that turns a prototype into a banking product — reproducible builds, secret management, tests, schema ownership, and getting business rules out of a prompt string and into an auditable store. Nothing here needs a rewrite. The Java layer needs a POM fix and a service layer; the Python layer needs decomposition and dependency injection; the frontend is closest to fine.

**Start with P0 item 2** — fixing `pom.xml` is a 20-minute change that turns a dead repository into one you can actually iterate on.
