# RAGLab's 13 CLI entries — what each one is, and how it should reach the admin

**Date:** 2026-09-15 · **Status:** decision document — nothing implemented. Attempt #1 was reverted
(see §7); this is the "elaborate all 13, then agree the shape" step.
**Companion:** `docs/RAGLAB_SWITCH_REVIEW.md` (the contract comparison this builds on).

---

## 1. The two things that are already decided by your messages

1. **RAGLab is a service on the network, addressed like in production** — `http://<ip>:<port>`
   (e.g. `http://10.20.30.40:8000`), not `localhost`, not a container name, not a Docker-network
   alias. Whatever we build has to be exercised against a real address, because that is what will
   run in prod.
2. **The 13 CLI entries are the functional spec.** They are what an operator must be able to do
   from the admin; the question is *how* each one lands in the UI, not *whether*.

## 2. Ground rules I propose (correct me if any is wrong)

| # | Proposal | Why |
|---|---|---|
| G1 | The **Spring Boot backend is the only client** of RAGLab. The browser never calls the RAGLab box directly. | One origin, no CORS on an internal IP, RAGLab has no auth of its own, and roles/quotas/audit already live in Java. |
| G2 | The target address is **configuration**, shown permanently at the top of the console, and every screen fails loudly and visibly when it is unset or unreachable. | "Connect to an IP:port like prod" only works if the address is visible and testable, not hidden in a compose file. |
| G3 | **Read-only entries default on; state-changing entries default off**, flipped per deployment by an env flag. | RAGLab mutating endpoints are unauthenticated (`POST /profile`, `/ingest`, `/keys`). The only guard today is the network. |
| G4 | Every state-changing action carries a **reason** and is written to our DB (who, when, what, result). | Same pattern as the existing governance writes (`BusinessFactAudit`). |
| G5 | We render **claims + verbatim quotes**, never the free-text `answer` field under a "verified" badge. | Per the contract, only `evidence[].quote` is gate-checked; `answer` and `claims[].text` are model prose. |
| G6 | Entries that spend money or take minutes are **explicit, confirmed, one at a time** — never on page load, never polled. | `/evaluate`, `/ingest`, `/embeddings/sanity` are unattended spend otherwise. |

## 3. The 13 at a glance

| # | CLI entry | RAGLab call(s) | Changes state? | Spends | Where it belongs |
|---|---|---|---|---|---|
| 1 | status / doctor | `GET /health` (+ job state) | no | free | always open — the console's home |
| 2 | providers & models | `GET /models`, `POST /profile` | **yes** (global profile) | free now, rebuild later | config panel, gated |
| 3 | API keys | `GET /keys`, `POST /keys`, `DELETE /keys/{env}` | **yes** (service secrets) | free | config panel, strictest role |
| 4 | inspect corpus | `GET /inspect` | no | free (CPU) | always open |
| 5 | search chunks for a text/quote | `POST /chunks/search` | no | free | always open — support tool |
| 6 | embedding sanity | `POST /embeddings/sanity` | no | **1 batched embed call** | on-demand button |
| 7 | ingest / rebuild | `POST /ingest?reset=`, job state | **yes** (the index) | **n embed calls** | gated, confirmed, progress |
| 8 | retrieval query | `POST /search` | no | 1 embed call | always open — diagnosis |
| 9 | grounded answer | `POST /answer` | no | 1 embed + 1 chat | on-demand, the money screen |
| 10 | chat over the documents | `/answer` in a loop | no | per turn as #9 | convenience wrapper |
| 11 | evaluate a question set | `POST /evaluate` | no | **N × embed (+ chat?)** | gated, long-running |
| 12 | service settings | service config (chunking / retrieval / corpus) | **yes** | free | config panel, gated |
| 13 | diagnostics | `POST /diagnostics/harness50`, `/diagnostics/catalog` | no | harness: long/offline · catalog: live provider call | gated, long-running |
| 0 | quit | — | — | — | *not a feature; the CLI's exit. Nothing to build.* |

## 4. One entry at a time

### 1. `status` / `doctor` — "is it alive, and what is it waiting for?"
- **In:** nothing. **Out:** service version, active profile (embedder + chunking), index/collection
  state, whether provider keys are present, current ingest job, and the error vocabulary
  (`empty_index`, `missing_api_key`, `provider_error`, `profile_switching_disabled`).
- **Cost / risk:** none / none.
- **Screen:** a card showing the target (`http://<ip>:<port>`), reachable-or-not, latency, version,
  active profile, index line (built for which profile, how many chunks, age), key presence, and a
  **plain-language verdict + next action** ("index empty → run 7", "no API key → set it in 3").
- **To confirm:** does `GET /health` expose the index age/chunk count, or only `POST /inspect` does?
  Do we poll it (I propose: on open, manual refresh, and after every mutation — never in the
  background)?

### 2. `providers & models` — "which embedder and which chat model are we on?"
- **In:** nothing to read; to change: the profile pair. **Out:** the provider/model catalogue and
  the currently pinned pair (per the contract: NVIDIA embeddings + xKiro chat, and the index is a
  collection *per `(embedder, chunking)` pair*).
- **Cost / risk:** the switch itself is free, but **switching to a pair with no index leaves the
  service with nothing to retrieve** until #7 runs — i.e. the bad case is a silent
  `empty_index` for customers. `profile_switching_disabled` exists precisely to prevent this.
- **Screen:** current profile read-only at the top; the catalogue as a list; a switch control that
  shows, per candidate pair, whether an index already exists, and a loud "this needs a rebuild
  (entry 7) and answers change when it finishes".
- **To confirm:** is profile switching enabled on your box at all (i.e. can the console even offer
  it, or is the CLI reading a flag)? Are the "models" per provider a static list or a live provider
  call?

### 3. `API keys` — "keep / change / add / remove a key on the service"
- **In:** provider env name + secret value (write-only). **Out:** the list of provider env names and
  whether each is set.
- **Cost / risk:** free, **highest blast radius of the four config entries**: replacing a working key
  with a wrong one, or deleting a key the active profile needs, stops every answer (`missing_api_key`
  → `provider_error`). Per your note, the service redacts keys out of its own error bodies — we must
  not undo that by logging the value on our side.
- **Screen:** table `provider env → set / not set`; actions *keep* (do nothing), *replace*, *remove*;
  the field is never pre-filled, never echoed back, never stored in our DB — only the env name and
  the outcome ("replaced at 14:02 by admin@…"). After a change, the screen re-runs #1 to show the
  effect.
- **To confirm:** does `POST /keys` persist to the service's `.env`/disk or only into the running
  process? (It decides whether the console must warn "lost on restart".) Can `GET /keys` return a
  masked tail, or only presence?

### 4. `inspect corpus` — "how is my corpus actually cut into chunks?" (no model calls)
- **In:** optionally a limit/document filter. **Out:** the documents found in the corpus directories,
  the chunk count/size distribution, and the actual chunk boundaries with their text.
- **Cost / risk:** free (CPU only) / none. A very large corpus can be slow → we cap the response.
- **Screen:** totals (files, chunks, chars, min/avg/max chunk size); per-document table; chunk viewer
  with text and offsets; a small "pathologies" hint (chunk too short to cite, duplicate chunks,
  document silently absent). This is also where an operator *sees* that the corpus path from #12 is
  the right folder.
- **To confirm:** does `/inspect` list the files it discovered (so it doubles as a path check), and
  does it read the live directory or a cached stat?

### 5. `search the chunks for a text/quote` — "could this quote ever validate?" (no model calls)
- **In:** a text/quote. **Out:** the chunk(s) containing it, with document, chunk id, heading.
- **Cost / risk:** free (no embedding, no chat) / none.
- **Screen:** one search box; results as `document › heading › chunk_id` + snippet with the match
  highlighted; a verdict line: *"present in 2 chunks — a quote from here can validate"* vs
  *"not found anywhere in the corpus"*. This is the answer to the support question "the model quoted
  the document correctly and the answer was still refused — why?" (pair it with #1/#8/#9).
- **To confirm:** exact substring or normalized/fuzzy? Case/accent/diacritic sensitivity matters for
  French and Arabic text, and it decides whether "not found" is a hard fact or a near-miss.

### 6. `embedding sanity check` — "is the embedder sane and are the languages comparable?"
- **In:** nothing (fixed probe sentences). **Out:** cross-lingual cosine similarities (FR/AR/EN of
  the same meaning should sit close), dimension/model identity.
- **Cost / risk:** **one batched embedding call** (real money, small) / none; nothing written.
- **Screen:** a *Run check* button that states the cost before it fires, then a cosine matrix and a
  verdict ("embedder responds, languages comparable" / "vectors look wrong — check the profile and
  the key"), plus raw values on demand.
- **To confirm:** are the probe sentences the service's fixed set or do we pass ours? Does it need the
  index, or only the embedder + key?

### 7. `ingest / rebuild the index for this profile` — the one that costs money and changes answers
- **In:** a reset flag at most. **Out:** a job (one at a time, whole corpus — the caller cannot scope
  it to a single document).
- **Cost / risk:** **n embedding calls, minutes to tens of minutes.** It is the *only* entry that
  changes what customers retrieve: mid-rebuild (or after a failed rebuild) retrieval can be empty or
  partial, and a `reset=true` wipe is destructive until the new pass finishes.
- **Screen:** index state line (which profile, chunk count, age); *Rebuild* with an optional reset
  toggle; confirm + reason; progress (n/m chunks if the job reports it); log tail; an explicit
  warning that the retrieved corpus changes when it finishes; the button disabled while a job runs
  (the service enforces one job anyway).
- **To confirm:** progress granularity; is the service still able to answer during an ingest; does
  `reset=true` drop only the active profile's collection or everything; what happens to a rebuild
  that fails halfway (empty index or previous data kept)?

### 8. `retrieval query` — "is the right passage even reachable?" (no chat model)
- **In:** a question, `k` (1–20), `mode ∈ vector | rrf | blend`, optional `lang_filter`,
  `neighbor_radius` (0–2). **Out:** ranked chunks with scores and their document/heading.
- **Cost / risk:** 1 embedding call for the query / none. This separates *retrieval* from
  *generation*: when a customer gets a refusal, this says whether the passage was there at all.
- **Screen:** question box + the four controls (with sane defaults, not all 20 combinations exposed);
  ranked hits with score, document, heading, snippet; a note of the score a refusal would trip on.
- **To confirm:** defaults for `k`/`mode`/`neighbor_radius`; is there a visible refusal threshold, or
  is `insufficient_evidence` decided inside `/answer` only?

### 9. `grounded answer` — "one question, answered and cited" (the product call)
- **In:** a question (+ the same retrieval knobs). **Out:** `status ∈ answered | refused | greeting`,
  `answer` (prose), `claims[{text, evidence[{source_id, quote}]}]`, `sources[]`, and the flags
  `cached` / `inference_performed` / `seconds`.
- **Cost / risk:** 1 embed + 1 chat call (skipped for greeting / cache hit) / low; the risk is
  *presentational*: badging this as "verified" would overstate it (G5).
- **Screen:** the ask box; the **claim → quote table** as the centrepiece (that is RAGLab's real
  feature); the source list (document, chunk id, heading); status + reason in plain language
  (`insufficient_evidence`, `no_context`, `private_or_live_request`, `invalid_output` — each with
  "what to do next"); a raw-payload toggle. On refusal, offer the one-click jump to #8/#5 with the
  same question.
- **To confirm:** do we want an excerpt toggle (`include_excerpts`) in the console, and is `cached`
  worth surfacing (it changes what a repeated question costs)?

### 10. `chat over the documents` — several questions in a row
- **In:** a sequence of questions. **Out:** per the contract, RAGLab has **no sessions**: each turn
  is an independent `/answer`; history is the client's job. (Today our edge assembles history and
  neither engine reads it — a separate, Java-side improvement.)
- **Cost / risk:** as #9 per turn / none.
- **Screen:** a transcript that renders each turn with the same claim/quote view, an explicit
  "no memory between turns" note, and — optionally — a toggle to prepend the last N turns to the
  question client-side so we can see whether follow-ups ("et pour la carte Gold ?") improve. Without
  that toggle the console would merely hide the limitation.
- **To confirm:** do you want the console to test client-side history rewriting, or stay a faithful
  stateless loop?

### 11. `evaluate a question set` — regression numbers, not one-offs
- **In:** a question set. **Out:** hit@1/3/5 by category and by language (per the contract).
- **Cost / risk:** N × embed, plus N × chat if it answers rather than only retrieves; minutes of
  service time while the box is busy. Runs **supervised** — never scheduled by us.
- **Screen:** choose the set → run (progress + elapsed) → results table with the per-category/per-
  language breakdown; the run is recorded in our audit table (who, when, which set, headline
  numbers), so a before/after comparison across config changes is possible.
- **To confirm:** where does the question set live (files on the RAGLab host? can the console pick
  from a list?) and does `/evaluate` call the chat model or only retrieval? That single answer decides
  whether this entry costs cents or euros.

### 12. `service settings` — chunking, retrieval, corpus
- **In:** the service's own knobs: chunk size/overlap (their pinned chunking is `restructure`,
  220/40 tokens), retrieval defaults (`k`, `mode`, thresholds), corpus directories (`data_dirs` —
  which are paths **on the RAGLab host**, not ours).
- **Cost / risk:** free, but three different traps: changing chunking **invalidates the index**
  (rebuild required); pointing the corpus at the wrong folder produces a silent `empty_index`;
  changing thresholds quietly changes how often customers are refused.
- **Screen:** a form pre-filled with current values, each field tagged with its consequence
  ("needs rebuild" / "changes refusal rate" / "service-side path"), confirm + reason + audit, and a
  *Rebuild now* shortcut that jumps to #7 with the new settings.
- **To confirm:** which of these settings actually exist, are they persisted on the RAGLab host or
  per-process, and does the service validate them (or accept a bad corpus path silently)?

### 13. `diagnostics` — offline harness + model catalogue
- **In:** nothing. **Out:** with `harness50`, the 50-case offline harness result (a regression
  check for upgrades); with `catalog`, the xKiro model catalogue (which chat models the provider
  actually offers, pinned pair included).
- **Cost / risk:** harness is offline but **long** (minutes); the catalogue is documented as a live
  provider call — i.e. the only way to prove a model SKU still exists. Nothing touches the corpus.
- **Screen:** two clearly separate actions with expected duration and what each proves; results as a
  report (pass/fail counts; the model list), with the raw payload on demand; both filed in the audit
  log so an upgrade has a "before".
- **To confirm:** exact cost/duration of each, whether `harness50` spends anything at all, and
  whether the catalogue is a provider call or a local list.

## 5. Cross-cutting: connection, roles, cost, failure

**The address.** One place holds it — the backend config — and one screen shows it. I propose:
`RAGLAB_URL=http://10.20.30.40:8000` (no trailing slash, `https://` allowed if prod terminates TLS),
plus an optional header token if a gateway sits in front. Every call uses it; nothing anywhere
assumes `localhost` or a container hostname. Timeouts differ by call class: seconds for
status/inspect/search/answer, **minutes** for ingest/evaluate/harness — the long ones are polled by
job status instead of holding one HTTP request open.

**Roles.** Three tiers, mapped to what already exists in the repo (`SUPER_ADMIN`,
`ADMIN_DOCUMENTS`, `ADMIN_SUPERVISION`, `VIEWER`): *read* (1, 4, 5, 8 — the diagnostic core, any
staff), *spend* (6, 9, 10, 11, 13 — costs money: SUPER_ADMIN + ADMIN_DOCUMENTS + ADMIN_SUPERVISION),
*write* (2, 3, 7, 12 — changes the service: SUPER_ADMIN + ADMIN_DOCUMENTS; **keys: SUPER_ADMIN
only**).

**Audit.** Every write and every spend produces a row in our DB (actor, entry, parameters — never
key values — result, timestamp, reason). This is what makes "who emptied the index at 3 a.m." and
"who spent 400 embedding calls" answerable later.

**Failure.** The console must stay useful when RAGLab is down: a status screen that says
"unreachable at `http://<ip>:<port>` — `connect timed out`", and every other panel disabled with the
same reason. No half-states, no silent fallbacks; and — decision point — **customer chat is not
switched by accident** (see question 3).

## 6. Suggested order, if you agree with the staging

1. **Slice A (proves the connection, cannot break anything):** 1 status · 4 inspect · 5 chunks
   search · 8 retrieval · 9 grounded answer. Read-only or single-call, and 9 is the screen that
   tells us whether the answers are worth the switch at all.
2. **Slice B (configuration):** 2 profile · 12 settings · 3 keys — all gated, all audited, keys last.
3. **Slice C (heavy):** 7 ingest with progress · 11 evaluate · 13 diagnostics · 6 sanity · 10 chat.

## 7. Where we stand right now

Branch `arena/01a0a056-bank-assistant` was rolled back to the commit before attempt #1
(`ac71b73`), plus one workflow-only commit; attempt #1 is preserved as tag
`archive/raglab-attempt-1` (`b061f7b`) if any part of it is worth salvaging. What was wrong with it,
honestly: it was designed and built before this conversation — it invented the UI's structure
(15 panels of my choosing, not your 13), picked the networking (`localhost` / Docker aliases) instead
of a production IP:port, decided on its own that an admin would switch the customer-facing engine,
and guessed at the contract wherever the pasted document was silent. This document exists so that
those four things are your decisions this time.

## 8. What I need from you to start

1. The decisions in the four questions I asked in the chat (address handling, UI shape, engine
   scope, write policy).
2. The **real contract**: the CLI's own `--help`/README and the service's `/openapi.json` (or the
   endpoint list with field names). Your paste said `/openapi.json` is truth for field names — with
   it, the elaboration above becomes exact instead of inferred, and the panels can be written
   against real payloads.
3. The **target address for testing** (`http://<ip>:<port>`) and whether the box is reachable from
   this sandbox's network at all — if it is not, the UI can still be built and unit-tested against
   recorded payloads, but end-to-end verification will have to happen on your side.
