# The assistant in the admin — capabilities first, screens second

**Date:** 2026-09-15 · **Status:** design agreed in outline; no code yet.
**Companions:** `docs/RAGLAB_13_ENTRIES.md` (the functional inventory extracted from the CLI),
`docs/RAGLAB_SWITCH_REVIEW.md` (the contract comparison).

This document does **not** render the 13 CLI entries as 13 screens. It takes what those entries let
an operator *do*, turns that into the admin's jobs, and places them in the admin we already have
(Pilotage · Connaissance · Administration).

---

## 0. Decisions taken

| # | Decision |
|---|---|
| D1 | The RAGLab address is a real `<ip>:<port>` like in prod: the deployment sets the default, a **SUPER_ADMIN can change it from the console**, it is persisted, audited, and effective on the next call. |
| D2 | The assistant is meant to end up served by the **new agent**; the admin sets the serving engine from the panel, without touching a config file. |
| D3 | Each engine is **configured separately** once it is selected — including the in-house one, whose Python service gains its own configuration endpoints (D7). |
| D4 | The monitoring room observes **either** engine through a selector, with **per-engine KPIs** and a per-engine detail panel. |
| D5 | No env kill switch: the guard on state-changing actions is the **SUPER_ADMIN role** (profile, keys, ingest, settings). |
| D6 | The admin surface is a **dedicated "Assistant IA" section** (option A), not entries scattered across existing pages. |
| D7 | The in-house agent becomes configurable too: **new endpoints on the Python service** (models, keys, thresholds), so both engines are administered from the same place. |
| D8 | **One drop zone, two worlds:** the admin uploads documents once in the front; both engines read the same files and each builds **its own chunks and its own index/DB**. |
| D9 | Console calls that are not customer turns (an admin's `/search`, `/answer`, `/evaluate`) are recorded in the **audit journal**, not in the chat monitoring.
| D10 | The shared files reach RAGLab the same way they reach the in-house agent: **the same host, the same volume** (`./backend/uploads` mounted at RAGLab's `data_dirs`). No second copy, no sync job. |
| D11 | Dropping/validating a document **publishes it to both engines**: the in-house side indexes it routinely; RAGLab sees it at its next whole-corpus rebuild, which the admin triggers and pays for. |
| D12 | The in-house agent's new configuration endpoints cover **models and keys** (`LLM_MODEL`, `EMBEDDING_MODEL`, `EMBEDDING_DIM`, `GOOGLE_API_KEY`, `GROQ_API_KEY`) — the same shape as RAGLab's profile + keys panels. Thresholds and chunking stay code/env-side for now. |
| D13 | **Documents stays the single drop zone**: each row gains an index state per engine, and *Ingestion & indexation* shows the two index columns with the RAGLab rebuild.

## 1. The capabilities — what an admin actually does

| | Capability | The question it answers | Fed by (CLI entry) | Touches prod answers? | Role |
|---|---|---|---|---|---|
| **C1** | Choose the serving engine | *"Which agent answers our customers right now — and what changes if I switch?"* | 1 + the switch itself | **yes** (serving path) | SUPER_ADMIN |
| **C2** | See whether the assistant is healthy | *"Is it working, and if not, what is missing?"* — target, version, profile, index, keys, running job, last error reason | 1 | no | any staff |
| **C3** | Test answer quality | *"For this question: what was retrieved, what is claimed, which verbatim quote backs each claim — and could this quote ever validate?"* | 5, 6, 8, 9, 10, 11, 13 | no (spends) | read: staff · spend: tiered |
| **C4** | Keep the knowledge current — one drop zone, two indexes | *"What files are published, how is each engine cutting them into chunks, and is each index built from the current files?"* | 4, 7 (+ our Documents/Ingestion pages) | **yes** (retrieval) | SUPER_ADMIN for rebuild |
| **C5** | Manage models & secrets, per engine | *"Which embedder and which chat model answer for this engine, and are the provider keys in place?"* | 2, 3 (+ the in-house config endpoints) | **yes** | SUPER_ADMIN |
| **C6** | Tune retrieval & chunking, per engine | *"Chunk size, overlap, top-k, thresholds, corpus folders — what are they, and what would changing them break?"* | 12 (+ the in-house config endpoints) | **yes** | SUPER_ADMIN |
| **C7** | Watch production | *"How many turns, how many refused and why, how fast — per engine — and what did the engine actually rely on?"* | existing monitoring + engine attribution | no | any staff |
| **C8** | Know who changed what | *"Who switched the engine, replaced a key, rebuilt an index, ran an evaluation, and why?"* | ours (audit table) | no | read: staff · write: SUPER_ADMIN |

Two shapes worth keeping in mind:
- **C2 vs C3** — "is it up?" and "is it any good?" are different questions, asked by different people
  at different times. Different screens, even though both read the same service.
- **C4 is deliberately not "one index"** — see §4b. The files are shared; the chunk worlds are not,
  and the admin must always see *which* world each number belongs to.

## 2. The layout (option A, agreed)

```
Pilotage
  Tableau de bord                         (unchanged)
  Statistiques d'usage                    (unchanged)
  Monitoring du chat                       + C7: selector [ Tous | Interne | RAGLab ] + per-engine KPIs

Assistant IA                               ← NEW section
  Moteur de l'assistant                    C1: which engine serves, switch, consequences, per-engine health
  Santé & diagnostic                       C2: target <ip:port>, version, profile, index, keys, running job
  Qualité des réponses                     C3: ask → claims/quotes · retrieval · quote lookup ·
                                               embedding sanity · chat · evaluate · diagnostics
  Configuration de l'agent                 C5 + C6, scoped by engine:
                                               [ Interne ]  models · clés · seuils · chunks · historique
                                               [ RAGLab  ]  profil · clés · chunking · récupération · corpus

Connaissance
  Documents                                C4: THE drop zone (upload once, publish to both)
  Ingestion & indexation                    C4: two index columns — Interne | RAGLab — rebuild each
  Faits gouvernés · Actualités · Taxonomie  (unchanged)

Administration
  Journal des actions de l'agent            C8: engine switches, keys, ingests, evaluations, console calls
  Utilisateurs & rôles · Historique clients (unchanged)
```

Considered and set aside: **B** (spread across existing pages — configuration ends up mixed with
pilotage, "where do I change the model?" has no obvious answer) and **C** (one hub page with tabs —
everything at the same level, and the serving state is invisible from the sidebar).

## 3. "Configure each agent" — what is configurable, and what that costs

| Knob | RAGLab | In-house agent |
|---|---|---|
| Model / profile choice | ✅ its own surface (per the CLI: `POST /profile`, `GET /models`) — switching the embedder means rebuilding its index; from our console: **read-only**, we show only what it reports | ✅ **editable**: the chat model (`LLM_MODEL`) — applied to the next turn · the embedder (`EMBEDDING_MODEL`, `EMBEDDING_DIM`) is **read-only on purpose**: changing it invalidates the existing index (decision D6) |
| Provider API keys | ✅ its own surface (`POST /keys`, `DELETE /keys/{env}`); **read-only** from our console | ✅ **editable**: `GROQ_API_KEY` (chat, next turn), `GOOGLE_API_KEY` (embeddings, after a restart). Write-only: never read back, never in our DB, never logged |
| Retrieval / classifier thresholds | ✅ service settings; from our console: read-only | ❌ not editable (D6) — shown read-only so the operator knows what he is judging answers with |
| Chunking | ✅ service settings (its pinned chunking: 220/40 tokens, `restructure`) | ❌ not editable (D6) — the ingestion path's child-chunk size stays a deployment decision |
| Conversation history | ❌ none (stateless, "history is the client's job") | ❌ `HISTORY_MAX_TURNS` (10) shown read-only: it is how the *edge* passes turns, not an agent setting |
| Corpus location | `data_dirs` — paths **on the RAGLab host** | `UPLOAD_DIR` — the shared mount it already reads (shown read-only) |
| What is indexed | `POST /ingest` (whole corpus, one job) | our pipeline: Documents → validation → ingestion queue → per-document status |
| Behaviour prompt | ❌ not exposed | ❌ not exposed (code-side) |

**The in-house workstream (D7 + slice 5) — shipped.** The agent gained `GET /config` and
`PUT /config` (FastAPI, `albaraka/server/app.py`), behind the same `X-Service-Token` as `/invoke`:
no second door (AUDIT A3/A4). The two constraints above are answered rather than postponed:

- **What applies when is a property of the code, and the console says it.** The text generator is
  built per turn, so the chat model and its key are live from the next question; the embedder is
  built once at start-up, so the embedding key waits for a restart — and the response carries
  `restartPending`, computed by comparing the stored values with the ones the running process
  actually applied. The screen pairs that with the exact command (`docker compose restart agent`)
  instead of a vague "may require a restart".
- **The dimension trap is closed by not opening it**: `EMBEDDING_MODEL`, `EMBEDDING_DIM`,
  `COLLECTION` and the thresholds are not editable at all (D6). A wrong embedding dimension breaks
  retrieval silently; a read-only row plus a sentence saying why is the cheaper protection.
- **Keys move one way.** `GET /config` returns `configured` + an 8-character SHA-256 fingerprint
  (enough to recognise the key in place, not to recover it); a key never enters a log line, a
  journal row, or our database. The journal records the *field names* changed (`CONFIG_SET`), the
  reason, and who did it.

## 4b. One drop zone, two worlds (D8)

Today the files already flow through a single place: the backend writes uploads to
`backend/uploads`, and the in-house agent reads that same directory through a shared volume
(`UPLOAD_DIR=/app/uploads`), indexing per document over the Redis queue. RAGLab adds a second
consumer of the same files, with different internals: it reads `data_dirs` **on its own host**, cuts
them with its own chunking, and indexes them into its own collection.

What has to be true, and what the UI must show:

1. **One place to drop a file.** The admin uploads once (today's Documents page), the file becomes
   the shared source of truth, and nobody ever uploads "for RAGLab".
2. **Two index states, always labelled.** A file can be indexed in-house and missing from RAGLab (or
   the reverse), and — because RAGLab ingests the whole corpus at once — a document added *after* the
   last RAGLab ingest is invisible to it until the next rebuild. That divergence must be visible as
   a state, not discovered as a wrong answer.
3. **Per-engine rebuild, per-engine cost.** The in-house side indexes one document at a time (cheap,
   routine). RAGLab rebuilds everything (expensive, minutes) — so "publish this document" cannot mean
   "spend an embedding pass over the whole corpus" without the admin saying so.
4. **How the files reach RAGLab's host — decided (D10):** RAGLab runs on the same host, in the same
   compose, with `./backend/uploads` mounted at its `data_dirs`. Its own `POST /ingest` then reads
   exactly the files we wrote, and no copy/sync machinery exists anywhere.

## 5. Monitoring (D4/D9)

The existing page filters `tous / fondés / refus` and replays a turn's pipeline trace. With two
engines it keeps that shape and gains:

1. **A selector**: `[ Tous | Interne | RAGLab ]` over the turn list (turns already carry `domaine`).
2. **A KPI strip split by engine**: turns, grounding rate, refusal **reasons** (RAGLab's are
   machine-coded: `insufficient_evidence`, `no_context`, `private_or_live_request`, `invalid_output`),
   p50 latency, cache hits.
3. **A per-engine detail panel**: the in-house trace stays a pipeline (route → retrieve → gates);
   a RAGLab turn has no pipeline — it has **claims + verbatim quotes + scores**, so the right-hand
   panel renders evidence instead of steps.

Non-customer calls (the console's own `/search`, `/answer`, `/evaluate`) do **not** appear here; they
belong to the audit journal (D9), where their cost and author are recorded. The workbench implements
this: a grounded answer and a retrieval query each write a journal row (`CONSOLE_ANSWER`,
`CONSOLE_SEARCH`) with the actor, a truncated question and the outcome; the free quote lookup does
not, because it spends nothing — a journal full of free lookups would bury the rows that matter.

## 6. Build order

| Slice | Contains | Why in this position |
|---|---|---|
| **0** ✅ shipped | C2 Santé & diagnostic + the persisted, audited address setting (D1) | proves the connection to `http://<ip>:<port>` and cannot break anything |
| **1** ✅ shipped | C3 read-only half (ask → claims/quotes, retrieval, quote lookup) — `/admin/agent/quality` | this is what shows whether the new engine deserves the switch |
| **2a** ✅ shipped | C1 engine switch (`/admin/agent/engine`) + both-engine liveness + the consequence list | the switch lands once the console can show what it will serve |
| **2b** ✅ shipped | C7 monitoring selector and per-engine KPIs | needs 2a's engine attribution to be meaningful |
| **3a** ✅ shipped | C4 corpus & indexes (`/admin/agent/corpus`) | the write the whole feature depends on: two indexes, one file world |
| **3b** ✅ shipped | C5 models & keys (`/admin/agent/config`), C6 retrieval settings shown read-only | after the corpus, because keys and knobs only mean something next to an index that works |
| **4a** ✅ shipped | embedding sanity (CLI entry 6): the index checked against the running embedder, plus a live embedding probe | catches the failure that leaves no trace anywhere else |
| **4b** | C3 heavy half (evaluate, harness, chat replay) | long-running and expensive; needs the rest in place |
| **4c** ✅ shipped | C8 journal polish (`/admin/agent/journal`) | every action the section can take now has to be findable by who, when, and why |
| **5** ✅ shipped (with 3b) | D7 the in-house agent's configuration endpoints (Python side: `GET/PUT /config`) | C5 needed it; it was independent of RAGLab, so it landed together with the screen |

## 6b. Slice 1 in detail (shipped)

`/admin/agent/quality` answers three questions, in the order they are asked during an incident:

1. **Réponse sourcée** (spends an embedding + a chat call) — the claim → verbatim-quote table is the
   centrepiece, because the quotes are the only part the service gate-checks. The free-text `answer`
   is shown as prose with an explicit "not verified" note (never under a verified badge), claims
   without evidence are flagged, and sources no claim used are listed: a retrieved source nobody
   cites is the signal that a confident-looking answer is thin.
2. **Récupération seule** (one embedding call, no chat) — ranked chunks with scores, which separates
   "the passage was never retrieved" from "the model refused anyway". A refusal links straight here
   with the same question.
3. **L'extrait existe-t-il dans le corpus ?** (free) — substring lookup, the same question support
   asks after "the model quoted correctly and it was still refused". A miss is a hard fact: no
   citation of that text can validate.

Roles: the two spending panels need SUPER_ADMIN, ADMIN_DOCUMENTS or ADMIN_SUPERVISION; the free
lookup is open to every staff role including VIEWER. Every payload is shown twice — the typed view
and the raw JSON — so a service version that renames a field degrades to "read it yourself" instead
of a blank screen.

## 6c. Slice 2a in detail (shipped)

`/admin/agent/engine` is the one product decision of this section, so it gets its own screen:

- **What serves now**, with the provenance of that choice (an admin's row in `agent_engine`, or the
  deployed `AGENT_PROVIDER_DEFAULT`) and who moved it last.
- **Both engines side by side** with address and liveness, so the operator sees the state of the
  engine they are about to hand customers to (`GET /engines` probes both).
- **The switch itself**: SUPER_ADMIN only, mandatory reason, journalled (`ENGINE_SET`). It is refused
  when RAGLab has no address — a switch that "succeeds" and breaks the next customer question is
  worse than a refusal that names the fix.
- **The consequence list**, before the button rather than after the incident: no token streaming, no
  governed facts, no per-document versions, no PII scrub on the output, whole-corpus ingest — and the
  genuine gain, per-claim verbatim quotes.
- **Effect**: the next turn. The serving path resolves the engine per call
  (`AgentRouter` → `AgentProviderService`), so there is no restart and no cache to clear, and the
  monitoring page attributes each turn to the engine that answered it (`messages.domaine`).

Engines are behind one interface (`ServingAgent`), which is what keeps the edge engine-agnostic:
quotas, metering, persistence, SSE framing and monitoring did not change. The two capability gaps are
handled explicitly rather than papered over — a streamed RAGLab turn emits a single delta and then the
done frame (it cannot stream), and `explain` probes retrieval instead of a pipeline trace (there is
none).

## 6d. Slice 2b in detail (shipped)

The monitoring room observes **either** engine (D9). The interesting decision here was where the
engine attribution comes from.

- **Attribution is read from the intention**, not a new column: RAGLab turns are named
  `RAGLAB_<STATUS>` (and `RAGLAB_REFUSED_<REASON>`) by the mapper, everything else came from the
  in-house pipeline. One rule, one place, nothing that can drift out of sync with reality — and it
  works for the turns already stored.
- **`GET /turns?engine=LEGACY|RAGLAB|ALL`** filters the feed; filtering happens over the recent
  window rather than in SQL, because that is what the page shows and the KPI math already needs the
  same rows.
- **`GET /kpis?window=200`** returns both engines' numbers over that window: turns, grounded,
  refused, grounding rate, share with sources, and refusal reasons. Derived from the stored turns
  (`MonitoringKpiService`, pure and unit-tested), so no counter to maintain and no second source of
  truth. A rate is **null, not 0**, when an engine served nothing — "0 % grounded" and "never asked"
  are opposite conclusions.
- **The KPI strip always shows both engines**, even when the feed is filtered to one: the point of a
  two-engine edge is comparing them. It is labelled with the window ("sur les N derniers échanges"),
  because these are the last 200 turns, not all time.
- **A RAGLab turn has no steps**, so the detail pane shows what this engine does have — each claim
  with the verbatim quote that supports it, the uncited claims called out, and the sources retrieval
  returned but no claim used. For a live test question, RAGLab is probed through retrieval (`explain`
  has no pipeline to replay) and the pane links to the quality workbench to get a real grounded
  answer. The in-house engine keeps its full step-by-step trace, unchanged.

Duration is deliberately absent from the KPIs: inventing a statistic out of the turns that happen to
carry one would read as a measurement. The per-turn number is shown where it is real — `messages`
stores `latency_ms`, `tokens_in/out`, `model` and the two timestamps, and
`GET /live-feed?limit=50[&since=…]` returns them per exchange, newest question first, with
`IN_PROGRESS` for a turn still being generated. `since` is what makes the console a feed rather than
a table that resets: it asks only for what it has not seen and updates rows in place (2,5 s).

**Sources are documents, never citation labels (2026-09-21).** The customer sees no provenance at
all (`AnswerSanitizer` strips it at the edge), so the console is the only place it exists, and it
has to be worth reading: `sources_utilisees` stores document identifiers — for a RAGLab turn that
means the service's `document`, not its `S1`/`S2` alias, which is also what the document-usage
governance query (`/api/documents/{id}/utilisations`) matches on. Beside it, `documents[]` replays
every chunk retrieval consulted from the stored trace, with the section, the chunk id, the
similarity, and whether the relevance threshold dropped it: "no source found" and "a weak source was
found" are different diagnoses, and only the trace tells them apart.

## 6e. Slice 3a in detail (shipped)

C4 is the capability with a write in it, so it gets the strictest treatment.

- **The screen never blends the two worlds.** `GET /corpus` returns three separate things: the shared
  file world (our own `documents` table — the same list the drop zone publishes), the in-house
  engine's index (state, counts, and the ingestion journal the worker already writes), and the
  external engine's index (only what that service says about itself). C4's question is *"is each
  index built from the current files?"*, and averaging two engines would answer neither half.
- **The in-house world has a real state**, derived and not stored: `CURRENT`, `STALE`, `RUNNING`,
  `DEGRADED`, `EMPTY` — with the sentence that explains it. `POST /corpus/rebuild/legacy`
  (SUPER_ADMIN, mandatory reason, journalled `CORPUS_REBUILD`) re-queues every published document
  through the *existing* durable ingestion path, one at a time, and reports per document what was
  accepted and what was skipped and why. A corpus-wide rebuild is a loop over a tested mechanism,
  not a second mechanism.
- **RAGLab's rebuild stays manual (D8), and the screen says so.** There is no remote trigger,
  because there is no trustworthy way for this console to start one; a button that pretended to
  would be worse than no button. So the card states the rebuild happens out of band, offers the
  operator a way to *record* it (`POST /corpus/rebuild/raglab`, journalled
  `CORPUS_REBUILD_RAGLAB_DECLARED`, with who/when/why), and shows the last such declaration — while
  the index facts next to it remain "non communiqué" whenever the service does not say.
- **Provenance, never invention.** Every fact read out of the service goes through one tolerant
  lookup (top level, or nested under `index`/`corpus`/`stats`/`status`; several plausible names per
  fact), and a fact that is absent stays **null** rather than becoming zero. `0 chunks` and "we
  cannot tell how many chunks" are opposite statements, and a console that confuses them is worse
  than one that says nothing. The raw payload is one click away for the case where the vocabulary
  differs from every alias.
- **Files that fell through are named.** A published file in `ERREUR`/`EN_ATTENTE` is surfaced above
  the rebuild button: that is exactly the drift C4 exists to catch.

## 6f. Slice 3b + 5 in detail (shipped)

- **Two engines, two shapes, one screen.** `GET /api/admin/agent/config` returns the in-house agent's
  own view passed through untouched (values, provenance, what applies when) plus RAGLab's card, which
  carries only what that service reports about itself. Modelling RAGLab's settings shape locally
  would be inventing one; saying "it is configured by its CLI, here is what it reports" is the truth.
- **The edit surface is exactly models and keys** (D6): `PUT /api/admin/agent/config/legacy`
  (SUPER_ADMIN, mandatory reason, journalled `CONFIG_SET`). Absent field = unchanged, empty string =
  clear the override so the deployed value applies again. The agent's own view comes back, so the
  console shows what the service accepted, not what was sent.
- **Overrides are stored where they survive a restart** — `agentconfig:/app/data` in compose,
  `AGENT_CONFIG_FILE` in the image — because a change that vanished on the next `docker compose up`
  would be worse than no console. Only names are logged, never values.
- **A key is write-only, end to end.** The agent reports `configured` and a fingerprint; the edge
  never asks for more; the journal records the field names; the response body carries no secret. The
  test suite asserts the absence of the key from the response and from the view JSON.
- **C6 is shown, not offered.** Retrieval thresholds, embedding model/dimension, collection, schema
  version and history depth are listed read-only with one line each on what they decide — including
  the one that matters most: changing the embedding model invalidates the index you already have.
- **What is still pending** for these two capabilities: RAGLab's own write surface (needs its
  contract — the CLI `--help` / `/openapi.json` we are still asking for) and chunking knobs on either
  side.

## 6g. Slice 4a in detail (shipped)

Entry 6 is the check that pays for itself the first time it fires, because the failure it catches is
invisible: when the embedder no longer matches the stored vectors, retrieval returns unrelated chunks,
the relevance gate rejects them, and the assistant starts refusing questions it used to answer — with
a healthy API and clean logs.

- **One read answers three questions** (`GET /index/state` on the agent, `GET /api/admin/agent/index/state`
  on the edge): how many chunks the collection holds, the dimension of the vectors *stored* there
  (read from the vector column itself), and whether that matches the dimension this process embeds
  with. The verdict is machine-readable — `ok` / `empty` / `dimension_mismatch` / `missing` / `unknown`
  — and the detail sentence says what to do about it.
- **`unknown` is a first-class verdict.** An unreachable database, a vector store whose table has a
  different name, or a dimension that cannot be read all report "we could not tell", with the error
  text attached. A diagnostic that raises is a diagnostic nobody runs, and a diagnostic that guesses
  is worse than none: the one branch that would have said "mismatch" without knowing both sides was
  caught by its own test and turned into `unknown`.
- **The live probe is separate, and honest about what it costs.** `POST /index/probe` embeds one fixed
  sentence through *the very embedder this process answers with* — the only way to prove the key and
  model work now rather than at start-up — and it is on the spend tier, journalled (`CONSOLE_INDEX_PROBE`).
  Its failure message is the provider's own (`401 API key not valid`), because that is the operator's
  next step. A failed probe never hides the rest of the screen: the corpus read is a different call.
- **What is still open on this entry**: the equivalent read for RAGLab's own index — the corpus screen
  shows what it reports, and its `dimension_mismatch` cannot be established from here until its
  contract (CLI `--help`, `/openapi.json`) is known.

## 6h. Slice 4c in detail (shipped)

C8's question — *"who switched the engine, replaced a key, rebuilt an index, ran an evaluation, and
why?"* — is the question an incident ends with, so the journal became a screen rather than a table
under another screen.

- **Filtering happens in SQL, not in the page.** `GET /audit?action=&limit=` (cap 500) exists because
  "who rebuilt an index" must be answerable when the last hundred rows are something else. The
  engine page keeps its own `ENGINE_*` window and links here for the rest.
- **The filter's values come from the journal** (`GET /audit/actions` counts the codes actually
  present), so a new action appears in the filter the day it is journalled — no list to keep in step.
- **The reason column is the point.** Writes carry a mandatory motive; the screen separates them from
  the console's spending reads (`CONSOLE_*`), because at audit time "who allowed this" and "who spent
  our budget" are different questions — the badge colour and the closing paragraph say which is which.
- **Labelled with its window**: "N lignes affichées sur les M dernières lues", because this is the
  journal you can read from here, not all time.

## 7. Open points (asked in the chat)

All four were settled on 2026-09-15 — see D10–D13 in §0; the sections above already reflect them.
Remaining dependency: the RAGLab CLI/`--help` and `/openapi.json` (field names) plus the target
`http://<ip>:<port>`, needed from Slice 3 onward (`RAGLAB_13_ENTRIES.md` §8). Slice 0 needs neither.

Still needed from you, independent of the above: the RAGLab CLI/`--help` and `/openapi.json` (field
names), and the target `http://<ip>:<port>` to exercise against — see `RAGLAB_13_ENTRIES.md` §8.
