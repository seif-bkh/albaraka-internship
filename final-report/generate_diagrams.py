#!/usr/bin/env python3
"""
Generates clean, high-DPI, zero-artifact institutional logos and 7 crisp,
publication-grade vector (.svg) + 300-DPI (.png) engineering diagrams for
Farah AYARI's Internship Report (ESPRIT x Al Baraka Bank Tunisia).
"""
import os
import numpy as np
from PIL import Image, ImageFilter
import typst

ROOT = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(ROOT, "assets")
DIAGRAMS_DIR = os.path.join(ASSETS, "diagrams")
FONTS_DIR = os.path.join(ROOT, "fonts")


def build_clean_logos() -> None:
    """Produce crisp, transparent-background, true-aspect-ratio logos."""
    os.makedirs(ASSETS, exist_ok=True)

    # 1. Al Baraka Bank single horizontal mark (from albaraka-front.png)
    ab_src = os.path.join(ASSETS, "albaraka-front.png")
    if os.path.exists(ab_src):
        ab = Image.open(ab_src).convert("RGBA")
        arr = np.array(ab)
        alpha = arr[:, :, 3]
        rgb = arr[:, :, :3]
        mask = (alpha > 15) & ~(
            (rgb[:, :, 0] > 248) & (rgb[:, :, 1] > 248) & (rgb[:, :, 2] > 248)
        )
        ys, xs = np.where(mask)
        pad = 8
        ab_crop = ab.crop((
            max(0, int(xs.min()) - pad),
            max(0, int(ys.min()) - pad),
            min(ab.width, int(xs.max()) + pad + 1),
            min(ab.height, int(ys.max()) + pad + 1),
        ))
        ab_hi = ab_crop.resize(
            (ab_crop.width * 2, ab_crop.height * 2), Image.Resampling.LANCZOS
        )
        ab_hi.save(os.path.join(ASSETS, "logo_albaraka_clean.png"))

    # 2. ESPRIT horizontal logo with flat color unmixing (eliminates JPEG mottling)
    es_src = os.path.join(ASSETS, "esprit-tunisia-logo-private-engineering--3.jpg")
    if os.path.exists(es_src):
        es = Image.open(es_src).convert("RGB")
        es5 = es.resize((es.width * 5, es.height * 5), Image.Resampling.BICUBIC)
        arr = np.array(es5).astype(np.float32)
        r, g, b = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2]
        lum = 0.299 * r + 0.587 * g + 0.114 * b
        h, w = lum.shape

        red_raw = ((r - 0.5 * (g + b)) > 48).astype(np.float32) * 255.0
        # 'espr' on left (x < 0.692*W) AND 'Se former autrement' across bottom (y > 0.65*H)
        in_black_zone = (np.arange(w)[None, :] < 0.692 * w) | (
            np.arange(h)[:, None] > 0.65 * h
        )
        black_raw = (in_black_zone & (lum < 150)).astype(np.float32) * 255.0
        in_gray_zone = (~in_black_zone) & ((r - 0.5 * (g + b)) <= 48)
        gray_raw = (in_gray_zone & (lum < 212)).astype(np.float32) * 255.0

        def smooth_mask(m: np.ndarray, radius: float = 1.35) -> np.ndarray:
            im = Image.fromarray(m.astype(np.uint8), "L").filter(
                ImageFilter.GaussianBlur(radius)
            )
            v = np.array(im).astype(np.float32) / 255.0
            return np.clip((v - 0.22) / 0.56, 0.0, 1.0)

        a_black = smooth_mask(black_raw)
        a_gray = smooth_mask(gray_raw)
        a_red = smooth_mask(red_raw)

        canvas = np.zeros((h, w, 4), dtype=np.float32)
        for mask_a, color in [
            (a_gray, (154.0, 160.0, 168.0)),   # ESPRIT cool silver-gray ('it')
            (a_black, (17.0, 21.0, 28.0)),     # Crisp charcoal black
            (a_red, (224.0, 24.0, 45.0)),      # Crisp ESPRIT crimson arrow
        ]:
            src_a = mask_a[:, :, None]
            dst_a = canvas[:, :, 3:4]
            out_a = src_a + dst_a * (1.0 - src_a)
            safe_a = np.where(out_a > 1e-5, out_a, 1.0)
            src_rgb = np.array(color)[None, None, :]
            canvas[:, :, :3] = (
                src_rgb * src_a + canvas[:, :, :3] * dst_a * (1.0 - src_a)
            ) / safe_a
            canvas[:, :, 3:4] = out_a

        ys, xs = np.where(canvas[:, :, 3] > 0.05)
        out_u8 = np.zeros((h, w, 4), dtype=np.uint8)
        out_u8[:, :, :3] = np.clip(canvas[:, :, :3], 0, 255).astype(np.uint8)
        out_u8[:, :, 3] = np.clip(canvas[:, :, 3] * 255.0, 0, 255).astype(np.uint8)
        es_clean = Image.fromarray(out_u8, "RGBA").crop((
            max(0, int(xs.min()) - 12),
            max(0, int(ys.min()) - 12),
            min(w, int(xs.max()) + 13),
            min(h, int(ys.max()) + 13),
        ))
        es_clean.save(os.path.join(ASSETS, "logo_esprit_clean.png"))


PREAMBLE = r"""
#set page(width: 166mm, height: auto, margin: 0pt, fill: white)
#set text(font: "Inter", size: 8pt, fill: rgb("#1E293B"))

#let navy = rgb("#1B2A4E")
#let crimson = rgb("#D91938")
#let amber = rgb("#D97706")
#let teal = rgb("#0F766E")
#let slate = rgb("#475569")
#let muted = rgb("#64748B")
#let linecol = rgb("#CBD5E1")
#let softbg = rgb("#F8FAFC")
#let cardbg = rgb("#F1F5F9")
#let navybg = rgb("#EEF2F9")
#let redbg = rgb("#FDF2F4")
#let tealbg = rgb("#F0FDFA")
#let amberbg = rgb("#FFFBEB")

#let pill(label, fg: navy, bg: navybg) = box(
  fill: bg,
  stroke: 0.45pt + fg.lighten(55%),
  radius: 3pt,
  inset: (x: 5pt, y: 2pt),
  text(size: 6.6pt, weight: "semibold", fill: fg)[#label]
)

#let mono(txt) = text(font: "JetBrains Mono", size: 6.9pt, fill: navy)[#txt]

#let frame(title, subtitle, content, tag: "ARCHITECTURE") = block(
  width: 100%,
  fill: white,
  stroke: 0.75pt + linecol,
  radius: 6pt,
  inset: 11pt,
  [
    #grid(
      columns: (1fr, auto),
      align: (left + horizon, right + horizon),
      [
        #text(size: 9.2pt, weight: "bold", fill: navy)[#title]
        #v(1.5pt)
        #text(size: 7.2pt, fill: muted)[#subtitle]
      ],
      box(
        fill: navy,
        radius: 3pt,
        inset: (x: 6pt, y: 2.5pt),
        text(size: 6.3pt, weight: "bold", fill: white, tracking: 0.04em)[#tag]
      )
    )
    #v(6pt)
    #line(length: 100%, stroke: 0.5pt + rgb("#E2E8F0"))
    #v(8pt)
    #content
  ]
)

#let down_arrow(label) = align(center)[
  #v(2pt)
  #box(inset: (x: 8pt, y: 1.5pt), fill: softbg, stroke: 0.45pt + linecol, radius: 10pt)[
    #text(size: 6.8pt, weight: "medium", fill: slate)[↓  #label]
  ]
  #v(2pt)
]
"""

DIAGRAMS = {
    "fig01_architecture": PREAMBLE + r"""
#frame(
  "End-to-End Platform Architecture: Customer Edge to Governed Intelligence",
  "Strict separation between public delivery, Spring Boot relational ownership, and stateless RAGLab grounding"
)[
  // Tier 1: Public Browser + Reverse Proxy
  #block(width: 100%, fill: softbg, stroke: 0.6pt + linecol, radius: 5pt, inset: 8pt)[
    #grid(
      columns: (auto, 1fr),
      gutter: 8pt,
      align: horizon,
      pill("ZONE 1 · PUBLIC DMZ", fg: crimson, bg: redbg),
      text(size: 7.1pt, fill: muted)[Only externally reachable entry point · TLS 1.3 + HSTS + strict CSP]
    )
    #v(5pt)
    #grid(
      columns: (1fr, auto, 1.15fr),
      gutter: 7pt,
      align: horizon,
      block(fill: white, stroke: 0.6pt + linecol, radius: 4pt, inset: 7pt, width: 100%)[
        #text(weight: "bold", size: 7.8pt, fill: navy)[Browser Client (Angular 21 SPA)]
        #v(2pt)
        #text(size: 7pt, fill: slate)[Trilingual UI (FR / AR / EN + first-class RTL), public widget, client portal #mono("/portal"), staff console #mono("/admin/**")]
      ],
      text(weight: "bold", size: 8pt, fill: crimson)[⇄ HTTPS / SSE],
      block(fill: white, stroke: 0.6pt + linecol, radius: 4pt, inset: 7pt, width: 100%)[
        #text(weight: "bold", size: 7.8pt, fill: navy)[Nginx Reverse Proxy & Static Host]
        #v(2pt)
        #text(size: 7pt, fill: slate)[Serves immutable hashed bundles (#mono("index.html") no-cache), enforces security headers, streams #mono("/api/*") SSE unbuffered]
      ]
    )
  ]

  #down_arrow("Internal reverse-proxy route (/api/**) · Private application network")

  // Tier 2: Bank Edge + Persistence
  #block(width: 100%, fill: navybg, stroke: 0.7pt + navy.lighten(45%), radius: 5pt, inset: 8pt)[
    #grid(
      columns: (auto, 1fr),
      gutter: 8pt,
      align: horizon,
      pill("ZONE 2 · BANK APPLICATION EDGE", fg: navy, bg: white),
      text(size: 7.1pt, fill: slate)[Single relational schema owner · Opens conversation turn BEFORE calling engine]
    )
    #v(5pt)
    #grid(
      columns: (1.35fr, auto, 1fr),
      gutter: 7pt,
      align: horizon,
      block(fill: white, stroke: 0.6pt + navy.lighten(55%), radius: 4pt, inset: 7pt, width: 100%)[
        #text(weight: "bold", size: 7.8pt, fill: navy)[Spring Boot 4 Edge Service (Java 21 Virtual Threads)]
        #v(2.5pt)
        #text(size: 7pt, fill: slate)[• JWT auth + token-family rotation & 12-capability dynamic RBAC\
        • Two-phase quota metering (#mono("gate()") / #mono("settle()")) via 64 striped locks\
        • Derived masked slices (#mono("CustomerContextService")) & 5-tab RAGLab Console]
      ],
      text(weight: "bold", size: 7.8pt, fill: navy)[⇄ JDBC / TCP],
      block(fill: white, stroke: 0.6pt + navy.lighten(55%), radius: 4pt, inset: 7pt, width: 100%)[
        #text(weight: "bold", size: 7.8pt, fill: navy)[PostgreSQL 16 + Redis Queue]
        #v(2.5pt)
        #text(size: 7pt, fill: slate)[• Flyway #mono("V1..V15") (#mono("ddl-auto=validate"))\
        • Core banking, RBAC, audits, chat history\
        • Redis #mono("BRPOP") durable job queue]
      ]
    )
  ]

  #down_arrow("Pinned HTTP/1.1 stream · Constant-time X-Service-Token · Correlation X-Turn-Id")

  // Tier 3: Governed Intelligence Engine + Pinned Models
  #block(width: 100%, fill: tealbg, stroke: 0.7pt + teal.lighten(45%), radius: 5pt, inset: 8pt)[
    #grid(
      columns: (auto, 1fr),
      gutter: 8pt,
      align: horizon,
      pill("ZONE 3 · GOVERNED INTELLIGENCE ENGINE", fg: teal, bg: white),
      text(size: 7.1pt, fill: slate)[Zero public ingress · Zero write coupling to bank database · Explicit no-fallback policy]
    )
    #v(5pt)
    #grid(
      columns: (1.35fr, auto, 1fr),
      gutter: 7pt,
      align: horizon,
      block(fill: white, stroke: 0.6pt + teal.lighten(55%), radius: 4pt, inset: 7pt, width: 100%)[
        #text(weight: "bold", size: 7.8pt, fill: teal)[RAGLab Microservice (Python 3.11 · FastAPI · 25+ Routes)]
        #v(2.5pt)
        #text(size: 7pt, fill: slate)[• 339 restructured Arabic legal chunks (#mono("chunk_fp v4")) in local ChromaDB + BM25\
        • Deterministic intent, evidence plan, sufficiency controller & 198-unit Law index\
        • 3-stage Citation Gate (quote substring + exact digits + policy) → PII scrub → JSONL audit]
      ],
      text(weight: "bold", size: 7.8pt, fill: amber)[⇄ HTTPS API],
      block(fill: amberbg, stroke: 0.6pt + amber.lighten(45%), radius: 4pt, inset: 7pt, width: 100%)[
        #text(weight: "bold", size: 7.8pt, fill: rgb("#92400E"))[Pinned Model Endpoints]
        #v(2.5pt)
        #text(size: 7pt, fill: slate)[• Embeddings: #mono("nemotron-3-embed-1b") (native 2048-d cosine)\
        • Generation: #mono("qwen3.8-max")\
        • Untrusted-data prompt · zero tools]
      ]
    )
  ]
]
""",

    "fig02_chat_sequence": PREAMBLE + r"""
#frame(
  "Customer Conversation Lifecycle: Two-Phase Metering, Streaming & Persistence",
  "Every exchange is persisted before engine invocation, verified before emission, and settled deterministically",
  tag: "EXECUTION LIFECYCLE"
)[
  #grid(
    columns: (1fr, 1fr, 1fr),
    gutter: 8pt,
    block(fill: softbg, stroke: 0.6pt + linecol, radius: 5pt, inset: 8pt, width: 100%)[
      #pill("STEP 1 · QUOTA GATE", fg: navy, bg: navybg)
      #v(4pt)
      #text(weight: "bold", size: 7.8pt, fill: navy)[Striped Lock & Admission]
      #v(2.5pt)
      #text(size: 7pt, fill: slate)[Browser opens #mono("POST /api/chat/message/stream"). Edge acquires per-session striped #mono("ReentrantLock") (64 stripes, virtual-thread safe) and checks token/message budget via #mono("gate()").]
    ],
    block(fill: softbg, stroke: 0.6pt + linecol, radius: 5pt, inset: 8pt, width: 100%)[
      #pill("STEP 2 · PRE-PERSIST", fg: navy, bg: navybg)
      #v(4pt)
      #text(weight: "bold", size: 7.8pt, fill: navy)[Open Turn in PostgreSQL]
      #v(2.5pt)
      #text(size: 7pt, fill: slate)[Java mints correlation #mono("turnId"), opens #mono("ConversationEntity") + #mono("MessageEntity") immediately, and builds masked category slices (no raw IBAN, CIN, or balance leaves the bank).]
    ],
    block(fill: redbg, stroke: 0.6pt + crimson.lighten(50%), radius: 5pt, inset: 8pt, width: 100%)[
      #pill("STEP 3 · ENGINE STREAM", fg: crimson, bg: white)
      #v(4pt)
      #text(weight: "bold", size: 7.8pt, fill: crimson)[Pinned HTTP/1.1 Call]
      #v(2.5pt)
      #text(size: 7pt, fill: slate)[#mono("AgentClient.invokeStream") calls RAGLab over private IP with #mono("X-Service-Token"), #mono("X-Turn-Id"), and last 10 turns — pinned to HTTP/1.1 to avoid #mono("h2c") upgrade rejection.]
    ],
    block(fill: tealbg, stroke: 0.6pt + teal.lighten(50%), radius: 5pt, inset: 8pt, width: 100%)[
      #pill("STEP 4 · SUFFICIENCY", fg: teal, bg: white)
      #v(4pt)
      #text(weight: "bold", size: 7.8pt, fill: teal)[Retrieve & Verify Plan]
      #v(2.5pt)
      #text(size: 7pt, fill: slate)[RAGLab executes dense top-5 search + sufficiency check. If uncovered, runs 1 guided round / bounded 262-node interrogation or refuses cleanly without hallucinating.]
    ],
    block(fill: tealbg, stroke: 0.6pt + teal.lighten(50%), radius: 5pt, inset: 8pt, width: 100%)[
      #pill("STEP 5 · CITATION GATE", fg: teal, bg: white)
      #v(4pt)
      #text(weight: "bold", size: 7.8pt, fill: teal)[Quote, Digit & PII Gate]
      #v(2.5pt)
      #text(size: 7pt, fill: slate)[Every claim must carry a verbatim ≥12-char quote containing every digit cited. Output scrubber masks any #mono("EMAIL / PHONE / RIB / CIN") before JSONL audit emission.]
    ],
    block(fill: amberbg, stroke: 0.6pt + amber.lighten(45%), radius: 5pt, inset: 8pt, width: 100%)[
      #pill("STEP 6 · SETTLE & RELAY", fg: rgb("#92400E"), bg: white)
      #v(4pt)
      #text(weight: "bold", size: 7.8pt, fill: rgb("#92400E"))[SSE Relay & Audit Seal]
      #v(2.5pt)
      #text(size: 7pt, fill: slate)[Edge streams tokens to Angular UI, persists verified provenance + engine tag (#mono("RAGLAB_*")), settles consumed quota via #mono("settle()"), and releases session lock.]
    ]
  )
]
""",

    "fig03_raglab_pipeline": PREAMBLE + r"""
#frame(
  "RAGLab Intelligence Pipeline: Structural Indexing & Citation-Gated Answering",
  "Two-stage architecture turning raw scanned Arabic legal PDFs into deterministic, quote-verified responses",
  tag: "DATA & RAG PIPELINE"
)[
  // Track A: Offline Indexing
  #block(width: 100%, fill: navybg, stroke: 0.65pt + navy.lighten(48%), radius: 5pt, inset: 8pt)[
    #pill("TRACK A · OFFLINE CORPUS RESTRUCTURING & INDEXING", fg: navy, bg: white)
    #v(5pt)
    #grid(
      columns: (1fr, auto, 1.12fr, auto, 1.12fr, auto, 1.1fr),
      gutter: 5pt,
      align: horizon,
      block(fill: white, stroke: 0.5pt + linecol, radius: 4pt, inset: 6pt, width: 100%, height: 48pt)[
        #text(weight: "bold", size: 7.3pt, fill: navy)[1. Scanned Corpus]
        #v(2pt)
        #text(size: 6.7pt, fill: slate)[Law 2016-48, Circ. 2019-08, Ops Guide & Madkhal (4 docs)]
      ],
      text(weight: "bold", fill: navy)[→],
      block(fill: white, stroke: 0.5pt + linecol, radius: 4pt, inset: 6pt, width: 100%, height: 48pt)[
        #text(weight: "bold", size: 7.3pt, fill: navy)[2. Linguistic Repair]
        #v(2pt)
        #text(size: 6.7pt, fill: slate)[NFKC, visual-to-logical RTL zone rebuild, 29 audited batches]
      ],
      text(weight: "bold", fill: navy)[→],
      block(fill: white, stroke: 0.5pt + linecol, radius: 4pt, inset: 6pt, width: 100%, height: 48pt)[
        #text(weight: "bold", size: 7.3pt, fill: navy)[3. Structural Split]
        #v(2pt)
        #text(size: 6.7pt, fill: slate)[#mono("h1 > h2 > h3") ancestry + 220/40 split → 339 chunks]
      ],
      text(weight: "bold", fill: navy)[→],
      block(fill: white, stroke: 0.5pt + linecol, radius: 4pt, inset: 6pt, width: 100%, height: 48pt)[
        #text(weight: "bold", size: 7.3pt, fill: navy)[4. Dual Index + Units]
        #v(2pt)
        #text(size: 6.7pt, fill: slate)[Chroma 2048-d cosine + BM25 + 198 Law units & 53 numbers]
      ]
    )
  ]

  #v(6pt)

  // Track B: Online Answering
  #block(width: 100%, fill: softbg, stroke: 0.65pt + linecol, radius: 5pt, inset: 8pt)[
    #pill("TRACK B · ONLINE DETERMINISTIC UNDERSTANDING & CITATION GATE", fg: crimson, bg: redbg)
    #v(5pt)
    #grid(
      columns: (1fr, auto, 1.08fr, auto, 1.14fr, auto, 1.22fr),
      gutter: 5pt,
      align: horizon,
      block(fill: white, stroke: 0.5pt + linecol, radius: 4pt, inset: 6pt, width: 100%, height: 50pt)[
        #text(weight: "bold", size: 7.3pt, fill: navy)[1. Intent & Plan]
        #v(2pt)
        #text(size: 6.7pt, fill: slate)[Rule-based intent, compound split, cross-script bridges, proof plan]
      ],
      text(weight: "bold", fill: crimson)[→],
      block(fill: white, stroke: 0.5pt + linecol, radius: 4pt, inset: 6pt, width: 100%, height: 50pt)[
        #text(weight: "bold", size: 7.3pt, fill: navy)[2. Sufficiency Loop]
        #v(2pt)
        #text(size: 6.7pt, fill: slate)[Dense top-5 + guided rescue or 1-call 262-node interrogation]
      ],
      text(weight: "bold", fill: crimson)[→],
      block(fill: redbg, stroke: 0.6pt + crimson.lighten(45%), radius: 4pt, inset: 6pt, width: 100%, height: 50pt)[
        #text(weight: "bold", size: 7.3pt, fill: crimson)[3. Three-Stage Gate]
        #v(2pt)
        #text(size: 6.7pt, fill: slate)[(i) Verbatim quote ≥12c\ (ii) All digits in quote\ (iii) Source policy active]
      ],
      text(weight: "bold", fill: teal)[→],
      block(fill: tealbg, stroke: 0.6pt + teal.lighten(45%), radius: 4pt, inset: 6pt, width: 100%, height: 50pt)[
        #text(weight: "bold", size: 7.3pt, fill: teal)[4. Scrubbed Delivery]
        #v(2pt)
        #text(size: 6.7pt, fill: slate)[Grounded answer + quotes OR missing-proof refusal + JSONL log]
      ]
    )
  ]
]
""",

    "fig04_webapp_isolation": PREAMBLE + r"""
#frame(
  "Bank Assistant Functional Surfaces & Customer-Staff Identity Boundary",
  "Three segregated user channels enforcing customer PII masking, RBAC governance, and source isolation",
  tag: "IDENTITY & ACCESS"
)[
  #grid(
    columns: (1fr, 1fr, 1.12fr),
    gutter: 8pt,
    block(fill: softbg, stroke: 0.6pt + linecol, radius: 5pt, inset: 8pt, width: 100%, height: 142pt)[
      #pill("SURFACE 1 · PUBLIC CHANNEL", fg: amber, bg: amberbg)
      #v(4pt)
      #text(weight: "bold", size: 8pt, fill: navy)[Landing & Floating Widget]
      #v(3pt)
      #text(size: 7pt, fill: slate)[
        • Route #mono("/") + floating SSE assistant\
        • Trilingual FR / AR / EN with native RTL\
        • Anonymous quota: *3,000 tokens / 20 turns* (max 5 sessions/h)\
        • Zero personal data access; live-balance prompts refused locally\
        • Internal document filenames hidden
      ]
    ],
    block(fill: navybg, stroke: 0.6pt + navy.lighten(48%), radius: 5pt, inset: 8pt, width: 100%, height: 142pt)[
      #pill("SURFACE 2 · CLIENT PORTAL", fg: navy, bg: white)
      #v(4pt)
      #text(weight: "bold", size: 8pt, fill: navy)[Client Portal #mono("/portal")]
      #v(3pt)
      #text(size: 7pt, fill: slate)[
        • Dedicated *Client* login tab (#mono("UTILISATEUR"))\
        • Accounts, masked cards, transactions, Murabaha/Ijara simulations, advisor booking\
        • Quota: *10,000 tokens / 50 turns*\
        • #mono("CustomerContextService") derives *masked slices only* — raw IBAN/CIN/balance never sent to LLM
      ]
    ],
    block(fill: redbg, stroke: 0.6pt + crimson.lighten(48%), radius: 5pt, inset: 8pt, width: 100%, height: 142pt)[
      #pill("SURFACE 3 · STAFF CONTROL ROOM", fg: crimson, bg: white)
      #v(4pt)
      #text(weight: "bold", size: 8pt, fill: crimson)[Admin Back-Office #mono("/admin/**")]
      #v(3pt)
      #text(size: 7pt, fill: slate)[
        • Dedicated *Staff* login tab + 12 DB permissions\
        • Governed Facts CRUD (rates outrank corpus)\
        • Sealed Taxonomy releases & rollback\
        • *5-Tab RAGLab Console:* Health, Quality Workbench, Dual-World Docs, Config, Audit Journal & 200-turn KPIs
      ]
    ]
  )
  #v(7pt)
  #block(width: 100%, fill: cardbg, stroke: 0.55pt + linecol, radius: 4pt, inset: 7pt)[
    #grid(
      columns: (auto, 1fr),
      gutter: 8pt,
      align: horizon,
      pill("SHARED PERSISTENCE INVARIANT", fg: navy, bg: white),
      text(size: 7pt, fill: slate)[PostgreSQL V15 single-writer schema owns #mono("clients, comptes, cartes (masked), transactions, financements, conversations, admin_roles, agent_admin_audit") — every write action logs actor, timestamp, and mandatory reason.]
    )
  ]
]
""",

    "fig05_benchmarks": PREAMBLE + r"""
#let bar_row(label, sub, h1, h3, h5, accent, badge_txt: none) = block(
  width: 100%,
  inset: (y: 4pt),
  [
    #grid(
      columns: (1.25fr, 1.75fr, auto),
      gutter: 8pt,
      align: horizon,
      [
        #text(weight: "semibold", size: 7.5pt, fill: navy)[#label]
        #if badge_txt != none [ #h(3pt) #pill(badge_txt, fg: accent, bg: softbg) ]
        #v(1pt)
        #text(size: 6.6pt, fill: muted)[#sub]
      ],
      stack(
        spacing: 2.5pt,
        grid(
          columns: (32pt, 1fr, 24pt),
          gutter: 5pt,
          align: horizon,
          text(size: 6.4pt, weight: "medium", fill: slate)[hit\@1],
          block(width: 100%, height: 5.5pt, fill: cardbg, radius: 3pt)[
            #block(width: h1 * 1%, height: 100%, fill: accent, radius: 3pt)[]
          ],
          text(font: "JetBrains Mono", size: 6.6pt, weight: "semibold", fill: accent)[#str(h1)%]
        ),
        grid(
          columns: (32pt, 1fr, 24pt),
          gutter: 5pt,
          align: horizon,
          text(size: 6.4pt, fill: muted)[hit\@5],
          block(width: 100%, height: 4.5pt, fill: cardbg, radius: 3pt)[
            #block(width: h5 * 1%, height: 100%, fill: accent.lighten(38%), radius: 3pt)[]
          ],
          text(font: "JetBrains Mono", size: 6.4pt, fill: slate)[#str(h5)%]
        )
      ),
      box(
        fill: softbg,
        stroke: 0.45pt + linecol,
        radius: 3pt,
        inset: (x: 6pt, y: 3.5pt),
        text(font: "JetBrains Mono", size: 6.8pt, fill: navy)[#str(h1) / #str(h3) / #str(h5)]
      )
    )
    #v(3pt)
    #line(length: 100%, stroke: 0.4pt + rgb("#E2E8F0"))
  ]
)

#frame(
  "Measured Retrieval & Knowledge Benchmarks (hit@1 / hit@3 / hit@5)",
  "Paired evaluations on frozen benchmark artifacts (questions_50.json & Law 2016-48 unit benchmark)",
  tag: "BENCHMARK RESULTS"
)[
  #bar_row("Fixed-Window Chunking (size, 220/40)", "Legacy baseline — cuts legal articles mid-definition", 51, 64, 71, slate)
  #bar_row("Score Fusion (blend λ = 0.7)", "Linear combination of cosine + normalized BM25", 62, 80, 84, amber)
  #bar_row("Reciprocal Rank Fusion (RRF k = 60)", "Rank-fused dense + lexical arm on live set", 71, 80, 82, navy)
  #bar_row("Structure-Aware Dense Retrieval", "Production default — +20 pp hit@1 over fixed window", 71, 80, 87, crimson, badge_txt: "DEFAULT")
  #bar_row("Feature Reranker (Offline Pass)", "Deterministic second-pass gain: 62/82/87 → 62/89/89", 62, 89, 89, teal)
  #bar_row("Law 2016-48 Declarative Unit Index", "198 curated article units — every law query at rank 1", 100, 100, 100, teal, badge_txt: "100% RANK-1")
]
""",

    "fig06_security_layers": PREAMBLE + r"""
#frame(
  "Seven-Layer Defense-in-Depth & Banking PII Governance Architecture",
  "No single layer is trusted alone — controls span network topology, cryptography, prompts, and audit",
  tag: "DEFENSE IN DEPTH"
)[
  #grid(
    columns: (1fr, 1fr),
    gutter: 7pt,
    block(fill: softbg, stroke: 0.55pt + linecol, radius: 4pt, inset: 7pt, width: 100%)[
      #pill("L1 · NETWORK TOPOLOGY", fg: navy, bg: navybg)
      #v(3pt)
      #text(size: 7pt, fill: slate)[Only Nginx exposed publicly (443 TLS + HSTS). Spring Boot, PostgreSQL, Redis, and RAGLab run on private internal subnets with host ports bound to #mono("127.0.0.1").]
    ],
    block(fill: softbg, stroke: 0.55pt + linecol, radius: 4pt, inset: 7pt, width: 100%)[
      #pill("L2 · CRYPTOGRAPHIC AUTH", fg: navy, bg: navybg)
      #v(3pt)
      #text(size: 7pt, fill: slate)[≥256-bit JWT access tokens (≤1h) + single-use family refresh rotation (reuse burns entire token family). Constant-time #mono("hmac.compare_digest") on #mono("X-Service-Token").]
    ],
    block(fill: softbg, stroke: 0.55pt + linecol, radius: 4pt, inset: 7pt, width: 100%)[
      #pill("L3 · DYNAMIC RBAC", fg: navy, bg: navybg)
      #v(3pt)
      #text(size: 7pt, fill: slate)[All admin endpoints #mono("@PreAuthorize") gated across 12 granular DB capabilities. Client #mono("UTILISATEUR") strictly segregated; unknown roles fail closed (#mono("403")).]
    ],
    block(fill: softbg, stroke: 0.55pt + linecol, radius: 4pt, inset: 7pt, width: 100%)[
      #pill("L4 · INPUT SANITIZATION", fg: amber, bg: amberbg)
      #v(3pt)
      #text(size: 7pt, fill: slate)[Strict filename character whitelist, 20 MiB upload ceiling (#mono("413")), strict enum validation (#mono("400")), and SHA-256 duplicate corpus guard (#mono("409")).]
    ],
    block(fill: redbg, stroke: 0.55pt + crimson.lighten(50%), radius: 4pt, inset: 7pt, width: 100%)[
      #pill("L5 · PROMPT INJECTION GUARD", fg: crimson, bg: white)
      #v(3pt)
      #text(size: 7pt, fill: slate)[Prompt #mono("grounded-v1") isolates questions & passages as untrusted data with zero tool access; local regex guard rejects live-account queries with zero LLM call.]
    ],
    block(fill: tealbg, stroke: 0.55pt + teal.lighten(50%), radius: 4pt, inset: 7pt, width: 100%)[
      #pill("L6 · POST-GATE PII SCRUB", fg: teal, bg: white)
      #v(3pt)
      #text(size: 7pt, fill: slate)[Runs *after* citation verification so exact numeric checks succeed, replacing #mono("EMAIL, PHONE, TN59 IBAN/RIB, CIN") with #mono("[LABEL]") before user delivery.]
    ]
  )
  #v(6pt)
  #block(width: 100%, fill: navybg, stroke: 0.6pt + navy.lighten(48%), radius: 4pt, inset: 7pt)[
    #grid(
      columns: (auto, 1fr),
      gutter: 8pt,
      align: horizon,
      pill("L7 · END-TO-END AUDITABILITY", fg: navy, bg: white),
      text(size: 7pt, fill: slate)[Every answer carries verified quotes + engine attribution; every admin mutation logs mandatory motive in #mono("agent_admin_audit")\; #mono("X-Turn-Id") correlates Java MDC + Python JSONL logs.]
    )
  ]
]
""",

    "fig07_deployment_topology": PREAMBLE + r"""
#frame(
  "Immutable Digest Promotion & Production Container Topology",
  "Build once, verify by Git SHA, promote identical container digests across dev → staging → prod",
  tag: "DEVOPS & TOPOLOGY"
)[
  // Promotion pipeline
  #block(width: 100%, fill: softbg, stroke: 0.6pt + linecol, radius: 5pt, inset: 8pt)[
    #pill("IMMUTABLE ARTIFACT PROMOTION (deploy/promote.sh)", fg: navy, bg: navybg)
    #v(5pt)
    #grid(
      columns: (1fr, auto, 1fr, auto, 1.15fr),
      gutter: 6pt,
      align: horizon,
      block(fill: white, stroke: 0.55pt + linecol, radius: 4pt, inset: 6.5pt, width: 100%, height: 60pt)[
        #text(weight: "bold", size: 7.5pt, fill: navy)[1. Dev Environment]
        #v(2pt)
        #text(size: 6.8pt, fill: slate)[#mono("docker compose up") · Swagger ON · plain/JSON logs · throwaway seed data · 605 checks]
      ],
      text(weight: "bold", size: 8pt, fill: crimson)[→ SHA →],
      block(fill: white, stroke: 0.55pt + linecol, radius: 4pt, inset: 6.5pt, width: 100%, height: 60pt)[
        #text(weight: "bold", size: 7.5pt, fill: amber)[2. Staging Gate]
        #v(2pt)
        #text(size: 6.8pt, fill: slate)[Same image digest · #mono("prod") profile · Swagger OFF · ECS structured logs · anonymized data]
      ],
      text(weight: "bold", size: 8pt, fill: teal)[→ SHA →],
      block(fill: tealbg, stroke: 0.6pt + teal.lighten(45%), radius: 4pt, inset: 6.5pt, width: 100%, height: 60pt)[
        #text(weight: "bold", size: 7.5pt, fill: teal)[3. Production Runtime]
        #v(2pt)
        #text(size: 6.8pt, fill: slate)[Verified via #mono("/actuator/info") & #mono("/version.json == HEAD") · platform secrets · PITR]
      ]
    )
  ]

  #v(6pt)

  // Runtime topology strip
  #block(width: 100%, fill: navybg, stroke: 0.6pt + navy.lighten(48%), radius: 5pt, inset: 8pt)[
    #pill("PRODUCTION RUNTIME & BACKUP TOPOLOGY", fg: crimson, bg: white)
    #v(5pt)
    #grid(
      columns: (1fr, auto, 1fr, auto, 1.1fr, auto, 1.15fr),
      gutter: 5pt,
      align: horizon,
      block(fill: white, stroke: 0.5pt + linecol, radius: 4pt, inset: 6pt, width: 100%, height: 50pt)[
        #text(weight: "bold", size: 7.2pt, fill: crimson)[Edge TLS Proxy]
        #v(1.5pt)
        #text(size: 6.6pt, fill: slate)[Port 443 · TLS + HSTS · #mono("X-Forwarded-For")]
      ],
      text(weight: "bold", fill: navy)[→],
      block(fill: white, stroke: 0.5pt + linecol, radius: 4pt, inset: 6pt, width: 100%, height: 50pt)[
        #text(weight: "bold", size: 7.2pt, fill: navy)[Nginx SPA (:80)]
        #v(1.5pt)
        #text(size: 6.6pt, fill: slate)[Static Angular + unbuffered #mono("/api")]
      ],
      text(weight: "bold", fill: navy)[→],
      block(fill: white, stroke: 0.5pt + linecol, radius: 4pt, inset: 6pt, width: 100%, height: 50pt)[
        #text(weight: "bold", size: 7.2pt, fill: navy)[Spring Boot + RAGLab]
        #v(1.5pt)
        #text(size: 6.6pt, fill: slate)[Private VPC · Flyway V1..V15 boot check · Prometheus + JSONL]
      ],
      text(weight: "bold", fill: teal)[→],
      block(fill: white, stroke: 0.5pt + linecol, radius: 4pt, inset: 6pt, width: 100%, height: 50pt)[
        #text(weight: "bold", size: 7.2pt, fill: teal)[PostgreSQL PITR]
        #v(1.5pt)
        #text(size: 6.6pt, fill: slate)[Daily full + continuous WAL · 30-day retention · quarterly drill]
      ]
    )
  ]
]
""",
}


def build_diagrams() -> list[str]:
    """Render all 7 diagrams to both vector .svg and 300-DPI .png."""
    os.makedirs(DIAGRAMS_DIR, exist_ok=True)
    built = []
    for name, source in DIAGRAMS.items():
        svg_bytes = typst.compile(
            source.encode("utf-8"),
            format="svg",
            font_paths=[FONTS_DIR],
        )
        png_bytes = typst.compile(
            source.encode("utf-8"),
            format="png",
            ppi=300,
            font_paths=[FONTS_DIR],
        )
        svg_path = os.path.join(DIAGRAMS_DIR, f"{name}.svg")
        png_path = os.path.join(DIAGRAMS_DIR, f"{name}.png")
        with open(svg_path, "wb") as f:
            f.write(svg_bytes)
        with open(png_path, "wb") as f:
            f.write(png_bytes)
        built.append(name)
    return built


if __name__ == "__main__":
    build_clean_logos()
    names = build_diagrams()
    print(f"Built clean logos + {len(names)} vector/PNG diagrams in {DIAGRAMS_DIR}")
