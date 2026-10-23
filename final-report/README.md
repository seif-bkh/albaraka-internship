# Final Internship Report — Farah AYARI

**ESPRIT · Al Baraka Bank Tunisia — Centre Urbain Nord**  
Supervised by **Mrs. Ahlem Ben Haddoud** · Academic Year **2025–2026**

This directory contains the unified, publication-grade English **Internship Report (23 pages total: Cover + 3 Front-Matter pages `i–iii` + 19 Body & Appendix pages `1–19`)** documenting the architecture, implementation, security governance, and benchmark evaluation of the integrated banking intelligence platform:

- **RAGLab** — Stateless Python/FastAPI intelligence microservice indexing 4 real Arabic regulatory & operational banking texts into 339 structure-aware fragments (`+20 pp hit@1`), paired with a 198-article declarative Law 2016-48 index (`100%` rank-1 recall) and a deterministic three-stage citation gate.
- **Bank Assistant** — Trilingual (French / Arabic / English + native RTL) Angular 21 + Spring Boot 4 (Java 21 virtual threads) + PostgreSQL 16 digital banking application owning authentication, token-family rotation, dynamic 12-permission RBAC, two-phase striped-lock quota metering, masked customer slices, and a five-tab staff governance console.

---

## Deliverables

| File | Description |
|---|---|
| [`Farah_Ayari_Internship_Report.pdf`](./Farah_Ayari_Internship_Report.pdf) | **Print-ready A4 PDF deliverable (23 pages)** — clean institutional cover, bilingual English/French abstracts, clickable Table of Contents & List of Figures, 10 chapters, 13 formatted tables, **7 pure-vector SVG architectural & benchmark diagrams** (zero bitmap stretching or pixelation), references, and appendices. |
| [`report.typ`](./report.typ) | Typst typesetting manuscript powering `Farah_Ayari_Internship_Report.pdf`. |
| [`generate_diagrams.py`](./generate_diagrams.py) | Programmatic generator producing high-DPI cleaned institutional logos and all 7 vector (`.svg`) + 300-DPI (`.png`) engineering diagrams under `assets/diagrams/`. |
| [`generate_pdf.py`](./generate_pdf.py) | Single-command build script generating logos, diagrams, and the final PDF (`python3 final-report/generate_pdf.py`). |
| [`report.md`](./report.md) | Complete Markdown version linked to the clean SVG diagrams in `assets/diagrams/`. |
| [`report.tex`](./report.tex) | Overleaf-ready `pdflatex` LaTeX manuscript configured with `[width=\textwidth,keepaspectratio]` on the 300-DPI diagrams. |
| [`fonts/`](./fonts) | Self-hosted `Inter` and `JetBrains Mono` TrueType fonts ensuring deterministic cross-platform PDF rendering. |

---

## Vector Diagrams (`assets/diagrams/`)

1. **`fig01_architecture`** — End-to-End Platform Architecture across Public DMZ, Bank Application Edge, and Private Intelligence Engine
2. **`fig02_chat_sequence`** — Six-Step Customer Conversation Lifecycle (Striped Quota Gate, Pre-Persist, HTTP/1.1 Stream, Sufficiency, Citation Gate, Settle)
3. **`fig03_raglab_pipeline`** — Dual-Track RAGLab Pipeline (Offline Arabic PDF Restructuring & Indexing + Online Deterministic Gating)
4. **`fig04_webapp_isolation`** — Bank Assistant Segregated Surfaces & Customer-Staff Identity Boundary
5. **`fig05_benchmarks`** — Paired Retrieval & Declarative Knowledge Benchmark Results (`hit@1 / hit@3 / hit@5`)
6. **`fig06_security_layers`** — Seven-Layer Banking Defense-in-Depth & PII Governance Model
7. **`fig07_deployment_topology`** — Immutable Git-SHA Promotion (`dev → staging → prod`) & Production Runtime Topology

---

## Reproduce Locally

```bash
pip install typst pillow numpy
python3 final-report/generate_pdf.py
# -> Outputs final-report/Farah_Ayari_Internship_Report.pdf
```

*Confidentiality Note: By design, no customer identifiers, repository URLs, or credentials appear anywhere in this report. All figures are traceable to frozen repository benchmark artifacts.*
