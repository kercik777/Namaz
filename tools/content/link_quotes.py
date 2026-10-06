#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Привязывает «Günüň sözi» к месту в книге.

Для каждой цитаты ищет тот же текст в книгах и записывает book / chapter / block —
по нажатию на карточку приложение открывает это место.

    python3 tools/content/link_quotes.py [--dry]
"""
import argparse
import glob
import json
import os
import re
import sys

ASSETS = "app/src/main/assets/content"
sys.path.insert(0, os.path.join(os.path.dirname(__file__)))

from merge_books import load_dictionary, good_heading   # noqa: E402


def norm(s):
    s = (s or "").lower()
    s = s.replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ý", "y")
    s = s.replace("ň", "n").replace("ş", "s").replace("ž", "z").replace("ç", "c")
    s = re.sub(r"[^\w]+", " ", s, flags=re.UNICODE)
    return " ".join(s.split())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args()

    books = {}
    for f in glob.glob(os.path.join(ASSETS, "*.json")):
        if f.endswith("quotes.json"):
            continue
        d = json.load(open(f, encoding="utf-8"))
        if isinstance(d, list):
            d = d[0]
        books[d["id"]] = d

    quotes = json.load(open(os.path.join(ASSETS, "quotes.json"), encoding="utf-8"))
    found = 0
    for q in quotes:
        needle = norm(q.get("tk", ""))[:44]
        q.pop("book", None)
        q.pop("chapter", None)
        q.pop("block", None)
        if len(needle) < 12:
            continue
        for bid, book in books.items():
            hit = None
            for i, b in enumerate(book["blocks"]):
                if b.get("t") in ("title", "center"):
                    continue
                text = norm((b.get("x") or "") + " " + " ".join(b.get("items") or []))
                if needle and needle in text:
                    hit = i
                    break
            if hit is None:
                continue
            chapter = 0
            for k, t in enumerate(book.get("toc", [])):
                if t.get("b", 0) <= hit:
                    chapter = k
            q["book"] = bid
            q["chapter"] = chapter
            q["block"] = hit
            found += 1
            break
        print("  %-5s %s" % ("найдено" if "book" in q else "нет", q.get("tk", "")[:52]))
    print("привязано цитат: %d из %d" % (found, len(quotes)))
    if not a.dry:
        json.dump(quotes, open(os.path.join(ASSETS, "quotes.json"), "w", encoding="utf-8"),
                  ensure_ascii=False, separators=(",", ":"))


if __name__ == "__main__":
    main()
