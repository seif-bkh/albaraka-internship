# RAGLab `1.0.0` vs. the current agent — comparison and switch decision

**Date:** 2026-09-14 · **Author:** Arena agent · **Status:** analysis only, no code changed
**Subject:** the pasted RAGLab HTTP contract (service version `1.0.0`) as a **replacement** for the
agent we run today (`albaraka/server/`, served on `:8000`, called only by the Spring Boot edge).

**Read this first — my honest opinion, in one paragraph.** RAGLab is a *better grounding engine*
than what we have: per-claim verbatim quotes, chunk-level citations, structured refusal reasons, a
diagnostic that tells you *why* a correct quote was refused. But it is a **worse fit for this
bank's product** than what we already built: no streaming, one global corpus with one global
ingest, no per-document versions, no governed facts, no PII scrub on output, no service auth, one
replica, a global mutable profile, and — in its own words — *"not production-ready for a banking
service, supervised pilot standing."* Swapping it in today would trade away capabilities the repo
lists as north-star properties, and the trade isn't close enough to make blind. My recommendation
is **don't switch the serving path yet**: run RAGLab in shadow mode for a week (1–2 days of work),
harvest the parts that are unambiguously better (the quote gate, structured refusals), then decide
with numbers. Details, per-area diffs and a costed path are below.

---

## 1. What exactly is being compared

| | Current agent | RAGLab `1.0.0` |
|---|---|---|
| Code / entrypoint | `albaraka/server/main.py` → `create_app(make_deps())`, started by `docker/agent_entrypoint.sh` (uvicorn, 2 workers) + background ingestion worker | External service, `:8000`, root-relative paths, OpenAPI at `/openapi.json` |
| Our client | `backend/.../services/AgentClient.java` (`/invoke`, `/invoke/stream`, `/explain`) | Would need a new client (or a rewrite of `AgentClient`) |
| Contract artifact | `contracts/agent-invoke.v1.json` (JSON Schema) + `tests/test_contract.py` + `AgentContractTests.java` | Human doc + `/openapi.json`; *"when this doc and /openapi.json disagree, /openapi.json is truth for field names"* |
| Designed role | Stateless inference worker inside our VPC, Java is the only edge | Stateful single worker ("one profile, one index, one ingest at a time") |

---

## 2. Comparative table

### 2.1 Transport, contract, auth

| Aspect | Current agent | RAGLab | Verdict |
|---|---|---|---|
| Serving surface | `POST /invoke`, `POST /invoke/stream` (SSE), `POST /explain`, `GET /health`, `GET /metrics` | `POST /answer`, `/search`, `/chunks/search`, `/inspect`, `/ingest`, `/profile`, `/keys`, `/evaluate`, `/diagnostics/*`, `GET /health`, `/models`, `/` | RAGLab has far more surface (a lab workbench); ours has exactly the two serving paths the edge uses |
| Auth | `X-Service-Token`, constant-time `hmac.compare_digest` (`app.py`) | **None** — *"the service trusts its network"*, CORS `*` by default | **Regression.** Also RAGLab's mutating endpoints (`POST /profile`, `POST /ingest`, `POST /keys`, `DELETE /keys/{env}`) are unauthenticated: whoever can reach it can change the global model and drop the index |
| Idempotency / correlation | `turn_id` in body **and** `X-Turn-Id` header; retry-safe correlation, used in logs | No turn id, no idempotency key | Minor but real for banking audit trails |
| Streaming | SSE `meta` → `delta*` → `done`, relayed by Java to the browser, host-validated | **"no streaming"** | **Hard product regression** — the public chat widget streams today (`chat-widget.ts`, `/message/stream`) |
| Request shape | `{session_id, turn_id, message, language∈fr/ar/en/auto, history[≤40]}` | `{question, k, include_excerpts, query_lang, mode, lang_filter}` | RAGLab has no place for `history`; ours transports it (see §2.7) |
| Server state | None in the agent (Java owns sessions/quota/persistence) | Global profile + collection + ingest job state | Our "agent stays stateless" decision (PLAN_TO_PROD §1) is directly contradicted |
| Error model | `401` token, `503` not configured, `500` with a raw Python message | Catalogue of stable machine reasons (`empty_index`, `retrieval_refused`, `missing_api_key`, `provider_error`, …) with a client-action column | **Better** in RAGLab — this is the single best artifact in their doc |
| Status semantics | `200` always for answered; `grounded:false` + `gate_reasons` inside the body | `200` for answered **and** refused (`status` field); distinguishing errors use 4xx/5xx | Both fine; RAGLab's is more explicit but needs a `status`-aware client |

### 2.2 Retrieval and knowledge

| Aspect | Current agent | RAGLab | Verdict |
|---|---|---|---|
| Store | Postgres **pgvector**, collection `albaraka_documents`, Gemini `models/gemini-embedding-001` @768d (child chunks, 400 chars) | **local ChromaDB**, collection per `(embedder, chunking)`, e.g. `raglab_app_nvidia_..._restructure`, chunking `restructure` 220/40 tokens | RAGLab moves the index into the service container: a second index, out of our Flyway/backup story |
| Scope of a query | `metadata.domain`-filtered from the classifier; widened to the whole collection only when the filtered query returns **zero** rows; `score ≥ 0.50` floor | Whole corpus, `top_k` 1–20, `mode ∈ vector/rrf/blend`, optional `lang_filter`, `neighbor_radius` 0–2 | We lose domain routing/precision; we gain neighbour-chunk widening (and our parent chunks are computed but **never retrieved** — `RAG_LOGIC.md` §2.3) |
| Routing / intent | Hybrid classifier → 8 intents → 8 routes, per-route required sections (`EXPLAIN`, `RATE`, `ELIGIBILITY`, …); `OVERVIEW` = the question plus 4 fixed probes, `top_k=12`, deduped | None: one question → one retrieval | **Regression** for structured banking questions (`RATE`/`ELIGIBILITY` are exactly the ones that need the whole section filled) |
| Governed facts | `business_fact` rows, admin CRUD with mandatory reason + `BusinessFactAudit`, injected into `what_it_is`/`rate_terms`/`conditions`, `facts_used` returned | **No equivalent** | **Biggest governance regression.** README's north star is *"facts beat documents"*; with RAGLab a rate/fee is just text in a chunk |
| Document versions | `document_version = v{epoch_ms}-{uuid8}`, cited in `provenance[(source, version)]`, cache eviction keyed to versions | None; `sources[]` carries document + chunk id + heading only | **Compliance gap.** Today an answer can be tied to a *version* of a fee schedule; RAGLab cannot express that |
| Cache | `build_cache_store()` → `None`; semantic cache deliberately off today | Server-side answer cache keyed by question text, shared across all callers (`cached: true`) | Neutral-to-risky: a global cache is the same "no tenant scoping" issue AUDIT.md §208 already flags; acceptable only because answers are corpus-only |

### 2.3 Answer shape and trust

| Aspect | Current agent | RAGLab | Verdict |
|---|---|---|---|
| Answered shape | `{text, structured{domain,intent,topic,coverage,citations[],citation_titles[],facts_used[]}, grounded, gate_reasons[]}` | `{status:"answered", answer, claims[{text, evidence[{source_id, quote}]}], sources[{source_id, document, chunk_id, heading}]}` | RAGLab's shape is richer for citations, poorer for routing facts |
| Citation granularity | **Document-level**: distinct `source` of retrieved docs + version | **Chunk-level** with `document`, `chunk_id`, `heading` and a **verbatim quote per claim** | **Strong improvement** |
| What is machine-verified | Citations present; every required section covered; **every number in the text must appear literally** in the source material or a governed-fact key (`gates.py`) | **Only the quotes** — quote membership inside the cited chunk | Mixed. Better against invented quotes; see the hole below |
| What is **not** verified (same in both) | Claim-level entailment (a well-cited sentence can still overreach) | **Also**: `answer` (the human-readable summary the doc tells you to render, §5.3) and `claims[].text` are model prose that no gate checks — only the `evidence[].quote` is validated | ⚠️ Do not render `answer` as-is under a "verified" badge; render claims + quotes |
| Numeric-claim check | Yes, deterministic (`unsupported numeric claim`) | Not stated in the contract | If we drop the current gate entirely we **lose** the only defence against a number being invented around a valid quote |
| Refusal | Fixed strings; `grounded:false`, `gate_reasons` (`knowledge_gap: …`, `out_of_scope`) | `status:"refused"` + `reason ∈ private_or_live_request / insufficient_evidence / no_context / invalid_output`, with a user-safe `answer` | **Better** — machine-actionable reasons, and the "do not retry" rule is documented |
| Greeting | Fixed SMALLTALK text, `grounded:true` | Local greeting, `inference_performed:false` | RAGLab's flag is more honest for UI badging |
| Language | `langdetect`, reply language = message language, prompt may translate passages | Claims in the user's language, quotes in the document's language, `query_lang` forces both; `lang_filter` restricts retrieval to one corpus language | Comparable; RAGLab's "claim ≠ quote language is normal" rule is a genuinely good integration note |
| **PII on output** | `PiiScrubber` (email, TN phone, 20-digit RIB, 8-digit CIN → `[LABEL]`) runs as the last gate before text leaves (`gates.py`, `app.py`) | **Nothing.** Their redaction filter covers *API keys in error bodies* only | **Regression for a bank.** A chunk containing an example RIB/IBAN or a real contact would be echoed verbatim into a claim — and RAGLab's whole premise is verbatim quoting |

### 2.4 Ingestion and corpus lifecycle

| Aspect | Current agent | RAGLab | Verdict |
|---|---|---|---|
| Trigger | Admin sets document `VALIDE` → Java `LPUSH agent:ingestion:queue` → worker `BRPOP` | `POST /ingest?reset=` (whole corpus), one job at a time | RAGLab is coarser and **the caller cannot scope it to one document** |
| Unit of work | **One document**, with `document.statutIndexation`, a `journal_ingestion` row, chunk count, version, and a callback to Java (`POST /api/internal/ingestion/result`) | **The whole `data_dirs` corpus**; job state in `/health.ingest` (`idle/running/done/error` + `stored`) | **Workflow regression**: publishing one fee update means re-embedding everything, minutes of window, and the admin UI's per-document status/journal stops having a source |
| Replace / delete | `delete_source` is a **no-op** → re-ingest appends (known defect, `RAG_LOGIC.md` §1.5) | `reset=true` drops and rebuilds the collection — clean, but all-or-nothing | RAGLab fixes our dedupe defect, at the cost of granularity |
| Versions | Generated per ingest, recorded, cited | None | see §2.2 |
| Formats | pdf (incl. tables), docx, txt/md/xlsx/csv/pptx via Java upload whitelist | "corpus directories on the service host" — format handling not specified in the contract | Unknown; must be verified before relying on it |
| Failure reporting | `job.error`, journal row, admin error column | `state:"error"` + redacted error string | Comparable |
| Cost of a publish | 1 embed per changed child chunk (cache makes re-runs cheap) | 1 embed per chunk **of the whole corpus** | Material budget difference at any corpus size |

### 2.5 Operations, deployment, observability

| Aspect | Current agent | RAGLab | Verdict |
|---|---|---|---|
| Replicas | Stateless by design; `UVICORN_WORKERS=2` | **One replica** (local Chroma + in-process caches); *"do not load-balance across replicas"* | **Regression** vs the locked architecture |
| Runtime config | 12-factor env at boot; secrets from compose `env_file` | Env at boot **plus** runtime mutation (`POST /profile`) gated by `RAGLAB_ALLOW_PROFILE_SWITCH`; host paths in `data_dirs` | ⚠️ Must pin `RAGLAB_ALLOW_PROFILE_SWITCH=0` in Docker, and firewall `/profile`, `/keys`, `/ingest`, `/diagnostics/*` from everything except an operator |
| Credentials | Env only, never returned; `GET /keys` not exposed | `GET /keys` (masked, first 8 chars), `POST /keys` with optional persist to the service host's `raglab/.env`, `DELETE /keys/{env}` | A second place keys live, on another host, with no auth. The contract's own Docker caveat ("does not survive container recreation") is a hint this is a dev convenience, not a deployment mechanism |
| Metrics | `/metrics` Prometheus (`METRICS`, `TurnTimer`, `ChatMetrics`) | Not stated; `/health` + ingest state + lab endpoints | **Regression** for monitoring; we'd be blind on latency/grounded-rate until we instrument the new client |
| Per-turn trace | `trace.steps` (language, classify, route, retrieve, gate, llm_prompt) persisted, admin monitoring page filters grounded/refused, `POST /explain` for on-demand replay | `seconds`, `retrieved`, `dropped_for_budget`, `cached`, `inference_performed` — counters, **no step trace** | **Regression for the admin story**; but RAGLab's `POST /chunks/search` is a *better* answer to the #1 operator question ("why was a correct-looking quote refused?") |
| Diagnostics | `tests/regression/` golden set, `run_eval.py`, `albaraka/eval.py` | `POST /evaluate` (hit@1/3/5 by category/language), `POST /embeddings/sanity` (cross-lingual cosines), `POST /diagnostics/harness50`, `POST /diagnostics/catalog` | **Better** — this is real lab tooling, and the cross-lingual cosine check is directly useful for AR/FR/EN |
| Contract tests | Schema validated on both sides in CI (`tests/test_contract.py`, `AgentContractTests`) | None in our repo; would need a new pinned schema + tests | Real cost, see §4 |

### 2.6 Cost, quota, budget controls

| Aspect | Current agent | RAGLab | Verdict |
|---|---|---|---|
| Embedding calls | 1 per question (Gemini); cache off | 1 per `/search`, `/answer`; cached by query text | Comparable |
| Chat calls | 1 when the grounded path renders | 1 unless greeting / local refusal / cache hit | RAGLab slightly cheaper |
| Quota / metering | Java two-phase metering before the call (anon 3000 tok/20 msg, auth 10000/50); agent returns no usage, Java estimates `len/4` | No quota concept; caller's job | Neutral — Java keeps owning it either way |
| Budget visibility | Estimated tokens only | `/health` + per-answer `seconds`/`cached`; `POST /evaluate` and `harness50` spend unsupervised | RAGLab is more transparent |

### 2.7 A gap **neither** agent closes (don't let this be sold as a win)

Conversation history is **transported but never read** today (`RAG_LOGIC.md` §2.3: *"is not read
anywhere in orchestrate.py, answer.py or generate.py"*). RAGLab has no `history` field at all, and
*"there are none [sessions] — chat history is the client's job."* So follow-ups like *"et pour la
carte Gold ?"* are answered as standalone questions before and after the switch. If we want that
fixed, the fix is Java-side query rewriting with the last-N turns it already assembles
(`PublicChatController.buildHistory`) before calling either agent — that is a genuine improvement
available **now**, independent of this decision.

---

## 3. Where RAGLab is genuinely better (adopt these ideas regardless)

1. **Per-claim verbatim evidence, validated against the source chunk.** Today a "citation" is a
   document filename; nothing ties a sentence to the passage that supports it. This is the single
   most valuable idea in the contract, and it can be copied into `albaraka/gates.py` in about a
   day using the chunks we already retrieve.
2. **`POST /chunks/search` — "could this quote ever validate?"** Nothing we have answers the
   support question "the model quoted correctly but was refused". Ours could, from the chunk set.
3. **Structured, machine-coded refusals** with a client-action column; `inference_performed` and
   `cached` flags for honest UI badging.
4. **The error catalogue + "which endpoints spend money" table** — a documentation pattern this
   repo should steal for `contracts/` regardless of what serves the answers.
5. **Cross-lingual embedding sanity check** (`/embeddings/sanity`) — cheap insurance for a
   trilingual corpus, and we currently have no such check.

## 4. What the switch would cost us (concretely, in this repo)

| Lost / must be rebuilt | Where it lives today | Effort to restore |
|---|---|---|
| Token streaming end-to-end | `app.py:/invoke/stream`, `AgentClient.invokeStream`, `PublicChatController:/message/stream`, `chat-widget` | RAGLab cannot stream; only option is fake chunking of a finished answer, or a UX downgrade |
| Per-document ingestion lifecycle (status, journal, retry, version) | `IngestionQueueProducer`, `ingestion_cli.py`, `IngestionResultService`, `admin-ingestion` page | RAGLab's ingest is whole-corpus; parity is **not achievable** without one RAGLab instance per collection |
| Governed facts + audit trail | `facts.py`, `business_fact` + `BusinessFactAudit`, `admin-facts` page, `GovernedFactsService` | Rebuild as a Java-side preamble → then the facts are outside RAGLab's citation gate (two trust paths) |
| Intent routing / taxonomy / catalog releases | `core/classifier.py`, `core/router.py`, `catalog_store.py`, `TaxonomyService`, `/api/admin/taxonomy`, Redis `agent:catalog:active` | No consumer left for these admin features |
| Per-turn trace + admin monitoring replay | `trace.py`, `AdminMonitoringController`, `AgentClient.explain` | RAGLab has no step trace |
| Output PII scrub | `gates.py:PiiScrubber` | Must be re-implemented in Java (the only path to the browser) |
| Cross-repo contract tests + golden regression set | `contracts/agent-invoke.v1.json`, `tests/test_contract.py`, `AgentContractTests`, `tests/regression/` | New schema + new tests on both sides, or lose the CI gate |
| Service auth | `X-Service-Token` | Must be enforced at the network layer instead |
| Prometheus metrics | `/metrics`, `ChatMetrics` | Re-instrument |

## 5. Switches that are not just code (ask before assuming)

- **Vendor/SKU:** the pinned pair is NVIDIA embeddings (`nvidia/nemotron-3-embed-1b`) + xKiro
  (`qwen/qwen3.8-max:free`). That changes our embedding vendor (currently Google) and our LLM
  vendor (currently Groq) in one step. Per `AGENTS.md` §2 ("verify that a package, version, or API
  actually exists before depending on it") the SKUs and their terms need verification, and a
  *`:free`* chat SKU answering questions about internal bank documents is a data-processing
  question, not an engineering one. The contract is explicit that only the pinned pair may be
  quoted as benchmarked.
- **Where the corpus lives:** RAGLab reads `data_dirs` *on its own host*. Our documents arrive at
  runtime through the admin UI into `backend/uploads`. Either we share a volume and accept
  whole-corpus rebuilds, or we keep copying files into a second system.
- **Operational ownership:** one stateful replica with an unauthenticated admin plane has a
  different runbook (restart = empty index risk; who owns `raglab/.env`?) than a stateless worker.
  `docs/RUNBOOK.md` and `docs/DEPLOYMENT.md` would both need rewriting.

---

## 6. Options, with my recommendation

| Option | What it means | Effort | Verdict |
|---|---|---|---|
| **A — Shadow / bake-off (recommended first step)** | Keep the current agent serving. Add a flag so Java *also* calls RAGLab `POST /answer` (or `/search`) out of band, logs both, and compares. Run the 50-question set + `tests/regression/` + a week of real traffic. Produce one report: grounding agreement, refusals where we answered, answers where we refused, latency, cost, and a manual read of 30 claims vs. our texts. | **1–2 days** | Buys the only thing that can settle this: evidence. Zero user impact, no contract change for the frontend |
| **B — Harvest, don't adopt** | Copy the ideas that are strictly better into our pipeline: verbatim-quote check per cited sentence in `gates.py`, structured refusal reasons in `structured.rejection_reason`, a chunk-level `POST /explain` "why was this refused" view for admins. Keep our contract, streaming, versions, facts, PII | **3–5 days** | Highest value per day. Makes our agent better *and* makes a later switch cheaper to evaluate |
| **C — Full switch with parity work** | Java calls RAGLab only; we rebuild PII scrub in Java, facts injection, a new versioned contract file + tests on both sides, re-map monitoring; accept no streaming, single replica, no document versions, whole-corpus ingest, no service auth | **3–5 weeks** | Only rational if the pilot (A) shows a decisive quality win *and* the business accepts the streaming/versioning/facts losses. Note the leftover work is mostly re-implementing what we already have |
| **D — Full switch as-is** | Point `AgentClient` at `/answer`, map `grounded = status=="answered"`, ship | ~1 week | I'd advise against it. It drops streaming, per-document versions, governed facts and output PII scrubbing in a banking product, and adopts a service whose own contract says *"Not production-ready for a banking service — supervised pilot standing"* |

**Recommendation:** do **A then B** now, revisit **C** with the pilot report in hand. The switch is
worth having as a *destination only if* the four product questions in §7 come back in favour of
what RAGLab can actually do — otherwise we'd be buying a better citation model and paying for it
with the governance layer this repo spent its whole roadmap building.

**If we do proceed to C**, the minimum gate before any customer traffic:
1. `RAGLAB_ALLOW_PROFILE_SWITCH=0` pinned; `/profile`, `/keys`, `/ingest`, `/diagnostics/*`
   unreachable except from an operator subnet.
2. Output PII scrub re-implemented in Java, running on the final string.
3. Render `claims[].text` + quotes, **never** `answer` under a "verified" badge; keep the numeric
   check on whatever text we render.
4. A pinned JSON Schema for the RAGLab subset we use, validated in CI on the Java side (their
   `/openapi.json` is truth for field names, so pin the fields we consume and fail loudly on
   change).
5. A written, signed-off answer to §7.

## 7. Open questions (business/policy — I can't resolve these from the repo)

1. **Is RAGLab ours or a third party's?** Who operates it, on which network, and is "no auth, trusts
   its network" acceptable given Java is the only public edge?
2. **Are the pinned SKUs approved for internal bank documents?** NVIDIA embeddings + an xKiro
   `:free` chat model replace Google + Groq; a `:free` tier usually means different data terms.
3. **Is per-document versioning a compliance requirement?** Today answers cite `(source, version)`.
   RAGLab cannot express versions. If a regulator/auditor needs that, C is blocked until RAGLab
   grows versions or we store the mapping ourselves.
4. **Is streaming a shipped UX requirement?** If yes, "no streaming" is a hard blocker, not a
   downgrade to negotiate.
5. **Who owns the index backup/restore** once it lives in a ChromaDB volume inside a single
   container instead of in Postgres alongside everything else?
