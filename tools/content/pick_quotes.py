#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Выбирает «Günüň sözi» прямо из текста книг.

Цитата дня должна открываться в книге по нажатию, поэтому берём только
целые предложения из самих книг и запоминаем их место.

    python3 tools/content/pick_quotes.py [--dry] [--count 24]
"""
import argparse
import json
import os
import re
import sys

ASSETS = "app/src/main/assets/content"
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))) + "/content")
from merge_books import load_dictionary, body_vocabulary, tokens   # noqa: E402

THEME = ("sabur saglyk iman ybadat namaz kalp goni yagsy erbet dogry yhlas "
         "pikir ynsan adam ogul gyz ata ene ylym bilim akyly arassa "
         "halal haram jennet dowzah ahyret Alla pygamber".split())

BAD = re.compile(r"[\[\]{}_|<>*№§]|http|\d|«|»|&")


def sentence_ok(t, dic, vocab):
    t = t.strip()
    if len(t) < 70 or len(t) > 170:
        return False
    if t.isupper():
        return False
    if BAD.search(t):
        return False
    if t.count(".") != 1 or not t.endswith("."):
        return False
    if t[0].islower() or t[0] in ",.;:!?»":
        return False
    words = tokens(t)
    if len(words) < 9 or len(words) > 26:
        return False
    # все слова должны быть знакомыми
    known = 0
    for w in words:
        lw = w.lower()
        if len(lw) < 3 or lw in dic or lw in vocab or lw.rstrip("larlerdan") in dic:
            known += 1
    if known < len(words) * 0.86:
        return False
    # без странных пропусков: рядом стоящие короткие обрывки
    if re.search(r"\b\w{1,2}\s+\w{1,2}\s+\w{1,2}\b", t.lower()):
        return False
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry", action="store_true")
    ap.add_argument("--count", type=int, default=24)
    a = ap.parse_args()

    books = []
    for bid in ("akyda", "namaz_kitaby"):
        p = os.path.join(ASSETS, bid + ".json")
        if os.path.exists(p):
            books.append(json.load(open(p, encoding="utf-8")))

    all_blocks = [b for bk in books for b in bk["blocks"]]
    dic = load_dictionary()
    vocab = body_vocabulary(all_blocks)

    cands = []
    for bk in books:
        toc = bk.get("toc", [])
        for i, b in enumerate(bk["blocks"]):
            if b.get("t") in ("title", "center", "h1", "h2", "h3", "part", "q", "a"):
                continue
            text = b.get("x") or ""
            if b.get("items"):
                text = text or " ".join(b["items"])
            if not sentence_ok(text, dic, vocab):
                continue
            low = text.lower()
            score = sum(1 for w in THEME if w in low)
            # компенсируем смещение к началу книги
            score -= abs(i / max(1, len(bk["blocks"])) - 0.45) * 2
            cands.append((score, bk["id"], i, text.strip(),
                          toc_title(toc, i)))

    cands.sort(key=lambda x: -x[0])
    picked, used_chapters = [], set()
    for c in cands:
        key = (c[1], c[4])
        if key in used_chapters:
            continue
        used_chapters.add(key)
        picked.append(c)
        if len(picked) >= a.count:
            break

    quotes = []
    for score, bid, i, text, chap in picked:
        quotes.append({
            "tk": text,
            "ru": "",
            "src": chap or (books[0]["title"].get("tk") if bid == "akyda" else "Namaz kitaby"),
            "book": bid,
            "chapter": 0,
            "block": i,
        })
    print("найдено кандидатов: %d, взято %d" % (len(cands), len(quotes)))
    for q in quotes:
        print("  %-12s b=%-5d %s" % (q["book"], q["block"], q["tk"][:90]))
    if not a.dry:
        json.dump(quotes, open(os.path.join(ASSETS, "quotes.json"), "w", encoding="utf-8"),
                  ensure_ascii=False, separators=(",", ":"))
        print("записано:", os.path.join(ASSETS, "quotes.json"))


def toc_title(toc, block):
    title = None
    for t in toc:
        if t.get("b", 0) <= block:
            title = t.get("x")
    return title


if __name__ == "__main__":
    main()
