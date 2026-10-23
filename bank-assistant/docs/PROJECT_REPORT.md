# Al Baraka Bank Assistant — Project Report

**Prepared by:** AMIRA AYARI
**Period covered:** 1 September 2026 → 23 October 2026
**Scope of the record:** every commit of the `bank-assistant` repository, from `b278b88` ("first
commit") to `61f9904`, 255 commits in total, plus this report
**Verified against:** commit `61f9904`; all counts and file paths below were read from that tree,
not recalled

---

## 1. Summary

This report covers the construction of a trilingual (French / Arabic / English) customer assistant
for Al Baraka Bank Tunisia, and the operations console the bank's staff use to run it. The product
answers customer questions about the bank's own products and documents, refuses honestly when it
cannot ground an answer, never shows a customer the internal provenance of an answer, and lets a
supervising member of staff watch every conversation as it happens.

The work spans eight weeks and 255 commits. It started from a repository containing an audit, a
plan and a partially written Python service, and it ends with three cooperating layers: an Angular 21
single-page application, a Spring Boot 4 edge that owns the database schema and every write, and an
external retrieval engine (RAGLab) that produces grounded answers. A fourth layer — an in-house
Python agent that originally answered the questions — was built, validated, and then deliberately
removed at the end of the period, when the bank chose the external engine as the single serving path.
That removal is the single largest structural decision recorded here, and §6 explains why it was
taken rather than kept as an option.

| | |
|---|---|
| Commits | 255 (one merge commit; the rest linear) |
| First commit | `b278b88`, 1 September 2026 |
| Last commit | `61f9904`, 23 October 2026 |
| Net change since the first commit | 426 files, +45 942 / −7 776 lines |
| Tracked files now | 428, this report included |
| Backend | Java 21, Spring Boot 4, Flyway (15 migrations), JWT; 16 controllers, 27 services, 29 entities, 25 repositories |
| Frontend | Angular 21 standalone components, Vitest; 18 screens, 32 routes, 91 unit tests |
| Assistant engine | RAGLab, external, addressed by `ip:port`; not part of this repository |
| Database | PostgreSQL, schema owned by Flyway, `ddl-auto=validate` |
| CI | 5 jobs: changes, migrations, backend, frontend, Sonar (optional) |
| Backend tests | 24 test classes, 123 `@Test` methods |
| Documented in | 30 Markdown files: `README.md`, `AGENTS.md`, six other root guides, 20 under `docs/`, 2 inside subprojects |

---

## 2. What the product is

### 2.1 Actors

There are exactly two kinds of identity in the system, and the distinction is a product rule rather
than an implementation detail.

**Customers** sign in to a portal. They see their own accounts, cards, transactions, financings and
beneficiaries, can simulate a financing, ask for an appointment with an advisor, and talk to the
assistant. They never see which document an answer came from, and they never see another customer's
data.

**Staff** sign in to a back office. They are the only identities that can hold roles, and roles are
granted by the general manager alone. Staff watch the live conversation feed, read the audit trail,
manage governed business facts (the bank's own statements about its products, which outrank any
document), manage the corpus through the RAGLab console, and configure the engine.

### 2.2 Runtime shape

```
browser ─▶ nginx (Angular SPA, /api proxy) ─▶ Spring Boot edge ─▶ RAGLab (external, http://<ip>:<port>)
             security headers, SSE            auth, quotas,        retrieval → grounded answer → gates
                                              persistence, admin
                                                     │
                                                     ▼
                                               PostgreSQL (schema owner)
```

The edge is the only writer. RAGLab answers and persists nothing in this database; it owns its own
index and its own document screens, and the edge never writes to it except through explicit console
actions. That boundary is what makes the answer path auditable: the edge opens the exchange row
before calling the engine, so an admin sees a conversation from its first message rather than its
last, and so a failure is visible even when no answer is produced.

### 2.3 The answer path, as it stands

1. The SPA posts a message with a session identifier and a language.
2. The edge checks the session quota (tokens and message count) *before* spending anything on the engine.
3. The edge calls `POST {RAGLAB_URL}/answer` with the shared service token, and — for a signed-in
   customer who asked about their own banking situation — a masked context derived at that moment from
   their records (§6.4).
4. RAGLab retrieves, generates, and returns text, claims, sources and a grounding status.
5. The edge persists the exchange, applies an output PII scrub, replaces any engine sentence that is
   not the bank's own refusal wording, settles the quota, and streams the final frame.

---

## 3. How the work was run

Three practices shaped the result more than any technical choice.

**A written contract with the agent.** `AGENTS.md` (382 lines) states what an agent may decide alone,
what must be asked, how work is committed, and what "done" means. It exists because the failure mode
of an autonomous agent is not bad code but unrequested scope: a rule that says "ask, never guess, on
business rules, destructive operations and product direction" is what kept profit rates, regulatory
wording and data-retention choices with the bank instead of with the tool.

**CI as the referee, not the decoration.** Every push runs five jobs. Two of them exist purely
because a previous incident had made "green" meaningless: a migrations job that applies the entire
Flyway chain to a real PostgreSQL and asserts the data guarantees hold, and a `changes` job that
filters which real jobs run. The second one caused a documented blind spot (a push touching no
filtered path reports success with every real job skipped); it is now recorded as risk R9 rather than
hidden.

**Verification on the host.** The agent sandbox has no JDK, no Maven, no Docker, and cannot reach the
GitHub log CDN. Every turn that changed code therefore ended with the exact commands for the
maintainer to run, and the repository kept a Docker-based harness (`verify-local.sh`) whose own logs
were committed so a result could be read without access to a runner.

---

## 4. The history, phase by phase

| Phase | Dates | Landmark commits | Outcome |
|---|---|---|---|
| 0. Bootstrap and CI | 1–4 Sep | `d442e12`, `c87b522` | Repository contract, first green pipeline, Docker stack |
| 1. Green-field agent core | 3 Sep | `8ecda66`, `2762409` | In-house RAG service: ingest, retrieve, answer, gates |
| 2. Evaluation harness | 3–5 Sep | `6d0ef2f`, `740f341` | Classification/live evals, baseline recorded |
| 3. Persistence and metering | 14–16 Sep | `4a9ade6`, `4d7d65a` | Edge owns the schema, the exchange and the quota |
| 4. Ingestion at scale | 15–16 Sep | `7c59b42`, `af2dd85` | Durable queue, worker, corpus registry, cache provenance |
| 5. Product surface | 21–22 Sep | `9042a26`, `909aa1c` | i18n, streaming, admin control room, portal, V8 |
| 6. Observability and security | 21–22 Sep | `7d3146f`, `106a1a1` | Metrics, threat pass, virtual threads |
| 7. Quality gates and ops | 22 Sep | `890d98d`, `260742a` | Coverage gates, contract tests, deployment, refresh tokens |
| 8. Engine evaluation and switch | 22 Sep–14 Oct | `ac71b73`, `8eb2905` | RAGLab analysed, adopted, console built |
| 9. Consolidation | 22–23 Oct | `de7ae17`, `9bbb9f7` | One engine, customer model V15, console redesign, hygiene |

Phases overlap in time — several workstreams ran in parallel — so the table orders the work rather
than partitioning the commits. The daily distribution in §5.1 is the exact record.

### Phase 0 — Bootstrap (1–4 September)

The repository opened with an audit (`AUDIT.md`), a production plan (`PLAN_TO_PROD.md`) and a
working contract (`AGENTS.md`) — three documents written before any feature, which is why every later
decision has a place to be recorded. The first pipeline failed immediately on two counts that are
worth naming because they recur: the build expected a Java version the runner did not install, and
the Spring context would not boot in CI without a database. The first was fixed by pinning JDK 21,
the second by giving the test profile an in-memory database and the properties that `@Value` fields
need at startup — a decision that later had to be revisited for real PostgreSQL behaviour (§11).

### Phase 1 — The in-house agent (3 September)

The bulk of the early work, and the period with the highest commit density of the project: 128
commits in a single day. In sequence: request normalisation, configuration and provider abstraction,
retrieval with grounding gates, an answer path over the corpus, and an `/invoke` contract. The
service was written as a Python package with its own tests, its own Dockerfile, and later a streaming
variant. Ingestion at this stage was a fire-and-forget POST from the Java edge.

This phase is also where the corpus arrived: an internal Islamic-banking guide, a BCT circular and
Loi 2016-48, checked into `documents/` and used as the retrieval corpus for every subsequent test.

### Phase 2 — Evaluation (3–5 September)

A classification and evaluation harness, a live-eval job in CI against real model credentials, and a
recorded baseline (domain 100 %, intent 93.75 %). The practice established here — a golden set and a
number that can regress — survived every later refactor, including the one that deleted the service
it was built for.

### Phase 3 — The edge takes ownership (14–16 September)

The most consequential architectural phase. The edge had been a thin proxy over a Python service that
also wrote to the database; two writers on one schema is not a design, it is a race. The phase
proceeded in stages: Java-owned chat entities and a persistence service; the edge persisting the
exchange itself; a captured `V1` baseline so Flyway could own the schema with `ddl-auto=validate`; a
consistent JSON error contract; and two-phase metering with a per-session concurrency guard so a slow
engine cannot be used to exhaust a session's budget.

The baseline capture was imperfect in a way that took three weeks to surface: `V1` was not idempotent,
so a fresh database could not be built from the chain. It was rewritten and baselined at 0 (`b3ea17a`),
which is what makes today's migrations job possible at all.

### Phase 4 — Ingestion (15–16 September)

A durable Redis queue replaced the fire-and-forget POST, with a standalone worker CLI, status
reconciliation from worker to edge, a live corpus registry, catalog versioning and pinning, and
provenance-aware cache invalidation. It worked and was validated end to end. It was also entirely
deleted five weeks later (§6.1) — which makes this phase the clearest example in the project of work
that a later decision can retire, and of why the decision had to be taken on operational grounds
rather than technical ones.

### Phase 5 — The product (21–22 September)

Three things shipped together and defined the customer experience: a French/Arabic/English i18n layer
with right-to-left layout for the public chat (the back office stayed French, a deliberate scope
decision); server-sent events from engine to edge to browser, so an answer appears as it is written;
and an admin control room for governed facts, taxonomy releases and ingestion operations. A public
site, a client portal and the `V8` client schema followed, along with financing simulation, advisor
appointments and news.

Two defects from this phase are worth recording because they are invisible in unit tests. Nginx
cached `index.html`, so a rebuilt frontend served an unstyled page (`dbf8cd7`). And critical-CSS
inlining injected an `onload` handler that the Content-Security-Policy blocks, which was fixed by
turning the inlining off (`2a6c4eb`).

### Phase 6 — Observability and the threat pass (21–22 September)

Correlation identifiers through every layer, structured logs, Prometheus metrics, and a per-turn
pipeline trace that the admin can read as a screen rather than as a table under another screen. The
threat pass that followed removed a second, unauthenticated chat door, replaced string comparisons
with constant-time token checks, and hardened response headers.

### Phase 7 — Gates, contracts, operations (22 September)

Coverage gates on all three layers, a RAG regression golden set on every push, a JSON-Schema contract
checked from both sides of the edge–engine boundary, a nine-check end-to-end smoke through nginx, a
server-side refresh-token rotation, a production compose profile, a deployment guide and a runbook.
This is the phase that turned a working prototype into something a bank could operate.

### Phase 8 — The engine question (22 September – 14 October)

A written comparison between RAGLab's contract and the in-house agent, an integration analysis, and a
recommendation to switch. The switch was then performed in slices rather than as a rewrite: a
connection and health screen, a quality workbench for grounded answers and retrieval, an admin-chosen
serving engine effective on the next turn, monitoring that can observe either engine, and one file
world with two index worlds that are never blended. A dedicated `X-Service-Token` was added, and a
long debugging session on a `422` from the engine ended in a one-line cause — the streaming client
had to be pinned to HTTP/1.1.

### Phase 9 — Consolidation (22–23 October)

The bank chose one engine, and the repository was reshaped around that choice:

- the in-house Python service, its Dockerfile, its tests and its compose service removed (`de7ae17`);
- the internal document screen, the ingestion panel and the Redis queue removed (`ea4f566`), since the
  corpus and its index now belong to RAGLab;
- the engine selector, `/admin/agent/engine` and the dual-engine KPIs removed (`34c90c2`), with one
  historical KPI bucket deliberately kept so a before/after comparison stays possible (`59ed875`);
- the customer model rebuilt in the shape of a core-banking system (`V15`), with a development
  population and a masked context for the assistant (`a35b382`);
- a personal-data report written for the bank's developers (`docs/CUSTOMER_PII_REPORT.md`);
- the RAGLab console redesigned and its polling rewritten (§10).

---

## 5. Quantitative profile

### 5.1 Commit distribution

| Day | Commits |
|---|---|
| 1 Sep | 13 |
| 2 Sep | 1 |
| 3 Sep | 128 |
| 4 Sep | 13 |
| 5 Sep | 4 |
| 7 Sep | 4 |
| 14 Sep | 1 |
| 15 Sep | 24 |
| 16 Sep | 23 |
| 21 Sep | 6 |
| 22 Sep | 13 |
| 14 Oct | 3 |
| 22 Oct | 10 |
| 23 Oct | 12 |
| **Total** | **255** |

The shape says as much as the numbers: a dense construction burst (3 September), a long middle of
disciplined small commits around persistence and ingestion, and a final week of removals. The 37
`verify:` commits are the harness recording its own build results; they are excluded from any claim
about feature throughput, and are listed in the appendix only for completeness.

Of the 255 subjects, 207 follow the conventional prefix: `docs` 44, `fix` 42, `feat` 37, `verify` 37,
`ci` 16, `test` 12, `chore` 8, `refactor` 4, `build` 2, and one each for `sec`, `perf`, `ops`, `nginx`
and `flyway`. The remaining 48 predate the convention and are written as plain sentences. A project in
which fixes and documentation outnumber features is a project that was tested against reality rather
than demonstrated.

### 5.2 Code and assets

| Area | Measure |
|---|---|
| Java (main) | 14 637 lines, 164 files |
| TypeScript (excluding specs) | 9 369 lines, 87 files |
| Templates / styles | 5 039 HTML + 7 134 CSS lines |
| SQL migrations | 15 files, `V1` … `V15` |
| Unit tests | 24 backend classes (123 test methods); 20 frontend spec files (91 tests) |
| Documentation | 30 Markdown files, ~6 200 lines including this 900-line report |
| Corpus | 1.0 MB of source documents in `documents/` |

### 5.3 Surface

Sixteen REST controllers cover public chat (sync and SSE), authentication and refresh rotation,
the client portal (profile, accounts, simulations, advisor requests), admin access control, governed
facts, taxonomy, news, monitoring, and the RAGLab console (health, ask, documents, retrieval,
settings, evaluations). The SPA exposes 32 routes across 18 screens: public site, login and
registration, six portal screens, and eleven administrative screens including the five-tab RAGLab
console.

---

## 6. Decisions that shaped the system

Each of these was a fork in the road, not an implementation detail; each is recorded with what it
cost.

### 6.1 One serving engine, RAGLab — the in-house agent was removed

**Decision.** RAGLab answers every customer turn. The Python service, its queue, its Redis
dependency and its document screens were deleted rather than kept as a fallback.

**Why.** Two engines meant two corpora, two index worlds, two sets of KPIs, two refusal wordings, and
a selector that made behaviour depend on a setting. The bank operated RAGLab itself; maintaining a
parallel implementation bought nothing but divergence. Keeping it "just in case" would have kept the
whole dual-engine surface — selector, engine table, per-engine buckets, ingestion queue — alive
indefinitely.

**Cost.** Redis and the ingestion panel had to be removed from the product, a UI a few weeks old.
`V11`/`V12` remain in the chain (Flyway history is append-only), and one KPI bucket was kept so a
before/after comparison stays possible.

### 6.2 No customer data in the retrieval index

**Decision.** Customer records are never indexed into RAGLab. What the engine receives, when a
signed-in customer asks about their own situation, is a **masked context derived at the moment of the
question** and injected into that one request.

**Why.** A shared index with customer data is a cross-customer leak waiting for a retrieval bug. The
masked context has a lifetime of one request, cannot be enumerated, and disappears with the exchange.

**Cost.** Questions that need a lookup the engine cannot do are answered by the edge's own path, and
the blast radius of a refusal is larger than in an index-everything design. This was accepted
deliberately: a refusal is cheap, a leak is not.

### 6.3 Roles belong to staff only, and only the general manager grants them

**Decision.** Customers never hold roles. Staff roles are granted exclusively by the general manager,
through a screen with two enforced constraints.

**Why.** A permission system that customers can reach is a permission system with a second, unowned
boundary. The constraint has a direct security consequence: the escalation path through a
customer-facing surface does not exist.

### 6.4 Development fixtures only behind a flag

**Decision.** Accounts with known passwords exist only when `DEV_SEED=true`, and production stacks
never set it. The fixtures are not a migration.

**Why.** A known password in a live database is the cheapest way to lose one. Putting fixtures in a
Flyway migration, as originally proposed, would have seeded every environment including production —
that proposal was rejected outright.

**Gap found later.** Two of those accounts were still created unconditionally by the application's
startup runner, and the frontend shipped their passwords in the production bundle. Both were closed in
this period (§10).

### 6.5 French back office, trilingual customer surface

**Decision.** The customer-facing application is French / Arabic / English with right-to-left layout;
the administrative console is French only.

**Why.** A bank teller works in one language, and translating eleven admin screens triples the surface
that must be kept in step for no operational gain. The customer surface is where language is a
product requirement.

### 6.6 Governed facts outrank documents

**Decision.** The bank's own statements about products and terms live in a governed store with an
audit trail, and are served ahead of document retrieval.

**Why.** A BCT circular and an internal guide can disagree about a rate. The bank needs a place to
state what is true today, with a name and a date attached, without re-indexing a corpus.

### 6.7 The console is a product, not a debug page

**Decision.** The RAGLab console was rebuilt around what an operator needs to know in two minutes —
can it answer, and is what it answers current? — and its polling was moved from the service to the
screen.

**Why.** The console had become the place where every internal concept of the engine was exposed. And
the status poll ran from a root-level service, every three seconds, on every screen of the
application, in hidden tabs: 28 800 requests a day per open tab, each triggering application-wide
change detection.

**Cost.** A real state strip, a route guard that sends a read-only operator to the screen they can
actually open, and a poll that adapts (2 s while ingesting, 20 s healthy, 60 s down, nothing while
hidden) — details in `docs/CONSOLE_UI.md`.

---

## 7. Defects found and fixed

The list is deliberately mechanical: each row is a defect that reached a real environment, with its
cause, because the causes are more instructive than the fixes.

| # | Symptom | Root cause | Fix |
|---|---|---|---|
| 1 | CI could not build | POM compiled against a JDK the runner did not have | Pin JDK 21 for Spring Boot 4 |
| 2 | Spring context failed in CI | No database, missing `app.*` properties | H2 test profile with explicit properties |
| 3 | Flyway silently ignored | Boot 4 needs the Flyway **starter**, not `flyway-core` | `spring-boot-starter-flyway` (`c77da38`) |
| 4 | A fresh database could not be built | `V1` was not idempotent | Rewritten, baselined at 0 (`b3ea17a`) |
| 5 | Backend container refused to start after update | `V15` renamed account types *before* dropping the old check constraint | Reorder: drop, rename, re-add (`e29866c`) |
| 6 | Migrations job failed on an empty schema | Legacy insert omitted a now-required column | Column supplied; failures now print the SQL state (`a4a448b`) |
| 7 | Rebuild served an unstyled app | nginx cached `index.html` | Cache-control on the entry document (`dbf8cd7`) |
| 8 | CSP console error, page unstyled | Critical-CSS inlining emits an `onload` handler | Inlining disabled (`2a6c4eb`) |
| 9 | Engine returned `422` on every stream | Streaming client negotiated HTTP/2 | Pinned to HTTP/1.1 (`ffa5284`) |
| 10 | A successful engine reply was unparseable | Response read as a typed tree, not as text | Read as `String`, then parse (`9809ffd`) |
| 11 | An https engine address became http | Address validation lower-cased the scheme | Pattern-based validation (`6aa2e1d`, `a5a2281`) |
| 12 | Customers saw document names | Provenance rendered in the client widget | Stripped at the edge; admin keeps it (`ce4cedc`) |
| 13 | A refusal quoted the engine's own sentence | No rewrite layer on refusals | Bank's wording substituted (`be37966`) |
| 14 | Old stored answers re-opened a leak | History replay served rows as stored | Replay sanitises (`2cb417a`) |
| 15 | Leaked API key and chat transcripts in the repo | Transcript HTML checked in by hand | Files removed, `.gitignore` hardened, key rotated (`672895d`) |
| 16 | Production could boot with `admin@albaraka.tn / postgres` | Startup seeding ran unconditionally | Gated behind `DEV_SEED`, passwords no longer logged (`9bbb9f7`) |
| 17 | Production build auto-signed visitors in as the demo accounts | `angular.json` had no `fileReplacements`, so `production` was always `false` | File replacement added; fixture credentials only in the dev environment (`9bbb9f7`) |
| 18 | A read-only operator was ejected from the console | `/admin/raglab` redirected everyone to a screen its guard refused | Landing guard chooses the first permitted screen |

The last three were found during this period's cleanup rather than during feature work, and two of
them were live in the shipped application. That is the argument for reading a repository as a whole
after a period of removals: deleting a feature leaves behind the assumptions that feature needed.

---

## 8. Quality assurance

**CI, five jobs.** `changes` computes which areas a push touched; `migrations` applies the whole
Flyway chain to a real PostgreSQL 16 and asserts the `V15` guarantees; `backend` compiles and runs the
whole suite, lints the Spring YAML for duplicate keys (a class of bug that prevents startup), and
asserts the Flyway starter is packaged; `frontend` installs, builds for production, builds the
container and verifies the served version metadata, then runs the unit suite; `sonar` runs only when
a server and token are configured.

**Gates that can fail a build.** Backend: JaCoCo line coverage of 0.80 on three security-critical
classes (`GovernedFactsService`, `RefreshTokenService`, `CorrelationFilter`). Frontend: Vitest
thresholds of 50 % lines and statements. Both are honest scopes rather than global percentages: a
global gate on a codebase this young would have been met by testing getters.

**Local harness.** `verify-local.sh` builds and tests in throwaway containers against a dedicated
PostgreSQL, writes its logs into `verify/out/`, and pushes them, so a build result is readable from
the repository without a runner.

**End to end.** Nine checks through nginx, exactly as a browser behaves: SPA and security headers,
health through the proxy, an answer over the corpus, a refusal for an out-of-scope question, an SSE
stream, admin login with refresh rotation and logout, the client portal, governed facts, and metrics.
It requires a reachable engine, so it runs against a deployed environment rather than every push.

**What could not be verified here.** The sandbox has no JDK, Maven or Docker, so Java changes were
verified only by CI; and the browser needed for screenshot testing cannot be downloaded, so the
console's visual result was verified structurally (rendered DOM in tests, CSS by inspection) rather
than by picture. Both limitations are stated wherever they apply instead of being smoothed over.

---

## 9. Security posture

Layered defence, each layer recorded in `docs/THREAT_PASS_6.2.md` and the personal-data report:

- **Transport and headers** — CSP, HSTS, frame denial, no-sniff, and a strict referrer policy served
  by nginx.
- **Service-to-service** — a pre-shared `X-Service-Token` on every engine call, compared in constant
  time; the edge never exposes it to a browser.
- **Identity** — JWT access tokens with server-side refresh rotation and revocation; a revoked refresh
  token cannot mint a new access token.
- **Authorisation** — roles for staff, fine-grained permissions inside them, enforced at the route,
  at the controller and by method security; the general manager alone assigns roles.
- **Data** — no customer records in the retrieval index (§6.2); output PII scrub on every answer; the
  admin sees provenance, the customer never does.
- **Secrets** — no key in the tree; `.env` ignored, `.env.example` documents every variable; the
  development JWT secret is explicitly documented as a development default rather than quietly
  shipped.

**Outstanding, and named rather than deferred silently:** the leaked API key that was rotated must be
revoked at the provider (a rotation only takes effect once the old key is disabled); `SONAR_TOKEN` and
`SONAR_HOST_URL` are still unset, so the Sonar job reports "not configured"; and the development JWT
default is safe only because production compose requires an override — a startup assertion would be
better and is listed in §11.

---

## 10. The closing pass: console redesign and repository hygiene

The last five commits of the period are a different kind of work from everything before them: not
features, but a final reading of the repository for what a rebuild leaves behind.

### 10.1 The RAGLab console

The console was rebuilt as an instrument strip of six readings (service, liaison, models, corpus,
keys, ingestion), one lamp per reading, a "verified at HH:mm:ss" timestamp so an operator can tell a
stale screen from a failing service, and one sentence saying what the screen is for. The design
process — a token plan, then a critique of the first draft against the generic admin-console pattern,
then the build — is recorded in `docs/CONSOLE_UI.md`, together with the two public agent skills whose
methodology it applies.

The performance change is the part with a number: polling moved from a root-level service to the
console's own lifecycle, with adaptive intervals, no polling in a hidden tab, at most one request in
flight, and the timer outside Angular's zone. From 28 800 requests and full change-detection passes a
day per open tab to 4 320 when idle, zero when hidden, and zero for anyone not looking at the console.

### 10.2 Repository hygiene

| Change | Why |
|---|---|
| Startup seeding gated behind `DEV_SEED`; passwords no longer logged | A production boot could create `admin@albaraka.tn / postgres` |
| `fileReplacements` added; fixture credentials moved to `environment.devAccounts` | Production builds shipped the dev environment and silently signed visitors in as the fixtures |
| One-click login strip compiled out of production | Same cause; the buttons were rendered unconditionally |
| Five console stylesheets mapped onto the design tokens (442 literals) | Four different greens for "ok", a `var(--brand-red, …)` that resolved nowhere; a brand change is now one file |
| Unreferenced `bankimg.jpg`, duplicate `logo.png` removed | 143 kB nothing referenced |
| Stale Redis comment in the POM removed | Described a queue deleted five weeks earlier |
| Stray `'` and `"` entries in `.gitignore` removed | Left by a shell redirect |
| Stale `verify/out` logs removed | Described a stack that no longer exists |
| `start-backend.bat` removed | Hardcoded `c:\banque_assistant`; Docker is the supported entry |
| `AGENTS.md` rules pointing at four missing files rewritten | A contract that cites absent files is either obeyed by invention or ignored |
| A real person's name removed from the public landing page and from working documents | A decorative card in a customer-facing hero, and a repository the bank may share |

Verification for the period: production build green with the fixture strings absent from `dist/`,
20 frontend spec files and 91 tests green, backend compile and full suite green in CI, the migrations
job green, and the Sonar job green where configured.

---

## 11. Current state and remaining work

The product is deployable and operable today. What is left is not "finishing" but a list of known
edges, ordered by what would bite first.

1. **Revoke the leaked provider key** at the provider's console. Until then a rotation protects the
   repository but not the key.
2. **Assert a production JWT secret at startup.** The development default is documented, which is
   better than hidden, but a boot that refuses to start is better still.
3. **Testcontainers for the database.** Context tests run on H2; `pgvector`, the check constraints and
   the migration chain are covered only by the CI migrations job, not by the unit suite.
4. **Convert the five console screens to `OnPush` and signals.** They update plain fields from RxJS
   subscriptions, so switching alone would silently freeze them; the conversion needs its own tests.
5. **Split the settings screen** (348 TypeScript + 477 template lines) into models, keys and profile
   children before it grows further.
6. **Screenshot regression** for the console, which needs a browser on the runner.
7. **Run the nine-check end-to-end smoke in CI** behind a scheduled job with a reachable engine.
8. **Move the 1 MB corpus in `documents/` to external storage or LFS**, if the repository is to be
   shared more widely.
9. **Retire the historical plans** (`AUDIT.md`, `PLAN_TO_PROD.md`) into an archive note: their value
   now is the record of what was believed at the time, not instructions.

---

## Appendix A — Migrations

| Version | Purpose |
|---|---|
| `V1` | Baseline: conversations, messages, documents, users, clients, accounts, cards, transactions |
| `V2` | Governed business facts |
| `V3` | Taxonomy catalogue |
| `V4` | Refresh tokens |
| `V5` | Ingestion error text |
| `V6` | Catalogue overview intent |
| `V7` | Monitoring and service exemplars |
| `V8` | Client portal |
| `V9` | Advisor requests |
| `V10` | News |
| `V11` | Engine administration |
| `V12` | Engine table |
| `V13` | Chat realtime observability |
| `V14` | Staff roles and permissions |
| `V15` | Core-banking-shaped customer model (branches, beneficiaries, financings, account types, cards) |

## Appendix B — Screens and routes

**Public and portal:** landing, public chat, login, registration, portal home, portal profile, portal
accounts, portal assistant, advisor requests.
**Administration:** dashboard, users, roles, access control, governed facts, taxonomy, news,
monitoring, statistics, history, agent health, agent journal, agent quality, RAGLab console (ask,
corpus, retrieval, settings, evaluations).

## Appendix C — CI jobs

| Job | Runs when | What it proves |
|---|---|---|
| `changes` | always | Which of the following jobs are relevant to this push |
| `Migrations (Postgres)` | backend, SQL or migration changes | The whole Flyway chain builds a real database and the `V15` guarantees hold |
| `Backend (Spring Boot)` | backend changes | Compiles, tests, YAML lint, Flyway starter packaged |
| `Frontend (Angular)` | frontend changes | Production build, container build, served version metadata, unit suite |
| `SonarQube (optional)` | when a server and token exist | Static analysis and the coverage upload |

## Appendix D — Commit index

255 commits, oldest first, from `b278b88` to `61f9904`. The commit that adds this report is the next one. Subjects are reproduced as written, except where a subject named the repository's owner by name; those two words are replaced by "the maintainer".

**2026-09-01** — 13 commits

| Commit | Subject |
|---|---|
| `b278b88` | first commit |
| `5dfe4c3` | docs: add architecture & code audit, CI workflow, Python deps, env template |
| `d442e12` | docs: add AGENTS.md working contract |
| `86d5ba6` | ci initiated |
| `346c950` | Merge remote-tracking branch 'origin/master' into arena/01a05c28-bank-assistant Co-authored-by: arena-agent <297053741+arena-agent@users.noreply.github.com> |
| `ccf3753` | fix(ci): make frontend build/test green and repair Spring Boot 4 POM |
| `3c6d063` | ci: require JDK 21 for Spring Boot 4 backend, bump setup-java to v5 |
| `ba71960` | test(backend): boot the context against H2 so contextLoads works in CI |
| `b14ab3b` | ci to jdk 21 |
| `c87b522` | feat(docker): run the entire stack with docker compose |
| `799972f` | fix(test): supply app.cors.allowed-origins to the test context |
| `46edbe8` | fix(chat): keep the agent URL environment-driven and drop wildcard CORS |
| `7b634f1` | fix(chat): show the backend's error instead of a generic bubble |

**2026-09-02** — 1 commit

| Commit | Subject |
|---|---|
| `f63a5da` | docs(agents): update CI rule — agent now pushes workflow changes itself |

**2026-09-03** — 128 commits

| Commit | Subject |
|---|---|
| `8ecda66` | feat: green-field agent core — slice #1, request normalization (f(x)) |
| `546fc0c` | docs: add f(x) pipeline workflow diagram (svg source + png render) |
| `9fe44d1` | ci: re-run agent test job to capture result |
| `3ad9c7d` | fix(ci): make pytest resolve the albaraka package |
| `98a3774` | docs: add end-to-end plan to production (PLAN_TO_PROD.md) |
| `bbfc332` | feat: green-field agent core — slices 1.2 & 1.3 (config, providers, pipeline) |
| `35d1293` | test: make llm provider test hermetic to CI dummy keys |
| `9b8a998` | docs: update PLAN_TO_PROD progress tracker (slices 1.2 & 1.3 done) |
| `2762409` | feat: green-field agent core completes Phase 1 — retrieval, answers, gates, /invoke |
| `6d0ef2f` | feat: eval harness for classification quality (plan phase 2.2) |
| `be0bc73` | feat: add LLM-only classification mode so the agent runs with just a Groq key |
| `fe524cc` | ci: add live-eval job using real model secrets; grow eval set to FR/EN/AR |
| `1ff55d6` | ci: fix live-eval job gating (no secrets in job-level if) |
| `f07d899` | ci: map GEMINI_API_KEY secret into GOOGLE_API_KEY env for the agent |
| `740f341` | docs: record live eval baseline (domain 100% / intent 93.75%) in PLAN_TO_PROD |
| `bbe464f` | fix(auth): restore id + roles on token refresh (Phase 3.6 / S4) |
| `bf913ea` | docs: add Phase 3 execution roadmap + persistence-handover checkpoint |
| `34120f7` | ci: path-filter jobs to changed areas + publish failure logs to an Issue |
| `bad22a7` | ci probe: backend-only comment to prove path filtering |
| `04912c1` | ci: fix paths-filter base so jobs actually skip on non-affected pushes |
| `8022f33` | ci probe: backend-only change after paths-filter base fix |
| `163f363` | ci: run only on main/master merges + PRs; add local Docker verify harness |
| `d3c5e51` | verify: update build/test logs (all; result=FAIL) @ 163f363 |
| `fb364f1` | fix(verify): run compose as a command array, not a quoted string |
| `126a900` | verify: update build/test logs (backend; result=GREEN) @ fb364f1 |
| `dc66725` | verify: update build/test logs (all; result=GREEN) @ 126a900 |
| `127318a` | verify: make local re-runs incremental (no clean, reuse deps) |
| `9ef2969` | verify: update build/test logs (all; result=GREEN) @ 127318a |
| `1b01d76` | fix(backend) Phase 3.6 batch 1: slf4j filter, refresh 401, hardened uploads |
| `49baa1a` | verify: update build/test logs (backend; result=GREEN) @ 1b01d76 |
| `600d9b3` | feat(backend) Phase 3.2 stage 1: Java-writable chat entities + ChatPersistenceService |
| `efb0f62` | verify: update build/test logs (backend; result=GREEN) @ 600d9b3 |
| `84b6ce6` | feat(backend) Phase 3.5 stage 2: Java now persists the public-chat exchange |
| `b4238bb` | verify: update build/test logs (backend; result=GREEN) @ 84b6ce6 |
| `4a9ade6` | flyway: capture V1 baseline from live Postgres schema |
| `9c4c89c` | feat(backend) Phase 3.2 stage 3: Flyway owns the schema (ddl-auto=validate) |
| `3480905` | docs: mark Phase 3.2 (Flyway+validate) done, 3.5 partial |
| `34eb497` | feat(backend) Phase 3.3 stage 4a: consistent JSON error contract |
| `8b2c22f` | verify: update build/test logs (backend; result=GREEN) @ 34eb497 |
| `4d7d65a` | feat(backend) Phase 3.4 stage 4b: two-phase metering + per-session concurrency guard |
| `4dc93e7` | verify: update build/test logs (backend; result=GREEN) @ 4d7d65a |
| `72fbb5f` | docs: Phase 3 core complete + dedupe Phase 3 checklist |
| `45f9f80` | feat(agent) Phase 4.1a: greenfield ingestion core (chunk + version/domain tagging) |
| `0f6d50e` | verify: update build/test logs (agent; result=FAIL) @ 45f9f80 |
| `a1084d5` | fix(agent) Phase 4.1a: correct paragraph-boundary test expectation |
| `c44ab70` | verify: update build/test logs (agent; result=GREEN) @ a1084d5 |
| `7c59b42` | feat(agent) Phase 4.1b: durable Redis ingestion queue + worker |
| `5a128b0` | verify: update build/test logs (agent; result=GREEN) @ 7c59b42 |
| `6cdd2fe` | docs: Phase 4 python half complete; defer 4.1c+ to live-stack integration |
| `ac8df39` | feat(backend) Phase 4.1c: durable Redis ingestion producer (replace fire-and-forget POST) |
| `d77f443` | verify: update build/test logs (backend; result=FAIL) @ ac8df39 |
| `095ebb0` | fix(backend) Phase 4.1c: widen updateDocument return type to ResponseEntity<?> |
| `ba23512` | verify: update build/test logs (backend; result=FAIL) @ 095ebb0 |
| `cb81013` | fix(backend) Phase 4.1c: drop ObjectMapper dependency in IngestionQueueProducer |
| `5f705dc` | verify: update build/test logs (backend; result=GREEN) @ cb81013 |
| `0953402` | feat(agent) Phase 4.1c: standalone ingestion worker CLI |
| `9ea8a29` | fix(agent) Dockerfile.agent: copy the albaraka/ package into the image |
| `d61213b` | feat(agent) Phase 4.1c: log each ingestion outcome in run_worker |
| `a667610` | feat(ingest) Phase 4.1c: worker->backend status reconciliation |
| `4607f12` | verify: update build/test logs (all; result=FAIL) @ a667610 |
| `33b61cc` | fix(agent) Phase 4.1c: make ingestion_cli tests independent of ambient env |
| `83d25c4` | verify: update build/test logs (agent; result=GREEN) @ 33b61cc |
| `f70f78e` | docs: Phase 4.1 complete + live-validated end-to-end on the stack |
| `daf2114` | feat(agent) Phase 4.2/4.3: catalog version/pin + provenance-aware cache invalidation |
| `7d9d05d` | verify: update build/test logs (agent; result=GREEN) @ daf2114 |
| `af2dd85` | feat(agent) Phase 4.2/4.3: live corpus registry wired into the worker |
| `7aee96b` | verify: update build/test logs (agent; result=GREEN) @ af2dd85 |
| `8855907` | docs: Phase 4 complete (mechanism + live wiring), verified on the stack |
| `82d71b4` | feat(agent) serving adoption 1: shared corpus collection + live catalog version |
| `a7fcd37` | feat(backend) serving adoption: greenfield AgentClient + last-N history in PublicChatController |
| `6457e24` | verify: update build/test logs (backend; result=GREEN) @ a7fcd37 |
| `08a2fc0` | feat(agent) serving adoption: flip agent container to the greenfield server |
| `7b755d5` | fix(agent) serving adoption: expose embed_query/embed_documents on GoogleEmbedder |
| `93c09c7` | docs: greenfield /invoke live-validated (grounded knowledge_gap) |
| `d54d1aa` | docs: greenfield full switchover complete + live-validated via public chat |
| `2ecec99` | feat(agent) 4.3 answer-time cache provenance |
| `c11e24e` | verify: update build/test logs (agent; result=GREEN) @ 2ecec99 |
| `9042a26` | feat(frontend) Phase 5.3: i18n infra (FR/AR/EN) + public chat + RTL |
| `885a862` | verify: update build/test logs (frontend; result=GREEN) @ 9042a26 |
| `b368ffe` | docs: i18n scoped to public chat; admin back-office stays French |
| `6639d3a` | feat(agent) Phase 5.5: greenfield streaming (SSE) — additive /invoke/stream |
| `fa52964` | verify: update build/test logs (agent; result=GREEN) @ 6639d3a |
| `b2ce46c` | feat(backend) Phase 5.5 slice 2: SSE relay /api/chat/message/stream |
| `0afa91d` | verify: update build/test logs (backend; result=FAIL) @ b2ce46c |
| `a717ecf` | fix(backend) Phase 5.5 slice 2: drop unreachable IOException catch |
| `e3f712e` | verify: update build/test logs (backend; result=FAIL) @ a717ecf |
| `2288de0` | fix(backend) Phase 5.5 slice 2: keep sessionId effectively final |
| `8b40f0c` | verify: update build/test logs (backend; result=GREEN) @ 2288de0 |
| `4454a23` | feat(frontend) Phase 5.5 slice 3: streamed token render for the public chat |
| `4b1f8bd` | verify: update build/test logs (frontend; result=FAIL) @ 4454a23 |
| `51c2d5f` | fix(frontend) Phase 5.5 slice 3: reassignable stream-fallback headers |
| `5d67682` | verify: update build/test logs (frontend; result=GREEN) @ 51c2d5f |
| `b4f62a7` | docs: Phase 5.5 streaming slices complete + host-verified GREEN |
| `93ab645` | diag(backend) surface the agent's non-2xx body (422 validation detail) |
| `747c551` | session_history |
| `ffa5284` | fix(backend): pin agent streaming client to HTTP/1.1 to stop uvicorn 422 |
| `2edb13a` | docs: Phase 5.5 streaming live-validated end-to-end; record HTTP/1.1 root cause |
| `aeecb8b` | feat(admin): governed business facts store + admin CRUD API (3.7 slice A) |
| `c770dc8` | feat(admin): taxonomy exemplar CRUD + sealed catalog releases (3.7 slice B) |
| `004a8ea` | feat(admin): re-ingest, cache-invalidation and ingestion-status endpoints (3.7 slice C) |
| `6df4e2c` | verify: update build/test logs (backend; result=GREEN) @ 004a8ea |
| `8d13ccc` | verify: update build/test logs (backend; result=GREEN) @ 6df4e2c |
| `8148b6d` | test(backend): make messages.sources_utilisees DDL portable and assert every table exists |
| `a57b71a` | ci: build on pushes to arena/** session branches; drop legacy ci/ci.yml duplicate |
| `909aa1c` | feat(frontend): admin control room — governed facts, taxonomy/releases, ingestion ops (5.4) |
| `7d3146f` | feat(observability): turn correlation, structured logs and Prometheus metrics (6.1) |
| `106a1a1` | sec: threat pass 6.2 — drop legacy chat door and scratch creds, constant-time tokens, hardening headers |
| `3eb77f0` | docs: add the 6.2 threat-pass report |
| `444f133` | ci: queue runs instead of cancelling — a cancelled push's diff is otherwise never built |
| `e7298e7` | perf(6.3): virtual threads + ReentrantLock striping on the edge, multi-worker stateless agent |
| `07a8e26` | test(7.2): RAG regression golden set run on every push |
| `890d98d` | test(7.1): coverage gates on all three layers + auth/governance/facts tests |
| `9c4718f` | ci: make the frontend test step a hard gate (7.1) |
| `260742a` | test(7.3): edge<->agent JSON-Schema contract on both sides + dockerized e2e job |
| `2c8a5f2` | test: fix expected message in GovernedFactsServiceTests |
| `fcbc49e` | ops(8.x): Redis auth end to end, prod profile, deployment guide, runbook, README |
| `df4688b` | docs(7.4): release-gate checklist assessed with current status and owner decisions |
| `f46a3ca` | feat(3.6): server-side refresh-token rotation and revocation; host test compose; LICENSE |
| `862deb1` | fix(backend): remove duplicate top-level logging key in application.yml; lint YAML for duplicate keys in CI |
| `6a8bba0` | ci: run YAML lint relative to the backend working directory |
| `c77da38` | fix(backend): use spring-boot-starter-flyway so migrations actually run on Boot 4 |
| `b3ea17a` | fix(db): make V1 baseline idempotent and baseline at 0 so the schema is created on any fresh database; drop compose e2e job from CI |
| `f58fe54` | docs: ordered manual test plan in HOST_TESTING.md |
| `8e585a0` | fix(rag): retrieval falls back to the whole corpus when the domain slice is empty; embedding backoff on 429; document domain picker; error column to text |
| `3d101b2` | documents added |
| `e99c57f` | chore: untrack accidental .env copy and runtime uploads; document the source corpus |
| `37363ec` | feat(i18n): detect document and question language automatically; answers translate across source languages |
| `674b864` | fix(answers): plain-text rendering, human source titles, no internal section names in citations |

**2026-09-04** — 13 commits

| Commit | Subject |
|---|---|
| `dcf92ae` | fix(chat): answer 'what do you offer' with a capabilities overview instead of the greeting |
| `171368d` | test: follow catalog default bump to 2026.09.2 |
| `0187e44` | feat(chat): 'what do you offer' is answered from the corpus, not a template |
| `62e81ac` | chore(lint): pin ruff rule set, fix all findings in albaraka/ and tests, make lint blocking |
| `c46bb68` | fix(overview): anchor the offer list to named terms; sweep the whole corpus with per-document cap |
| `c4447f1` | fix(overview): widen candidate pool per probe so small documents survive the per-doc cap |
| `3d1a97e` | docs: Windows developer setup without Docker (VS Code, four terminals, smoke test) |
| `38e7f37` | chore(windows): setup.bat + start.bat to install, configure and run the stack without Docker or git |
| `c700c71` | feat(agent): per-turn pipeline trace, retrieval relevance threshold, hours/contact exemplars |
| `d10db8c` | feat(monitoring): admin page explaining every chat turn; chat shows source chips |
| `23c794b` | feat(ui): professional redesign of the admin portal, login and client chat |
| `dcbce1a` | docs: code-derived description of the RAG ingestion and answer paths |
| `f341bef` | Create chat_history_2.html |

**2026-09-05** — 4 commits

| Commit | Subject |
|---|---|
| `4fc53aa` | Public site, client portal and client data model (V8) |
| `7e6febe` | Fix CI: define the missing toConversationDto mapper |
| `dbf8cd7` | nginx: never cache index.html (fixes unstyled app after rebuilds) |
| `2a6c4eb` | build: disable critical-CSS inlining — inline onload handler is blocked by CSP |

**2026-09-07** — 4 commits

| Commit | Subject |
|---|---|
| `154cb2c` | docs: contract — P0/P2 decisions, UI/UX research rule and research record |
| `c1f7348` | feat(backend): P0+P2 — financing simulation, advisor requests, news |
| `4ff989d` | feat(frontend): P0+P2 — simulator/appointment/advisor dialogs, news + prayer times |
| `baa6599` | test(backend): assert advisor contacts in fact-key order |

**2026-09-14** — 1 commit

| Commit | Subject |
|---|---|
| `ac71b73` | docs: RAGLab contract vs current agent — comparison and switch recommendation |

**2026-09-15** — 24 commits

| Commit | Subject |
|---|---|
| `1b537f0` | ci: stop artifact uploads from failing an otherwise green build |
| `c46dd12` | docs: elaborate RAGLab's 13 CLI entries before building anything |
| `328f05e` | docs: organise the RAGLab capabilities across the admin (not 13 screens) |
| `98cafaf` | docs: fold the agreed decisions into the assistant admin design |
| `6a63acd` | docs: record the corpus/transport/config decisions (D10-D13) |
| `a8477e0` | feat(admin): Assistant IA — connection & health for the RAG engine (slice 0) |
| `a5a2281` | fix(admin): read the scheme before trimming slashes in address validation |
| `6aa2e1d` | fix(admin): validate the RAG address with a pattern, not java.net.URI |
| `51c2e79` | feat(admin): quality workbench — grounded answer, retrieval, quote lookup (slice 1) |
| `bb0fece` | feat(agent): admin-selectable serving engine, effective on the next turn (slice 2a) |
| `f32bd29` | feat(monitoring): observe either engine, with per-engine KPIs (slice 2b) |
| `e3a1280` | feat(corpus): one file world, two index worlds, never blended (slice 3a) |
| `16a817e` | feat(config): models & keys per engine, and the in-house agent's config endpoints (slices 3b+5) |
| `d4543e8` | feat(index): check that the index we search is the one we built (slice 4a, entry 6) |
| `ed52961` | docs(assistant): operating guide with the exact commands to pull, run and use the section |
| `320d7b7` | feat(journal): make the journal a screen, not a table under another screen (slice 4c) |
| `a3c03a1` | Remap agent host port 8000 -> 8888 (internal port stays 8000) |
| `7f05ee2` | Complete a missing http(s) scheme on the RAG service address |
| `dc081c4` | Serialize RAG/agent payloads as plain values, not Jackson 2 JsonNode |
| `57a2f6a` | docs(raglab): how the integration was built, how it was meant to work, why it failed |
| `f0539f2` | docs(agents): target architecture for the agent gateway, and its sequencing |
| `18e3016` | test(admin): expect HTTPS addresses to keep https, not downgrade to http |
| `d3a1d86` | docs(agents): mark step 0 done, CI green |
| `b0485d2` | docs(agents): fold in the RAGLab document store contract |

**2026-09-16** — 23 commits

| Commit | Subject |
|---|---|
| `392f843` | docs(agents): record the confirmed RAGLab contract; nothing is blocked now |
| `62ff9a1` | feat(raglab): present X-Service-Token on every call to the RAG service |
| `9809ffd` | fix(raglab): read responses as String so a successful reply can be parsed |
| `7d7ae2b` | refactor(agents): one adapter contract, a registry, and no hard-coded pair |
| `2c03740` | docs(agents): steps 1 and 1b done; record the defect step 1b uncovered |
| `a5cd07f` | docs(contract): always hand the maintainer the commands to run on the Linux host |
| `e65ab55` | verify: update build/test logs (backend; result=GREEN) @ a5cd07f |
| `86749b8` | verify: update build/test logs (all; result=FAIL) @ e65ab55 |
| `ad510f5` | fix(verify): rebuild the agent image so a new dependency is installed |
| `671452f` | docs(agents): record R9, the CI path-filter blind spot |
| `99af636` | verify: update build/test logs (agent; result=GREEN) @ 671452f |
| `81e79cd` | feat(documents): allow depositing a document with no domain |
| `eae4d89` | fix(ingest): purge a document's old chunks before re-ingesting it |
| `b0dcca6` | test(catalog): exempt NON_CLASSE from the exemplar invariant |
| `14aea74` | fix(lint): drop an unnecessary noqa and use an explicit conversion flag |
| `c76e78f` | docs(agents): R6 and section 5.3 — the in-house purge now exists |
| `c00a49e` | chore(ports): in-house agent takes 8888 so RAGLab can keep 8000 |
| `f7dbdb5` | ci: stop running the Python agent in GitHub Actions |
| `db35b66` | fix(uploads): store uploads in a named volume so uid 1000 can write |
| `0bf9ed0` | fix(ports): the agent listens on 8888, not just published as 8888 |
| `8eb2905` | feat: implement RAGLab v1.2.3 agent console, Spring BFF proxy and test suites |
| `462e111` | fix(frontend): refine RAGLab ask UI rendering, citation badges and reactive real-time updates |
| `4ad123e` | feat(auth): automate service-to-service tokens and one-click dev authentication |

**2026-09-21** — 6 commits

| Commit | Subject |
|---|---|
| `6d23ec5` | feat(observability): assistant widget UX, citation sanitization, and realtime admin monitoring |
| `53c4425` | fix(backend): align ChatPersistenceService exchange signatures and LocalDateTime types |
| `0bf3578` | fix(ui): one fixed widget box, and no answer shows its sources |
| `ce4cedc` | fix(security): provenance is stripped at the edge, and the admin sees the documents |
| `1cc2fe7` | fix(admin): let the client-history endpoint reach the observability mapping |
| `1767fe8` | fix(backend): the sanitizer repairs holes, it does not restyle the answer |

**2026-09-22** — 13 commits

| Commit | Subject |
|---|---|
| `961c26d` | fix(agent): a financing question is answered by the paragraph it asks for |
| `be37966` | fix(assistant): a refusal is the bank's sentence, never the engine's document list |
| `c1e1090` | fix(frontend): the ungrounded notice asks for a confirmation, not for a citation |
| `3ace239` | verify: update build/test logs (backend; result=GREEN) @ c1e1090 |
| `4aa7296` | verify: update build/test logs (frontend; result=GREEN) @ 3ace239 |
| `1c569b7` | verify: update build/test logs (agent; result=GREEN) @ 4aa7296 |
| `1144c06` | fix(backend): a request for private data is not a gap, and a refusal says so in the log |
| `f35eb4a` | docs(agent): a universal system prompt for the reasoning engine, with its 7 pillars |
| `2cb417a` | fix(backend): history replay sanitises, so an old row cannot re-open the leak |
| `02f80f3` | feat(agent): the universal system prompt is now what the agent sends |
| `ee9c6da` | fix(backend): one-off backfill cleans stored customer answers at rest |
| `52a6a4a` | feat(deploy): promote by commit SHA, and make "which build is live?" answerable |
| `9549137` | feat(deploy): one command that updates the site and says what it did |

**2026-10-14** — 3 commits

| Commit | Subject |
|---|---|
| `cbe406a` | feat: add configurable staff access control |
| `0af3402` | docs: record access control CI results |
| `2b7535f` | fix: write frontend version metadata to built output |

**2026-10-22** — 10 commits

| Commit | Subject |
|---|---|
| `672895d` | chore: drop leaked chat transcripts, fix ruff violations |
| `34c90c2` | refactor: RAGLab is the only serving engine |
| `299857f` | fix: drop the stale history argument from settleAndEmitDone |
| `9769e32` | fix: import the AgentWire class where its nested types are named |
| `0a3f36f` | test: drop the engine table from the schema smoke test |
| `59ed875` | fix: keep the historical engine bucket so the KPI comparison stays stable |
| `ea4f566` | refactor: drop the document drop-zone, the ingestion queue and Redis |
| `de7ae17` | refactor!: remove the in-house Python service |
| `2266c5d` | fix(host-test): make the documented local run actually work |
| `904bfed` | docs: JWT_SECRET has a public dev default, state that instead of a refusal |

**2026-10-23** — 12 commits

| Commit | Subject |
|---|---|
| `a35b382` | feat(customers): a core-banking-shaped customer model, a dev population, and a masked context for the assistant |
| `9474411` | ci: apply every migration to a real Postgres, and assert the role boundaries |
| `450d2e6` | docs: report on customer personal data for the bank's developers |
| `e2e257b` | build(sonar): make the repository scannable by SonarQube |
| `e29866c` | fix(db): V15 dropped the old account-type constraint AFTER renaming rows |
| `a4a448b` | ci: fix the legacy-clients insert and make migration failures readable |
| `376df4c` | docs(host-test): what to do when a migration fails and the backend will not start |
| `005ea6f` | Console RAGLab: instrument-strip redesign, poll lifecycle moved to the screen |
| `ccb6050` | Console RAGLab: put the five screens back on the bank's tokens |
| `9bbb9f7` | chore: close the holes the demo fixtures left open, and drop dead weight |
| `e324b2b` | chore: refer to the maintainer by role, and take a real name off a public page |
| `61f9904` | chore: drop the launcher nobody can run and the rules for files that are gone |

