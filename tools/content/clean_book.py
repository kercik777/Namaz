#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Приведение распознанной книги в порядок (правит JSON на месте).

Что делает:
  * нормализует пробелы и знаки препинания;
  * склеивает разорванные заголовки (когда строка заголовка распозналась по словам);
  * выкидывает мусорные блоки (только цифры, только знаки, повторы);
  * чистит оглавление: убирает обрывки, дубликаты и слишком короткие пункты;
  * склеивает короткие обрывки абзацев и переносы слов.

Запуск:
    python3 tools/content/clean_book.py app/src/main/assets/content/namaz_kitaby.json
    python3 tools/content/clean_book.py --all app/src/main/assets/content
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys

SPACE_BEFORE = re.compile(r"\s+([,.;:!?%»)\]])")
SPACE_AFTER_OPEN = re.compile(r"([«(\[])\s+")
MANY_SPACES = re.compile(r"[ \t]{2,}")
MULTI_PUNCT = re.compile(r"([,.;:!?])\1{1,}")
DIGITS_ONLY = re.compile(r"^[\d\s\-–—.,;:()\[\]]+$")
PUNCT_ONLY = re.compile(r"^[^\w\u0400-\u04FF\u0600-\u06FF]+$", re.UNICODE)
END_PUNCT = (".", "!", "?", ":", ";", "»", '"', ")")


def norm_text(t: str) -> str:
    t = t.replace("\u00a0", " ").replace("\u200b", "")
    t = MANY_SPACES.sub(" ", t)
    t = SPACE_BEFORE.sub(r"\1", t)
    t = SPACE_AFTER_OPEN.sub(r"\1", t)
    t = MULTI_PUNCT.sub(r"\1", t)
    t = re.sub(r"\s+([)»])", r"\1", t)
    return t.strip()


def is_junk(t: str) -> bool:
    s = t.strip()
    if len(s) <= 1:
        return True
    if DIGITS_ONLY.match(s) or PUNCT_ONLY.match(s):
        return True
    # строка из 1–2 коротких «слов» без букв-гласных — мусор распознавания
    words = s.split()
    if len(words) <= 2 and all(len(w) <= 2 for w in words) and not any(ch.isalpha() for ch in s):
        return True
    return False


def looks_like_heading(t: str) -> bool:
    """Заголовок: без кавычек, двоеточий, цифр, не начинается со строчной буквы."""
    s = t.strip()
    if len(s) < 4 or len(s) > 90:
        return False
    if any(ch in s for ch in '«»"()[]{}'):
        return False
    if any(ch in s for ch in ":;,"):
        return False
    if s.endswith("-"):
        return False
    if s[0] in ',.;:!?-/–—':
        return False
    if s[0].islower():
        return False
    if any(c.isdigit() for c in s):
        return False
    words = s.split()
    if len(words) > 9:
        return False
    if sum(1 for w in words if len(w) <= 1) > 1:
        return False
    return True


def demote_bad_headings(blocks):
    """Блоки-«заголовки», которые на самом деле обрывки текста, делаем обычным текстом."""
    out = []
    for b in blocks:
        if b["t"] in ("h1", "h2") and not looks_like_heading(b.get("x", "")):
            b = dict(b)
            b["t"] = "p"
        out.append(b)
    return out


def merge_headings(blocks):
    """«TÜRKMENISTANYŇ» + «MÜDIRIÝETI» + «MÜFTÜSINIŇ» -> один заголовок."""
    out = []
    for b in blocks:
        if (out and b["t"] in ("h1", "h2") and out[-1]["t"] in ("h1", "h2")
                and len(out[-1].get("x", "")) <= 46 and len(b.get("x", "")) <= 46
                and not out[-1].get("x", "").endswith((".", "!", "?", ":"))):
            prev = out[-1]
            joined = (prev["x"] + " " + b["x"]).strip()
            if len(joined) <= 90:
                prev["x"] = norm_text(joined)
                prev["t"] = "h1" if (prev["t"] == "h1" or b["t"] == "h1") else "h2"
                continue
        out.append(b)
    return out


def drop_junk(blocks):
    out = []
    for b in blocks:
        if b["t"] in ("page", "title", "center"):
            out.append(b)
            continue
        if b["t"] == "list":
            items = [norm_text(i) for i in b.get("items", []) if not is_junk(i)]
            if not items:
                continue
            b["items"] = items
            out.append(b)
            continue
        x = norm_text(b.get("x", ""))
        if is_junk(x):
            continue
        b["x"] = x
        out.append(b)
    return out


def drop_duplicates(blocks):
    out = []
    for b in blocks:
        if out and b["t"] in ("p", "note", "h2") and out[-1]["t"] == b["t"] and out[-1].get("x") == b.get("x"):
            continue
        out.append(b)
    return out


def join_fragments(blocks):
    """Склеивает обрывки абзацев и перенесённые слова."""
    out = []
    for b in blocks:
        if out and b["t"] == "p" and out[-1]["t"] in ("p", "note"):
            prev = out[-1]
            px, bx = prev.get("x", ""), b.get("x", "")
            if px.endswith("-") and len(px) > 3:
                prev["x"] = px[:-1] + bx.lstrip()
                continue
            short = len(px) < 26 and not px.endswith(END_PUNCT)
            if short and bx[:1].islower():
                prev["x"] = (px + " " + bx).strip()
                continue
        out.append(b)
    return out


def rebuild_toc(blocks, old_toc):
    """Оглавление: только осмысленные заголовки, без дублей и обрывков."""
    toc = []
    seen = set()
    for i, b in enumerate(blocks):
        if b["t"] not in ("h1", "h2") or i < 4:
            continue  # титульные страницы в оглавление не берём
        text = b.get("x", "").strip()
        if len(text) < 5 or DIGITS_ONLY.match(text):
            continue
        if len(text.split()) == 1 and i < 14:
            continue  # одиночные слова на титульных страницах — не пункты оглавления
        key = text.lower()
        if key in seen:
            continue
        seen.add(key)
        toc.append({"level": 1 if b["t"] == "h1" else 2, "x": text, "b": i})
    if len(toc) < 2:
        return old_toc
    return toc


def clean(path, verbose=True):
    data = json.load(open(path, encoding="utf-8"))
    before = len(data.get("blocks", []))
    before_chars = sum(len(b.get("x", "")) for b in data.get("blocks", []))
    blocks = data.get("blocks", [])
    blocks = drop_junk(blocks)
    blocks = demote_bad_headings(blocks)
    blocks = merge_headings(blocks)
    blocks = demote_bad_headings(blocks)
    blocks = drop_duplicates(blocks)
    blocks = join_fragments(blocks)
    data["blocks"] = blocks
    data["toc"] = rebuild_toc(blocks, data.get("toc", []))
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False)
    after_chars = sum(len(b.get("x", "")) for b in blocks)
    if verbose:
        print("%s: блоков %d -> %d, символов %d -> %d, пунктов оглавления %d" % (
            os.path.basename(path), before, len(blocks), before_chars, after_chars,
            len(data["toc"])))
    return data


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="*")
    ap.add_argument("--all", help="каталог с книгами content")
    args = ap.parse_args(argv)
    targets = list(args.paths)
    if args.all:
        targets += sorted(glob.glob(os.path.join(args.all, "*.json")))
        targets = [t for t in targets if not t.endswith("quotes.json")]
    if not targets:
        ap.error("укажите файл книги или --all <каталог>")
    for t in targets:
        clean(t)
    return 0


if __name__ == "__main__":
    sys.exit(main())
