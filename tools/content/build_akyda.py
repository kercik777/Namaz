#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Собирает книгу «Akyda bölümi» из PDF (в нём есть текстовый слой) в JSON для приложения.

Запуск: python3 tools/content/build_akyda.py
Результат: app/src/main/assets/content/akyda.json
"""
import json
import os
import re
import sys

try:
    import pymupdf
except ImportError:
    import fitz as pymupdf

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PDF = os.path.join(ROOT, "aqyda bölümi.pdf")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "content", "akyda.json")

ARABIC = re.compile(r"[\u0600-\u06FF\u0750-\u077F\uFB50-\uFDFF\uFE70-\uFEFF]")
DERS = re.compile(r"^\s*([A-ZÇÄŇŞÝÜÖŽ,\.\s]{3,}\s+DERS\s*:?)\s*$", re.IGNORECASE)
PAGE_NO = re.compile(r"^\s*\d{1,3}\s*$")

# Небольшие правки распознавания/типографики исходного PDF
FIXES = [
    ("MUSILMAN", "MUSULMAN"),
    ("ALLA TA,ALA", "ALLAH TA,ALA"),
    ("ALLA TA,ALANYŇ", "ALLAH TA,ALANYŇ"),
    ("HHSAN", "IHSAN"),
    ("ŞÜ DYNY", "ŞU DÜNYÄ"),
    ("ŞÜ DYNYŇ", "ŞU DÜNYÄNIŇ"),
    ("DYN YOLYNI", "DIN ÝOLUNY"),
    ("DOERDENDIGNI", "DÖREDENDIGINI"),
    ("DÖERDENDIGNI", "DÖREDENDIGINI"),
    ("PIGAMBER", "PYGAMBER"),
    ("IÝLÇISI", "ILÇISI"),
    ("IÝLÇILERI", "ILÇILERI"),
    ("IÝBEREN", "IBEREN"),
    ("IÝMAM", "IÝMAN"),
    ("DIÝER LER", "DIÝERLER"),
    ("FERIŞTELERÖŇ", "FERIŞTELERIŇ"),
    ("DYNY NA", "DYNYNA"),
    ("NÄME LER", "NÄMELER"),
    ("MA,NASY", "MANYSY"),
    ("BÜÝRYKLARNY", "BUÝRUKLARYNY"),
    ("BÜÝRMADYK", "BUÝURMADYK"),
    ("ALAÝHISSALATÜ WESSALAMGA", "ALAÝHISSALATÜ WESSALAMGA"),
    ("ONYNJY", "ONUNJY"),
    ("DOKGYZYNJY", "DOKUZYNJY"),
    ("SEKGIZINJI", "SEKIZINJI"),
    ("KÖB", "KÖP"),
    ("GALJAK", "GALJAK"),
    ("SÜBUTI SYFATLRY", "SÜBUTY SYFATLARY"),
    ("SYFALARY", "SYFATLARY"),
    ("AWWAKYSY", "AWWALKYSY"),
    ("SELBI SYFATLARY BU LAR DUR", "SELBI SYFATLARY BULAR DUR"),
    ("IKINJISI :", "IKINJISI :"),
    ("ŞU DÜNYÄNIŇ", "ŞU DÜNYÄNIŇ"),
]

LIST_ITEM = re.compile(
    r"^(AWWAL\w*|IKINJI\w*|ÜÇÜNJI\w*|DÖRDINJI\w*|BÄŞINJI\w*|ALTYNJY\w*|ÝEDINJI\w*|"
    r"SEKIZINJI\w*|DOKUZYNJY\w*|ONUNJY\w*|ON BIRINJI\w*|\d+[\.\)])\s*:?\s*",
    re.IGNORECASE)
def clean(line: str) -> str:
    s = line.replace("\u00a0", " ")
    s = re.sub(r"[ \t]{2,}", " ", s).strip()
    for a, b in FIXES:
        s = s.replace(a, b)
    return s


def main() -> int:
    doc = pymupdf.open(PDF)

    blocks = []
    current = None    # {"t": ..., "x": ...}
    list_items = []   # элементы списка

    def flush_list():
        if list_items:
            blocks.append({"t": "list", "items": [re.sub(r"\s+", " ", x).strip() for x in list_items if x.strip()]})
            list_items.clear()

    def close_current():
        nonlocal current
        flush_list()
        if current is not None:
            txt = re.sub(r"\s+", " ", current["x"]).strip()
            if txt:
                current["x"] = txt
                blocks.append(current)
            current = None

    blocks.append({"t": "part", "x": "AKYDA BÖLÜMI"})
    blocks.append({"t": "p", "x": "Imanyň esaslary: Alla, pygamberler, kitaplar, ahyret we kadar baradaky on bir ders."})

    # Сначала собираем все значимые строки (учитывая, что в PDF каждая строка
    # отделена пустой строкой, а предложения переносятся по строкам).
    lines = []
    for page in doc:
        for line in page.get_text().split("\n"):
            c = clean(line)
            if not c or PAGE_NO.match(c):
                continue
            lines.append(c)

    def next_line(i):
        return lines[i + 1] if i + 1 < len(lines) else ""

    for i, s in enumerate(lines):
        if DERS.match(s):
            close_current()
            blocks.append({"t": "h1", "x": re.sub(r"\s+", " ", s.strip().rstrip(":")).strip()})
            continue
        if ARABIC.search(s):
            close_current()
            blocks.append({"t": "ar", "x": s})
            continue
        m_q = re.match(r"^[SŠ]\s*:\s*(.*)$", s)
        if m_q:
            close_current()
            current = {"t": "q", "x": m_q.group(1).strip()}
            continue
        m_a = re.match(r"^[JĴ]\s*:\s*(.*)$", s)
        if m_a:
            close_current()
            current = {"t": "a", "x": m_a.group(1).strip()}
            continue

        if current is None:
            current = {"t": "q" if s.endswith("?") or s.rstrip().endswith(":") else "p", "x": s}
            continue

        if current["t"] == "q" and not current["x"].rstrip().endswith("?"):
            current["x"] += " " + s
            continue

        # строка начинает вопрос, если следующая строка заканчивается «?»
        starts_question = (not s.endswith((".", ",", ";", ":"))) and next_line(i).endswith("?")

        if s.endswith("?") or (s.rstrip().endswith(":") and len(s) < 70) or starts_question:
            close_current()
            current = {"t": "q", "x": s}
            continue

        if current["t"] == "q":
            close_current()
            current = {"t": "a", "x": s}
            continue

        if LIST_ITEM.match(s):
            list_items.append(s)
            continue

        current["x"] += " " + s
    close_current()
    doc.close()

    data = {
        "v": 1,
        "id": "akyda",
        "script": "latin",
        "has_scan": False,
        "scan_pages": 0,
        "accent": 0xFF0E4A36,
        "title": {"ru": "Акыда", "tk": "Akyda"},
        "subtitle": {"ru": "Основы вероучения — одиннадцать уроков", "tk": "Iman esaslary — on bir ders"},
        "author": {"ru": "По изданию «Namaz kitaby»", "tk": "«Namaz kitaby» neşirine görä"},
        "edition": {"ru": "Türkmenistanyň Müftüsiniň Müdirligi", "tk": "Türkmenistanyň Müftüsiniň Müdirligi"},
        "blocks": blocks,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print("Готово:", OUT)
    print("Блоков:", len(blocks), "| символов:", sum(len(b.get("x", "")) + sum(len(i) for i in b.get("items", [])) for b in blocks))
    return 0


if __name__ == "__main__":
    sys.exit(main())
