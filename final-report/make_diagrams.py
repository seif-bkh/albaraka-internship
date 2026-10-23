#!/usr/bin/env python3
"""Generate clean, self-made diagrams for the report — no external pictures."""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(ROOT, "assets")
os.makedirs(ASSETS, exist_ok=True)

NAVY = "#182640"
RED = "#E0122F"
GRAY = "#737D91"
LIGHT = "#F0F2F7"
LIGHT2 = "#FAFBFD"
DARK = "#202430"
TEAL = "#1F7A5C"
GOLD = "#B57A14"
BORDER = "#DCE0EA"

def save(fig, name):
    path = os.path.join(ASSETS, name)
    fig.savefig(path, dpi=300, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print(f"saved {name}  ({os.path.getsize(path)//1024} KB)")

# ── Diagram 1: System Architecture (browser -> nginx -> Spring Boot -> RAGLab -> DB) ──
def diagram_architecture():
    fig, ax = plt.subplots(figsize=(7.2, 3.8))
    fig.patch.set_facecolor("white")
    ax.set_facecolor("white")
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 4.2)
    ax.axis("off")

    # helper to draw box
    def box(x, y, w, h, text, sub=None, color=NAVY, fill=LIGHT, textcolor=NAVY):
        fancy = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.04,rounding_size=0.12",
                                facecolor=fill, edgecolor=color, linewidth=1.1, zorder=2)
        ax.add_patch(fancy)
        ax.text(x+w/2, y+h*0.62, text, ha="center", va="center", fontsize=7.5, weight="bold", color=textcolor)
        if sub:
            ax.text(x+w/2, y+h*0.30, sub, ha="center", va="center", fontsize=5, color=GRAY, style="italic")

    def arrow(x1, y1, x2, y2):
        ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                    arrowprops=dict(arrowstyle="-|>", color=NAVY, lw=1.1, mutation_scale=8, shrinkA=0, shrinkB=1))

    # Row y = 1.6
    box(0.3, 1.5, 1.35, 1.1, "Browser", "Angular SPA", color=NAVY, fill="white")
    box(2.05, 1.5, 1.35, 1.1, "nginx", "proxy + SSE\nsecurity headers", color=NAVY, fill="white")
    box(3.80, 1.5, 1.95, 1.1, "Spring Boot Edge", "Java 21  |  JWT, quotas\nFlyway, audit, SSE relay", color=RED, fill="#FFF2F4")
    box(6.15, 1.5, 1.85, 1.1, "RAGLab", "Python  FastAPI\nretrieval + grounded answer", color=TEAL, fill="#EEF6F3")
    box(8.40, 1.5, 1.30, 1.1, "PostgreSQL", "15 migrations\nsingle writer", color=NAVY, fill="white")

    arrow(1.65, 2.05, 2.05, 2.05)
    arrow(3.40, 2.05, 3.80, 2.05)
    arrow(5.75, 2.05, 6.15, 2.05)
    # DB vertical arrow
    ax.annotate("", xy=(4.78, 1.5), xytext=(4.78, 0.85),
                arrowprops=dict(arrowstyle="-|>", color=NAVY, lw=1.1, mutation_scale=8, connectionstyle="arc3,rad=-0.25"))
    ax.text(4.78, 0.65, "owns schema  •  ddl-auto=validate", ha="center", va="center", fontsize=4.8, color=GRAY, style="italic")
    # RAGLab not public note
    ax.text(7.05, 0.95, "private network\nX-Service-Token", ha="center", va="center", fontsize=4.5, color=TEAL, style="italic",
            bbox=dict(boxstyle="round,pad=0.25", facecolor="#EEF6F3", edgecolor="#C8DDD6", lw=0.6))

    # labels
    ax.text(5, 3.05, "End-to-End Architecture  —  One Product, Two Systems, One Private Contract", ha="center", fontsize=7, weight="bold", color=NAVY)
    ax.text(5, 2.85, "Browser is the only public edge  •  RAGLab is never exposed to the Internet", ha="center", fontsize=5, color=GRAY, style="italic")
    # legend dots
    ax.text(0.3, 0.35, "Angular 21", fontsize=5, color=NAVY, bbox=dict(boxstyle="square,pad=0.2", facecolor="white", edgecolor=BORDER, lw=0.5))
    ax.text(1.35, 0.35, "Java 21 + Flyway", fontsize=5, color=RED, bbox=dict(boxstyle="square,pad=0.2", facecolor="#FFF2F4", edgecolor="#EAB8BF", lw=0.5))
    ax.text(2.85, 0.35, "Python + ChromaDB", fontsize=5, color=TEAL, bbox=dict(boxstyle="square,pad=0.2", facecolor="#EEF6F3", edgecolor="#C8DDD6", lw=0.5))

    fig.tight_layout(pad=0.5)
    save(fig, "diagram_architecture.png")

# ── Diagram 2: Request lifecycle (question -> ... -> answer) ──
def diagram_lifecycle():
    fig, ax = plt.subplots(figsize=(7.2, 4.6))
    fig.patch.set_facecolor("white")
    ax.set_facecolor("white")
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 5.0)
    ax.axis("off")

    ax.text(5, 4.65, "Request Lifecycle  —  From Question to Grounded Answer", ha="center", fontsize=7, weight="bold", color=NAVY)
    ax.text(5, 4.42, "Every step before generation is deterministic  •  The model is called only when evidence exists", ha="center", fontsize=5, color=GRAY, style="italic")

    steps = [
        ("1\nQuestion\n(ar / fr / en)", NAVY, LIGHT),
        ("2\nAuth + Quota\ngate()", RED, "#FFF2F4"),
        ("3\nClassify Intent\nplan evidence", NAVY, LIGHT),
        ("4\nRetrieve\ntop-5 dense\n+ BM25 arm", TEAL, "#EEF6F3"),
        ("5\nCheck\nSufficiency\nsufficient?", GOLD, "#FFF7E6"),
        ("6\nGenerate\nqwen  •  grounded-v1\nJSON claims + quotes", TEAL, "#EEF6F3"),
        ("7\nGate\nquote + number\npolicy", RED, "#FFF2F4"),
        ("8\nScrub + Audit\nstream to browser", NAVY, LIGHT),
    ]
    # layout 4x2 or 8 in row? Let's do 8 in single row with arrows
    n = len(steps)
    w = 1.05
    gap = 0.18
    total = n*w + (n-1)*gap
    x0 = (10 - total)/2
    y = 2.8
    h = 1.15
    for i, (txt, col, fill) in enumerate(steps):
        x = x0 + i*(w+gap)
        fancy = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.02,rounding_size=0.14",
                                facecolor=fill, edgecolor=col, linewidth=1.0, zorder=2)
        ax.add_patch(fancy)
        lines = txt.split("\n")
        # title line bold
        ax.text(x+w/2, y+h*0.72, lines[0], ha="center", va="center", fontsize=6, weight="bold", color=col)
        for j, ln in enumerate(lines[1:]):
            ax.text(x+w/2, y+h*0.50 - j*0.18, ln, ha="center", va="center", fontsize=4.6, color=DARK)
        if i < n-1:
            ax.annotate("", xy=(x+w+gap, y+h/2), xytext=(x+w, y+h/2),
                        arrowprops=dict(arrowstyle="-|>", color=NAVY, lw=0.9, mutation_scale=7))
    # branching: sufficient vs insufficient
    # add two small boxes below step 5
    ax.text(x0+4*(w+gap)+w/2, 1.55, "refuse honestly\n(name what's missing)", ha="center", fontsize=4.5, color=RED,
            bbox=dict(boxstyle="round,pad=0.25", facecolor="#FFF2F4", edgecolor="#EAB8BF", lw=0.7))
    ax.text(x0+4*(w+gap)+w/2, 1.05, "1 bounded re-try: interrogate → new retrieval", ha="center", fontsize=4.5, color=GOLD,
            bbox=dict(boxstyle="round,pad=0.25", facecolor="#FFF7E6", edgecolor="#E8C99A", lw=0.7))
    # arrows from step 5 down
    sx = x0+4*(w+gap)+w/2
    ax.annotate("", xy=(sx-0.3, 1.85), xytext=(sx-0.15, y+0), arrowprops=dict(arrowstyle="-|>", color=RED, lw=0.8, mutation_scale=6, connectionstyle="arc3,rad=0.2"))
    ax.annotate("", xy=(sx+0.3, 1.35), xytext=(sx+0.15, y+0), arrowprops=dict(arrowstyle="-|>", color=GOLD, lw=0.8, mutation_scale=6, connectionstyle="arc3,rad=-0.2"))
    # note
    ax.text(5, 0.45, "Stateless engine  •  Java opens the row BEFORE calling RAGLab, so even a failure is visible  •  History = last 10 turns", ha="center", fontsize=4.7, color=GRAY, style="italic")

    fig.tight_layout(pad=0.4)
    save(fig, "diagram_lifecycle.png")

# ── Diagram 3: Chunking & retrieval comparison ──
def diagram_chunking():
    fig, ax = plt.subplots(figsize=(7.2, 3.9))
    fig.patch.set_facecolor("white")
    ax.set_facecolor("white")
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 4.4)
    ax.axis("off")

    ax.text(5, 4.05, "Structure-Aware Chunking  —  Why +20 pp hit@1", ha="center", fontsize=7, weight="bold", color=NAVY)
    ax.text(5, 3.82, "Fixed window cuts articles  •  Structure preserves them  •  Every chunk carries its ancestry", ha="center", fontsize=5, color=GRAY, style="italic")

    # left: legacy window
    ax.add_patch(FancyBboxPatch((0.4, 1.0), 4.4, 2.4, boxstyle="round,pad=0.08,rounding_size=0.16", facecolor="#FFF2F4", edgecolor="#EAB8BF", lw=1.0))
    ax.text(2.6, 3.18, "Legacy  —  size  220 / 40", ha="center", fontsize=6, weight="bold", color=RED)
    ax.text(2.6, 2.95, "blind 220-token windows + 40 overlap", ha="center", fontsize=4.6, color=GRAY, style="italic")
    # draw chopped article
    for i, (y_, txt) in enumerate([(2.5, "Article 42  —  Murabaha defi... [CUT]"), (2.15, "[CUT] ...nition continues"), (1.80, "Article 43  —  ..."), (1.45, "Table: fees  —  broken mid-row")]):
        ax.text(0.85, y_, txt, fontsize=4.8, color=DARK, family="monospace",
                bbox=dict(boxstyle="square,pad=0.18", facecolor="white", edgecolor=BORDER, lw=0.6))
        if i==0:
            ax.text(4.25, 2.5, "✕", fontsize=7, color=RED, weight="bold", ha="center", va="center")
    ax.text(2.6, 1.25, "hit@1  51  •  noisy, unverifiable", ha="center", fontsize=5, color=RED, weight="bold",
            bbox=dict(boxstyle="round,pad=0.3", facecolor="white", edgecolor="#EAB8BF"))

    # right: restructure
    ax.add_patch(FancyBboxPatch((5.2, 1.0), 4.4, 2.4, boxstyle="round,pad=0.08,rounding_size=0.16", facecolor="#EEF6F3", edgecolor="#A9CFC0", lw=1.0))
    ax.text(7.4, 3.18, "Adopted  —  restructure", ha="center", fontsize=6, weight="bold", color=TEAL)
    ax.text(7.4, 2.95, "semantic norm.  +  breadcrumb  +  structural split", ha="center", fontsize=4.6, color=GRAY, style="italic")
    for y_, txt in [(2.5, "h1: Law 2016-48 > Title II > Chap.3"), (2.25, "## Art.42  —  Murabaha (full definition)"), (1.95, "breadcrumb: Title II > Chap.3 > Art.42"), (1.60, "Table: fees (with ancestry)  —  kept whole")]:
        bg = LIGHT if "breadcrumb" in txt else "white"
        ec = BORDER if "breadcrumb" in txt else BORDER
        ax.text(5.6, y_, txt, fontsize=4.7, color=NAVY if "Art.42" in txt else DARK, family="monospace" if "##" in txt else "sans-serif",
                bbox=dict(boxstyle="square,pad=0.18", facecolor=bg, edgecolor=ec, lw=0.6))
    ax.text(7.4, 1.25, "hit@1  71  •  verifiable, anchored", ha="center", fontsize=5, color=TEAL, weight="bold",
            bbox=dict(boxstyle="round,pad=0.3", facecolor="white", edgecolor="#A9CFC0"))

    # arrow between
    ax.annotate("", xy=(5.2, 2.2), xytext=(4.8, 2.2), arrowprops=dict(arrowstyle="-|>", color=NAVY, lw=1.2, mutation_scale=9))
    ax.text(5, 2.45, "+20 pp", ha="center", fontsize=5.5, weight="bold", color=NAVY,
            bbox=dict(boxstyle="round,pad=0.2", facecolor="white", edgecolor=NAVY, lw=0.7))

    # bottom note
    ax.text(5, 0.65, "Each chunk carries: document • heading • language • section_type • chunk_fp v4 • embedding_fp", ha="center", fontsize=4.6, color=GRAY, style="italic")
    ax.text(5, 0.38, "Fingerprint guard refuses stale indexes before any model call", ha="center", fontsize=4.6, color=GRAY, style="italic")

    fig.tight_layout(pad=0.5)
    save(fig, "diagram_chunking.png")

diagram_architecture()
diagram_lifecycle()
diagram_chunking()
print("all diagrams done")
