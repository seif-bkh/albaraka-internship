#!/usr/bin/env python3
"""
Generate a beautiful 18-page internship report PDF via fpdf2.
Assets expected in ./assets/
Requires: pip install fpdf2 pillow
"""
from fpdf import FPDF
from PIL import Image
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(ROOT, "assets")
OUTPUT = os.path.join(ROOT, "Farah_Ayari_Internship_Report.pdf")

# Colors (RGB)
ABARAKA_RED = (224, 18, 47)
ABARAKA_ORANGE = (245, 136, 28)
ESPRIT_NAVY = (27, 42, 78)
ESPRIT_NAVY_LIGHT = (45, 65, 115)
TEXT_DARK = (26, 31, 46)
MID_GRAY = (232, 236, 243)
SOFT_GRAY = (245, 247, 251)
ACCENT_TEAL = (31, 122, 92)
ACCENT_GOLD = (181, 122, 20)
WHITE = (255,255,255)

W, H = 210, 297  # A4

class ReportPDF(FPDF):
    def __init__(self):
        super().__init__(orientation='P', unit='mm', format='A4')
        self.set_auto_page_break(auto=True, margin=18)

    def header(self):
        if self.page_no() == 1:
            return
        # thin line
        self.set_draw_color(*MID_GRAY)
        self.set_line_width(0.2)
        self.line(12, 10, 198, 10)
        # header text
        self.set_xy(12, 11)
        self.set_font("Helvetica", "I", 6.5)
        self.set_text_color(120, 130, 150)
        self.cell(80, 4, "Farah AYARI  |  ESPRIT  -  Al Baraka Bank", align="L")
        self.set_xy(130, 11)
        self.cell(68, 4, "A Governed Intelligence for Islamic Banking", align="R")

    def footer(self):
        if self.page_no() == 1:
            return
        self.set_y(-10)
        self.set_font("Helvetica", "", 6)
        self.set_text_color(130, 140, 160)
        self.cell(0, 4, f" Internship Report   -   Centre Urbain Nord   -   Mrs. Ahlem Ben Haddoud    |    {self.page_no()} ", align="C")
        # thin bottom line
        self.set_draw_color(*MID_GRAY)
        self.set_line_width(0.15)
        self.line(12, H-11, 198, H-11)

def safe_image(path, w=None, h=None, x=None, y=None, keep_aspect=True):
    """Return True if exists"""
    return os.path.exists(path)

pdf = ReportPDF()

# ──────────────────────────────────────────────────────────
# COVER PAGE  -  custom drawing
# ──────────────────────────────────────────────────────────
pdf.add_page()
# Background white
pdf.set_fill_color(*WHITE)
pdf.rect(0,0,210,297,'F')
# Top red band
pdf.set_fill_color(*ABARAKA_RED)
pdf.rect(0,0,210,38,'F')
# gradient effect: thin orange band
pdf.set_fill_color(*ABARAKA_ORANGE)
pdf.rect(0,38,210,7,'F')
# gold accent line
pdf.set_fill_color(*ACCENT_GOLD)
pdf.rect(0,45,210,0.6,'F')
# bottom navy band
pdf.set_fill_color(*ESPRIT_NAVY)
pdf.rect(0,285,210,12,'F')
pdf.set_fill_color(*ABARAKA_RED)
pdf.rect(0,283.5,210,1.5,'F')

# Top small text on red
pdf.set_xy(10, 6)
pdf.set_font("Helvetica", "", 7)
pdf.set_text_color(*WHITE)
pdf.cell(190, 4, "REPUBLIC OF TUNISIA  |  Ministry of Higher Education and Scientific Research", align="C")
pdf.set_xy(10, 11)
pdf.set_font("Helvetica", "", 6.5)
pdf.cell(190, 3.5, "Private Higher School of Engineering and Technology  -  ESPRIT    -    Al Baraka Bank Tunisia", align="C")
pdf.set_xy(10, 15.5)
pdf.set_font("Helvetica", "I", 5.5)
pdf.set_text_color(255,255,255)
# translucent white
pdf.cell(190, 3, "Centre Urbain Nord, Avenue Cheikh Mohamed Fadhel Ben Achour, 1082 Tunis", align="C")

# Logos row  -  two white cards
# ESPRIT card
pdf.set_fill_color(*WHITE)
pdf.set_draw_color(255,255,255)
# card with rounded effect simulated via rect
pdf.rect(14, 52, 88, 22, 'F')
pdf.rect(14, 52, 88, 22, 'D')
# separator line
pdf.set_draw_color(220,225,235)
pdf.set_line_width(0.15)
pdf.line(105, 53, 105, 73)

# Al Baraka card
pdf.set_fill_color(*WHITE)
pdf.rect(108, 52, 88, 22, 'F')
pdf.rect(108, 52, 88, 22, 'D')

# Try to place logo images if exist
esprit_logo = None
for cand in ["esprit-tunisia-logo-private-engineering--1.png", "esprit-ariana.jpg", "esprit-tunisia-logo-private-engineering--3.jpg"]:
    p = os.path.join(ASSETS, cand)
    if os.path.exists(p):
        esprit_logo = p
        break
albaraka_logo = None
for cand in ["al-baraka-bank-tunisia-logo-red-orange-1.png", "albaraka-front.png", "al-baraka-bank-tunisia-logo-red-orange-2.png"]:
    p = os.path.join(ASSETS, cand)
    if os.path.exists(p):
        albaraka_logo = p
        break

if esprit_logo:
    try:
        pdf.image(esprit_logo, x=18, y=54, w=30, h=14)
        # text beside
        pdf.set_xy(50, 56)
        pdf.set_font("Helvetica", "B", 8)
        pdf.set_text_color(*ESPRIT_NAVY)
        pdf.cell(45, 4, "ESPRIT", align="L")
        pdf.set_xy(50, 60)
        pdf.set_font("Helvetica", "", 5.5)
        pdf.set_text_color(80,90,115)
        pdf.cell(45, 3, "Private Higher School of", align="L")
        pdf.set_xy(50, 63)
        pdf.cell(45, 3, "Engineering & Technology", align="L")
        pdf.set_xy(50, 66.5)
        pdf.set_font("Helvetica", "I", 4.5)
        pdf.set_text_color(120,130,150)
        pdf.cell(45, 3, "Ariana  -  est. 2003", align="L")
    except: pass
else:
    pdf.set_xy(16, 60)
    pdf.set_font("Helvetica","B",9)
    pdf.set_text_color(*ESPRIT_NAVY)
    pdf.cell(84,5,"ESPRIT",align="C")

if albaraka_logo:
    try:
        pdf.image(albaraka_logo, x=112, y=54, w=30, h=14)
        pdf.set_xy(144, 56)
        pdf.set_font("Helvetica","B",8)
        pdf.set_text_color(*ABARAKA_RED)
        pdf.cell(48,4,"Al Baraka Bank",align="L")
        pdf.set_xy(144,60)
        pdf.set_font("Helvetica","",5.5)
        pdf.set_text_color(110,70,80)
        pdf.cell(48,3,"Your Partner Bank",align="L")
        pdf.set_xy(144,63)
        pdf.cell(48,3,"Islamic Banking since 1983",align="L")
        pdf.set_xy(144,66.5)
        pdf.set_font("Helvetica","I",4.5)
        pdf.set_text_color(130,110,110)
        pdf.cell(48,3,"44 branches  -  5 IFN Awards 2024",align="L")
    except: pass
else:
    pdf.set_xy(110,60)
    pdf.set_font("Helvetica","B",9)
    pdf.set_text_color(*ABARAKA_RED)
    pdf.cell(84,5,"Al Baraka Bank",align="C")

# Main title block
pdf.set_xy(10, 84)
pdf.set_font("Helvetica", "", 8)
pdf.set_text_color(*ESPRIT_NAVY_LIGHT)
pdf.cell(190, 5, "INTERNSHIP REPORT  |  END-OF-STUDIES PROJECT", align="C")
# large title
pdf.set_xy(10, 91)
pdf.set_font("Helvetica","B",24)
pdf.set_text_color(*ESPRIT_NAVY)
pdf.cell(190, 10, "A Governed Intelligence", align="C")
pdf.set_xy(10, 102)
pdf.set_text_color(*ABARAKA_RED)
pdf.cell(190, 10, "for Islamic Banking", align="C")
# subtitle
pdf.set_xy(10, 115)
pdf.set_font("Helvetica","I",9)
pdf.set_text_color(60,70,95)
pdf.cell(190,5,"The Integrated RAGLab & Bank Assistant Platform", align="C")
# tagline box
pdf.set_fill_color(*SOFT_GRAY)
pdf.set_draw_color(*MID_GRAY)
pdf.rect(24, 123, 162, 11, 'DF')
pdf.set_xy(24, 124.5)
pdf.set_font("Helvetica","I",6.5)
pdf.set_text_color(70,80,110)
pdf.cell(162,4,"A retrieval-augmented system where every answer is traceable, every refusal is honest,",align="C")
pdf.set_xy(24,128)
pdf.cell(162,4,"and every decision is auditable  -  because in banking, a wrong rate is a legal risk.",align="C")
# subject tag
pdf.set_xy(55, 139)
pdf.set_fill_color(*ESPRIT_NAVY)
pdf.rect(55,138.5,100,7,'F')
pdf.set_font("Helvetica","B",6.5)
pdf.set_text_color(*WHITE)
pdf.cell(100,7,"  Final-Year Engineering Internship  |  Software Engineering  ",align="C")

# info card
pdf.set_fill_color(*WHITE)
pdf.set_draw_color(*MID_GRAY)
pdf.rect(16, 153, 178, 32, 'DF')
# red accent left border
pdf.set_fill_color(*ABARAKA_RED)
pdf.rect(16,153,1.6,32,'F')

pdf.set_xy(21, 155)
pdf.set_font("Helvetica","B",6.5)
pdf.set_text_color(*ESPRIT_NAVY)
pdf.cell(28,4,"Student",align="L")
pdf.set_font("Helvetica","",6.5)
pdf.set_text_color(*TEXT_DARK)
pdf.cell(140,4,"Farah AYARI  -  Private Higher School of Engineering and Technology (ESPRIT), Software Engineering",align="L")

pdf.set_xy(21, 160.5)
pdf.set_font("Helvetica","B",6.5)
pdf.set_text_color(*ESPRIT_NAVY)
pdf.cell(28,4,"Host Organization",align="L")
pdf.set_font("Helvetica","",6.5)
pdf.set_text_color(*TEXT_DARK)
pdf.cell(140,4,"Al Baraka Bank Tunisia  -  Centre Urbain Nord, Avenue Cheikh Mohamed Fadhel Ben Achour, 1082 Tunis",align="L")

pdf.set_xy(21, 166)
pdf.set_font("Helvetica","B",6.5)
pdf.set_text_color(*ABARAKA_RED)
pdf.cell(28,4,"Supervisor",align="L")
pdf.set_font("Helvetica","B",6.5)
pdf.set_text_color(*TEXT_DARK)
pdf.cell(140,4,"Mrs. Ahlem Ben Haddoud  -  Advisor, Al Baraka Bank (Centre Urbain Nord)",align="L")

pdf.set_xy(21, 171.5)
pdf.set_font("Helvetica","B",6.5)
pdf.set_text_color(*ESPRIT_NAVY)
pdf.cell(28,4,"Academic Year",align="L")
pdf.set_font("Helvetica","",6.5)
pdf.set_text_color(*TEXT_DARK)
pdf.cell(70,4,"2025 - 2026",align="L")
pdf.set_font("Helvetica","",6)
pdf.set_text_color(110,120,145)
pdf.cell(70,4,"ESPRIT  |  Engineering Degree",align="L")

pdf.set_xy(21, 177.5)
pdf.set_font("Helvetica","",5.5)
pdf.set_text_color(100,110,135)
pdf.cell(172,4,"Architecture, integration and governance of a banking RAG platform  -  Two systems, one product, zero fabrication.",align="L")

# bottom quote
pdf.set_xy(10, 210)
pdf.set_font("Helvetica","I",7)
pdf.set_text_color(*ESPRIT_NAVY)
pdf.cell(190,5,"Trust through traceability   -   Every answer cites its source   -   Every refusal explains why",align="C")
pdf.set_xy(10, 215)
pdf.set_font("Helvetica","",5.5)
pdf.set_text_color(120,130,150)
pdf.cell(190,4,"This report contains no account identifiers, repository URLs, or credentials. All figures are traceable to frozen benchmark artifacts.",align="C")

# small illustration placeholder: workflow mini
# draw decorative line
pdf.set_draw_color(*ABARAKA_RED)
pdf.set_line_width(0.3)
pdf.line(88, 225, 122, 225)
pdf.set_fill_color(*ABARAKA_RED)
pdf.circle(105, 225, 1.2, 'F')

# footer text on navy band
pdf.set_xy(10, 288)
pdf.set_font("Helvetica","",6)
pdf.set_text_color(*WHITE)
pdf.cell(190,4,"ESPRIT  -  Al Baraka Bank Tunisia  -  Centre Urbain Nord  -  A Governed Intelligence for Islamic Banking",align="C")

# ──────────────────────────────────────────────────────────
# Helpers for content pages
# ──────────────────────────────────────────────────────────
def add_chapter(num, title, subtitle=None):
    pdf.add_page()
    pdf.set_font("Helvetica","B",32)
    pdf.set_text_color(235,240,248)
    pdf.set_xy(170, 12)
    pdf.cell(20,10,str(num).zfill(2),align="R")
    pdf.set_xy(12, 16)
    pdf.set_draw_color(*ABARAKA_RED)
    pdf.set_line_width(0.7)
    pdf.line(12, 18, 50, 18)
    pdf.set_xy(12, 19.5)
    pdf.set_font("Helvetica","B",6)
    pdf.set_text_color(*ABARAKA_RED)
    pdf.cell(40,4,f"CHAPTER  {num}",align="L")
    pdf.set_xy(12, 24)
    pdf.set_font("Helvetica","B",12)
    pdf.set_text_color(*ESPRIT_NAVY)
    pdf.multi_cell(170, 5, title, align="L")
    if subtitle:
        pdf.set_font("Helvetica","I",6.8)
        pdf.set_text_color(90,100,130)
        pdf.set_x(12)
        pdf.multi_cell(170,3.5,subtitle,align="L")
    pdf.set_draw_color(*MID_GRAY)
    pdf.set_line_width(0.15)
    y = pdf.get_y()+0.5
    pdf.line(12,y,198,y)
    pdf.set_y(y+2.5)

def add_section(title, numbered=True):
    pdf.set_font("Helvetica","B",7.5)
    pdf.set_text_color(*ESPRIT_NAVY)
    pdf.set_x(12)
    pdf.set_fill_color(*ABARAKA_RED)
    x = pdf.get_x()
    y = pdf.get_y()
    pdf.circle(x+1, y+2, 0.7, 'F')
    pdf.set_x(x+4)
    pdf.cell(0,4.5,title,align="L")
    pdf.ln(5)

def add_subsection(title):
    pdf.set_font("Helvetica","B",7.5)
    pdf.set_text_color(*ACCENT_TEAL)
    pdf.set_x(12)
    pdf.cell(0,4,title.upper(),align="L")
    pdf.ln(5)

def add_paragraph(text, bold_prefix=None):
    pdf.set_font("Helvetica","",6.6)
    pdf.set_text_color(*TEXT_DARK)
    pdf.set_x(12)
    if bold_prefix:
        pdf.set_font("Helvetica","B",6.6)
        w = pdf.get_string_width(bold_prefix+" ")
        pdf.cell(w,3.6,bold_prefix+" ",align="L")
        pdf.set_font("Helvetica","",6.6)
        pdf.multi_cell(0, 3.4, text, align="J")
    else:
        pdf.multi_cell(186, 3.4, text, align="J")
    pdf.ln(0.8)

def add_bullet(text, indent=14):
    pdf.set_font("Helvetica","",6.6)
    pdf.set_text_color(*TEXT_DARK)
    x = indent
    pdf.set_x(x)
    pdf.set_fill_color(*ESPRIT_NAVY)
    pdf.circle(x-2, pdf.get_y()+1.8, 0.7, 'F')
    pdf.multi_cell(184-x+12, 3.4, text, align="J")
    pdf.ln(0.6)

def add_highlight_box(title, text):
    y0 = pdf.get_y()
    pdf.set_fill_color(*SOFT_GRAY)
    pdf.set_draw_color(*MID_GRAY)
    pdf.set_font("Helvetica","",6.6)
    pdf.set_xy(12, y0)
    pdf.set_font("Helvetica","B",6.2)
    pdf.set_text_color(*ESPRIT_NAVY)
    pdf.set_fill_color(235,238,245)
    pdf.rect(12, y0, 186, 4.5, 'DF')
    pdf.set_xy(14, y0+0.8)
    pdf.cell(182,3,title,align="L")
    pdf.set_xy(14, y0+6)
    pdf.set_font("Helvetica","",6.4)
    pdf.set_text_color(50,60,85)
    pdf.multi_cell(182, 3.2, text, align="J")
    y1 = pdf.get_y()
    pdf.rect(12, y0, 186, y1 - y0, 'D')
    pdf.ln(1.2)

def add_table(headers, rows, col_widths, caption=None):
    pdf.set_font("Helvetica","B",5.5)
    pdf.set_fill_color(*ESPRIT_NAVY)
    pdf.set_text_color(*WHITE)
    x0=12
    for i, h in enumerate(headers):
        pdf.set_xy(x0, pdf.get_y())
        pdf.cell(col_widths[i], 4.8, " "+h, align="L", border=0, fill=True)
        x0+=col_widths[i]
    pdf.ln(4.8)
    for r_idx, row in enumerate(rows):
        fill = (r_idx % 2 == 0)
        pdf.set_font("Helvetica","",5.5)
        pdf.set_text_color(*TEXT_DARK)
        y0 = pdf.get_y()
        heights = []
        for i, cell in enumerate(row):
            w = col_widths[i]-2
            words = cell.split()
            line=""; h=0
            for wrd in words:
                test = line+" "+wrd if line else wrd
                if pdf.get_string_width(test) > w:
                    h+=3.4
                    line=wrd
                else:
                    line=test
            h+=3.4
            heights.append(h)
        rh = max(heights)
        if y0+rh > 282:
            pdf.add_page()
            y0 = pdf.get_y()
        pdf.set_fill_color(*SOFT_GRAY) if fill else pdf.set_fill_color(*WHITE)
        pdf.rect(12, y0, sum(col_widths), rh, 'F')
        pdf.set_draw_color(*MID_GRAY)
        pdf.rect(12, y0, sum(col_widths), rh, 'D')
        x = 12
        for i, cell in enumerate(row):
            pdf.set_xy(x+1, y0+0.8)
            pdf.set_font("Helvetica","",5.5)
            pdf.set_text_color(*TEXT_DARK)
            if cell.startswith("**"):
                cell_clean = cell.strip("*")
                pdf.set_font("Helvetica","B",5.5)
                pdf.multi_cell(col_widths[i]-2, 2.9, cell_clean, align="L")
            else:
                pdf.multi_cell(col_widths[i]-2, 2.9, cell, align="L")
            x+=col_widths[i]
        pdf.set_y(y0+rh)
    pdf.ln(0.6)
    if caption:
        pdf.set_font("Helvetica","I",5)
        pdf.set_text_color(110,120,145)
        pdf.set_x(12)
        pdf.cell(186,2.5,caption,align="C")
        pdf.ln(3)

def add_image(path, w=150, h=None, caption=None):
    if not os.path.exists(path):
        return
    y0 = pdf.get_y()
    if y0 > 235:
        pdf.add_page()
    try:
        with Image.open(path) as im:
            iw, ih = im.size
            aspect = ih/iw
            if h is None:
                h = w*aspect*0.2645
                if h>60:
                    h=60
                    w=h/aspect/0.2645
            pdf.image(path, x=(210-w)/2, y=pdf.get_y(), w=w, h=h)
            pdf.set_y(pdf.get_y()+h+1.5)
            if caption:
                pdf.set_font("Helvetica","I",5)
                pdf.set_text_color(100,110,135)
                pdf.cell(0,2.5,caption,align="C")
                pdf.ln(3)
    except Exception as e:
        print("image error", e)

# ──────────────────────────────────────────────────────────
# PAGE 2: Dedication & Acknowledgments + Abstract
# ──────────────────────────────────────────────────────────
pdf.add_page()
# Dedication block
pdf.set_fill_color(*SOFT_GRAY)
pdf.rect(14, 14, 182, 38, 'F')
pdf.set_draw_color(*ABARAKA_RED)
pdf.rect(14,14,182,38,'D')
# small red left accent
pdf.set_fill_color(*ABARAKA_RED)
pdf.rect(14,14,1.8,38,'F')
pdf.set_xy(18, 16)
pdf.set_font("Helvetica","B",8)
pdf.set_text_color(*ABARAKA_RED)
pdf.cell(60,4,"Dedication",align="L")
pdf.set_draw_color(*ABARAKA_RED)
pdf.set_line_width(0.2)
pdf.line(18,21,42,21)
pdf.set_xy(19, 24)
pdf.set_font("Helvetica","I",7)
pdf.set_text_color(60,70,90)
pdf.multi_cell(172, 3.8, 'To my family, whose unwavering support made this journey possible,\nto Mrs. Ahlem Ben Haddoud, whose guidance, rigor and trust shaped every decision in this work,\nand to everyone who believes that technology, when governed, can serve trust.', align="L")
pdf.set_xy(19, 43)
pdf.set_font("Helvetica","",6)
pdf.set_text_color(110,120,145)
pdf.cell(172,3,"Farah AYARI  -  ESPRIT, Engineering Student",align="R")

pdf.ln(8)
# Acknowledgments
pdf.set_fill_color(*WHITE)
pdf.rect(14, pdf.get_y(), 182, 42, 'DF')
pdf.set_fill_color(*ESPRIT_NAVY)
pdf.rect(14, pdf.get_y(), 1.8, 42,'F')
pdf.set_xy(18, pdf.get_y()+2)
pdf.set_font("Helvetica","B",8)
pdf.set_text_color(*ESPRIT_NAVY)
pdf.cell(60,4,"Acknowledgments",align="L")
pdf.set_draw_color(*ESPRIT_NAVY)
pdf.line(18, pdf.get_y()+4, 52, pdf.get_y()+4)
pdf.set_xy(19, pdf.get_y()+8)
pdf.set_font("Helvetica","",6.8)
pdf.set_text_color(*TEXT_DARK)
pdf.multi_cell(172, 3.6, "I express my deepest gratitude to Mrs. Ahlem Ben Haddoud, my internship supervisor at Al Baraka Bank Tunisia - Centre Urbain Nord, for her availability, her precise feedback, and the clarity of her decisions. At every critical junction - from the choice of the corpus to the final engine switch - her judgment turned a technical possibility into a responsible banking feature.\n\nI thank the teams of Al Baraka Bank for their welcome, the documents they entrusted to this project, and the operational feedback that revealed defects invisible in tests. I thank the faculty of ESPRIT for the training that made this project possible, and in particular for the emphasis on measurable engineering over mere demonstration.", align="J")
pdf.ln(6)
# Abstract box
add_highlight_box("Abstract  -  In One Paragraph", "In regulated banking, a wrong answer about a rate, a fee or a deadline is not a minor inaccuracy - it is a legal risk. This internship delivered a complete, governed and measurable answer: an integrated platform where a bank-operated web application consumes a dedicated RAG (Retrieval-Augmented Generation) intelligence engine, both designed to never invent figures or rules. The platform comprises two cooperating systems: RAGLab, a document-grounded QA engine over four real Arabic banking documents, and Bank Assistant, a trilingual customer and staff web application (Angular 21 + Spring Boot 4 + PostgreSQL) that owns identity, quotas and audit and calls RAGLab over an authenticated, streaming-aware contract. Together they form a clean separation: the edge handles governance; the engine handles retrieval, grounding and generation - stateless, auditable and versioned.")
pdf.ln(1)
pdf.set_font("Helvetica","B",6)
pdf.set_text_color(*ESPRIT_NAVY)
pdf.set_x(12)
pdf.cell(25,4,"Keywords:",align="L")
pdf.set_font("Helvetica","",6)
pdf.set_text_color(*TEXT_DARK)
pdf.multi_cell(0,4,"Retrieval-Augmented Generation  |  Islamic Banking  |  Grounded Answers  |  Citation Verification  |  Arabic NLP  |  Spring Boot  |  Angular  |  AI Governance",align="L")
pdf.ln(1)
# small note
pdf.set_font("Helvetica","I",5.5)
pdf.set_text_color(120,130,155)
pdf.set_x(12)
pdf.multi_cell(186,3,"Note: This report contains no account identifiers, repository URLs or credentials. All figures are traceable to frozen benchmark artifacts. It is written around ideas and architecture, not a chronology.",align="L")

# ──────────────────────────────────────────────────────────
# TOC simulated
# ──────────────────────────────────────────────────────────
pdf.add_page()
pdf.set_font("Helvetica","B",10)
pdf.set_text_color(*ESPRIT_NAVY)
pdf.cell(0,6,"Contents",align="L")
pdf.set_draw_color(*ABARAKA_RED)
pdf.set_line_width(0.35)
pdf.line(12, pdf.get_y()+7, 55, pdf.get_y()+7)
pdf.ln(8)
toc_items = [
    ("1", "Host Organization: Al Baraka Bank Tunisia", "6"),
    ("2", "Project Vision: A Governed Banking Intelligence", "7"),
    ("3", "System Architecture: The Full Picture", "8"),
    ("4", "The Intelligence Engine  -  RAGLab: How It Thinks", "10"),
    ("5", "The Digital Bank  -  The Web Application: How It Serves", "12"),
    ("6", "Problems Solved: The Hard Parts & How They Were Fixed", "14"),
    ("7", "Security, Privacy & Data Governance", "16"),
    ("8", "Deployment & Operations", "17"),
    ("9", "Results, Evaluation & Limitations", "18"),
    ("10", "Conclusion & Perspectives", "19"),
    ("A", "References & Appendices", "20"),
]
for num, title, pg in toc_items:
    y = pdf.get_y()
    # dots
    pdf.set_font("Helvetica","B",6.5)
    pdf.set_text_color(*ABARAKA_RED if num.isdigit() else ESPRIT_NAVY)
    pdf.set_x(14)
    pdf.cell(8,5,num,align="C",border=0)
    pdf.set_font("Helvetica","",7)
    pdf.set_text_color(*TEXT_DARK)
    # title
    pdf.cell(150,5,title,align="L")
    # page
    pdf.set_font("Helvetica","",7)
    pdf.set_text_color(120,130,155)
    pdf.cell(20,5,pg,align="R")
    pdf.ln(6)
    # dotted line
    pdf.set_draw_color(215,220,230)
    pdf.set_line_width(0.1)
    # pdf.dashed_line(14, y+5, 196, y+5, 1,1)
    pdf.line(14, y+5.5, 196, y+5.5)
# small note at bottom of TOC
pdf.ln(6)
pdf.set_font("Helvetica","I",5.5)
pdf.set_text_color(110,120,145)
pdf.multi_cell(186,3,"How to read this report: Chapters 1-3 set the stage (who, why, how it fits). Chapters 4-5 are the technical core (engine + web app). Chapter 6 is the most important - it tells what broke and how it was fixed. Chapters 7-9 close with security, deployment and measured results.",align="J")

# ──────────────────────────────────────────────────────────
# CHAPTER 1 - Host Organization
# ──────────────────────────────────────────────────────────
add_chapter(1, "Host Organization:", "Al Baraka Bank Tunisia  -  Centre Urbain Nord")
add_section("Identity and History")
add_paragraph("Al Baraka Bank Tunisia is the first Islamic bank in Tunisia and the Maghreb. Created on 15 June 1983 as Beit Ettamouil Tounsi Saoudi (BEST Bank) by the Tunisian State and Sheikh Saleh Abdallah Kamel (founder of the Dallah Al Baraka Group), it was renamed Al Baraka Bank Tunisia in 2009 as part of the Group's identity unification. In 2013, it obtained approval to transform from a non-resident bank into a universal resident bank, allowing it to serve individuals, professionals and enterprises with Sharia-compliant products.")
add_paragraph("The Group, Al Baraka Group B.S.C. headquartered in Bahrain Bay, operates over 600 branches in 12 countries (Jordan, Egypt, Tunisia, Sudan, Bahrain, Turkey, South Africa, Algeria, Lebanon among others) and pioneered participative finance worldwide. Today the bank operates 44 branches across Tunisia, headquartered at Centre Urbain Nord in Tunis - one of the capital's largest economic hubs - and has been elected Best Islamic Bank in Tunisia (2018, 2019, 2021) with five IFN Awards in 2024.")
# table products
add_section("A Sharia-Compliant Product Universe")
add_paragraph("All offerings operate within Tunisian Law No. 2016-48 of 11 July 2016 on banks and financial institutions (Articles 12-16 define Islamic operations) and Central Bank Circular BCT 2019-08 - two documents that form the very corpus this project indexes.")
add_table(
    ["Family","Products"],
    [
        ["Trading & Investment","Murabaha (cost-plus), Salam (deferred delivery), Istisna'a (manufacturing), Mudaraba / Musharaka (partnership), Sukuk, participative deposits"],
        ["Leasing & Financing","Ijara with acquisition option, Beit Al Baraka (home), Tas'heelat (personal), equipment & working-capital financing"],
        ["International & Daily","Documentary credits, remittances, leasing, day-to-day banking"],
    ],
    [42,144],
    caption="Product families at Al Baraka Bank  -  the same products the assistant must explain without error."
)
add_section("The Internship Site")
add_highlight_box("Al Baraka Bank  -  Centre Urbain Nord (Operational Heart)",
    "Avenue Cheikh Mohamed Fadhel Ben Achour, Centre Urbain Nord, 1082 Tunis  -  Tel: (+216) 71 183 203  |  Head office: +216 71 18 65 00  |  SWIFT: BEITTNTT\n\nThe Centre Urbain Nord is not merely an address; it is the bank's operational heart, where product, compliance and IT decisions converge - and where this project was supervised by Mrs. Ahlem Ben Haddoud. The location matters because the work was shaped daily by real product questions, real compliance constraints and real customer language.")
# small illustration attempt
if os.path.exists(os.path.join(ASSETS, "albaraka-front.png")):
    add_image(os.path.join(ASSETS, "albaraka-front.png"), w=110, caption="The bank's visual identity (Al Baraka brand tokens: Red #E0122F + Orange #F5881C) drives the entire front-end design system.")

# ──────────────────────────────────────────────────────────
# CHAPTER 2 - Project Vision
# ──────────────────────────────────────────────────────────
add_chapter(2, "Project Vision:", "A Governed Banking Intelligence")
add_section("The Business Problem")
add_paragraph("Three tensions motivated the project. First, the user's question is rarely the document's question: a customer asks in everyday language ('can I open a shop?') where the corpus uses technical terms (sigha, mahal al-aqd, tamwil). Second, multilingual asymmetry: the corpus is Arabic, but questions arrive in French and English, and a French question must be answerable from an Arabic passage without translating either. Third, errors are costly: a false rate, deadline or sanction is a legal exposure, not a minor imprecision.")
add_section("The North Star: Five Properties")
add_table(
    ["#","Property","What it means in practice"],
    [
        ["1","Grounded & Auditable","Every answer traceable to a source; nothing fabricated about rates or terms"],
        ["2","Compliant","PII scrubbed on output; no learning from customer chats"],
        ["3","Quota-Controlled & Secure","Public channel metered; intelligence engine never on Internet"],
        ["4","Regulable by Staff","Admins edit taxonomy, governed facts and documents"],
        ["5","Multilingual (RTL first)","French / Arabic / English; Arabic RTL as first-class citizen"],
    ],
    [10,48,128],
    caption="Five properties governed every technical choice, in strict priority order."
)
add_section("What Was Delivered: Two Systems, One Product")
add_highlight_box("Deliverable in One Sentence",
    "RAGLab answers; Bank Assistant serves customers and governs what RAGLab knows. Neither writes into the other's store. Staff sees every conversation from its first message (Java persists before calling the engine), and a customer never sees which document an answer came from. That boundary is what makes the answer path auditable.")
add_paragraph("The web app exposes a public landing page, a floating assistant widget, an authenticated client portal (accounts, cards, transactions, financing simulations, advisor appointments) and an eleven-screen admin back-office including a five-tab RAGLab Console. The intelligence engine is a versioned, stateless HTTP microservice with 25+ routes, a frozen contract and a full audit trail.")
add_paragraph("The rest of this report explains how the two fit together, how the engine decides, how the app governs, and - most importantly - what broke and how it was fixed.")

# ──────────────────────────────────────────────────────────
# CHAPTER 3 - Architecture
# ──────────────────────────────────────────────────────────
add_chapter(3, "System Architecture:", "The Full Picture  -  From Browser to Intelligence")
add_section("End-to-End Flow")
# Big architecture image
add_image(os.path.join(ASSETS, "architecture_schema.png"), w=176, caption="Full architecture: from browser to intelligence. Generation 1 (flat lab)  -  pivot  -  Generation 2 (four governed layers)  -  current update. The engine is external and addressed by ip:port.")
add_paragraph("In words: the browser talks only to nginx (Angular SPA, security headers, SSE). Nginx proxies /api to the Spring Boot edge (Java 21) which owns authentication (JWT), roles, quotas, persistence and admin. The edge calls RAGLab over an authenticated internal contract (X-Service-Token) and streams the answer back. PostgreSQL (Flyway, ddl-auto=validate, 15 migrations, single writer) holds the business schema. The vector index lives inside the Python agent - no shared-DB write coupling.")
add_section("Why This Separation Is the Design")
add_paragraph("The split is not a convenience; it is the guarantee. The business schema can evolve without re-indexing; the index can be rebuilt without migrating the business database. The edge opens the exchange row before calling the engine, so even a failure is visible to staff; the engine is stateless and returns provenance that the edge records. An admin switching the engine, a document re-published, or a model key rotated all flow through explicit, audited actions.")
add_section("Technology Stack at a Glance")
add_table(
    ["Layer","Choice","Why this, not that"],
    [
        ["Frontend","Angular 21 (standalone), Vitest, Inter","SPA with lazy modules; no Google-fonts build dependency"],
        ["Backend Edge","Java 21, Spring Boot 4, Flyway, JWT, virtual threads","Owns schema with validate; avoids carrier pinning"],
        ["Intelligence","Python 3.11, FastAPI + uvicorn, explicit code","No LangChain/LlamaIndex - every behavior readable"],
        ["Vector Store","ChromaDB local (cosine) + BM25","Local avoids cloud cost & banking-data egress"],
        ["Embeddings / LLM","NVIDIA nemotron-3-embed-1b (2048-d) + Qwen","Measured best on Arabic; zero fallback"],
        ["Persistence","PostgreSQL, Redis queue, Flyway","Single owner; durable BRPOP queue"],
        ["Deployment","Docker Compose, offline tarball","Same images dev to prod; Windows-native path"],
    ],
    [28,58,100],
    caption="Stack choices: sobriety is a governance decision  -  no orchestration framework, no external vector DB, no vendor SDK."
)
add_section("The Two Lifecycles")
add_bullet("Document lifecycle: pushed via POST /documents (content-hash versioned; identical bytes = no-op; SHA-256 dedup guard), then batch-indexed via POST /ingest (one job at a time; 409 is a state, not an error). The edge's documents table holds a parallel record but no longer drives indexing - RAGLab owns its index and reports pending / indexed / stale honestly.")
add_bullet("Conversation lifecycle: browser -> edge (quota gate) -> RAGLab (retrieval -> generation -> gate) -> edge (scrub -> persist -> settle quota -> stream). The edge opens the row before calling the engine so even a failure is visible.")
add_image(os.path.join(ASSETS, "workflow.png"), w=155, caption="Customer chat path: two-phase metering, streaming relay and persistence. The edge is the single writer; streaming is end-to-end (Python -> Java -> browser).")

# ──────────────────────────────────────────────────────────
# CHAPTER 4 - RAGLab
# ──────────────────────────────────────────────────────────
add_chapter(4, "The Intelligence Engine:", "RAGLab  -  How It Thinks")
add_section("Corpus and the Arabic Challenge")
add_table(
    ["Document","Role","Fragments"],
    [
        ["Law 2016-48 (198 articles, 11 titles)","Superior legal reference","233"],
        ["BCT Circular 2019-08 (20 chapters)","Operational framing","13"],
        ["Internal Islamic Operations Guide","Internal policy & compliance","54"],
        ["Intro to Islamic Finance (Madkhal)","Training, definitions","39"],
        ["Total","4 real Arabic documents","339"],
    ],
    [70,70,46],
    caption="The governed corpus: four real documents => 339 structure-aware fragments."
)
add_paragraph("The PDFs are scanned legal Arabic: visual-order lines, reversed word order, corrupted digits, broken headings. Indexed directly, the corpus is nonsense. The fix is linguistic, not algorithmic (Chapter 6 tells how).")
add_section("Structure-Aware Chunking")
add_paragraph("Two chunkers are implemented and measured. The legacy size mode (220 tokens + 40 overlap) remains; the adopted restructure mode dominates via three explicit steps: (1) semantic normalization - gazette header cleanup, repeated-header dropping (>=3 occurrences), section-marker extraction (al-fasl / al-bab / al-qism), visual-order repair by zone, hierarchy into Markdown; (2) breadcrumb enrichment - injecting h1 > h2 > h3 ancestry above every heading/table so a chunk carries its context; (3) recursive structural splitting over ['\\n# ', '\\n## ', '\\n### ', '\\n\\n', '\\n', ' '] at the same 220/40 budget. Each chunk carries an identity card: document, chunk_index, heading, language, section_type, chunk_fp (fingerprint v4), embedding_fp, embedding_model.")
add_highlight_box("Measured Impact",
    "Structural chunking gains +20 points of hit@1 over window splitting (live: 51 -> 71; offline BM25: 42 -> 62). This single change outranked every retrieval-mode experiment and became the foundation for everything above it.")
add_section("Embeddings and Storage")
add_bullet("Model: nvidia/nemotron-3-embed-1b, native 2048 dimensions, via NVIDIA Build endpoint (batch 32, exponential backoff + Retry-After, bisect on degenerate vectors, resumable JSON cache).")
add_bullet("Store: one ChromaDB persistent collection per (embedding-space x chunking mode), hnsw:space=cosine. BM25 lexical index maintained in parallel for hybrid modes.")
add_bullet("Fingerprint guard: chunk_fp + embedding_fp stored per chunk; retrieval over a stale collection is refused before any model call with remediation instructions. No mixed indexes are ever served.")
add_section("Retrieval: Dense Wins")
add_table(
    ["Mode","hit@1","hit@3","hit@5"],
    [
        ["vector (dense, default)","71","80","87"],
        ["rrf (BM25 fused, k=60)","71","80","82"],
        ["blend (lambda=0.7)","62","80","84"],
    ],
    [56,42,42,46],
    caption="Retrieval modes measured identically (live models, 50 questions). In French: dense 50/67/67 vs RRF 33/33/50 - dense stays default."
)
add_paragraph("Decision: dense stays default; others remain explicit options, never silently blended. A deterministic feature-based reranker (62/82/87 -> 62/89/89 offline) provides a cheap second pass.")
add_section("Understanding the Question")
add_table(
    ["Module","Function"],
    [
        ["intent.py","Rule-based intent classification (first rule wins); splits compound requests; detects ambiguity"],
        ["evidence_plan.py","Derives evidence requirements (what must be present) - never the answer"],
        ["sufficiency.py","Confronts plan vs fragments; emits sufficient / insufficient / contradictory"],
        ["interrogate.py + topic_map.py","Only when nothing is covered: one bounded LLM call re-expresses the practical question as the nearest technical question (262-node map)"],
    ],
    [45,141],
    caption="Understanding layer: deterministic first, model second, never twice. Interrogation is fail-closed with full disclosure."
)
add_section("Delivery: Citation Gate and Audit")
add_bullet("Prompt (grounded-v1): question + sources declared untrusted data; model has no account, no tools; output is strict JSON with claims + evidence; quotes >=12 chars, contiguous, verbatim.")
add_bullet("Three-gate verification: (i) quote membership - normalized quote must be substring of cited source; (ii) numeric gate - every number in a claim must appear in its quotes (Arabic-Indic handled); (iii) policy gate - source must be retrieved, allowed and in force.")
add_bullet("Sufficiency commitment: nothing covered -> refuse before generation, naming missing requirements; partial -> one bounded regeneration. PII scrubbing after the gate (EMAIL/PHONE/RIB-IBAN/CIN -> [LABEL]); audit trail via GET /audit (500-entry cap) correlated by X-Request-Id / X-Turn-Id.")
add_section("Knowledge Layer on Law 2016-48")
add_bullet("198 knowledge units (= 198 articles), literal text, stable ID loi-2016-48:artNNN, hierarchical path. 77 internal cross-references + 1 anchoring relation (Circular -> Article 11). 53 structured number records (14 rates, 28 deadlines, 5 caps, 6 sanctions) via GET /numbers - the first additive-only endpoint under the freeze. 6 governance axes on fragment metadata.")
add_highlight_box("Impact of the Knowledge Layer",
    "A unit-description index answers law questions at 100/100/100 (hit@1/3/5) vs 67/92/92 baseline - every law question, including cross-lingual, finds its article at rank 1. This proves that structure beats scale: 198 well-formed descriptions beat thousands of overlapping windows.")

# ──────────────────────────────────────────────────────────
# CHAPTER 5 - Web App
# ──────────────────────────────────────────────────────────
add_chapter(5, "The Digital Bank:", "The Web Application  -  How It Serves")
add_section("Overall Shape")
add_paragraph("The web app is the only public edge. RAGLab is never exposed to the Internet; it is addressed by ip:port on a private network with a shared X-Service-Token. Frontend: Angular 21 (standalone, lazy modules) - public landing page (/), chat widget, authentication, client portal, admin back-office under /admin/**. Backend: Spring Boot 4 (Java 21) - JWT, Flyway migrations, quotas, persistence, monitoring, five-tab RAGLab Console. Database: PostgreSQL, Flyway single-owner (V1 ... V15), ddl-auto=validate. No second writer.")
add_section("Customer Experience")
add_bullet("Landing page at /: trilingual hero, products, assistant showcase, services, FAQ, footer - brand tokens from Al Baraka palette (Red #E0122F, Orange #F5881C) with full RTL support. A floating assistant widget offers the same SSE endpoints with its own session, without login, quota-limited.")
add_bullet("Authentication split: Client vs Staff tabs on login, role-based redirect, registration at /register. Client portal /portal: accounts, cards, transactions, financing simulations, advisor appointments, profile, and a ChatGPT-style assistant carrying the client's own conversation history.")
add_image(os.path.join(ASSETS, "before-after-01-login.png"), w=176, caption="Portal identity: dedicated Client vs Staff login - the split that makes role-based redirect and customer-data isolation possible.")
add_image(os.path.join(ASSETS, "before-after-02-dashboard.png"), w=176, caption="Client dashboard: accounts, cards and transactions in one place - the same data that the masked context summarizes for the assistant.")
add_image(os.path.join(ASSETS, "before-after-14-chat-answers.png"), w=176, caption="Chat answers: grounded responses with structured citations vs honest refusals  -  never a guess.")
add_subsection("Core Banking Data Model (V15)")
add_table(
    ["Domain","Tables & Key Fields"],
    [
        ["Identity & Risk","clients (CIN, ABK######, segment, langue_preferee, KYC, risk, branch agence_id)"],
        ["Money","comptes (IBAN, RIB, solde, decouvert_autorise), cartes (masked porteur e.g. 4571 **** **** 6842, no PAN/CVV), transactions"],
        ["Products","financements (murabaha/ijara, amounts, margin, installments), beneficiaires (RIB, bank)"],
        ["Service","advisor_requests (motive, branch, date/time, free-text), conversations, messages"],
    ],
    [42,144],
    caption="Customer domain: realistic core-banking shape. No full card numbers stored. Transactions may contain third-party personal data (store names) - DPIA-relevant."
)
add_paragraph("Two design facts matter: transactions may contain third-party personal data, and the assistant never sees raw balances - for a signed-in customer, a masked context (CustomerContextService) is derived at call time: slices and categories only, no direct identifiers leave the bank.")
add_section("Authentication, Roles and Quotas")
add_bullet("JWT with server-side rotation: access <=1h; refresh tokens carry jti in a family, single-use (rotated) with reuse detection (consumed jti burns the family -> 401). Revocation on logout and on any admin role change / deactivation / delete.")
add_bullet("Dynamic staff roles: admin_roles + admin_permissions + role_permission + admin_access_audit (Flyway V14). Twelve fixed permissions; SUPER_ADMIN reserved; unknown role -> fail-closed 403; every change audited with mandatory reason.")
add_bullet("Two-phase metering: gate() before the engine, settle() after. Anonymous: 3 000 tokens / 20 messages; authenticated: 10 000 / 50; max 5 new sessions/hour. Per-session striped ReentrantLocks (64 stripes, not synchronized) so a slow engine cannot be gamed - and because the backend runs on JDK 21 virtual threads, this avoids carrier pinning.")
add_section("Streaming and Persistence")
add_paragraph("End-to-end streaming: Python GroqCompletion.stream -> Java AgentClient.invokeStream (pinned to HTTP/1.1) -> Java SSE relay POST /api/chat/message/stream -> browser fetch + ReadableStream parser. Fallback to classic POST /message; abort cleanly cancels. Persistence: Java is the single writer. It opens the exchange row before calling RAGLab, persists after streaming, writes attribution (engine = RAGLab) and provenance, and sends the last 10 turns as context to the stateless engine (turn_id + X-Turn-Id for idempotency).")
add_section("Admin Console: The Control Room")
add_table(
    ["Screen","Function"],
    [
        ["/admin/facts","Governed facts CRUD - rates/fees/eligibility outrank any document; audit append-only"],
        ["/admin/taxonomy","Exemplar & release management; seal() freezes numbered release; publish rollback"],
        ["/admin/agent/health","Effective RAGLAB_URL (overridable by SUPER_ADMIN, audited), live probe"],
        ["/admin/agent/quality","Quality workbench: answer -> retrieval -> quote-existence, all verbatim"],
        ["/admin/agent/documents","One drop zone, two chunk worlds (honest pending/indexed/stale)"],
        ["/admin/agent/config","Models & keys (write-only, CONFIG_SET audited) - RAGLab settings read-only"],
        ["/admin/agent/journal","Every admin action, filterable by action, searchable, with motive column"],
        ["/admin/agent/monitor","Live feed + KPI strip (grounded rate, refusal reasons) over last 200 exchanges"],
    ],
    [45,141],
    caption="Admin control room: every write is audited, every read is role-gated. The back-office stays in French (internal staff)."
)

# ──────────────────────────────────────────────────────────
# CHAPTER 6 - Problems Solved
# ──────────────────────────────────────────────────────────
add_chapter(6, "Problems Solved:", "The Hard Parts & How They Were Fixed")
add_paragraph("This chapter is the heart of the internship: not what was planned, but what broke - and what architecture prevents it from breaking again. Each problem below was solved with a measurable fix and a regression test; none was patched silently.")
# Problem boxes simulated via highlight
def problem_box(title, problem, solution):
    # compact version: one block with title + two lines
    y0 = pdf.get_y()
    # check space: if less than 28mm remain, new page
    if y0 > 245:
        pdf.add_page()
        y0 = pdf.get_y()
    pdf.set_fill_color(*ABARAKA_RED)
    pdf.rect(12, y0, 186, 4.8, 'F')
    pdf.set_xy(14, y0+0.7)
    pdf.set_font("Helvetica","B",5.8)
    pdf.set_text_color(*WHITE)
    pdf.cell(182,3.2,title,align="L")
    # problem+ solution in one rect
    pdf.set_font("Helvetica","",6)
    # calc needed height: both texts approx
    def calc_h(txt, w=168):
        words=txt.split()
        line=""; h=0
        for wrd in words:
            test=line+" "+wrd if line else wrd
            if pdf.get_string_width(test) > w:
                h+=3.1
                line=wrd
            else:
                line=test
        return h+3.1
    hp = calc_h(problem)
    hs = calc_h(solution)
    total = max(12, hp) + max(10, hs) + 4
    if y0 + 4.8 + total > 282:
        pdf.add_page()
        y0 = pdf.get_y()
        pdf.set_fill_color(*ABARAKA_RED)
        pdf.rect(12, y0, 186, 4.8, 'F')
        pdf.set_xy(14, y0+0.7)
        pdf.set_font("Helvetica","B",5.8)
        pdf.set_text_color(*WHITE)
        pdf.cell(182,3.2,title,align="L")
    pdf.set_fill_color(*SOFT_GRAY)
    pdf.rect(12, y0+4.8, 186, hp+2, 'F')
    pdf.set_draw_color(*MID_GRAY)
    pdf.rect(12, y0+4.8, 186, hp+2, 'D')
    pdf.set_xy(14, y0+5.6)
    pdf.set_font("Helvetica","B",5.5)
    pdf.set_text_color(*ABARAKA_RED)
    pdf.cell(13,2.8,"Problem:",align="L")
    pdf.set_xy(27, y0+5.6)
    pdf.set_font("Helvetica","",5.8)
    pdf.set_text_color(*TEXT_DARK)
    pdf.multi_cell(169, 2.9, problem, align="J")
    sy = y0+4.8+hp+2
    pdf.set_fill_color(*WHITE)
    pdf.rect(12, sy, 186, hs+2, 'F')
    pdf.rect(12, sy, 186, hs+2, 'D')
    pdf.set_xy(14, sy+0.8)
    pdf.set_font("Helvetica","B",5.5)
    pdf.set_text_color(*ACCENT_TEAL)
    pdf.cell(13,2.8,"Fix:",align="L")
    pdf.set_xy(27, sy+0.8)
    pdf.set_font("Helvetica","",5.8)
    pdf.set_text_color(35,45,70)
    pdf.multi_cell(169, 2.9, solution, align="J")
    pdf.set_y(sy+hs+4)

problem_box("Arabic PDFs Are Not Text",
    "Scanned legal Arabic extracts in visual order (right-to-left lines as rendered, not logical order), with split words, repeated gazette headers/footers and every digit corrupted. Indexed directly, the corpus is nonsense.",
    "Dedicated restructure.py pipeline: NFKC + Arabic presentation-form repair, alef/yah unification, tatweel stripping, page-header cleanup and repeated-header dropping (>=3 occurrences), section-marker extraction (al-fasl / al-bab), and visual-order reconstruction by zone into logical order before chunking. Linguistic repair was performed batch by batch (29 batches for the law: 215 corrections; then 35, 23, 21 for the other docs), each checked by llm_repair_check.py and adopted after supervisor sign-off. Raw docs/ files are never modified; corrected codices are applied inside the pipeline and versioned in raglab/audits/.")
problem_box("A 220-Token Window Destroys Meaning",
    "Fixed-window chunking (220 tokens + 40 overlap) cuts a legal article mid-definition, making the passage unverifiable and retrieval noisy.",
    "Structure-aware chunking (semantic normalization -> breadcrumb enrichment -> recursive structural splitting) gains +20 points hit@1 (51 -> 71 live; 42 -> 62 offline). This single change outranked every retrieval-mode experiment and became the foundation for everything above it.")
problem_box("Searching in Arabic With French/English Questions",
    "Users write technical terms in Latin script (murabaha, sukuk) while documents use Arabic script. Lexical search has zero overlap; vector search may miss without a bridge.",
    "Layered: native multilingual embeddings (NVIDIA 2048-d) handle most cases; declared cross-script bridges (curated Latin->Arabic, df-checked so a bridge onto boilerplate can never anchor evidence); colloquial concept vocabulary (e.g., hana/khamara/bar -> khamr, qimar/casino -> maysir) with scope disclosure; and one bounded interrogation call that rewrites a practical question into the nearest technical question, re-evaluated deterministically.")
problem_box("The Guided Retrieval Round Was Dead in Production",
    "Sufficiency controller was called without a search function, so the guided rescue round never executed in production - only in offline tests.",
    "Live service now passes the search callable. This established the rule: any claim about the deployed service must be verified on the deployed service. A regression test now covers the deployed path, not just the unit path.")
problem_box("The Index Was Silently Duplicating",
    "Re-pushing an identical document created duplicates (859 of 1 713 observed), inflating term frequencies and corrupting retrieval. Legacy append-only path had no guard.",
    "RAGLab SHA-256 content-hash 409 guard (document_already_in_corpus, byte-identical = no-op); batch POST /ingest as one-job-at-a-time; POST /documents content-hash versioning (identical bytes = no-op; different = version bump). In-house path fixed similarly: delete_source purges by collection + jsonb before upsert.")
problem_box("French Intent Misclassified by a Substring",
    "Regex Quelle matched inside Quelles, misclassifying a procedural question as definitional.",
    "Anchored markers + dedicated French procedural patterns, covered by live-set regression tests.")
problem_box("Streaming Broke on HTTP/2 Upgrade",
    "java.net.http.HttpClient defaults to HTTP/2 and sends Upgrade: h2c; uvicorn rejected it with 422. Browser saw a broken stream.",
    "Pin AgentClient.invokeStream to HTTP/1.1 explicitly. One-line cause, days of debugging - and a clear lesson: infrastructure defaults are product decisions.")
problem_box("Two Sensitive Configuration Errors (Real Incidents)",
    "Hardcoded X-Service-Token in Compose silently overrode the operator's environment variable; network errors leaked as 500, hiding provider failures; nginx cached the wrong index.html.",
    "Env composition priority fixed (operator env wins); network errors now 502 with scrubbed detail; index.html no-cache + critical-CSS disabled. All three are regression-tested; the token incident was a real platform-team occurrence, not a hypothetical.")

# ──────────────────────────────────────────────────────────
# CHAPTER 7 - Security
# ──────────────────────────────────────────────────────────
add_chapter(7, "Security, Privacy", "& Data Governance")
add_section("Defense in Depth")
add_table(
    ["Layer","Mechanism"],
    [
        ["Network","Only nginx is public; backend + Postgres + RAGLab on private network; edge TLS + HSTS"],
        ["Authentication","JWT (>=256-bit, never in repo); X-Service-Token constant-time hmac.compare_digest"],
        ["Authorization","All admin @PreAuthorize; DB-backed staff permissions; client UTILISATEUR isolated; unknown -> 403"],
        ["Input","Filename validation; 20 MiB cap -> 413; enum validation -> 400"],
        ["Prompt Injection","System prompt declares Q+A as untrusted data; capability guard refuses live-data requests locally"],
        ["Output","PII scrub after gate: EMAIL/PHONE/RIB-IBAN/CIN -> [LABEL]"],
        ["Secrets","Env / platform secrets only; GET /keys presence + fingerprint; POST /keys rejects placeholders"],
    ],
    [28,158],
    caption="Security layers: each one is small; together they are complete. No layer is trusted alone."
)
add_section("Personal Data: What We Hold, What We Do Not")
add_paragraph("The platform holds nine families of customer personal data across clients, comptes, cartes, transactions, beneficiaires, financements, advisor_requests, conversations, refresh_tokens - from direct identifiers (email, CIN) to financial (IBAN/RIB) to free-text (chat, appointment messages). Two truths matter:")
add_bullet("Nothing identifiable leaves the bank beyond a masked slice/category context built at call time for an authenticated customer asking about their own situation.")
add_bullet("The highest real risk is free text: a question may contain an account number; it is stored verbatim and shown to staff. The output scrub + audit mitigate; a retention/deletion lifecycle remains a documented pre-production gap with the legal team.")
add_paragraph("No full card PAN or CVV is ever stored. Transactions may contain third-party personal data (store names) - a DPIA-relevant nuance. This transparency is deliberate: the Customer PII Report (CUSTOMER_PII_REPORT.md) lists every field, who writes it and who may see it.")
add_section("Audit and Traceability")
add_bullet("Every answer carries citations and engine attribution (RAGLAB_*); every admin action is written to agent_admin_audit with mandatory reason; every ingestion writes JournalIngestion; every conversation is visible from its first message.")
add_bullet("Monitoring KPIs (grounded rate, refusal reasons) over the last 200 exchanges attribute by engine even for historical turns - no new column needed, the intent tag carries the provenance.")
add_section("Threat Pass")
add_paragraph("A 20-point checklist (THREAT_PASS_6.2.md) drove concrete fixes: second unauthenticated chat door removed, string compares hardened to constant-time, hardening headers at nginx + Spring, Swagger off in prod, host ports bound to 127.0.0.1. Remaining documented gaps: magic-byte sniffing, per-request DB auth vs hourly rotation, Redis auth, nightly purge of expired refresh tokens.")

# ──────────────────────────────────────────────────────────
# CHAPTER 8 - Deployment
# ──────────────────────────────────────────────────────────
add_chapter(8, "Deployment & Operations", "One Image Set, Three Environments")
add_table(
    ["","dev","staging","prod"],
    [
        ["How","docker compose up","same images, real TLS","same images, HA stores"],
        ["Profile","(default)","prod","prod"],
        ["Swagger","on","off","off"],
        ["Logs","plain / json","ecs","ecs"],
        ["Secrets",".env (local)","platform secrets","platform secrets"],
        ["Data","throwaway","anonymized monthly","live"],
    ],
    [32,46,46,62],
    caption="Promotion moves image digests dev -> staging -> prod; never rebuild for prod."
)
add_paragraph("deploy/promote.sh enforces it: refuses a dirty tree, tags with commit SHA (never latest), and verify compares GET /actuator/info + GET /version.json against HEAD. Docker-compose.prod.yml (per host) sets prod profile, removes host port mappings, and points at secret files. Kubernetes translation is one-to-one.")
add_section("Topology")
add_bullet("Internet --443(TLS at edge, HSTS)--> Edge Proxy (Traefik/nginx/LB) --80(private)--> frontend (nginx SPA) --/api--> Spring Boot --> RAGLab (private, never published) --> Postgres (PITR: daily full + WAL, 30-day retention, quarterly drill). Only the edge proxy has a public address.")
add_bullet("Flyway runs at backend boot; migrations V1 ... V15 are expand->migrate->contract; a non-idempotent V1 was rewritten and baselined at 0 - the fix that makes the migrations CI job possible at all.")
add_section("Deploy Procedure")
pdf.set_font("Courier","",6)
pdf.set_fill_color(245,247,251)
pdf.set_draw_color(*MID_GRAY)
y0=pdf.get_y()
pdf.set_xy(12, y0)
pdf.set_font("Courier","",6)
pdf.set_text_color(40,50,75)
lines = [
    "TAG=v1.0.0 docker compose -f docker-compose.yml -f docker-compose.prod.yml pull",
    "docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d backend",
    "docker compose logs backend | grep -E \"Flyway|Migrating|Successfully\"",
    "docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d frontend",
    "E2E_BASE_URL=https://assistant.example.tn ./e2e/run.sh",
]
# draw code block
total_h = len(lines)*3.8 + 6
pdf.rect(12, y0, 186, total_h, 'DF')
for i, l in enumerate(lines):
    pdf.set_xy(14, y0+3+i*3.8)
    pdf.cell(182,3.8,l,align="L")
pdf.set_y(y0+total_h+3)
add_section("Observability")
add_bullet("Correlation: turnId minted once in Java, sent as body + X-Turn-Id, bound to MDC and Python contextvar - one grep spans both services; X-Request-Id echoed on every response.")
add_bullet("Metrics: backend GET /actuator/prometheus -> chat_turns_total / chat_turn_duration; Python GET /metrics -> agent_turns_total. OpenTelemetry tracing is the documented next step.")
add_bullet("Runbooks: RUNBOOK.md covers incidents, ingestion, rotation, regulatory audit queries. HOST_TESTING.md + docker-compose.test.yml give a one-command full-stack test on a workstation (seeded accounts: staff at every permission level, four customers with accounts/cards/movements/financings).")

# ──────────────────────────────────────────────────────────
# CHAPTER 9 - Results
# ──────────────────────────────────────────────────────────
add_chapter(9, "Results, Evaluation", "& Limitations")
add_section("How Quality Is Measured")
add_bullet("questions_50.json - 50 cases: 10 verbatim, 20 paraphrases, 15 cross-lingual (6 FR + 9 EN), 5 out-of-scope. 32 AR / 8 FR / 10 EN. Expected document + substring verified as distinctive per document.")
add_bullet("questions_targets.json - 10 Arabic cases: 2 per category (colloquial, synonyms, implicit, compound, ambiguity). Rules: paired comparison in same run; +-2 pp tolerance across runs; a frozen set is never edited to improve a result; offline BM25 filters cheaply, live runs decide.")
add_section("Headline Results")
add_table(
    ["What","Result"],
    [
        ["Chunking (live, 50)","size 51/64/71 -> restructure 71/80/87 (hit@1/3/5)  -  +20 pp"],
        ["Retrieval (restructure)","dense 71/80/87 > RRF 71/80/82 > blend 62/80/84  -  dense default"],
        ["Reranker (offline)","62/82/87 -> 62/89/89  -  activated"],
        ["Knowledge unit index (law)","67/92/92 -> 100/100/100  -  every article at rank 1"],
        ["Target categories","compound 100  |  implicit+ambiguous 50/100  |  synonyms 50  |  colloquial 0"],
        ["Model independence","agreement 0.720; Jaccard of sources 1.000  -  divergence is accept/refuse"],
        ["Final campaign","retrieval 5/7  |  topic map 5/5  |  3 answered / 3 refused, zero errors"],
        ["Robustness (6 adversarial)","Fabricated/borrowed/computed/unknown-marker/injection  -  all rejected"],
        ["Tests & CI","472 unit + 133 offline  |  8 workflows  |  469 hard-harness families  |  green at merge"],
    ],
    [52,134],
    caption="Results that are measured, not claimed. All numbers from frozen artifacts, not impressions."
)
add_section("What the System Does Not Do (Yet)")
add_table(
    ["Limitation","Scope"],
    [
        ["Citation existence != entailment","Gate proves words/numbers come from source, not that claim follows logically"],
        ["No similarity threshold","Top-5 transmitted even if off-topic; safety rests on sufficiency + refusal"],
        ["Single worker","One profile/index/ingest at a time; horizontal scale is designed, not deployed"],
        ["Updates by re-ingestion","Old fragments serve until next ingest (now surfaced as stale)"],
        ["Evaluation scale","50 + 10 cases small & correlated - not a security/legal guarantee"],
    ],
    [52,134],
)
add_highlight_box("Readiness Statement (from the repository itself)",
    "Suitable for a supervised pilot on approved non-confidential documents; not ready for unsupervised customer-facing banking advice. This honesty is not a disclaimer - it is a product property. The system refuses rather than guesses, and its limits are documented where any bank auditor can read them.")

# ──────────────────────────────────────────────────────────
# CHAPTER 10 - Conclusion
# ──────────────────────────────────────────────────────────
add_chapter(10, "Conclusion", "& Perspectives")
add_section("What Was Built")
add_paragraph("A working, documented and measurable platform: a complete language pipeline (reading and repair, structure-aware chunking, vector + lexical indexing, dense retrieval, intent and sufficiency reasoning, citation-gated generation, output scrubbing, audit) wrapped in a bank-operated web app that owns identity, quotas and persistence - all measured, versioned and traceable.")
add_section("Lessons That Outlive the Code")
add_bullet("Measurement replaces opinion. Every structural choice - restructure over window, dense over fusion, lexicon off, relational expansion abandoned - was settled by paired numbers, and several reasonable intuitions were rejected by data.")
add_bullet("Governance of decisions is architecture. The step-and-plan protocol, activation gates and frozen question sets allowed the project to survive interruptions without silent regression and to distinguish progress from displacement.")
add_bullet("Reality is the only judge. The most expensive defects - the inert guided round, the French intent misclassification, index duplication - were invisible in tests and obvious in operation. Any claim about the deployed service must be checked on the deployed service.")
add_section("Next Steps")
add_bullet("Per-document authorization and chunk-level topical metadata (path / level / parent_id) before any production re-index.")
add_bullet("Topical & faceted retrieval arms measured on frozen sets, fused with dense - without reintroducing known regressions.")
add_bullet("Closing measurement campaign - re-measure every arm, document limits, then decide defaults.")
add_bullet("Industrialization: multi-user semantics, incremental updates, observability (metrics + tracing), adversarial evaluation, upload magic-byte validation, OpenTelemetry, and a legal-team-reviewed retention/deletion lifecycle.")
add_bullet("Product: safely editable answer templates and conversation quality flags in the control room.")
# final quote
pdf.ln(4)
pdf.set_fill_color(*SOFT_GRAY)
pdf.set_draw_color(*MID_GRAY)
y0=pdf.get_y()
pdf.rect(22, y0, 166, 14, 'DF')
pdf.set_xy(24, y0+2)
pdf.set_font("Helvetica","I",7)
pdf.set_text_color(*ESPRIT_NAVY)
pdf.multi_cell(162,3.5,'"The best system we built is not the one that answers the most questions,\nbut the one that knows when not to answer - and can prove why."', align="C")
pdf.set_xy(24, y0+10)
pdf.set_font("Helvetica","",5.5)
pdf.set_text_color(100,110,135)
pdf.cell(162,3,"Farah AYARI, ESPRIT  -  Al Baraka Bank Tunisia, Centre Urbain Nord  -  supervised by Mrs. Ahlem Ben Haddoud",align="C")
pdf.set_y(y0+16)

# ──────────────────────────────────────────────────────────
# References
# ──────────────────────────────────────────────────────────
pdf.add_page()
pdf.set_font("Helvetica","B",10)
pdf.set_text_color(*ESPRIT_NAVY)
pdf.cell(0,6,"References",align="L")
pdf.set_draw_color(*ABARAKA_RED)
pdf.line(12, pdf.get_y()+7, 48, pdf.get_y()+7)
pdf.ln(8)
pdf.set_font("Helvetica","",6)
pdf.set_text_color(*TEXT_DARK)
refs = [
    ("RAGLab/README.md","Supported pipeline, measured A/B tables, run instructions"),
    ("RAGLab/RAGLAB_SPEC.md","Reverse-engineered specification (Observed / Inferred / Unknown)"),
    ("RAGLab/RAGLAB_GAP_ANALYSIS.md","Target-state gap analysis (ahead / behind per capability)"),
    ("RAGLab/RAGLAB_ROADMAP.md","Binding execution plan - step-and-gate protocol, decision log"),
    ("RAGLab/raglab/CONTRACT.md","Frozen HTTP contract (behaviors, states, error envelope)"),
    ("RAGLab/raglab/SERVICE.md","Service operation guide & env"),
    ("bank-assistant/README.md","Web-app overview & governance model"),
    ("bank-assistant/PLAN_TO_PROD.md","Phased plan (phases 0 -> 10)"),
    ("bank-assistant/docs/DEPLOYMENT.md","Deployment topology & promotion"),
    ("bank-assistant/docs/CUSTOMER_PII_REPORT.md","Customer PII inventory & gaps"),
    ("bank-assistant/docs/THREAT_PASS_6.2.md","20-point security checklist"),
    ("bank-assistant/docs/PROJECT_REPORT.md","Full history of the web app (255 commits)"),
    ("RAGLab/RAGLAB_FULL_REPORT.md","Full history of the engine (376 commits)"),
    ("Corpus: RAGLab/docs/","Loi 2016-48, BCT Circular 2019-08, Guide, Madkhal - 4 real documents"),
]
for doc, desc in refs:
    pdf.set_font("Helvetica","B",6)
    pdf.set_x(12)
    pdf.cell(58,4,doc,align="L")
    pdf.set_font("Helvetica","",6)
    pdf.cell(0,4,desc,align="L")
    pdf.ln(5.5)
pdf.ln(3)
add_section("Institutional Sources Consulted")
add_bullet("Al Baraka Bank Tunisia - Official Website (albaraka.com.tn): history, Centre Urbain Nord headquarters, 44 branches, IFN Awards, product catalog.")
add_bullet("Law 2016-48, Official Journal of the Tunisian Republic No. 58 of 15 July 2016; CMF legal reference site.")
add_bullet("ESPRIT - Private Higher School of Engineering and Technology, Ariana (2003), accredited by the Ministry of Higher Education; CTI EUR-ACE audit 2023.")

# ──────────────────────────────────────────────────────────
# Appendix A - Glossary
# ──────────────────────────────────────────────────────────
pdf.add_page()
pdf.set_font("Helvetica","B",10)
pdf.set_text_color(*ESPRIT_NAVY)
pdf.cell(0,6,"Appendix A  -  Glossary",align="L")
pdf.set_draw_color(*ABARAKA_RED)
pdf.line(12, pdf.get_y()+7, 62, pdf.get_y()+7)
pdf.ln(8)
add_table(
    ["Term","Transliteration","Meaning"],
    [
        ["Al-murabaha","al-murabaha","Cost-plus sale with disclosed capital and margin"],
        ["Al-ijara","al-ijara","Lease with acquisition option (Sharia-compliant)"],
        ["Al-sigha","al-sigha","Contractual form (offer & acceptance)"],
        ["Al-fasl","al-fasl","Article (law) / chapter (circular)"],
        ["Nafidh","nafidh","In force"],
        ["Kafin","kafin","Sufficient (evidence)"],
        ["Ghayr kafin","ghayr kafin","Insufficient"],
        ["Muta'arid","muta'arid","Contradictory (two sources in conflict)"],
        ["RAG"," - ","Retrieval-Augmented Generation"],
        ["hit@k"," - ","Whether expected document/fragment is in top-k"],
        ["RRF"," - ","Reciprocal Rank Fusion"],
        ["BM25"," - ","Lexical ranking function"],
        ["Citation gate"," - ","Quote membership + numeric gate"],
        ["Sufficiency"," - ","Whether evidence satisfies the proof plan"],
    ],
    [42,42,102],
)
pdf.set_font("Helvetica","I",5.5)
pdf.set_text_color(110,120,145)
pdf.multi_cell(186,3,"Note: Arabic terms are transliterated here so the PDF renders on standard fonts; the Markdown version preserves the original script where the platform font allows it.",align="L")
pdf.ln(4)
pdf.set_font("Helvetica","B",10)
pdf.set_text_color(*ESPRIT_NAVY)
pdf.cell(0,6,"Appendix B  -  Key Figures",align="L")
pdf.ln(8)
add_table(
    ["Fact","Source"],
    [
        ["RAGLab: 376 commits, 249 files, 86 modules, ~40.8k lines","Git history & RAGLAB_FULL_REPORT.md"],
        ["Corpus: 4 docs -> 339 fragments (233/54/39/13)","raglab/audits/MASTER_INDEX.md"],
        ["Tests: 472 unit + 133 offline, 8 workflows","CI at merge (service v1.5.0)"],
        ["Web app: 255 commits, 428 files, 15 migrations","bank-assistant/docs/PROJECT_REPORT.md"],
        ["Stack: NVIDIA 2048-d + Qwen (pinned, no fallback)","raglab/pipeline_policy.py"],
        ["Hit@1 live: 71/80/87 (restructure) vs 51/64/71 (size)","questions_50.json frozen set"],
    ],
    [62,124],
)
pdf.ln(2)
pdf.set_font("Helvetica","B",9)
pdf.set_text_color(*ESPRIT_NAVY)
pdf.cell(0,6,"Appendix C  -  Reproduction (Local)",align="L")
pdf.ln(7)
# code block
code = [
    "# RAGLab",
    "cd RAGLab/raglab",
    "python3 -m venv .venv && source .venv/bin/activate",
    "pip install -r requirements.txt",
    "./run_tests.sh --offline",
    "python main.py ingest --reset --data-dir ../docs",
    'python main.py answer "..." --query-lang ar',
    "",
    "# Web app (Docker)",
    "cp bank-assistant/.env.docker.example .env   # set RAGLAB_URL, RAGLAB_TOKEN, JWT_SECRET",
    "docker compose up --build                       # http://localhost:8080",
    "",
    "# Verify",
    "curl http://localhost:8000/health",
    "curl http://localhost:8080/api/agent/health -H \"Authorization: Bearer <token>\"",
]
pdf.set_fill_color(245,247,251)
pdf.set_draw_color(*MID_GRAY)
y0=pdf.get_y()
total_h = len(code)*3.6 + 6
pdf.rect(12, y0, 186, total_h, 'DF')
pdf.set_font("Courier","",5.8)
pdf.set_text_color(40,50,75)
for i,l in enumerate(code):
    pdf.set_xy(14, y0+3+i*3.6)
    pdf.cell(182,3.6,l,align="L")
pdf.set_y(y0+total_h+6)
pdf.ln(2)
# attestation
pdf.set_draw_color(*ABARAKA_RED)
pdf.set_line_width(0.25)
pdf.line(70, pdf.get_y(), 140, pdf.get_y())
pdf.ln(2)
pdf.set_font("Helvetica","I",6)
pdf.set_text_color(100,110,135)
pdf.cell(0,4,"Report prepared by Farah AYARI  -  ESPRIT, internship at Al Baraka Bank Tunisia  -  Centre Urbain Nord,",align="C")
pdf.ln(3.5)
pdf.cell(0,4,"supervised by Mrs. Ahlem Ben Haddoud. This document contains no account identifiers, repository URLs or credentials.",align="C")
pdf.ln(3.5)
pdf.set_text_color(130,140,160)
pdf.set_font("Helvetica","",5.5)
pdf.cell(0,4,"All figures are traceable to the two repositories and their frozen benchmark artifacts. |  15-20 pages  |  English  |  Governed Intelligence",align="C")

# back cover (navy band)
pdf.set_fill_color(*ESPRIT_NAVY)
pdf.rect(0, 283, 210, 14, 'F')
pdf.set_xy(10, 287)
pdf.set_font("Helvetica","B",6)
pdf.set_text_color(*WHITE)
pdf.cell(190,4,"ESPRIT  |  Al Baraka Bank Tunisia  |  A Governed Intelligence for Islamic Banking  |  Farah AYARI",align="C")
pdf.set_xy(10, 290)
pdf.set_font("Helvetica","",5)
pdf.set_text_color(200,210,230)
pdf.cell(190,3,"Centre Urbain Nord  -  Trust through traceability  -  Every answer cites its source, every refusal explains why.",align="C")

print(f"Pages: {pdf.pages_count}")
pdf.output(OUTPUT)
print(f"Written to {OUTPUT}  ({os.path.getsize(OUTPUT)/1024:.1f} KB)")

