#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Сборка книги для приложения: страницы-сканы + распознанный текст -> JSON.

Вход:
    --work   рабочий каталог build_pages.py (там meta.json)
    --pages  каталог с webp-сканами (вывод build_pages.py --out-assets)
    --ocr    JSON распознавания (вывод tools/ocr/recognize.py)
    --assets app/src/main/assets
Выход:
    app/src/main/assets/pages/<book_id>/<n>.webp   (n = 1..N, порядок чтения)
    app/src/main/assets/content/<book_id>.json     (в формате приложения)

Дополнительно может восстанавливать туркменскую диакритику (tools/tkcorrect.py).
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys

TOOLS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, TOOLS)

ARABIC = re.compile(r"[\u0600-\u06FF\u0750-\u077F\uFB50-\uFDFF\uFE70-\uFEFF]")
CYRILLIC = re.compile(r"[\u0400-\u04FF]")
LIST_RE = re.compile(r"^\s*(\d{1,3}|[a-zа-я])\s*[.)]\s+")

BOOKS = {
    "b1": {
        "id": "namaz_kitaby",
        "script": "latin",
        "accent": 0xFF0E4A36,
        "title": {"ru": "Намаз китабы", "tk": "Namaz kitaby"},
        "subtitle": {"ru": "Латиница • оригинал и распознанный текст",
                     "tk": "Latin elipbiýi • asyl nusga we tekst"},
        "author": {"ru": "По изданию «Namaz kitaby»", "tk": "«Namaz kitaby» neşirine görä"},
        "edition": {"ru": "Туркменистан", "tk": "Türkmenistan"},
    },
    "b2": {
        "id": "namaz",
        "script": "cyrillic",
        "accent": 0xFF14384F,
        "title": {"ru": "Намаз китабы — вторая книга", "tk": "Namaz kitaby — ikinji kitap"},
        "subtitle": {"ru": "Кириллица и арабица • оригинал и распознанный текст",
                     "tk": "Kirill we arap elipbiýi • asyl nusga we tekst"},
        "author": {"ru": "По изданию «Namaz kitaby»", "tk": "«Namaz kitaby» neşirine görä"},
        "edition": {"ru": "Туркменистан", "tk": "Türkmenistan"},
    },
}


def type_map(t):
    if t in ("h1", "h2", "p", "arabic", "footnote", "li"):
        return {"h1": "h1", "h2": "h2", "p": "p", "arabic": "ar",
                "footnote": "note", "li": "list"}[t]
    return "p"


def text_script(text):
    if ARABIC.search(text):
        return "arabic"
    if CYRILLIC.search(text):
        return "cyrillic"
    return "latin"


def correct_latin(blocks, corrector, freq):
    """Восстанавливает туркменскую диакритику в латинских блоках."""
    fixed = 0
    for b in blocks:
        text = b.get("x", "")
        if not text or text_script(text) != "latin":
            continue
        if len(text) < 3:
            continue
        new, _stats, _unk = corrector.correct_text(text, freq)
        if new and new != text:
            b["x"] = new
            fixed += 1
    return fixed


def page_order(meta, limit=0):
    """Порядок страниц книги (сканы в приложение не копируются — там свои рисунки)."""
    names = []
    for m in meta.get("pages", []):
        names.append(m["name"])
        if limit and len(names) >= limit:
            break
    return {name: i for i, name in enumerate(names, 1)}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", required=True)
    ap.add_argument("--pages", required=True)
    ap.add_argument("--ocr", required=True)
    ap.add_argument("--assets", required=True)
    ap.add_argument("--book", required=True, choices=sorted(BOOKS))
    ap.add_argument("--limit", type=int, default=0, help="только N страниц (отладка)")
    ap.add_argument("--correct", action="store_true", help="восстанавливать диакритику")
    ap.add_argument("--title-ru", default="")
    ap.add_argument("--title-tk", default="")
    args = ap.parse_args(argv)

    cfg = dict(BOOKS[args.book])
    if args.title_ru:
        cfg["title"]["ru"] = args.title_ru
    if args.title_tk:
        cfg["title"]["tk"] = args.title_tk

    meta = json.load(open(os.path.join(args.work, "meta.json"), encoding="utf-8"))
    ocr = json.load(open(args.ocr, encoding="utf-8"))
    by_name = {p["name"]: p for p in ocr.get("pages", [])}

    order = page_order(meta, limit=args.limit)
    scan_pages = len(order)

    blocks = []
    toc = []
    blocks.append({"t": "title", "x": cfg["title"]["tk"]})
    blocks.append({"t": "center", "x": cfg["subtitle"]["tk"]})
    last_text = ""
    arabic_count = 0

    for name, idx in sorted(order.items(), key=lambda kv: kv[1]):
        page = by_name.get(name)
        if not page:
            continue
        if any((b.get("text") or "").strip() for b in page.get("blocks", [])):
            # маркер страницы оригинала: в приложении страница листается ровно
            # как в книге — одна страница PDF, один лист
            blocks.append({"t": "page", "n": idx})
        for b in page.get("blocks", []):
            text = (b.get("text") or "").strip()
            if not text:
                continue
            t = type_map(b.get("type", "p"))
            if t == "list":
                parts = LIST_RE.split(text)
                items = []
                if len(parts) >= 3:
                    chunks = LIST_RE.findall(text)
                    pieces = re.split(LIST_RE, text)
                    items = [p.strip() for p in pieces if p and not LIST_RE.match(p) and p.strip()]
                items = [i for i in items if i]
                if len(items) >= 2:
                    blocks.append({"t": "list", "items": items})
                    continue
                t = "p"
            if t == "ar":
                arabic_count += 1
            # повторы колонтитулов подряд убираем
            if text == last_text and t in ("p", "h2", "center"):
                continue
            last_text = text
            blk = {"t": t, "x": text}
            if t in ("h1", "h2"):
                toc.append({"level": 1 if t == "h1" else 2, "x": text, "b": len(blocks)})
            blocks.append(blk)

    if args.correct:
        try:
            import tkcorrect
            dic = os.path.join(TOOLS, "data", "tk_TM.dic")
            aff = os.path.join(TOOLS, "data", "tk_TM.aff")
            corr = tkcorrect.TurkmenCorrector(dic, aff)
            freq = corr.build_frequency([b.get("x", "") for b in blocks if b.get("x")])
            fixed = correct_latin([b for b in blocks if b["t"] in ("p", "h1", "h2", "note")], corr, freq)
            print("исправлено блоков:", fixed, flush=True)
        except Exception as e:  # noqa: BLE001
            print("ВНИМАНИЕ: коррекция диакритики не выполнена:", e, flush=True)

    if len(toc) < 2:
        toc = []

    out = {
        "v": 1,
        "id": cfg["id"],
        "script": cfg["script"],
        "accent": cfg["accent"],
        "title": cfg["title"],
        "subtitle": cfg["subtitle"],
        "author": cfg["author"],
        "edition": cfg["edition"],
        "blocks": blocks,
        "toc": toc,
    }
    content_dir = os.path.join(args.assets, "content")
    os.makedirs(content_dir, exist_ok=True)
    out_path = os.path.join(content_dir, cfg["id"] + ".json")
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False)
    chars = sum(len(b.get("x", "")) + sum(len(i) for i in b.get("items", [])) for b in blocks)
    print("готово:", out_path, flush=True)
    print("  страниц-сканов:", scan_pages, "блоков:", len(blocks), "символов:", chars,
          "арабских блоков:", arabic_count, "пунктов оглавления:", len(toc), flush=True)
    print("  размер JSON: %.1f КБ" % (os.path.getsize(out_path) / 1024.0), flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
