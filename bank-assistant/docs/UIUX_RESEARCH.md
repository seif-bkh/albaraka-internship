# UI/UX research — P0 + P2 features (2026-09-07)

Required by AGENTS.md §9: before changing UI/UX, research how existing modern
web-banking solutions implement the feature, and record the findings. This file
covers the five features delivered in the 2026-09-07 batch: financing
simulator, appointment, advisor contact, news feed, prayer times.

Context: benchmark vs **BANECTI** (`banecti.com.tn` + the Al Baraka mobile
app) drove the gap list — see AGENTS.md §10.

---

## 1. Financing / loan simulator

**References**

| Source | What it shows |
|---|---|
| [Meritrust calculators](https://colorado.meritrust.org/calculators/) | "Enter the loan amount and we calculate your payment" — bidirectional; sliders change payment/amount/rate/term instantly |
| [MCT CU simple loan calculator](https://www.mctcu.org/calculator/simple-loan.html) | Slider + typed input; result shows monthly payment, total to repay, total interest; explicit "this is an estimate" disclaimer |
| [SliderCalc](https://www.mindshocker.com/slider-calc/) | What-if scenario tool: recalculation on every change, no extra clicks |

**Common pattern.** Inputs: amount + term (sliders with a typed value), rate
shown as a *fixed bank value*, never client-editable. Results: monthly payment
(big number), total to repay, total interest/profit. Recalculation is
instant. Every serious implementation carries an "estimate only" disclaimer.

**Adaptations for this project**

- **Rate is governed data, not UI data.** The rate comes from the
  `business_facts` table (`financement` / `rate` / `topic`). Only products
  with an active rate fact are offered; while none is configured the dialog
  shows the honest "not available yet" state — the same refuse-not-guess
  posture the chat uses.
- **Server-side computation.** `FinancingSimulationService` is the single
  source of truth (rates can be edited per language/admin at any time). The
  frontend re-requests on change (debounced), it never re-implements the math.
- **Islamic math, linear, no compounding** (Mourabaha: the profit margin is
  fixed at inception): `totalProfit = amount × rate × (months/12)`,
  `monthly = (amount + totalProfit) / months`.
- **Placement:** dialog opened from the portal-home quick tile (the home
  stays uncluttered); result inline; "estimate only" disclaimer in 3 languages.
- Amount bounds 500–5 000 000 TND, term 1–180 months (named constants).

## 2. Appointment (rendez-vous conseiller)

**References**

| Source | What it shows |
|---|---|
| [U.S. Bank — schedule an appointment](https://www.usbank.com/customer-service/knowledge-base/KB0069674.html) | Flow: type of appointment → service topic → meeting method (in-person / phone / video) → branch → optional preferred staff member → date & time. Always a confirmation |
| [Bank of Hawaii — Bank by Appointment](https://www.boh.com/bank-by-appointment) | Book by appointment type + free-text notes; confirmation sent to the client; 24 h minimum notice |

**Common pattern.** Topic/purpose first, then *how* (in person / phone /
video), then *where* (branch), then *when* (date + time slots), optional
notes; confirmation state right after submitting; no instant booking — the
bank confirms.

**Adaptations**

- No branch catalogue exists in governed data yet (P1 deferred — branch/hours
  dataset). So "preferred branch" is an **optional free-text field**, shown
  only when "in person" is chosen. We do not invent a branch list (governance:
  refuse rather than guess).
- Date: native date input, **min = tomorrow, max = +60 days** (24 h notice).
  Time: 30-minute slots 09:00–16:30.
- Persisted as a *request* (`advisor_requests`, status `EN_ATTENTE`) — the
  confirmation text tells the client the advisor calls back to confirm. This
  matches the "request, bank confirms" pattern of both references.
- Placement: dialog from the portal-home quick tile; success panel with a
  summary of the request.

## 3. Advisor contact

**References** — same bank scheduling pages above, plus standard bank
"contact us" pages: contact card (phone / e-mail) + a callback-request form
(phone, preferred moment, message).

**Adaptations**

- Contact details (phone/e-mail) are **governed facts**
  (`service` / `contact`), served by `GET /api/client/me/advisor/contacts`.
  While none is configured the dialog shows no numbers — it offers the
  callback form only. Nothing is hardcoded.
- Callback form: telephone (prefilled from the profile), preferred time
  (time slots), message. Persisted in the same `advisor_requests` table with
  `kind = CALLBACK`.

## 4. News feed

**References** — BANECTI's own dashboard (title + date + excerpt + "see more"
link, newest first) and standard bank web news sections.

**Adaptations**

- Admin CRUD in the back-office (`/admin/news`, FR-only like every other
  admin page): title, body, optional external link, publish date, active
  flag. Inactive/`published_at` in the future → not shown to clients.
- Display: `<app-news-section>` on the **portal home** and on the **landing**
  page (public `GET /api/news/active`, newest first, capped, hidden when
  empty).

## 5. Prayer times

**References**

- **Aladhan API** ([aladhan.com](https://aladhan.com), [calculation methods](https://aladhan.com/calculation-methods)):
  `GET https://api.aladhan.com/v1/timingsByCity?city=…&country=Tunisia&method=18`.
  **Method 18 is the official "Tunisia" method** (Fajr 18°, Isha 18°, default
  location Tunis 36.8065/10.1815) — verified against
  `https://api.aladhan.com/v1/methods`. The API is the one used by most
  public prayer-time widgets and is called client-side (CORS-open).
- BANECTI's dashboard renders the five prayer times as a card (their version
  ships a bug: raw i18n keys `PRAYER_TIME.AL_ASR` visible in production — we
  do the i18n properly).

**Adaptations**

- `<app-prayer-times>` on the portal home: five prayers (Fajr, Dhor, Asr,
  Maghrib, Isha) + **next-prayer highlight with a live countdown**
  (recomputed every minute).
- City/country/method/api-url come from `environment.ts` (config, not code);
  defaults: Tunis / Tunisia / method 18.
- **Fails open:** any network/parse error hides the widget entirely — a
  decorative widget must never break the portal or show a red error banner.
- Times are the city's local time (Tunisia). Viewers outside the same
  timezone see the Tunis times — acceptable and documented; the bank's
  audience is Tunisian.


---

## 6. Assistant widget — one fixed box, and no provenance for the customer (2026-09-21)

Requested by the maintainer: the whole answer must be readable without scrolling inside a
bubble, and customers (clients and non-clients) must never see which documents the
answer came from.

**What comparable products do**

| Product / source | Sizing | Provenance |
| --- | --- | --- |
| ChatGPT, Gemini, most hosted assistants (as summarised in the `indah` shell fix, PR #56) | the message area is a **fixed-height viewport** with the input pinned below; the container scrolls, the reply never grows its own scrollbar | sources are for the reviewer, and a collapsed affordance at most for the user |
| Bubble chat widget thread (forum 307488) | the classic bug is exactly the one we avoid: the message list scrolls *and* the page scrolls, pushing the composer off-screen | — |
| Chatbox CSS thread (SO 61609514) | `max-height` on a bubble container produces "no scrollbar at all" — the answer is clipped, not scrollable. Fix is: grow the bubble, scroll the thread | — |
| `trade/ui` issue #41 + OpenReplay on viewport units | `100vh` on mobile is the *large* viewport, so a full-height panel loses its composer behind the URL bar. Recommended: `vh` then `dvh` (or `svh`) with a fallback | — |
| Intercom Fin (Inkeep's comparison, 2026) | the customer-facing answer has no structured inline citation mechanism; the citations live in **agent-assist** and in the conversation log a human reviews. Per the Fin write-up, reps audit citations in the transcript, not in the widget | citations = reviewer surface |

**Decisions taken (and why)**

- **Fixed panel, sized for the answer**: 420 × 620 px on desktop, `100%` ×
  `calc(100vh - 80px)` on mobile (`dvh` where supported). 380 px of thread at
  16 px / 1.6 is ~14 lines — the reference answer length in this corpus — so the
  95 % case needs no scrolling at all. `max-width/max-height` remain only as
  viewport guards on a window shorter than the panel.
- **The bubble grows; the thread scrolls.** No `max-height` and no `overflow-y` on
  `.bubble` (an explicit `max-height: none; overflow: visible` documents the rule
  so nobody "improves" it back), bubble width capped at 90 % of the panel.
- **16 px, line-height 1.6** on both the bubble and the composer, up from 15 / 1.55:
  legible without zoom, still fits the fixed box.
- **Provenance is stripped at the edge, not in the browser** (`AnswerSanitizer`):
  markers (`[S1]`, `[doc_12]`), `(Source : …)` in FR/AR/EN, `Sources:` blocks, bare
  file names and internal URLs are removed from the *stored-and-served* answer, and
  the response object no longer carries a `sources` field at all. The front ends keep
  a display tidy-up as a belt for older/cached payloads only.
- **A streaming turn cannot leak a marker across two SSE frames**: the edge relays
  deltas through a bounded scrubber (≤ 48 chars held back) and the terminal `done`
  frame carries the fully sanitised text.
- **The mirror image is the admin**: `/api/admin/monitoring/live-feed` gains a
  `documents` list (every chunk retrieval consulted, with the dropped ones marked)
  and the tokens / latency / asked_at / answered_at row, and the customer history
  detail (`/api/clients/conversations/{id}/messages`) gains an `observability`
  block. What the customer is not shown is not deleted — it moves to the reviewer.

**Risks recorded**

- "Why do you not tell me where this comes from?" is a real question for a banking
  assistant. The honest answer shipped in the UI is the `chat.notGrounded` notice
  (an answer with nothing behind it says so) plus the disclaimer already in the
  widget footer; if the bank later wants *public* citations, that is a product
  reversal, and it belongs in the contract before the code.
- The landing FAQ promised the assistant "cites ses sources"; the copy now says it
  answers only from validated documents and keeps them to itself. The maintainer can reword.

---

## Out of scope this batch (recorded so the research stays honest)

- Public-chat right-rail widgets (orphaned `right.*` i18n keys exist — the
  original design intended prayer times / appointment / news there too).
- Branch/ATM directory, exchange-rate widget (P1, deferred).
- Appointment *confirmation* workflow on the admin side (the request is
  visible to staff via the DB; a dedicated admin screen can come later).
