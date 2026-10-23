# Final Internship Report — Farah AYARI

**ESPRIT · Al Baraka Bank Tunisia — Centre Urbain Nord**
Supervised by **Mrs. Ahlem Ben Haddoud** · 2025–2026

This folder is the **unified, English, 18-page** internship report that brings together the two systems built during the internship:

- **RAGLab** — the governed RAG intelligence engine (Python/FastAPI, 339 Arabic fragments, citation-gated answers)
- **Bank Assistant** — the bank-operated web application that consumes it (Angular 21 + Spring Boot 4 + PostgreSQL, quotas, streaming, admin console)

It is written around **ideas, architecture and problems solved**, not a chronology, and contains no dates beyond the academic year.

## Contents

| File | What it is | How to use it |
|---|---|---|
| [`Farah_Ayari_Internship_Report.pdf`](./Farah_Ayari_Internship_Report.pdf) | **The report as a beautiful PDF (18 pages)** — cover, dedications, 10 chapters, references & appendices | Open directly; print-ready (A4). Generated with `fpdf2` — no LaTeX install needed to read it. |
| [`report.md`](./report.md) | **The same report in Markdown** — with tables, figures and image links | Render on GitHub / VS Code. Source for the PDF content. |
| [`report.tex`](./report.tex) | **The same report in LaTeX** — compiles on Overleaf with `pdflatex` (2 passes) | Upload `report.tex` + the `assets/` folder to Overleaf. Uses only standard packages (`tikz`, `tcolorbox`, `booktabs`, `geometry`, `fancyhdr`). No Arabic fonts required (terms transliterated). |
| `assets/` | Logos (Al Baraka, ESPRIT), architecture diagram, workflow, UI screenshots | Referenced by all three formats. |
| `generate_pdf.py` | Python script that built the PDF (`pip install fpdf2 pillow`) | `python3 generate_pdf.py` regenerates the PDF. |

## How it was built

- **Data sources:** `RAGLab/RAGLAB_FULL_REPORT.md`, `RAGLab/internship_report/INTERNSHIP_REPORT.md`, `RAGLab/RAGLAB_SPEC.md`, `bank-assistant/docs/PROJECT_REPORT.md`, `bank-assistant/PLAN_TO_PROD.md`, `bank-assistant/docs/CUSTOMER_PII_REPORT.md`, plus institutional context scraped responsibly from `albaraka.com.tn` and the Tunisian Official Journal (Law 2016-48).
- **Cover & palette:** Al Baraka Red `#E0122F` + Orange `#F5881C` + ESPRIT Navy `#1B2A4E`, Soft Gray `#F5F7FB` — the same tokens the front-end uses, so the report and the product share an identity.
- **Figures:** `assets/architecture_schema.png` (3000×2780) and `assets/workflow.png` (1360×1180) from the repositories, plus `assets/before-after-*.png` UI screenshots.

## Reproduce

```bash
# PDF (no LaTeX needed)
pip install fpdf2 pillow --break-system-packages  # or in a venv
python3 final-report/generate_pdf.py  # -> final-report/Farah_Ayari_Internship_Report.pdf

# LaTeX (on Overleaf or locally if texlive installed)
# Upload final-report/report.tex + final-report/assets/ to Overleaf, compile twice with pdflatex
```

## Student

**Farah AYARI** — ESPRIT (Private Higher School of Engineering and Technology), Software Engineering — Internship at **Al Baraka Bank Tunisia, Centre Urbain Nord**, supervised by **Mrs. Ahlem Ben Haddoud**.

*This document contains no account identifiers, repository URLs or credentials. All figures are traceable to the repositories and their frozen benchmark artifacts.*
