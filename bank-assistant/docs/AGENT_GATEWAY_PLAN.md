# Agent gateway — target architecture and sequencing

> **Historical (2026-10-22).** This plan describes the in-house Python agent and the gateway
> that fronted it. Both were removed: RAGLab is the only engine. Kept for the reasoning, not
> as a description of the code.


**Date:** 2026-09-15 · **Status:** design agreed in discussion, **awaiting go-ahead — no code changed**
**Branch:** `arena/01a0a57f-bank-assistant` @ `57a2f6a`
**Companions:** [`RAGLAB_INTEGRATION_ANALYSIS.md`](./RAGLAB_INTEGRATION_ANALYSIS.md) (what exists today and why it's red),
[`ADMIN_AGENT_UX.md`](./ADMIN_AGENT_UX.md) (the current design this supersedes in part),
[`RAGLAB_13_ENTRIES.md`](./RAGLAB_13_ENTRIES.md) §8 (what we still need from RAGLab)

---

## 0. The one-paragraph version

We are turning the backend into a proper **agent gateway**: a registry of agents, one adapter per
agent, one document store it owns, and a feed that pushes documents to whichever agent is active.
RAGLab stops being a second-class read-only passenger and becomes a real agent like the in-house
one. Everything about the *request* path (front → back → agent) is in our control and can be built
now. Everything about the *document* path to RAGLab is **blocked on RAGLab gaining a document
endpoint**, which we have decided to ask for rather than work around. So the plan is sequenced to
deliver the gateway and the admin experience first, with the RAGLab feed landing behind an
interface that is already written and already working for the in-house agent.

---

## 1. Decisions taken (2026-09-15)

| # | Decision | Consequence |
|---|---|---|
| **A1** | **The backend is the gateway.** No new service in the request path. | It already is the only edge; generalise it into a registry + adapter layer. No new hop, no new failure mode, no re-implementing auth/quota/persistence. |
| **A2** | **Documents are pushed to the agent over HTTP by the backend.** Not a shared filesystem mount. | Supersedes decision D10 in `ADMIN_AGENT_UX.md` (shared volume at RAGLab's `data_dirs`). The backend owns the truth; agents only consume. |
| **A3** | **Strictly one agent at a time.** The active agent receives documents and configuration; the other is dormant. | No fan-out — the feed has exactly one target. But the dormant agent's index decays. |
| **A4** | **We ask RAGLab for a document endpoint.** No sidecar, no mount workaround. | Cleanest end state: the backend PUTs straight to the agent. **The RAGLab feed is blocked on a third party with no agreed timeline.** |
| **A5** | **Dormant = not fed, cannot be tested.** The quality workbench only ever exercises the active agent. | You cannot evaluate RAGLab before making it active. See §7, risk R1. |

### Two consequences of A3 worth stating plainly

- **The dormant agent's index decays.** Flip the switch after three weeks and the newly-active
  agent answers from a three-week-stale corpus, or an empty one if it was never fed. Handled in the
  UI, not by changing the rule: the engine screen badges each agent's index state
  (*à jour / en retard / vide*), and activating an agent offers an explicit **« alimenter et
  indexer »** action with real progress. Switching stays one click; catching up is a separate,
  confirmed, journalled action.
- **A switch is a cold start.** Activating an agent is not "instant" in the sense of *ready* — it
  is instant in the sense of *chosen*. The console must say so, and must not let an admin activate
  an agent whose index is empty without saying what the first customer will get.

---

## 2. Where we are today

| Target | Today | Gap |
|---|---|---|
| Gateway brokers front → back → agent | ✅ | `AgentAdapter` is the one contract; `AgentRegistry` resolves the active engine per call from every adapter bean. An engine now joins by being a bean, not by being named. |
| Two agents behind one contract | ✅ | `ServingAgent` + `AgentRouter` already resolve per call. `RagLabServingAgent` is a real adapter. |
| Admin picks the active agent | ✅ | `AgentProviderService`, SUPER_ADMIN, mandatory reason, journalled, effective next turn. |
| Backend owns the documents | ✅ | `documents` table + `uploads/`. |
| Backend pushes documents to the agent | ⚠️ | **In-house: works** (Redis queue + shared mount). **RAGLab: contract received 2026-09-15** — `POST/GET/DELETE /documents`, per-document and idempotent. Not yet implemented. See §5. |
| Admin configures each agent's profile | ⚠️ | In-house editable (models + keys). **RAGLab read-only by design** ("configured by its CLI"). |
| Clean, zero-friction admin UI | ⚠️ | Five screens exist and are thoughtful, but RAGLab's rebuild is a *declaration* button and its index state often reads *« non communiqué »*. |
| Green build | ✅ | Was 1 red test (`AgentEndpointServiceTests:33`, a TLS-downgrade assertion). **Fixed 2026-09-15**, CI green. |

**Net:** the request path is ~80 % there, the admin experience ~60 %, the document path to RAGLab 0 %.

---

## 3. The shape

```
Browser ──── /api ────► Spring Boot  (the gateway — already the only edge)
                          │
                          │  ┌─────────────────────────────────────────────┐
                          ├──┤ AgentRegistry                               │
                          │  │   registered · enabled · which is ACTIVE    │
                          │  │   (one at a time — A3)                      │
                          │  └───────────────┬─────────────────────────────┘
                          │                  │
                          │      ┌───────────┴────────────┐
                          │      ▼                        ▼
                          │  AgentAdapter: LEGACY    AgentAdapter: RAGLAB
                          │   /invoke                 /answer
                          │   /invoke/stream          /search
                          │   /explain                /chunks/search
                          │   /config                 /health
                          │   /index/state            /profile      ← read-only until
                          │                           /documents    ← contract in hand (§5)
                          │      │                        │
                          │      ▼                        ▼
                          │   in-house agent          RAGLab (external)
                          │   (pgvector)              (own ChromaDB, owns its docs)
                          │
                          ├── DocumentStore — the truth: documents + uploads/
                          │      │
                          │      └── DocumentFeed ──► pushes to the ACTIVE agent only
                          │             ├── LEGACY adapter: Redis queue (works today)
                          │             │                 no unpublish — no-op upstream
                          │             └── RAGLAB adapter: POST/GET/DELETE /documents
                          │                   idempotent (content hash) · per-doc status
                          │                   pending → indexed → stale
                          │
                          └── one admin API: /api/admin/agents/**

              Both engines report per-document state in ONE vocabulary:
              PENDING · INDEXING · INDEXED · STALE · FAILED · UNKNOWN   (§5.2)
```

**The point of the adapter contract:** adding a third agent later is one new class plus a row, not a
change to the controller, the persistence, the quota, or a single screen.

---

## 4. Sequencing

Ordered so that nothing in our control waits on anything outside it.

| # | Step | Blocked? | Effort |
|---|---|---|---:|
| ~~0~~ | ~~**Turn CI green**~~ — **DONE 2026-09-15** (`18e3016`). The test asserted that `HTTPS://10.20.30.40:8000` normalises to `http://…`; the code correctly returns `https://…`. Test fixed, not the code. CI green. | no | 15 min |
| ~~1~~ | ~~**Adapter contract + registry.**~~ **DONE 2026-09-15** (`7d7ae2b`). `ServingAgent` → `AgentAdapter`, which gains `health()`, `url()` and `label()`; `AgentClient.Health` and `RagLabClient.Health` collapse into one `AgentAdapter.Health`; `AgentRegistry` resolves the active engine per call; `AgentRouter` keeps the serving seam but stops implementing the interface, or it would be collected into the registry's own `List<AgentAdapter>`. CI green. | no | 1 d |
| ~~1b~~ | ~~**Send `X-Service-Token`.**~~ **DONE 2026-09-15** (`62ff9a1`, `9809ffd`). Header on every call, probe included; `RAGLAB_TOKEN` read and sent with no compose change. CI green, 71 tests. | no | 30 min |
| 2 | **`DocumentFeed` interface + LEGACY implementation.** Define the contract (`publish`, `unpublish`, `rebuild`, `state`) plus a capability declaration (§5.3). Implement it against the in-house agent using the queue path that already works. No screen yet. | no | 1 d |
| 3 | **RAGLAB feed adapter.** Contract confirmed (§5, §6). Implement `publish`/`unpublish`/`state` against `POST`/`GET`/`DELETE /documents`, **plus the batch ingest flow**: push without `?index=true`, then one `POST /ingest`, then poll `/ingest/status`. `409` becomes a state, not an exception. Idempotent by construction, so no retry ledger. | no | 1 d |
| 4 | **Admin UI/UX: manage agents.** One screen listing every agent with state (*en service / dormant*), reachability, index freshness, and the activate action with its consequences stated. Replaces the current two-engine screen; N agents, not two. | no | 1.5–2 d |
| 5 | **Admin UI/UX: per-agent settings.** One settings surface per agent, rendered from what each adapter declares it supports. In-house: models + keys, editable. RAGLab: profile, currently read-only. | ⛔ partly | 1 d |
| 6 | **RAGLab profile editing.** `/openapi.json` ships on every deployment (OpenAPI 3.1.0, 18 paths, 1.2.3), so the real field names are in hand. Also retires the guessed aliases in `RagLabViews`/`RagLabFacts` — which is what makes the corpus screen stop saying *« non communiqué »*. | no | 1 d |
| 7 | **End-to-end verification against a live RAGLab.** We stand it up ourselves — `docker compose up --build` from their repo root, mapping `8000:8000`. | ⛔ we run it | 0.5–1 d |

**~5–6 days of work that nothing outside can hold up.** All five open questions were answered on
2026-09-15 (§6), so **nothing on this list waits on RAGLab any more.** Step 7 waits only on us
standing an instance up.

---

## 5. The RAGLab document store (contract received 2026-09-15)

RAGLab gained a document store it owns, driven entirely over HTTP. This is the piece A4 was
waiting for, and **it is a better contract than the one we designed around.**

| Endpoint | Semantics |
|---|---|
| `POST /documents` | Push one doc — multipart upload or JSON `{id?, filename, content, content_encoding: text│base64}`. **Content-hash versioning**: same id + identical bytes = no-op, so a feed may retry freely; different bytes = version bump. `?index=true` starts the ingest job immediately. |
| `GET /documents`, `GET /documents/{id}` | Per-doc status: `pending` → `indexed` (chunks in the active collection, ingest newer than the doc) → `stale` (doc changed since last ingest — **old chunks still serving, surfaced honestly**). |
| `DELETE /documents/{id}` | Removes the file **and purges its chunks from every collection** — the index stays truthful with no re-ingest. |

### 5.1 What this changes

- **Step 3 is unblocked.** The RAGLab feed adapter now has a real contract to write against.
- **Per-document granularity now exists.** RAGLab could previously only ingest a whole corpus, so
  publishing one document meant re-embedding everything. It no longer does.
- **Decision D11 is obsolete.** `ADMIN_AGENT_UX.md` D11 says a document "is seen by RAGLab at its
  next whole-corpus rebuild". That is no longer true; the design should stop saying it.
- **The out-of-band rebuild declaration is nearly obsolete.** `POST /documents?index=true` makes
  publishing a real action, so `CORPUS_REBUILD_RAGLAB_DECLARED` and its "declare that you rebuilt"
  button only still earn their place for a **whole-corpus** rebuild — see question 4 in §6.
- **Idempotency removes a whole class of bookkeeping.** "Same id + identical bytes is a no-op"
  means the feed needs no dedup state and no retry ledger. Compare the in-house path, where
  re-ingesting appends, because its `delete_source` is a no-op pending slice 4.3
  (`albaraka/ingest.py:364`).

### 5.2 The two status vocabularies can be unified

This is the real prize for the corpus screen — both engines can be made to speak one language.

| Unified | RAGLab | In-house (today) |
|---|---|---|
| `PENDING` | `pending` | `statutIndexation = EN_ATTENTE` |
| `INDEXING` | (subsumed by `pending`) | `EN_COURS` |
| `INDEXED` | `indexed` | `INDEXE` |
| `STALE` | `stale` — native | **must be derived** (doc updated after its last successful ingest) |
| `FAILED` | — | `ERREUR` |
| `UNKNOWN` | not reachable / not configured | not reachable |

`STALE` is the one that costs work on the in-house side. `UNKNOWN` is the one that must survive:
it is what the screen shows when an engine cannot be asked. **Neither may collapse to `INDEXED`** —
a stale index that reports itself as current is exactly the failure this screen exists to catch.

### 5.3 Unpublishing — the asymmetry has narrowed

**Both engines can now remove a document's chunks.** RAGLab's `DELETE /documents/{id}` purges
chunks from every collection with no re-ingest. The in-house `delete_source` used to be a
documented **no-op pending slice 4.3** (`albaraka/ingest.py:364`) — and was never even called:
`ingestion.py` only ever did `indexer.upsert(chunks)`, so re-ingest was append-only.

**Fixed 2026-09-16** (`eae4d89`). `delete_source` now deletes from `langchain_pg_embedding`,
scoped three ways so it cannot reach another collection's vectors — by collection name, by
`collection_id`, and by `cmetadata->>'source'` (jsonb). Ingestion purges before it upserts, and a
purge that raises fails the job rather than falling through to a silent append.

Two consequences well beyond unpublishing:

- **A re-labelled document can finally be re-indexed.** The domain is stamped onto each chunk at
  ingestion time, so changing it used to leave the old chunks in place and the document went on
  answering under both domains. This is what "file it later" in §2 was waiting for.
- **The corpus rebuild was growing the index, not rebuilding it.** `rebuildLegacy()` enqueues
  every document individually down the same append-only path, so every press added chunks.

**Verification gap:** the delete SQL has no automated test (there is no pgvector in CI). It is
checked by `scripts/purge_chunks.py` against one document at a time, dry-run by default.

**Still open:** the purge removes chunk vectors only; the parent-document store
(`parent_doc_store/<id>.pkl`) is untouched. So in-house `unpublish()` means "removed from the
index", not "erased everywhere", and it is a purge rather than RAGLab's single idempotent call.
`DocumentFeed` should therefore still **declare `unpublish` as a capability** (option **a** below)
rather than promising it — but the gap it exists to hide is now much smaller than when §5.3 was
first written, and option **c** is no longer deferred work.

| Option | Status |
|---|---|
| **a. Capability declaration (recommended).** An adapter declares what it supports (`unpublish`, `perDocumentIndex`, `rebuild`); the UI renders only what the active agent can actually do. | Still recommended — see the caveat above. |
| **b. Best-effort + honest status.** `unpublish()` always exists and reports what happened. | Rejected: risks a "retirer" button that silently does nothing. |
| **c. Fix in-house `delete_source` first** (its slice 4.3). | **Done** — `eae4d89`. |

### 5.4 Indexing is a batch operation — confirmed

`?index=true` exists, but **it must not be the default**. Ingest jobs run one at a time:

- `POST /ingest` while a job runs → `409 ingest_already_running` — **rejected, not queued**.
- A push with `?index=true` while a job runs → **the push still succeeds** (the document is stored)
  and `notes[]` records that indexing did not start.
- While a job runs, index reads return `409 ingest_in_progress`.

So the publish flow is: **push N documents (idempotent, no `?index=true`), then one
`POST /ingest`, then poll `/ingest/status`.** This reverses the earlier recommendation in this
section, which assumed per-publish indexing.

Two things that are easy to get wrong:

- **`409` is a state, not an error.** Today `RagLabClient.httpFailure` turns any non-2xx into a
  `RagLabException`, mapping `>= 500` to 502. A `409` must become a first-class outcome the feed
  and the corpus screen can render (*indexation en cours*) rather than a red banner.
- **A publish can succeed while its index does not.** The document is stored and will be picked up
  by the next ingest. The screen must read *déposé · en attente d'indexation*, never *indexé*.

### 5.5 Whole-corpus rebuild exists — the declaration button goes away

`POST /ingest?reset=true` deletes the collection and rebuilds everything (pushed documents plus
the repo corpus). It is also the remedy named by the stale-fingerprint error.

So `CORPUS_REBUILD_RAGLAB_DECLARED` and its "declare that you rebuilt it" button are **deleted**,
replaced by a real rebuild action. That removes the one piece of the current design that existed
only because the console had no trustworthy way to trigger a rebuild.


## 6. The RAGLab contract, confirmed (2026-09-15)

All five open questions were answered, and the answers were verified live against a running
instance. This section is now the integration spec.

| # | Question | Answer | Consequence for us |
|---|---|---|---|
| **1** | Is `/documents` authenticated? | **Yes** — `X-Service-Token` on **every** endpoint, not just `/documents`, when a token is configured. Constant-time compare, `401 unauthorized` JSON otherwise, CORS preflights exempt. `RAGLAB_SERVICE_TOKEN` since 1.1.0; **1.2.3 also accepts `RAGLAB_TOKEN`** as an alias. | Our `RagLabClient` must send the header on every call. **It currently sends none** — the moment a token is configured, every RAGLab call 401s. Must be fixed before step 3 ships. See §7 R3. |
| **2** | Do ingest jobs serialise? | **Yes — one at a time, and a second is *rejected*, not queued** (`409 ingest_already_running`). | Batch: push N docs, then one `POST /ingest`, poll `/ingest/status`. See §5.4. |
| **3** | Is `id` client-supplied? | **Yes.** `{id?}`, defaulting to the filename stem. Charset `[A-Za-z0-9][A-Za-z0-9._-]{0,79}`. Same id + identical bytes = `unchanged` no-op; different bytes = version bump. | Use our `documents.id`. A positive `bigint` satisfies the charset and is well under 80 chars, so there is **no mapping table and no translation layer** — their document key *is* our id. |
| **4** | Whole-corpus rebuild? | **Yes** — `POST /ingest?reset=true`. Also the remedy named by the stale-fingerprint error. | Delete the declaration button. See §5.5. |
| **5** | `/openapi.json` and a reachable address? | **The schema ships on every deployment**: OpenAPI 3.1.0, 18 paths, version-tagged `1.2.3`; `GET /docs` is interactive, both on the same port as everything else. The address is ours to stand up (`docker compose up --build` from their repo root, `8000:8000`). | **Steps 6 and 7 are unblocked.** We can stop guessing: every alias in `RagLabViews` and `RagLabFacts` can be replaced by a real field name — which is what makes the corpus screen stop saying *« non communiqué »*. |

### 6.1 One correction, and where it leaves the token

The answer included a factual correction: that `RAGLAB_TOKEN` was never plumbed through *this*
repo, and that `.env.docker.example` does not exist here. **Checked, and that part does not hold**
— both files are tracked here, and compose already passes the variable into the backend container:

```
.env.example:61         RAGLAB_TOKEN=
.env.docker.example:35  RAGLAB_TOKEN=
docker-compose.yml:130      RAGLAB_TOKEN: ${RAGLAB_TOKEN:-}
```

Their *substantive* point is right, and it is the one that matters: **our backend never read it.**
`application.yml` declares `raglab.url` and nothing else, and `RagLabClient` sets only
`Content-Type`. The variable has been entering the container and being ignored — exactly the
dead-plumbing defect recorded in `RAGLAB_INTEGRATION_ANALYSIS.md` §6, finding 2.

The happy accident is that 1.2.3 accepting `RAGLAB_TOKEN` as an alias means **our existing wiring
lights up with no compose change**: read it, send it as `X-Service-Token`, done.

Two loose ends: the comment above the variable in `docker-compose.yml` still reads *"Optional
token for a gateway in front of RAGLab (it has no auth of its own)"*, which is no longer true; and
`RAGLAB_SERVICE_TOKEN` appears nowhere in this repo, so we do not need it.


## 7. Risks

| # | Risk | Why | Mitigation |
|---|---|---|---|
| **R1** | **You cannot evaluate RAGLab before making it active** (A5). | If the dormant agent is never fed, its index is stale or empty, so the quality workbench can only meaningfully test the agent already serving customers. The first time RAGLab answers a real customer is the first time it answers at all. | Cheap safety valve, if you want it: an **internal-only pilot** — RAGLab is fed and serves, but only to staff sessions, before it is made customer-facing. Half a day on top of step 4. Not required by the plan; say the word. |
| **R2** | ~~**The document feed is on someone else's roadmap.**~~ **Resolved 2026-09-15.** | A4 was chosen over a workaround, which left the headline feature without a date. | RAGLab shipped the document store (§5). The sidecar fallback is no longer needed — strike it. |
| **R3** | **Every RAGLab call will 401 the moment a token is configured.** | Confirmed: `X-Service-Token` is required on **every** endpoint (§6, Q1). `RagLabClient` sets only `Content-Type`, so the health screen, the workbench, the corpus screen and the config card would all start failing the day someone sets a token — including the screens that already exist today. | Step 1b: add `app.agent.raglab.token`, send the header on every call. Our `RAGLAB_TOKEN` wiring already reaches the container, so this is ~30 minutes with no compose change. |
| **R6** | ~~**The in-house agent cannot unpublish, RAGLab can.**~~ **Largely resolved 2026-09-16** (`eae4d89`). | `delete_source` on pgvector was a no-op pending slice 4.3 and was never even called, so re-ingest was append-only. | Implemented: purge by `cmetadata->>'source'`, scoped to the collection, then re-ingest (§5.3). Two bugs fell out of it — a re-labelled document answered under both domains, and every corpus rebuild grew the index. **Residual:** the purge removes chunk vectors only, not `parent_doc_store/<id>.pkl`, so keep `unpublish` behind a capability declaration. The SQL has no automated test: verify with `scripts/purge_chunks.py` before trusting a rebuild. |
| **R7** | **Ingest is a single global lock, and it changes what "published" means.** | One job at a time, and a second is *rejected*, not queued. A push can store a document that is not yet indexed, and index reads return `409` while a job runs. | Batch by default (§5.4). `409` becomes a first-class state, not an exception. The corpus screen must distinguish *déposé* from *indexé*, and must never show a rejected index as a failed publish. |
| **R4** | **Switching is a cold start.** | See §1. An admin can activate an agent whose index is empty. | Index-freshness badge + mandatory "feed and index" step, with progress. Never a silent empty index. |
| **R8** | ~~**`RagLabClient` could not parse any successful response.**~~ **Found and fixed 2026-09-15** (`9809ffd`). | It asked `RestTemplate` to deserialize straight into Jackson 2's `JsonNode`, but the converters Boot 4 wires speak Jackson 3, so every non-empty body threw `Type definition error: [simple type, class ...JsonNode]`. **The client had never parsed a real reply** — it could only ever report unreachable, refused or empty. | Responses are read as `String` and parsed with the client's own Jackson 2 mapper, which is what `AgentClient` already did with its `String`/`Map` responses. |
| **R9** | **"CI green" means "the parts this push touched build" — not "the repo builds".** | `ci.yml`'s `changes` filter maps `*.py`, `albaraka/**`, `tests/**`, `requirements*.txt`, `pyproject.toml` to Python; `backend/**` to backend; `frontend/**` to frontend. A push touching none of them runs **only** the `changes` job, and the run reports `success` with all four real jobs `skipped`. `verify-local.sh`, `VERIFY_LOCAL.md` and `docker/verify-agent.Dockerfile` match no filter at all, so the host harness is itself never exercised by CI. Found 2026-09-16: the maintainer's `./verify-local.sh all` failed with `ModuleNotFoundError: jsonschema` while every CI run that week was green and the Python suite had been skipped throughout. | The stale-image cause is fixed (`ad510f5`). Deliberately **not** proposed: widening the filters — for a backend-only push the skip is *correct*, and running everything on every push costs minutes for nothing. Instead: read "CI is green" as scoped, and run `./verify-local.sh all` before merging to master. |
| **R5** | Steps 1 and 4 touch the serving path and the schema. | A refactor of the request path is exactly where a mistake reaches customers. | Step 1 is behaviour-preserving and covered by the existing 66 green tests; step 4 is additive. Migrations stay additive (`CREATE TABLE`, no drops). |

---

## 8. Status

**Step 0 is done** (2026-09-15, `18e3016`). One assertion in `AgentEndpointServiceTests`, plus a
three-line comment saying why the expectation is what it is. CI is green — the backend job runs
`mvn -B -ntp clean verify`, so a green build means every test in the suite passed.

Housekeeping that is *not* done: CI files a GitHub issue on every failure, so issues #15–#19 are
now stale (the last one, #19, came from a docs-only push that happened to re-run the backend job
while the test was still red). Worth closing, but I have left them alone — say the word.

**Nothing on this list waits on RAGLab any more.** All five open questions were answered on
2026-09-15 and are recorded in §6; step 7 waits only on us standing an instance up.

**Steps 0, 1 and 1b are done** and CI is green (71 tests). The gateway now has one adapter
contract, a registry that resolves the active engine per call, and an authenticated RAGLab client.

Along the way, step 1b turned up a defect wider than the one it was written for — see **R8**:
`RagLabClient` asked `RestTemplate` to deserialize into Jackson 2's `JsonNode`, which Boot 4's
Jackson 3 converters cannot produce, so **it had never once parsed a successful RAGLab response.**
It could only ever report unreachable, refused or empty. Fixed by reading `String` and parsing
with the client's own mapper, which is what `AgentClient` already did.

That is now the third defect of this exact shape — it only appears once a real reply arrives. It is
the strongest argument for step 7 (standing RAGLab up and verifying end to end) landing before this
is called done, rather than last.

**Next: step 2** — the `DocumentFeed` interface plus its in-house implementation, ~1 day. The
contract for it is settled (§5, §6): push without `?index=true`, one `POST /ingest`, poll
`/ingest/status`, `409` as a state, and a capability declaration so the console never offers
"retirer" where the in-house agent would silently do nothing (§5.3).

Per `AGENTS.md` §1, step 2 involves a new component and touching the schema, so **I am waiting on
your go-ahead before writing it.**
