#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Пересборка книг приложения из распознанного текста — одной командой.

Порядок (каждый шаг — существующий инструмент):
    1. build_book.py  — распознанный текст -> книга (со страницами как в PDF);
    2. merge_books.py — «Namaz kitaby» + продолжение = одна книга;
    3. fix_text.py    — вычитка (аккуратная, проверенные правила);
    4. build_akyda.py — книга «Akyda» из PDF с текстовым слоем;
    5. make_quotes.py — «Günüň sözi» дословно из уже вычитанного текста.

Важно: шаг «--correct» (словарный корректор tools/tkcorrect.py) здесь НЕ
используется. Он менял правильные слова на неправильные («gelen» -> «geleň»,
«süresini» -> «širesini»), поэтому в сборке его нет.

    python3 tools/content/rebuild.py [--dry]
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TOOLS = os.path.join(ROOT, "tools")
CONTENT = os.path.join(ROOT, "tools", "content")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
PY = sys.executable


def run(args, dry=False):
    print("$", " ".join(os.path.basename(a) if i == 0 else a for i, a in enumerate(args)))
    if dry:
        return 0
    return subprocess.call(args, cwd=ROOT)


def make_meta(ocr_path, work, book, dry=False):
    """build_book.py ждёт meta.json: имена страниц в порядке чтения."""
    d = json.load(open(ocr_path, encoding="utf-8"))
    pages = [{"name": p["name"], "spread": i + 1, "side": p["name"][-1]}
             for i, p in enumerate(d["pages"])]
    if dry:
        return
    os.makedirs(work, exist_ok=True)
    with open(os.path.join(work, "meta.json"), "w", encoding="utf-8") as fh:
        json.dump({"book": book, "pages": pages}, fh, ensure_ascii=False)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args()
    dry = a.dry

    tmp = tempfile.mkdtemp(prefix="rebuild-")
    for book, ocr in (("b1", "content/ocr/b1.json"), ("b2", "content/ocr/b2.json")):
        work = os.path.join(tmp, book)
        make_meta(os.path.join(ROOT, ocr), work, book, dry)
        code = run([PY, os.path.join(CONTENT, "build_book.py"),
                    "--work", work, "--pages", work, "--ocr", os.path.join(ROOT, ocr),
                    "--assets", ASSETS, "--book", book], dry)
        if code:
            print("сборка", book, "не удалась")
            return code

    run([PY, os.path.join(CONTENT, "merge_books.py")], dry)
    run([PY, os.path.join(CONTENT, "fix_text.py"), "--all",
         os.path.join(ASSETS, "content")], dry)
    run([PY, os.path.join(CONTENT, "build_akyda.py")], dry)
    run([PY, os.path.join(CONTENT, "link_quotes.py")], dry)
    run([PY, os.path.join(CONTENT, "make_quotes.py")], dry)
    print("пересборка закончена")
    return 0


if __name__ == "__main__":
    sys.exit(main())
