# Console RAGLab — the visual pass and the performance pass

This is the record of one piece of work: reshaping the RAGLab console (the screen the bank
uses to operate the assistant) and removing the cost it added to the whole application.
It documents where the design rules came from, what was changed, and — the part that is
usually missing — what was *not* changed and why.

## 1. Skills used

Two agent skills were taken from the public `anthropics/skills` repository
(`github.com/anthropics/skills`, read at `dbd4588f9e10`, 2026-10-09) and applied as written:

| Skill | What it contributed here |
|---|---|
| `skills/frontend-design` | The two-pass process (token plan → critique → build), the ban on templated defaults, the rule that structure must encode information, and the "spend your boldness in one place" restraint |
| `skills/webapp-testing` | Its verification discipline — the app is checked through its own test suite and a real build; a browser pass with Playwright was attempted but the browser binaries cannot be downloaded from this sandbox (only GitHub/npm/PyPI are reachable), so the visual result was verified structurally instead of by screenshot |

Nothing from those skills is vendored into this repository: they are guidance, not
dependencies. The scanner-friendly, reproducible part is the code below.

## 2. The design plan (pass one, before touching code)

Subject: an operations console inside a Tunisian Islamic bank. Audience: document
managers, supervisors and the general manager — people who use it for two minutes, decide
whether the assistant is healthy, and leave. The job of the screen: **answer "can it
answer, and is what it answers current?" in one saccade, then get out of the way.**

- **Palette.** Reuse the bank's existing tokens (`--surface #fff`, `--bg #f4f5f7`,
  `--text #16181d`, `--border #e3e6eb`), brand red `#e0122f` for the current screen and
  focus only, and the semantic trio already in `styles.css` (`--ok`, `--warn`, `--err` with
  their soft variants) for the state lamps. No new colour was introduced; a console that
  invents a palette stops looking like part of the bank.
- **Type.** Inter, already self-hosted. A four-step scale only: 22 px (screen title),
  13 px (body and tabs), 12 px (values), 11 px (labels). Numbers use
  `font-variant-numeric: tabular-nums` so a fragment count does not wobble when it changes
  during a poll; model names and identifiers use the mono stack, because they are data,
  not prose.
- **Layout.** One column, three bands:
  - a header that states what the screen is for and links to the address/diagnostics screen;
  - a single instrument strip — six readings separated by hairlines, not six rounded pills
    in a row;
  - the screen itself in one bordered surface with a reserved minimum height.

  ```
  Console RAGLab                                   Adresse & diagnostic →
  ────────────────────────────────────────────────────────────────────────
  ● RAGLab v1.2.3 │ Liaison Connecté │ Modèles nvidia… │ Corpus 346 │ …
  ────────────────────────────────────────────────────────────────────────
  Question fondée │ Corpus │ Recherche │ Réglages │ Évaluations
  ────────────────────────────────────────────────────────────────────────
  ┌ the screen ──────────────────────────────────────────────────────────┐
  ```

- **Principles.** Separation lines replace boxes; a lamp is the only decoration and it
  carries state; every label is a noun an operator already uses ("Corpus", "Clés",
  "Liaison"); errors name the next action instead of apologising.

**Pass-two critique — what was rejected as the generic default.** The first draft had five
rounded pills with soft shadows and a badge saying "Service HTTP externe" — the standard
SaaS-card kit, and a label that said nothing. It was replaced by the instrument strip with
hairlines and one lamp per reading, and by a real "Liaison : Connecté / Injoignable"
reading. The subtitle that described the product in marketing terms became one sentence
about what the screen is for. Numbered markers, gradient washes and entrance animations
were never added: there is no sequence and there is nothing to reveal.

## 3. What changed

**Shell (`admin-raglab.component.*`)**
- `OnPush`, and the two permission flags are now signals fed from `currentUser$` rather
  than getters over a `BehaviorSubject` (a getter under `OnPush` can freeze a tab away if
  the user resolves after mount).
- Tabs renamed to the operator's vocabulary: *Question fondée*, *Corpus*, *Recherche*,
  *Réglages*, *Évaluations*.
- `aria-current="page"` on the active tab, `focus-visible` rings on tabs and links,
  `prefers-reduced-motion` respected, one breakpoint at 900 px.
- The shell now owns the poll lifecycle: it starts polling on mount and stops on destroy.

**Instrument strip (`status-bar/*`)**
- Rebuilt around six readings, each with a label, a value and optionally a lamp or a chip.
- `aria-live="polite"` only on the sealed banner (a live region on the whole strip would
  announce a countdown nobody asked for).
- A "vérifié à HH:mm:ss" reading, so an operator can tell a stale screen from a failing
  service.
- An unreachable service now explains what to do and links to *Adresse & diagnostic*,
  including the consequence: refusals rather than invented answers.
- The refresh control gained a text label next to the icon and disables itself while a
  request is in flight.

**The five screens** — the shell and strip were rebuilt against the bank's tokens, but the
screens they host were still painted from a private palette: 442 literal colours across five
stylesheets, four different greens for "ok", six blues, and a `var(--brand-red, #c80f28)`
whose property is defined nowhere, so the brand accent silently fell back to an undefined
variable. Every literal was mapped to the nearest existing token (neutrals to
`--text/--text-2/--text-3/--border/--surface-2`, semantic families to `--ok`, `--warn`, `--err`,
`--info` and their soft variants, the decorative magentas to `--neutral`), and the dead
`--brand-red` fallbacks became `--brand`. Only the strip's own deliberate fallbacks — for
contexts where the token file is absent — still carry hex values. Nothing was repositioned:
this is a colour pass, so the screens keep their layout and the risk stays at zero for
structure. The practical effect is that an operator moving from the strip to a screen no
longer crosses into a different application, and that a future brand change is one file.

**Lists** — `trackBy`/track expressions on the five heavy lists (chat turns, claims,
evidence, sources, document rows, upload queue, search hits, corpus rows, providers,
models). Re-running a search or refreshing after an upload now reuses the existing DOM
instead of rebuilding it, which also keeps scroll position and focus.

**Routing** — two fixes that the visual pass exposed:
- `/admin/raglab` used to redirect everyone to *ask*; a read-only role (documents/audit)
  was then refused by that child route's guard and thrown out of the console it had just
  opened. `RaglabLandingGuard` now lands them on the corpus.
- *Réglages* had no guard: hiding the tab was the only protection. It is now
  `RAGLAB_CONSOLE_MANAGE` at the route, like *Évaluations*.

## 4. The performance pass

The console was not the problem; the service behind it was.

| | Before | After |
|---|---|---|
| Where it polls | `RaglabService` constructor — `providedIn: 'root'`, so on the landing page, the portal, every admin screen, every tab | only while `AdminRaglabComponent` is mounted |
| Interval | fixed 3 s, forever | 2 s while an ingest runs · 20 s healthy · 60 s when the service is down |
| Hidden tab | kept polling | skipped; a fresh reading is taken the moment the tab becomes visible |
| Overlap | a slow backend could queue requests | at most one `/health` in flight; a manual refresh is ignored while one is pending |
| Change detection | each tick ran app-wide CD through zone.js | the timer is created with `runOutsideAngular`, and the strip is `OnPush` on signals, so a tick repaints the strip only |

Per open console, the old behaviour was 28 800 requests a day and 28 800 full
change-detection passes; the new one is 4 320 requests a day when idle, zero when the tab
is hidden, and zero for every user who is not looking at the console.

**Scope of `OnPush`.** It is applied to the two components that are signal-driven (shell and
strip). The five screens keep the default strategy on purpose: they update plain fields from
RxJS subscriptions, so switching them to `OnPush` without converting their state to signals
would produce a screen that silently stops refreshing — a worse bug than the one being
optimised. Converting them is a follow-up with its own tests, listed in §6.

## 5. How this was verified

- `npm run build` — production build green (lazy chunks unchanged in shape, no style-budget
  warning).
- `npm test -- --watch=false` — 20 files, 91 tests green, including six new ones on the
  polling contract: silence before `startPolling`, the 20 s cadence, the 2 s cadence while
  sealed, the 60 s backoff while down, no requests on a hidden tab plus an immediate one on
  return, no overlap, and the timestamp of the last reading. Two more cover the landing
  guard.
- Not verified: a screenshot. The Playwright skill needs browser binaries from
  `cdn.playwright.dev`, which this sandbox cannot reach. The layout was checked through the
  rendered DOM in tests and the CSS by inspection — a human should still open the console
  once on the workstation before this is called done.

## 6. What is deliberately left for later

1. `OnPush` + signals for the five screens (§4).
2. The settings screen is 348 lines of TypeScript and 477 of template; it deserves splitting
   into "modèles", "clés", "profil" child components before it grows further.
3. The documents screen still refreshes the whole list after each upload; a single-row
   update would be cheaper on a large corpus.
4. No screenshot-regression test: it needs a browser in CI, which the current runner setup
   does not provide.
