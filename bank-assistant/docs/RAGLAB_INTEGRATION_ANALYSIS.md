# RAGLab integration — how it was built, how it was meant to work, and why it failed

**Date:** 2026-09-15 · **Scope:** analysis only, no code changed
**Branch:** `arena/01a0a57f-bank-assistant` @ `dc081c4`
**Companions:** [`ADMIN_AGENT_UX.md`](./ADMIN_AGENT_UX.md) (the agreed design),
[`RAGLAB_SWITCH_REVIEW.md`](./RAGLAB_SWITCH_REVIEW.md) (engine comparison),
[`RAGLAB_13_ENTRIES.md`](./RAGLAB_13_ENTRIES.md) (the external contract)

---

## 0. The answer, first

**The RAGLab link is not broken. It is built, coherent, and about 90 % complete — and it has
never once been pointed at a real RAGLab.**

Two separate things ended in failure, and they are worth keeping apart:

| | What failed | Where the proof is |
|---|---|---|
| **1. The build** | `mvn verify` is red on **one assertion** in the RAG address validator: the test asserts that `HTTPS://10.20.30.40:8000` normalises to `http://10.20.30.40:8000` — a silent TLS downgrade. The code correctly returns `https://…`. **The test is wrong, not the code.** | CI run `34973931480`, GitHub issue #18, `AgentEndpointServiceTests.java:33` |
| **2. The wiring** | `RAGLAB_URL` ships **empty**. Out of the box the console says *« Aucune adresse n'est configurée »* and customer chat stays on the in-house agent. Nothing was ever connected, so nothing could fail at runtime. | `.env.example:59`, `application.yml:101-102` |

The tail of the work is four red builds — all in the same test class
(`AgentEndpointServiceTests`), all around the address validator being reworked three times
(issues #15 → #16 → #17 → #18). The last two pushes are consecutive and both red.

---

## 1. What RAGLab is (and is not)

RAGLab `1.0.0` is an **external** RAG service — not a module in this repo. It is an HTTP
service on its own host, with its own ChromaDB index, its own CLI, and **no authentication**.
This repo only ever talks to it over the network.

Four of its endpoints are used:

| RAGLab endpoint | What it gives | Called by |
|---|---|---|
| `GET /health` | liveness + whatever it reports about its own index and models | health screen, engine list, corpus screen, config screen |
| `POST /answer` | one grounded answer: `status`, `answer`, `claims[{text, evidence[{source_id, quote}]}]`, `sources[]` | **customer chat** (when RAGLab serves) + quality workbench |
| `POST /search` | ranked chunks for a question — embed, no chat model | quality workbench, `explain()` |
| `POST /chunks/search` | substring lookup: *"could this quote ever validate?"* — free, no model | quality workbench |

Its two headline properties, and the reason it was worth wiring at all: **chunk-level citations
with a verbatim quote per claim**, and **machine-readable refusal reasons**.

The costs it carries, which the design chose to accept and disclose rather than hide: no
token streaming, no conversation history, no governed facts, no per-document versions, no PII
scrub on output, no numeric gate, whole-corpus re-ingestion, and one replica.

---

## 2. How it was *supposed* to work

The design is recorded in `ADMIN_AGENT_UX.md` (decisions D1–D13, agreed 2026-09-15). Four ideas
carry the whole thing:

1. **Two engines, one contract.** RAGLab is dressed as a `ServingAgent` — the same interface the
   in-house pipeline satisfies. Quotas, metering, persistence, SSE framing and monitoring never
   learn which engine answered. The choice is a deployment decision, not a rewrite.
2. **The switch is an operator action, effective on the next turn.** The router resolves the
   engine *per call*, so switching needs no restart and no cache clear. It is SUPER_ADMIN-only,
   requires a written reason, and is journalled — because it changes what customers are told.
3. **Say what you cannot do.** RAGLab is configured by its CLI, not by this console — so its
   config card is read-only and shows only what the service reports. Its index cannot be rebuilt
   from here — so the screen offers a *declaration* button (who/when/why) instead of a trigger
   that would be a fiction.
4. **Provenance, never invention.** Every fact read out of the service goes through a tolerant
   lookup with several plausible field names; an absent fact stays `null`. `0 chunks` and
   *"we cannot tell how many chunks"* are opposite statements, and a console that confuses them
   is worse than one that says nothing.

And one deliberate guard: **every state-changing action is gated by the SUPER_ADMIN role, not by
an environment kill switch** (D5). Reads are open to all staff; panels that spend model budget
(embed + chat) sit behind a stricter tier.

---

## 3. The backend

### 3.1 Layer map

```
  AdminAgentController          PublicChatController        AdminMonitoringController
  /api/admin/agent/**           /api/public/chat/**         /api/admin/monitoring/**
  READ · SPEND · WRITE                 │                            │
         │                             │                            │
         ├── RagLabClient ─────────────┤                    MonitoringKpiService
         │   (the ONLY door out)       │                    (attribution by RAGLAB_ prefix)
         │                             │
         │                      AgentRouter ──► resolves per call
         │                        ├── AgentClient          (LEGACY)
         │                        └── RagLabServingAgent   (RAGLAB) ── implements ServingAgent
         │                                    │
         │                              RagLabViews  (tolerant payload → typed records)
         │                              RagLabFacts  (tolerant number/string lookup, null not 0)
         │
         ├── AgentEndpointService   where RAGLab lives      → V11 agent_endpoint
         ├── AgentProviderService   which engine serves     → V12 agent_engine
         ├── CorpusIndexService     two index worlds        → never blended
         ├── EngineConfigService    models & keys per engine
         └── AgentAdminAuditService the journal             → V11 agent_admin_audit
```

### 3.2 The files

| File | Lines | Role |
|---|---:|---|
| `services/RagLabClient.java` | 237 | The only place that talks to the service. Three `RestTemplate`s with three read timeouts (5 s probe/lookup, 20 s search, 90 s answer). Health returns failure **as data** (`Health.down`), never an exception. Non-2xx → `RagLabException` carrying a machine `reason` dug out of `reason` / `detail.reason` / `error` / `detail.code`. |
| `services/RagLabViews.java` | 250 | Payload → typed records (`AnswerView`, `ClaimView`, `SourceView`, `HitView`, `ChunkMatchView`). Every getter tolerant, every field tried under several aliases, **never throws** — a new service version must not blank the screen. Plain static, no Spring, so it is testable against stored payloads. |
| `services/RagLabFacts.java` | 72 | Facts out of a service whose vocabulary we do not control: tries each name at the top level and inside `index` / `corpus` / `stats` / `status`; absent returns `null`, never `0`. |
| `services/RagLabServingAgent.java` | 274 | **The adapter.** Implements `ServingAgent`. Provider `RAGLAB`. Admits two gaps honestly: no token stream (emits one `onDelta`, then `onDone`) and no pipeline trace (`explain()` probes retrieval and returns an empty step list). Maps an answer to the edge's own `AgentReply`, builds the replayable trace JSON. |
| `services/ServingAgent.java` | 40 | The contract: `provider()`, `invoke()`, `invokeStream()`, `explain()`. Documents that callers must not assume more than one `onDelta`. |
| `services/AgentRouter.java` | 56 | Sends each turn to the engine serving **right now** — resolved per call, on purpose. |
| `services/AgentProviderService.java` | 144 | Which engine serves: newest DB row wins, else `AGENT_PROVIDER_DEFAULT`. Accepts the spellings an operator types (`INTERNE`, `EXTERNE`, `RAG_LAB`, …). **Refuses to hand customers to RAGLab when no address is configured.** Mandatory reason, journalled. |
| `services/AgentEndpointService.java` | 213 | Where the service lives: DB row wins, else `RAGLAB_URL`, else nothing. Regex validation (not `java.net.URI`), scheme completion for bare `host:port`, port range check, trailing-slash trim, host:port display. |
| `services/CorpusIndexService.java` | 189 | The two index worlds, **never averaged**: our `documents` table, the in-house ingestion journal, and whatever RAGLab reports. |
| `services/EngineConfigService.java` | 116 | Models & keys per engine. In-house editable; RAGLab read-only ("configured by its CLI"). |
| `services/MonitoringKpiService.java` | 125 | Per-engine KPIs. Attribution read from the **intention prefix** `RAGLAB_`, not a new column — one rule, one place, works for turns already stored. Rates are `null`, not `0`, when an engine served nothing. |
| `controller/AdminAgentController.java` | 384 | The whole `/api/admin/agent/**` surface, with three authority tiers. |
| `controller/PublicChatController.java` | 546 | The serving path: `agentRouter.invoke` / `invokeStream`. |
| `dto/JsonPayload.java` | 30 | Jackson 2 → 3 boundary (see §5.3). |
| `db/migration/V11__agent_admin.sql` | — | `agent_endpoint`, `agent_admin_audit`. No seed row on purpose: "no row" = "use the deployed default". |
| `db/migration/V12__agent_engine.sql` | — | `agent_engine`, with a `CHECK (provider IN ('LEGACY','RAGLAB'))`. |

Tests: `RagLabViewsTests` (7), `RagLabServingAgentTests` (8), `RagLabFactsTests` (4),
`AgentProviderServiceTests` (5), `MonitoringKpiServiceTests` (5), `AgentEndpointServiceTests` (7).
67 backend tests total; **1 fails.**

### 3.3 How a RAGLab answer becomes a customer reply

`RagLabServingAgent.toReply()` is the hinge:

```
POST /answer  →  RagLabViews.answer(payload)
                   ├─ status  answered | refused | greeting
                   ├─ claims[] each with evidence[] (source_id + verbatim quote)
                   └─ sources[] (source_id, document, chunk_id, heading)
              →  text = answer, else joined claim texts, else the standard refusal
              →  grounded = status ∈ {answered, greeting}      ragUsed = status == answered
              →  intention = RAGLAB_ANSWERED | RAGLAB_REFUSED_<REASON> | RAGLAB_GREETING
              →  traceJson = status, reason, seconds, cached, claims+quotes, sources,
                             uncited_source_ids        ← what monitoring replays
```

The `intention` string is doing double duty: it is the **only** engine attribution mechanism, and
it is what turns a refusal reason into a KPI (`RAGLAB_REFUSED_INSUFFICIENT_EVIDENCE` →
`insufficient_evidence`).

**Two honest caveats are written into the code, not buried.** `toReply`'s Javadoc states that
nothing re-verifies the text — the numeric gate and the PII scrub the in-house pipeline applies
do not exist on this path — and the switch screen says so before an admin chooses it.

---

## 4. The frontend

### 4.1 Layer map

| File | Lines | Role |
|---|---:|---|
| `services/agent-admin.service.ts` | 502 | The typed client for `/api/admin/agent/**`. Mirrors the backend contracts exactly (`AnswerView`, `RetrievalHit`, `RagLabIndex`, `IndexSanity`, `EngineConfigRagLab`…). Sends **camelCase**; the snake_case names of the RAG service are applied server-side, so the external vocabulary stops at the edge. |
| `pages/admin-agent-engine/` | 187 + 184 | **C1** — which engine serves, both engines with live liveness, the switch, and *"ce que la bascule change vraiment"*: no streaming, no governed facts, no versions, no PII scrub, whole-corpus rebuild — and, in exchange, finer citations. |
| `pages/admin-agent-health/` | 171 + 205 | **C2** — where the service is (with provenance), is it answering, latency, raw `/health` payload, and the journal. Address editing is SUPER_ADMIN-only. |
| `pages/admin-agent-quality/` | 266 + 249 | **C3** — the workbench: grounded answer (claims + verbatim quotes), retrieval probe, quote lookup, chat. |
| `pages/admin-agent-corpus/` | 226 + 213 | **C4** — one file world, two index worlds, never blended; rebuild in-house, *declare* RAGLab's. |
| `pages/admin-agent-config/` | 183 + 161 | **C5 + C6** — models & keys, in-house editable, RAGLab read-only. |
| `pages/admin-agent-journal/` | 71 + 102 | **C8** — who changed what and why; server-side filtering, filter values read from the journal. |
| `pages/admin-monitoring/` | 265 + 285 | **C7** — engine selector, per-engine KPIs, per-engine detail pane. |

Routes: `/admin/agent` → `engine` (default), `health`, `journal`, `config`, `corpus`, `quality`.
All lazily loaded, all behind `AuthGuard` + `StaffGuard`.

### 4.2 How it was meant to feel

An operator opens **Moteur de l'assistant**, sees both engines and whether each answers, reads
what switching would cost them, types a reason, and confirms. The next customer question is
answered by the other engine — no restart, no deploy. Everything they did is journalled with
their name.

---

## 5. Why it failed

### 5.1 The build is red on one assertion

```
[ERROR] Tests run: 67, Failures: 1, Errors: 0
[ERROR]   AgentEndpointServiceTests.acceptsAnAddressAndPortAndDropsTheTrailingSlash:33
          expected: <http://10.20.30.40:8000> but was: <https://10.20.30.40:8000>
[ERROR] Failed to execute goal ...maven-surefire-plugin:3.5.6:test
```

`backend/src/test/java/.../AgentEndpointServiceTests.java`, lines 32–33:

```java
assertEquals(PROD, AgentEndpointService.normalize("HTTP://10.20.30.40:8000"));   // ✅
assertEquals(PROD, AgentEndpointService.normalize("HTTPS://10.20.30.40:8000"));  // ❌ PROD is http://
```

Line 33 is a copy-paste of line 32 with `HTTP` → `HTTPS` and the expected value left at `PROD`
(which is `http://10.20.30.40:8000`).

**The code is right; the assertion is wrong.** `AgentEndpointService.normalize()` captures the
scheme with `((?i:https?))`, lower-cases it, and keeps it. `HTTPS://10.20.30.40:8000` correctly
becomes `https://10.20.30.40:8000`. The test asks for a **silent TLS downgrade**: an operator who
types `HTTPS://` would have their address quietly rewritten to plaintext. Fixing the code to
satisfy this test would introduce a security defect in the path that carries customer banking
questions to an internal service.

The comment two lines above says what was intended — *"a scheme an operator typed in capitals
[is] legitimate"* — i.e. case normalisation, exactly what the code does.

### 5.2 Four red builds, all in the same test class

Every backend failure in this session's history is in `AgentEndpointServiceTests` — the RAG
**address validator**. The two most recent pushes are consecutive and both red.

| Run | Commit | Failing assertion (verbatim) |
|---|---|---|
| `34948870446` | feat(admin): Assistant IA — connection & health (slice 0) | `:35` — `Adresse invalide : 10.20.30.40:8000` — the rejection message did not carry the "hôte" hint the test looked for |
| `34956418029` | fix(admin): read the scheme before trimming slashes | `:42` — `Adresse invalide : http://` — same shape, one case further along |
| `34972885216` | Complete a missing http(s) scheme on the address | `:33` — `expected: <http://10.20.30.40:8000> but was: <https://10.20.30.40:8000>` |
| `34973931480` | Serialize RAG/agent payloads as plain values | `:33` — same |

The validator was reworked three times in one session and the test never fully caught up. Each
code fix was correct; the last one unmasked an assertion that had been encoding the old
behaviour. Issues #15–#18 are all still open.

### 5.3 The good news hiding in the last commit

HEAD (`dc081c4`) fixed a **real latent bug**, and it is worth knowing about because it is the
one defect that would only ever have appeared *after* a successful link:

> Spring Boot 4's MVC layer serialises responses with Jackson 3 (`tools.jackson`), which cannot
> serialise `com.fasterxml` types. Any DTO carrying a `JsonNode` field returns **500
> "Type definition error"** the moment the payload is non-null — i.e. the moment the external
> service is actually reachable.

In other words: **while RAGLab was unreachable, every RAGLab screen worked. The day it was wired
up, `/health`, `/answer`, `/search`, `/chunks/search` and both config cards would have started
500-ing.** `JsonPayload.toPlain()` converts nodes to plain values at the DTO boundary.

That is the signature of this whole episode: the integration was built and tested against a
service that was never there, so the failures are concentrated exactly where reality would have
been touched.

---

## 6. Findings I did not fix

Listed, not touched (AGENTS.md §1 — scope discipline).

| # | Finding | Why it matters | Effort |
|---|---|---|---|
| 1 | **`AgentEndpointServiceTests:33` asserts a TLS downgrade** | Blocks CI; would become a security defect if "fixed" the other way | 1 line |
| 2 | **The backend sends no `X-Service-Token`, so every RAGLab call will 401 the moment a token is configured.** `RAGLAB_TOKEN` is declared in `.env.example`, `.env.docker.example` and `docker-compose.yml` and passed into the backend container, but there is no `app.agent.raglab.*` property in `application.yml`, and `RagLabClient` sets only `Content-Type`. **Updated 2026-09-15:** RAGLab 1.2.3 *does* require `X-Service-Token` on every endpoint, and accepts `RAGLAB_TOKEN` as an alias — so our existing wiring lights up with no compose change. See `AGENT_GATEWAY_PLAN.md` §6.1, which also records where the RAGLab team's correction of this finding does and does not hold. | Every RAGLab screen built to date — health, workbench, corpus, config — starts failing the day someone sets a token. | ~30 min (step 1b of the plan) |
| 3 | **18 open "CI FAIL" issues, never closed** | Real signal is drowning in noise | ~10 min to triage/close |
| 4 | **RAGLab turns ignore conversation history.** `RagLabServingAgent.invoke` accepts `history` and never reads it — follow-ups are answered as standalone questions. Documented in the code, but no screen says it. | A customer asking "et pour une voiture ?" gets an answer with no memory of the previous turn | Doc/screen wording |
| 5 | **No PII scrub and no numeric gate on the RAGLab path.** Disclosed on the switch screen, which is the right call — but it is a disclosure, not a mitigation. | A chunk containing an example RIB/IBAN or a real contact would be echoed verbatim into a claim; RAGLab's whole premise is verbatim quoting | Product decision |
| 6 | **RAGLab's index state depends on guessed field names.** The corpus screen reads `documents`/`chunks`/`embeddings`/`model` from `/health` under several aliases. Tolerant by design, but until `/openapi.json` is seen it may show *« non communiqué »* everywhere. | The screen is honest about it; it just is not informative yet | Needs the contract |
| 7 | **`estimatedTokens = text.length() / 4`** on the RAGLab path — a rough proxy for quota, same as the in-house agent. | Quota metering is approximate | Acceptable, noted |

---

## 7. Recommended next step

**Fix the test, not the code** — one line, `AgentEndpointServiceTests.java:33`:

```java
assertEquals("https://10.20.30.40:8000", AgentEndpointService.normalize("HTTPS://10.20.30.40:8000"));
```

That should turn CI green (the other 66 tests pass, and `RagLabServingAgentTests`,
`RagLabViewsTests` and `RagLabFactsTests` — the ones that actually exercise the RAGLab mapping —
are all green).

Then, before anything is wired for real, finding **#2** is the one I would do next: a token the
operator believes is protecting the RAGLab hop but is not is worse than no token.

**Say the word and I will do either or both**, plus close the stale CI issues.

---

## Appendix — how to see the state yourself

```bash
# The red build, and the exact failure (logs are reposted as issues because the
# sandbox cannot reach GitHub's log CDN — see .github/workflows/ci.yml)
gh run view 34973931480 --json jobs
gh issue view 18

# The link is not configured out of the box
grep -n 'RAGLAB_URL' .env.example backend/src/main/resources/application.yml

# Every place RAGLab appears
grep -ril raglab . --exclude-dir=.git --exclude-dir=node_modules
```
