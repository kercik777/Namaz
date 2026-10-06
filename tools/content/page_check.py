#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Сверка страницы приложения с её страницей в PDF (глазами).

Номер страницы — тот, что стоит в книге и в приложении («Sahypa N»). Скрипт
рисует соответствующую половину разворота PDF и печатает текст этой страницы
так, как он лежит в приложении.

    python3 tools/content/page_check.py 30            # страница 30
    python3 tools/content/page_check.py 30 31 36      # несколько подряд
    python3 tools/content/page_check.py --map         # карта страниц
"""
from __future__ import annotations

import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "app/src/main/assets/content")
PDF = {
    "b1": "1Namaz_compressed.pdf",     # первая книга (страницы 1..94)
    "b2": "2Namaz 2_compressed.pdf",   # вторая часть (страницы 95..187)
}
OCR = {"b1": "content/ocr/b1.json", "b2": "content/ocr/b2.json"}


def load_book():
    d = json.load(open(os.path.join(ASSETS, "namaz_kitaby.json"), encoding="utf-8"))
    if isinstance(d, list):
        d = d[0]
    return d


def pages_of(book):
    """{номер страницы: [блоки]} по маркерам {"t":"page"}."""
    out = {}
    cur = None
    for b in book["blocks"]:
        if b.get("t") == "page":
            cur = b.get("n")
            out[cur] = []
            continue
        if cur is None:
            continue
        out[cur].append(b)
    return out


def scan_of(number):
    """Номер страницы -> (книга, имя скана, номер разворота PDF, левая ли половина)."""
    for b in ("b1", "b2"):
        d = json.load(open(os.path.join(ROOT, OCR[b]), encoding="utf-8"))
        idxs = [(i + 1, p["name"]) for i, p in enumerate(d["pages"])
                if any((x.get("text") or "").strip() for x in p.get("blocks", []))]
        for idx, name in idxs:
            if idx == number:
                return b, name
    return None, None


def text_of(blocks):
    lines = []
    for b in blocks:
        t = b.get("t")
        if t == "list":
            for i in b.get("items", []):
                lines.append("• " + i)
        elif t == "img":
            lines.append("[рисунок] " + str(b.get("src")))
        elif b.get("x"):
            lines.append("%-5s %s" % (t, b["x"]))
    return lines


def render(number, out_png, dpi=150):
    import fitz
    book, scan = scan_of(number)
    if not book:
        print("нет такой страницы:", number)
        return None
    idx = int(scan[:4])
    left = scan.endswith("l")
    spread = (idx + 1) // 2
    doc = fitz.open(os.path.join(ROOT, PDF[book]))
    page = doc[spread - 1]
    r = page.rect
    half = fitz.Rect(r.x0, r.y0, r.x0 + r.width / 2.0, r.y1) if left else \
        fitz.Rect(r.x0 + r.width / 2.0, r.y0, r.x1, r.y1)
    pix = page.get_pixmap(matrix=fitz.Matrix(dpi / 72.0, dpi / 72.0), clip=half)
    pix.save(out_png)
    return out_png


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    if "--map" in sys.argv:
        for n in range(1, 188):
            book, scan = scan_of(n)
            if book:
                print("%3d %s %s" % (n, book, scan))
        return
    if not args:
        print(__doc__)
        return
    pages = pages_of(load_book())
    for a in args:
        n = int(a)
        png = "/tmp/page_%d.png" % n
        render(n, png)
        print("=" * 70)
        print("СТРАНИЦА %d — картинка PDF: %s" % (n, png))
        for line in text_of(pages.get(n, [])):
            print("   " + line)


if __name__ == "__main__":
    main()
