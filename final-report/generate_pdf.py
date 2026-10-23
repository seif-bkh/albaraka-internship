#!/usr/bin/env python3
"""
Builds the publication-grade A4 Internship Report PDF for Farah AYARI
(ESPRIT x Al Baraka Bank Tunisia, 2025-2026).

1. Generates clean transparent high-DPI logos + 7 crisp vector (.svg) & 300-DPI (.png)
   architectural and benchmark diagrams via `generate_diagrams.py`.
2. Compiles `report.typ` using the embedded `typst` engine + bundled `Inter` and
   `JetBrains Mono` fonts into `Farah_Ayari_Internship_Report.pdf` with 100% vector
   diagrams, automatic clickable Table of Contents, and true print typography.

Dependencies: `pip install typst pillow numpy`
"""
import os
import typst
from generate_diagrams import build_clean_logos, build_diagrams

ROOT = os.path.dirname(os.path.abspath(__file__))
FONTS_DIR = os.path.join(ROOT, "fonts")
TYP_PATH = os.path.join(ROOT, "report.typ")
OUTPUT_PDF = os.path.join(ROOT, "Farah_Ayari_Internship_Report.pdf")


def main() -> None:
    build_clean_logos()
    diagrams = build_diagrams()
    pdf_bytes = typst.compile(
        TYP_PATH,
        root=ROOT,
        font_paths=[FONTS_DIR],
    )
    with open(OUTPUT_PDF, "wb") as f:
        f.write(pdf_bytes)
    size_kb = os.path.getsize(OUTPUT_PDF) / 1024.0
    print(f"Generated {len(diagrams)} vector diagrams + {OUTPUT_PDF} ({size_kb:.1f} KB)")


if __name__ == "__main__":
    main()
