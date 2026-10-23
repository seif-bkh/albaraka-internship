#!/usr/bin/env python3
"""
Simple, clean internship report PDF — Farah AYARI, ESPRIT × Al Baraka Bank
No over-design: white cover, correct image ratios, minimal colors.
"""
from fpdf import FPDF
from PIL import Image
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(ROOT, "assets")
OUTPUT = os.path.join(ROOT, "Farah_Ayari_Internship_Report.pdf")

# Minimal palette
NAVY = (24, 38, 72)
RED = (224, 18, 47)
DARK = (32, 36, 48)
GRAY = (115, 125, 145)
LIGHT = (240, 242, 247)
WHITE = (255,255,255)
BORDER = (220, 224, 232)

W, H = 210, 297

class PDF(FPDF):
    def header(self):
        if self.page_no() == 1:
            return
        self.set_draw_color(*BORDER)
        self.set_line_width(0.12)
        self.line(14, 9, 196, 9)
        self.set_font("Helvetica", "", 6)
        self.set_text_color(*GRAY)
        self.set_xy(14, 10.5)
        self.cell(0, 3, "Farah AYARI  |  ESPRIT  -  Al Baraka Bank Tunisia  -  Centre Urbain Nord", align="C")
    def footer(self):
        if self.page_no() == 1:
            return
        self.set_y(-9)
        self.set_font("Helvetica", "", 5.5)
        self.set_text_color(*GRAY)
        self.cell(0, 3, f"{self.page_no()}", align="C")

pdf = PDF(orientation='P', unit='mm', format='A4')
pdf.set_auto_page_break(auto=True, margin=16)

def safe_image(path):
    return path if os.path.exists(path) else None

def image_correct(path, x, y, w=None, h=None, max_w=None, max_h=None):
    """Place image preserving aspect ratio — never stretch."""
    if not path or not os.path.exists(path):
        return
    with Image.open(path) as im:
        iw, ih = im.size
        ar = ih / iw
        if w is not None and h is None:
            h = w * ar
        elif h is not None and w is None:
            w = h / ar
        elif w is None and h is None:
            w = 40
            h = w * ar
        # clamp to max
        if max_w and w > max_w:
            w = max_w; h = w * ar
        if max_h and h > max_h:
            h = max_h; w = h / ar
        # never go outside page
        pdf.image(path, x=x, y=y, w=w, h=h)

# ── COVER ──
pdf.add_page()
# top thin red line
pdf.set_fill_color(*RED)
pdf.rect(0, 0, 210, 1.8, 'F')
# small ESPRIT wordmark top left, Al Baraka top right — subtle
pdf.set_xy(14, 6)
pdf.set_font("Helvetica", "", 6)
pdf.set_text_color(*GRAY)
pdf.cell(60, 3, "ESPRIT  -  Private Higher School of Engineering and Technology", align="L")
pdf.set_xy(124, 6)
pdf.cell(72, 3, "Al Baraka Bank  -  Since 1983  -  44 branches", align="R")

# logos — perfectly centered, no stretch, equal visual height ~18mm
y_logos = 15
# ESPRIT logo
esprit = safe_image(os.path.join(ASSETS, "esprit-tunisia-logo-private-engineering--3.jpg"))
if not esprit:
    esprit = safe_image(os.path.join(ASSETS, "esprit-tunisia-logo-private-engineering--1.png"))
# albaraka
albaraka = safe_image(os.path.join(ASSETS, "al-baraka-bank-tunisia-logo-red-orange-1.png"))
if not albaraka:
    albaraka = safe_image(os.path.join(ASSETS, "albaraka-front.png"))

if esprit:
    image_correct(esprit, x=28, y=y_logos, h=16, max_w=55)
if albaraka:
    image_correct(albaraka, x=125, y=y_logos, h=16, max_w=55)

# center dot separator
pdf.set_fill_color(*BORDER)
pdf.circle(105, y_logos+8, 0.7, 'F')

# title
pdf.set_xy(14, 42)
pdf.set_font("Helvetica", "", 7)
pdf.set_text_color(*GRAY)
pdf.cell(182, 4, "INTERNSHIP REPORT", align="C")
pdf.set_xy(14, 48)
pdf.set_font("Helvetica", "B", 22)
pdf.set_text_color(*NAVY)
pdf.cell(182, 9, "A Governed Intelligence", align="C")
pdf.set_xy(14, 58)
pdf.set_font("Helvetica", "B", 22)
pdf.set_text_color(*RED)
pdf.cell(182, 9, "for Islamic Banking", align="C")
pdf.set_xy(14, 70)
pdf.set_font("Helvetica", "", 8.5)
pdf.set_text_color(*DARK)
pdf.cell(182, 4.5, "The Integrated RAGLab  &  Bank Assistant Platform", align="C")
# thin divider
pdf.set_draw_color(*BORDER)
pdf.set_line_width(0.25)
pdf.line(78, 77, 132, 77)
pdf.set_xy(14, 80)
pdf.set_font("Helvetica", "I", 6.8)
pdf.set_text_color(*GRAY)
pdf.multi_cell(182, 3.6, "Every answer cites its source. Every refusal explains why.\nA bank-operated web application consuming a governed RAG intelligence engine.", align="C")

# info block — simple, centered, no heavy background
pdf.set_xy(14, 97)
pdf.set_font("Helvetica", "", 7)
pdf.set_text_color(*DARK)
# use a light rounded box with thin border
y0 = 97
pdf.set_draw_color(*BORDER)
pdf.set_fill_color(250, 251, 253)
pdf.rect(22, y0, 166, 38, 'DF')
# content
pdf.set_xy(26, y0+5)
pdf.set_font("Helvetica", "B", 6.8)
pdf.set_text_color(*NAVY)
pdf.cell(32, 4, "Student", align="L")
pdf.set_font("Helvetica", "", 6.8)
pdf.set_text_color(*DARK)
pdf.cell(0, 4, "Farah AYARI  -  ESPRIT, Software Engineering", align="L")
pdf.set_xy(26, y0+12)
pdf.set_font("Helvetica", "B", 6.8)
pdf.set_text_color(*NAVY)
pdf.cell(32, 4, "Host organization", align="L")
pdf.set_font("Helvetica", "", 6.8)
pdf.set_text_color(*DARK)
pdf.cell(0, 4, "Al Baraka Bank Tunisia", align="L")
pdf.set_xy(26, y0+16.5)
pdf.set_font("Helvetica", "", 6.2)
pdf.set_text_color(*GRAY)
pdf.cell(32, 3.5, "", align="L")
pdf.cell(0, 3.5, "Centre Urbain Nord, Avenue Cheikh Mohamed Fadhel Ben Achour, 1082 Tunis", align="L")
pdf.set_xy(26, y0+22)
pdf.set_font("Helvetica", "B", 6.8)
pdf.set_text_color(*NAVY)
pdf.cell(32, 4, "Supervisor", align="L")
pdf.set_font("Helvetica", "", 6.8)
pdf.set_text_color(*DARK)
pdf.cell(0, 4, "Mrs. Ahlem Ben Haddoud  -  Advisor, Al Baraka Bank", align="L")
pdf.set_xy(26, y0+29)
pdf.set_font("Helvetica", "B", 6.8)
pdf.set_text_color(*NAVY)
pdf.cell(32, 4, "Academic year", align="L")
pdf.set_font("Helvetica", "", 6.8)
pdf.set_text_color(*DARK)
pdf.cell(0, 4, "2025 - 2026", align="L")

# bottom tagline + note
pdf.set_xy(14, 148)
pdf.set_font("Helvetica", "I", 6.2)
pdf.set_text_color(*GRAY)
pdf.cell(182, 3.5, "Internship report  -  No account identifiers, repository URLs or credentials are included by design.", align="C")
pdf.set_xy(14, 152)
pdf.set_font("Helvetica", "", 5.5)
pdf.set_text_color(150,160,180)
pdf.cell(182, 3, "All figures are traceable to frozen benchmark artifacts in the two project repositories.", align="C")

# small centered architecture preview — correct ratio, not stretched
arch = safe_image(os.path.join(ASSETS, "architecture_schema.png"))
if arch:
    # place as faint preview, centered, height limited
    image_correct(arch, x=30, y=164, w=150, max_h=55)

# bottom footer bar — minimal
pdf.set_fill_color(*NAVY)
pdf.rect(0, 285, 210, 12, 'F')
pdf.set_xy(14, 289)
pdf.set_font("Helvetica", "", 5.5)
pdf.set_text_color(*WHITE)
pdf.cell(182, 3, "ESPRIT  |  Al Baraka Bank Tunisia  |  A Governed Intelligence for Islamic Banking", align="C")
pdf.set_xy(14, 292)
pdf.set_font("Helvetica", "", 4.8)
pdf.set_text_color(190,200,215)
pdf.cell(182, 2.5, "Centre Urbain Nord  -  Trust through traceability", align="C")

# ── Helpers ──
def chapter(num, title, subtitle=None):
    pdf.add_page()
    pdf.set_xy(14, 14)
    pdf.set_font("Helvetica", "B", 6)
    pdf.set_text_color(*RED)
    pdf.cell(18, 4, f"CHAPTER {num}", align="L")
    pdf.set_font("Helvetica", "", 6)
    pdf.set_text_color(*GRAY)
    pdf.cell(0, 4, f"  -  {pdf.pages_count-1 if num!=1 else 1}", align="L")
    pdf.set_xy(14, 19.5)
    pdf.set_font("Helvetica", "B", 13)
    pdf.set_text_color(*NAVY)
    pdf.cell(0, 7, title, align="L")
    if subtitle:
        pdf.set_xy(14, 27)
        pdf.set_font("Helvetica", "", 7)
        pdf.set_text_color(*GRAY)
        pdf.cell(0, 4, subtitle, align="L")
    # thin line
    pdf.set_draw_color(*BORDER)
    pdf.line(14, 33, 196, 33)
    pdf.set_y(36)

def section(title):
    pdf.set_font("Helvetica", "B", 7.5)
    pdf.set_text_color(*NAVY)
    pdf.set_x(14)
    # small red square
    y = pdf.get_y()
    pdf.set_fill_color(*RED)
    pdf.rect(14, y+1.2, 2.2, 2.2, 'F')
    pdf.set_x(18.5)
    pdf.cell(0, 4.5, title, align="L")
    pdf.ln(6)

def paragraph(text):
    pdf.set_font("Helvetica", "", 6.9)
    pdf.set_text_color(*DARK)
    pdf.set_x(14)
    pdf.multi_cell(182, 3.6, text, align="J")
    pdf.ln(1.5)

def bullet(text):
    pdf.set_font("Helvetica", "", 6.9)
    pdf.set_text_color(*DARK)
    pdf.set_x(18)
    y = pdf.get_y()
    pdf.set_fill_color(*NAVY)
    pdf.circle(15, y+1.9, 0.6, 'F')
    pdf.multi_cell(178, 3.6, text, align="J")
    pdf.ln(0.8)

def note_box(title, text):
    y0 = pdf.get_y()
    if y0 > 245:
        pdf.add_page(); y0 = pdf.get_y()
    pdf.set_draw_color(*BORDER)
    pdf.set_fill_color(*LIGHT)
    # calc height
    pdf.set_font("Helvetica", "", 6.5)
    w = 174
    words = text.split()
    line=""; h=0
    for wd in words:
        test = line+" "+wd if line else wd
        if pdf.get_string_width(test) > w:
            h+=3.3; line=wd
        else:
            line=test
    h+=3.3; h+=8
    if y0+h > 282:
        pdf.add_page(); y0 = pdf.get_y()
    pdf.rect(14, y0, 182, h, 'DF')
    pdf.set_fill_color(*RED)
    pdf.rect(14, y0, 1.2, h, 'F')
    pdf.set_xy(18, y0+2)
    pdf.set_font("Helvetica", "B", 6.2)
    pdf.set_text_color(*NAVY)
    pdf.cell(0, 3.2, title, align="L")
    pdf.set_xy(18, y0+6)
    pdf.set_font("Helvetica", "", 6.5)
    pdf.set_text_color(*DARK)
    pdf.multi_cell(174, 3.3, text, align="J")
    pdf.set_y(y0+h+2.5)

def table(headers, rows, widths, caption=None):
    # header
    pdf.set_font("Helvetica", "B", 5.8)
    pdf.set_fill_color(*NAVY)
    pdf.set_text_color(*WHITE)
    x0=14
    for i,h in enumerate(headers):
        pdf.set_xy(x0, pdf.get_y())
        pdf.cell(widths[i], 5, " "+h, align="L", fill=True)
        x0+=widths[i]
    pdf.ln(5)
    for idx,row in enumerate(rows):
        fill = idx%2==0
        pdf.set_font("Helvetica", "", 6)
        pdf.set_text_color(*DARK)
        y0=pdf.get_y()
        # heights
        hs=[]
        for i,cell in enumerate(row):
            w=widths[i]-2
            words=cell.split()
            line=""; h=0
            for wd in words:
                test=line+" "+wd if line else wd
                if pdf.get_string_width(test) > w:
                    h+=3.3; line=wd
                else:
                    line=test
            h+=3.3
            hs.append(h)
        rh=max(hs)
        if y0+rh>282:
            pdf.add_page(); y0=pdf.get_y()
        pdf.set_fill_color(245,247,251) if fill else pdf.set_fill_color(*WHITE)
        pdf.rect(14, y0, sum(widths), rh, 'F')
        pdf.set_draw_color(*BORDER)
        pdf.rect(14, y0, sum(widths), rh, 'D')
        x=14
        for i,cell in enumerate(row):
            pdf.set_xy(x+1, y0+0.7)
            pdf.set_font("Helvetica","",6)
            pdf.multi_cell(widths[i]-2, 3, cell, align="L")
            x+=widths[i]
        pdf.set_y(y0+rh)
    pdf.ln(1)
    if caption:
        pdf.set_font("Helvetica","I",5.2)
        pdf.set_text_color(*GRAY)
        pdf.set_x(14)
        pdf.cell(182,3,caption,align="C")
        pdf.ln(4)

def fig(path, caption, w=158):
    if not path or not os.path.exists(path):
        return
    if pdf.get_y() > 220:
        pdf.add_page()
    y0=pdf.get_y()
    # place preserving aspect, centered
    with Image.open(path) as im:
        iw, ih = im.size
        ar = ih/iw
        h = w*ar*0.264583
        if h>65:
            h=65; w=h/ar/0.264583
        x = (210-w)/2
        # light border
        pdf.set_draw_color(*BORDER)
        pdf.rect(x, y0, w, h, 'D')
        pdf.image(path, x=x, y=y0, w=w, h=h)
        pdf.set_y(y0+h+1.5)
        if caption:
            pdf.set_font("Helvetica","I",5.2)
            pdf.set_text_color(*GRAY)
            pdf.set_x(14)
            pdf.multi_cell(182, 3, caption, align="C")
            pdf.ln(2)

# ── Dedication / Acknowledgments ──
pdf.add_page()
pdf.set_xy(14, 18)
pdf.set_font("Helvetica", "B", 9)
pdf.set_text_color(*NAVY)
pdf.cell(0,5,"Dedication",align="L")
pdf.set_draw_color(*RED)
pdf.set_line_width(0.2)
pdf.line(14, 24, 38, 24)
pdf.set_xy(14, 27)
pdf.set_font("Helvetica", "I", 7)
pdf.set_text_color(*DARK)
pdf.multi_cell(182, 3.8, "To my family, for their constant support,\nto Mrs. Ahlem Ben Haddoud, for the guidance, rigor and trust that shaped this work,\nand to everyone who believes technology, when governed, can serve trust.", align="L")
pdf.set_xy(14, 44)
pdf.set_font("Helvetica", "", 6)
pdf.set_text_color(*GRAY)
pdf.cell(0,3,"Farah AYARI  -  ESPRIT, Software Engineering",align="R")
pdf.ln(8)
pdf.set_xy(14, pdf.get_y())
pdf.set_font("Helvetica", "B", 9)
pdf.set_text_color(*NAVY)
pdf.cell(0,5,"Acknowledgments",align="L")
pdf.line(14, pdf.get_y()+6, 44, pdf.get_y()+6)
pdf.set_xy(14, pdf.get_y()+9)
pdf.set_font("Helvetica", "", 6.8)
pdf.set_text_color(*DARK)
pdf.multi_cell(182, 3.6, "My deepest thanks go to Mrs. Ahlem Ben Haddoud, internship supervisor at Al Baraka Bank Tunisia - Centre Urbain Nord, for her availability, clear decisions and thoughtful feedback at every key step.\n\nI thank the teams of Al Baraka Bank for their welcome and for the documents and operational feedback that revealed defects invisible in tests. I thank the faculty of ESPRIT for the education that made this project possible.", align="J")
pdf.ln(6)
pdf.set_draw_color(*BORDER)
pdf.line(14, pdf.get_y(), 196, pdf.get_y())
pdf.ln(4)
pdf.set_font("Helvetica", "B", 7)
pdf.set_text_color(*NAVY)
pdf.cell(0,4,"Abstract",align="L")
pdf.ln(5)
pdf.set_font("Helvetica", "", 6.8)
pdf.set_text_color(*DARK)
pdf.multi_cell(182, 3.6, "In regulated banking, a wrong answer about a rate, fee or deadline is a legal risk. This internship delivered a complete, governed answer: an integrated platform where a bank-operated web application consumes a dedicated RAG intelligence engine, both built to never invent figures or rules. The report covers the architecture, the major technical choices, the problems that were solved and the results that can be measured. No account identifiers or secrets appear in it by design.", align="J")
pdf.ln(2)
pdf.set_font("Helvetica", "B", 6)
pdf.set_text_color(*NAVY)
pdf.cell(14,3,"Keywords:",align="L")
pdf.set_font("Helvetica", "", 6)
pdf.set_text_color(*DARK)
pdf.multi_cell(0,3,"RAG  -  Islamic Banking  -  Grounded Answers  -  Citation Verification  -  Arabic NLP  -  Spring Boot  -  Angular",align="L")

# ── Contents ──
pdf.add_page()
pdf.set_font("Helvetica","B",9)
pdf.set_text_color(*NAVY)
pdf.cell(0,5,"Contents",align="L")
pdf.line(14,pdf.get_y()+6, 36, pdf.get_y()+6)
pdf.ln(7)
toc = [
    ("1","Host Organization - Al Baraka Bank Tunisia","4"),
    ("2","Project Vision - A Governed Banking Intelligence","5"),
    ("3","System Architecture - The Full Picture","6"),
    ("4","The Intelligence Engine - RAGLab","8"),
    ("5","The Digital Bank - The Web Application","10"),
    ("6","Problems Solved - What Broke & How It Was Fixed","12"),
    ("7","Security, Privacy & Governance","14"),
    ("8","Deployment & Operations","15"),
    ("9","Results & Limitations","16"),
    ("10","Conclusion & Perspectives","17"),
    ("A","References & Appendices","18"),
]
for n,t,p in toc:
    y=pdf.get_y()
    pdf.set_font("Helvetica","B",6.5)
    pdf.set_text_color(*RED if n.isdigit() else NAVY)
    pdf.set_x(14); pdf.cell(8,4.5,n,align="C")
    pdf.set_font("Helvetica","",6.8)
    pdf.set_text_color(*DARK)
    pdf.cell(152,4.5,t,align="L")
    pdf.set_font("Helvetica","",6.8)
    pdf.set_text_color(*GRAY)
    pdf.cell(14,4.5,p,align="R")
    pdf.ln(5.5)
    pdf.set_draw_color(235,238,245)
    pdf.line(14, y+5, 196, y+5)
pdf.ln(4)
pdf.set_font("Helvetica","I",5.8)
pdf.set_text_color(*GRAY)
pdf.multi_cell(182,3.3,"Reading guide: Ch.1-3 set the context (who/why/how it fits). Ch.4-5 are the technical core (engine + app). Ch.6 is the most important - it explains what broke and how it was fixed. Ch.7-9 close with security, deployment and measured results.",align="J")

# ── Ch1 ──
chapter(1, "Host Organization", "Al Baraka Bank Tunisia  -  Centre Urbain Nord, Tunis")
section("Identity and History")
paragraph("Al Baraka Bank Tunisia is the first Islamic bank in Tunisia and the Maghreb. Created on 15 June 1983 as Beit Ettamouil Tounsi Saoudi (BEST Bank) by the Tunisian State and Sheikh Saleh Abdallah Kamel (founder of the Dallah Al Baraka Group), it was renamed Al Baraka Bank Tunisia in 2009 as part of the Group's identity unification. In 2013 it became a universal resident bank, serving individuals, professionals and enterprises with Sharia-compliant products. The Group, Al Baraka Group B.S.C. in Bahrain Bay, runs 600+ branches in 12 countries. Today the bank operates 44 branches across Tunisia, headquartered at Centre Urbain Nord - elected Best Islamic Bank in Tunisia (2018, 2019, 2021) and honored with five IFN Awards in 2024.")
section("Product Universe")
paragraph("All products are framed by Tunisian Law No. 2016-48 of 11 July 2016 on banks and financial institutions (Articles 12-16 define Islamic operations) and by Central Bank Circular BCT 2019-08 - the same two documents that form the corpus of this project.")
table(["Family","Products"],[
 ["Trading & Investment","Murabaha (cost-plus), Salam (deferred delivery), Istisna'a, Mudaraba/Musharaka, Sukuk, participative deposits"],
 ["Leasing & Financing","Ijara with acquisition option, Beit Al Baraka (home), Tas'heelat (personal), equipment & working-capital"],
 ["International & Daily","Documentary credits, remittances, leasing, everyday banking"],
], [42,140], caption="Product families the assistant must explain without error.")
section("The Internship Site")
note_box("Al Baraka Bank - Centre Urbain Nord",
 "Avenue Cheikh Mohamed Fadhel Ben Achour, Centre Urbain Nord, 1082 Tunis  -  Tel: (+216) 71 183 203  -  Head office: +216 71 18 65 00  -  SWIFT: BEITTNTT\nThis district is the bank's operational heart, where product, compliance and IT decisions meet - and where the project was supervised by Mrs. Ahlem Ben Haddoud.")

# ── Ch2 ──
chapter(2, "Project Vision", "A Governed Banking Intelligence")
section("The Business Problem")
paragraph("Banks produce vast regulatory text: laws, central-bank circulars, internal guides. Finding a reliable answer is costly; giving it to a raw language model is risky - it always answers, even when it does not know. Three tensions drove the project: the user's everyday question rarely matches the document's technical wording (sigha, mahal al-aqd, tamwil); the corpus is Arabic but questions arrive in French and English; and a false rate or deadline is a legal exposure.")
section("North Star (5 Properties)")
table(["#","Property","In Practice"],[
 ["1","Grounded & Auditable","Every answer traceable to a source; nothing fabricated"],
 ["2","Compliant","PII scrubbed on output; no learning from chats"],
 ["3","Quota-Controlled & Secure","Public channel metered; engine off the Internet"],
 ["4","Regulable by Staff","Admins edit taxonomy, facts and documents"],
 ["5","Multilingual (RTL)","FR / AR / EN; Arabic RTL first-class"],
],[10,46,126], caption="Five properties governed every technical choice, in priority order.")
section("What Was Delivered")
note_box("Two Systems, One Product",
 "RAGLab answers; Bank Assistant serves customers and governs what RAGLab knows. Neither writes into the other's store. Staff sees every conversation from its first message; customers never see the source document reference. That boundary makes the answer path auditable.")
paragraph("The web app provides a public landing page, a floating assistant widget, an authenticated client portal (accounts, cards, transactions, simulations, advisor appointments) and an 11-screen admin back-office with a 5-tab RAGLab Console. The intelligence engine is a stateless, versioned HTTP microservice with 25+ routes, a frozen contract and a full audit trail.")

# ── Ch3 ──
chapter(3, "System Architecture", "The Full Picture  -  Browser to Intelligence")
section("End-to-End Flow")
fig(os.path.join(ASSETS,"architecture_schema.png"), "Full architecture: Generation 1 (flat lab) -> pivot -> Generation 2 (four governed layers) -> current update. The engine is addressed by ip:port on a private network.", w=155)
paragraph("In words: the browser talks only to nginx (Angular SPA, security headers, SSE). Nginx proxies /api to the Spring Boot edge (Java 21) which owns authentication, roles, quotas, persistence and admin. The edge calls RAGLab over an authenticated internal contract (X-Service-Token) and streams the answer back. PostgreSQL (Flyway, 15 migrations, single writer, ddl-auto=validate) holds the business schema. The vector index lives inside the Python agent - no shared-DB write coupling. The edge opens the exchange row before calling the engine, so even a failure is visible.")
section("Why This Separation Is the Design")
paragraph("The split is the guarantee: the business schema can evolve without re-indexing; the index can be rebuilt without migrating the business database. An admin switching the engine, a document re-published or a model key rotated all flow through explicit, audited actions.")
section("Technology Stack")
table(["Layer","Choice","Why"],[
 ["Frontend","Angular 21 (standalone), Vitest","SPA with lazy modules; no Google-fonts dependency"],
 ["Backend Edge","Java 21, Spring Boot 4, Flyway, JWT, virtual threads","Owns schema with validate; avoids carrier pinning"],
 ["Intelligence","Python 3.11, FastAPI + uvicorn","No LangChain - every behavior readable"],
 ["Vector Store","ChromaDB local (cosine) + BM25","Local avoids cloud cost & banking-data egress"],
 ["Embeddings / LLM","NVIDIA 2048-d + Qwen free","Measured best on Arabic; no fallback"],
 ["Persistence","PostgreSQL, Redis queue, Flyway","Single writer; durable BRPOP queue"],
 ["Deployment","Docker Compose, offline tarball","Same images dev -> prod; Windows path"],
],[28,56,98], caption="Stack: sobriety as a governance decision.")
section("Two Lifecycles")
bullet("Document: POST /documents (content-hash versioned; identical bytes = no-op; SHA-256 dedup) then batch POST /ingest (one job at a time; 409 is a state). The edge's documents table is a mirror - RAGLab owns the index and reports pending/indexed/stale honestly.")
bullet("Conversation: browser -> edge (quota gate) -> RAGLab (retrieval -> generation -> gate) -> edge (scrub -> persist -> settle -> stream).")
fig(os.path.join(ASSETS,"workflow.png"), "Customer chat path: two-phase metering, streaming relay and persistence. The edge is the single writer; streaming is end-to-end (Python -> Java -> browser).", w=148)

# ── Ch4 ──
chapter(4, "The Intelligence Engine - RAGLab", "How It Thinks: From Scan to Grounded Answer")
section("Corpus and the Arabic Challenge")
table(["Document","Role","Fragments"],[
 ["Law 2016-48 (198 articles)","Superior legal reference","233"],
 ["BCT Circular 2019-08","Operational framing","13"],
 ["Internal Islamic Operations Guide","Internal policy","54"],
 ["Intro to Islamic Finance (Madkhal)","Training, definitions","39"],
 ["Total","4 real Arabic documents","339"],
],[62,74,46], caption="Governed corpus: 4 real documents -> 339 structure-aware fragments.")
paragraph("The PDFs are scanned legal Arabic: visual-order lines, reversed word order, corrupted digits, broken headings. Indexed directly, the corpus is noise. The fix is linguistic - see Chapter 6.")
section("Structure-Aware Chunking")
paragraph("Two chunkers are measured. Legacy size mode (220 tokens + 40 overlap) remains; adopted restructure mode does three steps: (1) semantic normalization - gazette cleanup, repeated-header dropping (>=3), section-marker extraction (al-fasl/al-bab), visual-order repair by zone into Markdown; (2) breadcrumb enrichment - injecting h1 > h2 > h3 ancestry above every heading/table; (3) recursive structural split over ['\\n# ', '\\n## ', '\\n### ', '\\n\\n', '\\n', ' '] at 220/40. Each chunk carries an identity card: document, heading, language, section_type, chunk_fp (fingerprint v4), embedding_fp, model.")
note_box("Impact","+20 points hit@1 over window splitting (live: 51 -> 71; offline BM25: 42 -> 62). This single change outranked every retrieval experiment.")
section("Embeddings and Storage")
bullet("Model: nvidia/nemotron-3-embed-1b, native 2048-d, NVIDIA Build endpoint (batch 32, backoff + Retry-After, bisect on degenerate vectors, resumable cache).")
bullet("Store: one ChromaDB collection per (embedding-space x chunking mode), hnsw:space=cosine. BM25 lexical index in parallel for hybrid modes.")
bullet("Fingerprint guard: chunk_fp + embedding_fp stored per chunk; stale collection refused before any model call. No mixed indexes served.")
section("Retrieval: Dense Wins")
table(["Mode","hit@1","hit@3","hit@5"],[
 ["vector (dense, default)","71","80","87"],
 ["rrf (k=60)","71","80","82"],
 ["blend (lambda=0.7)","62","80","84"],
],[50,44,44,44], caption="Measured identically (live, 50 questions). Dense stays default; French gap is sharper: 50/67/67 vs 33/33/50.")
section("Understanding the Question")
table(["Module","Function"],[
 ["intent.py","Rule-based intent; splits compound requests; detects ambiguity"],
 ["evidence_plan.py","Derives evidence requirements - never the answer"],
 ["sufficiency.py","Checks plan vs fragments: sufficient/insufficient/contradictory"],
 ["interrogate + topic_map","Only when nothing covered: one bounded LLM call re-expresses the question (262-node map)"],
],[42,140])
section("Delivery: Citation Gate and Audit")
bullet("Prompt grounded-v1: question + sources as untrusted data; no tools; strict JSON claims+evidence; quotes >=12 chars, verbatim.")
bullet("Three gates: (i) quote membership - normalized substring; (ii) numeric - every number in claim must appear in quotes; (iii) policy - source retrieved and allowed.")
bullet("Sufficiency commitment: nothing covered -> refuse before generation naming what's missing; partial -> one bounded regeneration. PII scrub after gate; audit JSONL via GET /audit (500 cap) correlated by X-Request-Id.")
section("Knowledge Layer (Law 2016-48)")
bullet("198 units (=198 articles, stable ID loi-2016-48:artNNN), 77 cross-refs + 1 anchoring relation, 53 number records (rates/deadlines/caps/sanctions) via GET /numbers, 6 governance axes.")
note_box("Result","A unit-description index answers law questions at 100/100/100 vs 67/92/92 baseline - every law question, including cross-lingual, finds its article at rank 1.")

# ── Ch5 ──
chapter(5, "The Digital Bank - Web Application", "How It Serves: Portal, Auth and Control Room")
section("Overall Shape")
paragraph("The web app is the only public edge. RAGLab is never on the Internet; it is addressed by ip:port on a private network with X-Service-Token. Frontend: Angular 21 standalone lazy modules (landing, chat widget, auth, portal, admin /admin/**). Backend: Spring Boot 4 Java 21 - JWT, Flyway, quotas, persistence, monitoring, 5-tab RAGLab Console. Database: PostgreSQL, Flyway single-owner V1..V15, ddl-auto=validate.")
section("Customer Experience")
bullet("Landing at /: trilingual hero, products, assistant showcase, services, FAQ, footer - palette Red #E0122F / Orange #F5881C, RTL support. Floating widget with own session, quota-limited, no login needed.")
bullet("Auth split: Client vs Staff tabs, role-based redirect, /register. Portal /portal: accounts, cards, transactions, financing simulations, advisor appointments, profile + ChatGPT-style assistant with own history.")
fig(os.path.join(ASSETS,"before-after-01-login.png"), "Login: dedicated Client vs Staff tabs - the split that enables customer-data isolation.", w=158)
fig(os.path.join(ASSETS,"before-after-14-chat-answers.png"), "Chat: grounded answers with citations vs honest refusals - never a guess.", w=158)
section("Core Banking Data Model (V15)")
table(["Domain","Tables & Key Fields"],[
 ["Identity & Risk","clients (CIN, ABK######, KYC, risk, branch agence_id, employer, income)"],
 ["Money","comptes (IBAN, RIB, solde), cartes (masked porteur 4571 **** **** 6842, no PAN/CVV), transactions"],
 ["Products","financements (murabaha/ijara, margin, installments), beneficiaires"],
 ["Service","advisor_requests (free-text), conversations, messages"],
],[36,146], caption="Realistic core-banking shape. Transactions may contain third-party personal data (DPIA-relevant).")
paragraph("The assistant never sees raw balances - for an authenticated customer, a masked context (CustomerContextService) is derived at call time: slices and categories only, no identifiers leave the bank.")
section("Authentication, Roles and Quotas")
bullet("JWT + server-side rotation: access <=1h; refresh with jti/family, single-use, reuse burns family -> 401. Revoked on logout or admin role change.")
bullet("Dynamic roles: admin_roles + permissions + audits (V14). 12 fixed permissions; SUPER_ADMIN reserved; unknown -> 403; every change audited with reason.")
bullet("Two-phase metering: gate() before engine, settle() after. Anonymous 3000/20, authenticated 10000/50, 5 sessions/hour. Per-session striped ReentrantLocks (64 stripes) avoid carrier pinning on JDK 21 virtual threads.")
section("Streaming and Persistence")
paragraph("Streaming: Python stream -> Java invokeStream (pinned HTTP/1.1) -> Java SSE relay -> browser fetch+ReadableStream. Fallback to POST /message. Persistence: Java opens the row before calling RAGLab, persists after streaming, attributes engine=RAGLab and provenance, sends last 10 turns as context (turn_id + X-Turn-Id).")
section("Admin Control Room")
table(["Screen","Function"],[
 ["/admin/facts","Governed facts CRUD - rates/fees outrank documents; append-only audit"],
 ["/admin/taxonomy","Exemplar & release management; seal() + publish rollback"],
 ["/admin/agent/health","Effective RAGLAB_URL, live probe latency"],
 ["/admin/agent/quality","Answer -> retrieval -> quote existence checks"],
 ["/admin/agent/documents","One drop zone, two chunk worlds (pending/indexed/stale)"],
 ["/admin/agent/config","Models & keys (write-only, audited)"],
 ["/admin/agent/journal","Every action, filterable, with motive"],
 ["/admin/agent/monitor","Live feed + KPIs over last 200 exchanges"],
],[42,140], caption="Every write audited, every read role-gated. Back-office stays in French (internal).")

# ── Ch6 ──
chapter(6, "Problems Solved", "What Broke & How It Was Fixed  -  The Most Important Chapter")
paragraph("Not what was planned, but what broke - and what architecture prevents it from breaking again. Each fix is measurable and regression-tested.")
# compact problem list as table rather than heavy boxes
table(["Problem","Fix (Measurable)"],[
 ["Scanned Arabic PDFs in visual order, corrupted digits","restructure.py: NFKC + marker extraction + zone reconstruction; 29 batches (215 corrections), then 35/23/21; raw docs/ never modified; llm_repair_check.py gated"],
 ["220-token window cuts articles mid-definition","Structure-aware chunking: +20 pp hit@1 (51->71 live; 42->62 offline) - largest win"],
 ["Latin terms (murabaha) vs Arabic script","Multilingual embeddings + declared cross-script bridges (df-checked) + colloquial vocab + one bounded interrogation call"],
 ["Guided retrieval dead in production (no search fn passed)","Live service now passes search callable; lesson: verify on the deployed service, not only in tests"],
 ["Index duplication (859/1713 re-pushes)","RAGLab SHA-256 409 guard + content-hash versioning; batch POST /ingest as one job; in-house delete_source purge fix"],
 ["French 'Quelle' inside 'Quelles' misclassification","Anchored regex + dedicated French procedural patterns; live-set regression tests"],
 ["Streaming HTTP/2 Upgrade -> 422 on uvicorn","Pin java.net.http.HttpClient to HTTP/1.1 - one-line fix, days of debugging"],
 ["Hardcoded token + 500 masking + nginx cache","Env wins over Compose default; network errors -> 502; index.html no-cache; all regression-tested"],
],[52,130], caption="Eight hard parts - each with a measurable, audited fix. This table is the spine of the internship.")
paragraph("Two additional fixes worth noting: nginx cached the wrong index.html (now no-cache) and critical-CSS injected an onload handler blocked by CSP (now disabled). Both were invisible in unit tests and obvious in operation - the same lesson as above: reality is the only judge.")

# ── Ch7 ──
chapter(7, "Security, Privacy & Governance", "Defense in Depth for a Bank")
section("Layers")
table(["Layer","Mechanism"],[
 ["Network","Only nginx public; backend+Postgres+RAGLab on private network; TLS + HSTS"],
 ["Authentication","JWT (>=256-bit); X-Service-Token constant-time compare"],
 ["Authorization","All admin @PreAuthorize; DB-backed 12 permissions; unknown -> 403"],
 ["Input","Filename validation; 20 MiB cap -> 413; enum -> 400"],
 ["Prompt Injection","Prompt marks Q+A as untrusted; local guard refuses live-data requests"],
 ["Output","PII scrub after gate: EMAIL/PHONE/RIB-IBAN/CIN -> [LABEL]"],
 ["Secrets","Env / platform secrets only; GET /keys shows presence; POST rejects placeholders"],
],[30,152], caption="Each layer is small; together they are complete. No layer is trusted alone.")
section("Personal Data")
paragraph("Nine families across clients/comptes/cartes/transactions/beneficiaires/financements/advisor_requests/conversations/refresh_tokens - from identifiers (CIN) to financial (IBAN) to free-text (chat, appointments). Two truths: (1) nothing identifiable leaves the bank beyond a masked slice/category context built at call time for an authenticated customer; (2) the highest real risk is free text - a question may contain an account number, stored verbatim and shown to staff. Output scrub + audit mitigate; retention/deletion lifecycle remains a documented pre-production gap for Legal.")
paragraph("No full PAN or CVV stored. Transactions may contain third-party personal data - DPIA-relevant. The Customer PII Report lists every field, who writes it and who may see it.")
section("Audit & Threat Pass")
bullet("Every answer carries citations + engine attribution (RAGLAB_*); every admin action to agent_admin_audit with mandatory reason; every ingestion to JournalIngestion; every conversation visible from its first message; KPIs over last 200 exchanges.")
bullet("20-point checklist (THREAT_PASS_6.2.md) drove fixes: second unauth door removed, constant-time compares, hardening headers, Swagger off in prod, host ports to 127.0.0.1. Remaining: magic-byte sniffing, per-request DB auth, Redis auth, nightly purge of expired refresh tokens.")

# ── Ch8 ──
chapter(8, "Deployment & Operations", "One Image Set, Three Environments")
table(["","dev","staging","prod"],[
 ["How","docker compose up","same images, real TLS","same images, HA stores"],
 ["Profile","(default)","prod","prod"],
 ["Swagger","on","off","off"],
 ["Logs","plain / json","ecs","ecs"],
 ["Secrets",".env (local)","platform secrets","platform secrets"],
 ["Data","throwaway","anonymized monthly","live"],
],[28,48,48,58], caption="Promotion moves digests dev -> staging -> prod; never rebuild for prod.")
paragraph("deploy/promote.sh enforces: refuses dirty tree, tags with commit SHA (never latest), verify compares GET /actuator/info + /version.json against HEAD. docker-compose.prod.yml sets prod profile and secrets. Kubernetes translation is 1:1.")
section("Topology")
bullet("Internet --443(TLS,HSTS)--> Edge Proxy --80(private)--> nginx SPA --/api--> Spring Boot --> RAGLab (private) --> Postgres (PITR: daily full + WAL, 30-day retention, quarterly drill). Only the edge proxy is public.")
bullet("Flyway runs at boot; V1..V15 expand->migrate->contract; non-idempotent V1 was rewritten and baselined at 0.")
section("Deploy Procedure")
pdf.set_font("Courier","",5.5)
pdf.set_fill_color(250,251,253)
pdf.set_draw_color(*BORDER)
y0=pdf.get_y()
lines=["TAG=v1.0.0 docker compose -f docker-compose.yml -f docker-compose.prod.yml pull",
 "docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d backend",
 "docker compose logs backend | grep -E 'Flyway|Migrating|Successfully'",
 "docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d frontend",
 "E2E_BASE_URL=https://assistant.example.tn ./e2e/run.sh"]
h=len(lines)*3.5+6
pdf.rect(14,y0,182,h,'DF')
for i,l in enumerate(lines):
    pdf.set_xy(16,y0+3+i*3.5)
    pdf.set_text_color(45,55,80)
    pdf.cell(0,3.5,l,align="L")
pdf.set_y(y0+h+3)
section("Observability")
bullet("Correlation: turnId minted in Java, sent as body + X-Turn-Id, bound to MDC + Python contextvar - one grep spans both services; X-Request-Id echoed.")
bullet("Metrics: GET /actuator/prometheus (chat_turns_total) + GET /metrics (agent_turns_total). OpenTelemetry is the next step.")
bullet("Runbooks: RUNBOOK.md + HOST_TESTING.md + docker-compose.test.yml give a one-command workstation test (seeded staff + 4 customers with accounts/cards/movements/financings).")

# ── Ch9 ──
chapter(9, "Results & Limitations", "Measured, Not Claimed")
section("How Quality Is Measured")
bullet("questions_50.json - 50 cases: 10 verbatim, 20 paraphrases, 15 cross-lingual (6 FR + 9 EN), 5 out-of-scope. 32 AR / 8 FR / 10 EN. Expected substring verified as distinctive.")
bullet("questions_targets.json - 10 AR cases: 2 per category (colloquial, synonyms, implicit, compound, ambiguity). Rules: paired in same run; +-2 pp across runs; frozen set never edited to improve a result.")
section("Headline Results")
table(["What","Result"],[
 ["Chunking (live, 50)","size 51/64/71 -> restructure 71/80/87 (hit@1/3/5)  -  +20 pp"],
 ["Retrieval (restructure)","dense 71/80/87 > RRF 71/80/82 > blend 62/80/84"],
 ["Reranker (offline)","62/82/87 -> 62/89/89  -  activated"],
 ["Knowledge index (law)","67/92/92 -> 100/100/100  -  every article at rank 1"],
 ["Target categories","compound 100 | implicit+ambiguous 50/100 | synonyms 50 | colloquial 0"],
 ["Model independence","agreement 0.720; Jaccard 1.000 (divergence is accept/refuse)"],
 ["Final campaign (7+12+6)","retrieval 5/7 | topic map 5/5 | 3 answered / 3 refused, zero errors"],
 ["Robustness (6)","Fabricated/borrowed/computed/unknown/injection -> all rejected"],
 ["Tests & CI","472 unit + 133 offline | 8 workflows | 469 harness families | green"],
],[52,130], caption="All numbers from frozen artifacts, not impressions.")
section("Limitations")
table(["Limitation","Scope"],[
 ["Citation != entailment","Gate proves words/numbers come from source, not that claim logically follows"],
 ["No threshold","Top-5 transmitted even if off-topic; safety = sufficiency + refusal"],
 ["Single worker","One profile/index/ingest at a time; scale is designed, not deployed"],
 ["Updates by re-ingestion","Old fragments serve until next ingest (now surfaced as stale)"],
 ["Evaluation scale","50 + 10 cases small - not a security/legal guarantee"],
],[48,134])
note_box("Readiness (Stated in the Repository)",
 "Suitable for a supervised pilot on approved non-confidential documents; not ready for unsupervised customer-facing banking advice. This honesty is a product property - the system refuses rather than guesses, and its limits are documented where any auditor can read them.")

# ── Ch10 ──
chapter(10, "Conclusion & Perspectives", "What Was Built and What Remains")
section("What Was Built")
paragraph("A working, documented and measurable platform: a complete language pipeline (reading and repair, structure-aware chunking, vector + lexical indexing, dense retrieval, intent and sufficiency reasoning, citation-gated generation, output scrubbing, audit) wrapped in a bank-operated web app that owns identity, quotas and persistence - all measured, versioned and traceable.")
section("Lessons That Outlive the Code")
bullet("Measurement replaces opinion. Restructure over window, dense over fusion, lexicon off, expansion abandoned - all settled by paired numbers; several reasonable intuitions were rejected by data.")
bullet("Governance of decisions is architecture. Step-and-plan, activation gates and frozen sets let the project survive interruptions without silent regression.")
bullet("Reality is the only judge. The most expensive defects were invisible in tests and obvious in operation. Any claim about the deployed service must be checked on the deployed service.")
section("Next Steps")
bullet("Per-document authorization and chunk-level topical metadata (path/level/parent_id) before any production re-index.")
bullet("Topical & faceted retrieval arms measured on frozen sets, without reintroducing regressions.")
bullet("Closing measurement campaign - re-measure every arm, document limits, then decide defaults.")
bullet("Industrialization: incremental updates, observability, adversarial evaluation, magic-byte validation, OpenTelemetry and a legal-reviewed retention/deletion lifecycle.")
bullet("Product: safely editable answer templates and conversation quality flags.")
# quote
pdf.ln(4)
y0=pdf.get_y()
pdf.set_draw_color(*BORDER)
pdf.set_fill_color(250,251,253)
pdf.rect(20,y0,170,14,'DF')
pdf.set_xy(22,y0+2)
pdf.set_font("Helvetica","I",6.8)
pdf.set_text_color(*NAVY)
pdf.multi_cell(166,3.4,'"The best system we built is not the one that answers the most questions,\nbut the one that knows when not to answer - and can prove why."',align="C")
pdf.set_xy(22,y0+10)
pdf.set_font("Helvetica","",5)
pdf.set_text_color(*GRAY)
pdf.cell(166,2.5,"Farah AYARI, ESPRIT  -  Al Baraka Bank, Centre Urbain Nord  -  Mrs. Ahlem Ben Haddoud",align="C")
pdf.set_y(y0+16)

# ── References + Appendices ──
pdf.add_page()
pdf.set_font("Helvetica","B",9)
pdf.set_text_color(*NAVY)
pdf.cell(0,5,"References",align="L")
pdf.line(14,pdf.get_y()+6, 34, pdf.get_y()+6)
pdf.ln(7)
pdf.set_font("Helvetica","",6)
pdf.set_text_color(*DARK)
refs=[
 ("RAGLab/README.md","Supported pipeline, A/B tables"),
 ("RAGLab/RAGLAB_SPEC.md","Reverse-engineered spec"),
 ("RAGLab/RAGLAB_GAP_ANALYSIS.md","Gap analysis"),
 ("RAGLab/raglab/CONTRACT.md","Frozen HTTP contract"),
 ("bank-assistant/README.md","Web-app & governance"),
 ("bank-assistant/PLAN_TO_PROD.md","Phased plan 0->10"),
 ("bank-assistant/docs/DEPLOYMENT.md","Topology & promotion"),
 ("bank-assistant/docs/CUSTOMER_PII_REPORT.md","PII inventory & gaps"),
 ("bank-assistant/docs/THREAT_PASS_6.2.md","20-point checklist"),
 ("RAGLab/docs/ (4 docs)","Law 2016-48, Circular 2019-08, Guide, Madkhal"),
]
for a,b in refs:
    pdf.set_font("Helvetica","B",6)
    pdf.set_x(14); pdf.cell(58,4,a,align="L")
    pdf.set_font("Helvetica","",6)
    pdf.cell(0,4,b,align="L")
    pdf.ln(5)
pdf.ln(2)
pdf.set_font("Helvetica","I",5.5)
pdf.set_text_color(*GRAY)
pdf.multi_cell(182,3.2,"Institutional context: albaraka.com.tn (history, 44 branches, IFN Awards); Law 2016-48 Official Journal No.58; ESPRIT Ariana (2003, CTI EUR-ACE 2023).",align="J")
pdf.ln(5)
pdf.set_font("Helvetica","B",9)
pdf.set_text_color(*NAVY)
pdf.cell(0,5,"Appendix A - Glossary",align="L")
pdf.ln(6)
table(["Term","Transliteration","Meaning"],[
 ["al-murabaha","al-murabaha","Cost-plus sale with disclosed margin"],
 ["al-ijara","al-ijara","Lease with acquisition option"],
 ["al-sigha","al-sigha","Contractual form (offer & acceptance)"],
 ["al-fasl","al-fasl","Article (law) / chapter (circular)"],
 ["nafidh","nafidh","In force"],
 ["kafin / ghayr kafin","kafin","Sufficient / Insufficient"],
 ["muta'arid","muta'arid","Contradictory"],
 ["RAG / hit@k / RRF","-","Retrieval-Augmented Generation / top-k / Reciprocal Rank Fusion"],
 ["Citation gate","-","Quote membership + numeric gate"],
],[42,40,100])
pdf.ln(2)
pdf.set_font("Helvetica","B",9)
pdf.set_text_color(*NAVY)
pdf.cell(0,5,"Appendix B - Key Figures",align="L")
pdf.ln(6)
table(["Fact","Source"],[
 ["RAGLab: 376 commits, 249 files, 86 modules","Git history & RAGLAB_FULL_REPORT.md"],
 ["Corpus: 4 docs -> 339 fragments","raglab/audits/MASTER_INDEX.md"],
 ["Tests: 472 unit + 133 offline, 8 workflows","CI at merge (service v1.5.0)"],
 ["Web app: 255 commits, 428 files, 15 migrations","bank-assistant/docs/PROJECT_REPORT.md"],
 ["Stack: NVIDIA 2048-d + Qwen (no fallback)","pipeline_policy.py"],
],[52,130])
pdf.ln(2)
pdf.set_font("Helvetica","B",9)
pdf.set_text_color(*NAVY)
pdf.cell(0,5,"Appendix C - Reproduction",align="L")
pdf.ln(7)
pdf.set_fill_color(250,251,253)
pdf.set_draw_color(*BORDER)
y0=pdf.get_y()
lines=["# RAGLab","cd RAGLab/raglab && python main.py ingest --reset --data-dir ../docs",
 "python main.py answer \"...\" --query-lang ar","","# Web app","cp bank-assistant/.env.docker.example .env   # set RAGLAB_URL, JWT_SECRET",
 "docker compose up --build   # http://localhost:8080","","# Verify","curl http://localhost:8000/health",
 'curl http://localhost:8080/api/agent/health -H "Authorization: Bearer <token>"']
h=len(lines)*3.4+6
pdf.rect(14,y0,182,h,'DF')
pdf.set_font("Courier","",5.5)
pdf.set_text_color(45,55,80)
for i,l in enumerate(lines):
    pdf.set_xy(16,y0+3+i*3.4)
    pdf.cell(0,3.4,l,align="L")
pdf.set_y(y0+h+5)
pdf.set_draw_color(*RED)
pdf.line(70,pdf.get_y(),140,pdf.get_y())
pdf.ln(3)
pdf.set_font("Helvetica","I",5.8)
pdf.set_text_color(*GRAY)
pdf.cell(0,3.2,"Report by Farah AYARI - ESPRIT, internship at Al Baraka Bank Tunisia - Centre Urbain Nord, supervised by Mrs. Ahlem Ben Haddoud.",align="C")
pdf.ln(3.5)
pdf.set_font("Helvetica","",5)
pdf.set_text_color(140,150,170)
pdf.cell(0,3,"No identifiers or secrets are included. All figures traceable to frozen benchmark artifacts.  |  Internship  |  Centre Urbain Nord",align="C")

print(f"Pages: {pdf.pages_count}")
pdf.output(OUTPUT)
print(f"Written to {OUTPUT}  ({os.path.getsize(OUTPUT)/1024:.1f} KB)")
