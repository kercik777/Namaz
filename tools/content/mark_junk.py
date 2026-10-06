#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Чистит распознанный текст от «шума» и подсказывает читать такие места по скану.

На страницах с арабской графикой распознавание даёт набор отдельных букв («b», «y»,
«L G n»). Такие блоки (в которых нет ни одного слова с гласной) убираются, а если
на странице оригинала шума было больше половины — вставляется аккуратная пометка
с номером страницы: читатель открывает «Çap nusgasy» и видит подлинник.

Инструмент идемпотентен.

    python3 tools/content/mark_junk.py [--dry]
"""
import argparse
import json
import os
import re

ASSETS = "app/src/main/assets/content"
VOWELS = "aäeioöuüyý"
NOTE = "Arapça aýatlar — bu bölek programmada doly berilmeýär"
MARK = "arap_note"
NOISE_RATIO = 0.5     # доля шума на странице, после которой ставим пометку
MIN_NOISE = 4         # минимум шумных блоков


def tokens(text):
    return [t for t in re.split(r"[\s\W_]+", text, flags=re.UNICODE) if t]


def has_vowel(word):
    return any(ch in VOWELS for ch in word.lower())


def is_noise(block):
    """Шум: нет ни одного слова с гласной буквой («b», «y», «L G n», «4105»)."""
    if block.get("t") in ("img", "page"):
        return False
    if block.get("mark") == MARK:
        return False
    text = (block.get("x") or " ".join(block.get("items") or [])).strip()
    if not text:
        return True
    t = tokens(text)
    if not t:
        return True
    return not any(has_vowel(w) for w in t)


def clean(path, dry):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    if isinstance(data, list):
        data = data[0]
    blocks = data.get("blocks", [])

    # 1) убираем шум, запоминая, где он был
    kept = []
    noise_by_page = {}
    last_page = None
    for b in blocks:
        if b.get("n"):
            last_page = b["n"]
        if is_noise(b):
            page = b.get("n") or last_page
            if page:
                noise_by_page[page] = noise_by_page.get(page, 0) + 1
            continue
        kept.append(b)

    # 2) страницы, где шума было слишком много — заменяем пометкой со ссылкой на скан
    total_by_page = {}
    for b in kept:
        if b.get("n"):
            total_by_page[b["n"]] = total_by_page.get(b["n"], 0) + 1
    heavy = set()
    for page, noise in noise_by_page.items():
        total = total_by_page.get(page, 0) + noise
        if noise >= MIN_NOISE and noise / max(1, total) > NOISE_RATIO:
            heavy.add(page)

    out = []
    noted = set()
    for b in kept:
        out.append(b)
        page = b.get("n")
        if page in heavy and page not in noted:
            out.append({"t": "note", "x": NOTE, "n": page, "mark": MARK})
            noted.add(page)

    data["blocks"] = out
    if not dry:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    return len(blocks) - len(kept), noted, len(kept), len(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args()
    for book in ("namaz_kitaby", "namaz", "akyda"):
        path = os.path.join(ASSETS, book + ".json")
        noise, noted, was, now = clean(path, a.dry)
        pages = ", ".join(str(p) for p in sorted(noted)[:12])
        print("%-14s убрано шума: %4d   блоков %4d -> %4d   страницы-скан: %s%s"
              % (book, noise, was, now, pages, " …" if len(noted) > 12 else ""))


if __name__ == "__main__":
    main()
