#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Распознавание сканов книг (запускается в GitHub Actions, где есть интернет
для загрузки мультиязычных моделей PP-OCRv5: латиница, кириллица, арабица).

Вход:  1Namaz_compressed.pdf, 2Namaz 2_compressed.pdf (в корне репозитория)
Выход: content/ocr/book1.json, content/ocr/book2.json
       content/ocr/book1_pages/*.jpg (уменьшенные копии страниц — для сверки)

Каждая страница PDF — это разворот из двух книжных страниц,
поэтому половины распознаются отдельно.
"""
import json
import os
import re
import sys
import time

import numpy as np
import pymupdf

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT_DIR = os.path.join(ROOT, "content", "ocr")

BOOKS = [
    ("book1", "1Namaz_compressed.pdf", "latin"),
    ("book2", "2Namaz 2_compressed.pdf", "mixed"),
]

DPI = 220
ARABIC = re.compile(r"[\u0600-\u06FF\u0750-\u077F\uFB50-\uFDFF\uFE70-\uFEFF]")
LATIN_TK = re.compile(r"[äňşýüöžÄŇŞÝÜÖŽ]")
CYRILLIC = re.compile(r"[\u0400-\u04FF]")
PAGE_NUM = re.compile(r"^\s*[0-9]{1,3}\s*$")


def make_engines():
    from rapidocr import RapidOCR

    def engine(lang):
        params = {
            "Global.text_score": 0.30,
            "Global.use_cls": False,
            "Det.lang_type": "ch",
            "Det.model_type": "mobile",
            "Det.ocr_version": "PP-OCRv5",
            "Rec.lang_type": lang,
            "Rec.model_type": "mobile",
            "Rec.ocr_version": "PP-OCRv5",
            "EngineConfig.onnxruntime.use_cuda": False,
        }
        return RapidOCR(params=params)

    engines = {}
    for lang in ("latin", "cyrillic", "arabic"):
        started = time.time()
        engines[lang] = engine(lang)
        print(f"  модель {lang} готова за {time.time() - started:.1f} с", flush=True)
    return engines


def run_engine(engine, img):
    try:
        res = engine(img)
    except TypeError:
        res = engine(img, use_det=True, use_cls=False, use_rec=True)
    if res is None:
        return []
    txts = getattr(res, "txts", None)
    scores = getattr(res, "scores", None)
    boxes = getattr(res, "boxes", None)
    if txts is None and isinstance(res, (list, tuple)) and res:
        first = res[0]
        txts = [r[1] for r in first] if first else []
        scores = [r[2] for r in first] if first else []
        boxes = [r[0] for r in first] if first else []
    if txts is None:
        return []
    out = []
    for i, t in enumerate(txts):
        if t is None:
            continue
        box = None
        if boxes is not None and i < len(boxes) and boxes[i] is not None:
            box = np.asarray(boxes[i]).astype(float).tolist()
        sc = float(scores[i]) if scores is not None and i < len(scores) and scores[i] is not None else 0.0
        out.append({"box": box, "text": t, "score": sc})
    return out


def pick_best(cands):
    """Выбирает лучший вариант строки среди трёх языковых моделей."""
    best = None
    for lang, item in cands.items():
        text = (item.get("text") or "").strip()
        if not text:
            continue
        score = item.get("score", 0.0)
        # арабская вязь: у арабицы приоритет, если уверенность близка к лучшей
        if lang == "arabic" and ARABIC.search(text):
            score += 0.22
        if best is None or score > best[0]:
            best = (score, lang, text, item.get("score", 0.0), item.get("box"))
    if best is None:
        return None
    return {"text": best[2], "lang": best[1], "score": round(best[3], 3), "box": best[4]}


def main() -> int:
    os.makedirs(OUT_DIR, exist_ok=True)
    engines = make_engines()

    for book_id, pdf_name, kind in BOOKS:
        pdf_path = os.path.join(ROOT, pdf_name)
        if not os.path.exists(pdf_path):
            print("нет файла", pdf_path, flush=True)
            continue
        doc = pymupdf.open(pdf_path)
        pages_out = []
        pages_dir = os.path.join(OUT_DIR, book_id + "_pages")
        os.makedirs(pages_dir, exist_ok=True)
        started = time.time()
        for pno in range(doc.page_count):
            page = doc[pno]
            pix = page.get_pixmap(dpi=DPI)
            img = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)
            if pix.n >= 3:
                img = img[:, :, :3]
            h, w = img.shape[:2]
            halves = [("L", img[:, : w // 2]), ("R", img[:, w // 2:])]
            entry = {"spread": pno + 1, "halves": []}
            for side, half in halves:
                cands = {}
                for lang, eng in engines.items():
                    lines = run_engine(eng, np.ascontiguousarray(half))
                    cands[lang] = lines
                n = max((len(v) for v in cands.values()), default=0)
                merged = []
                for i in range(n):
                    per_lang = {}
                    for lang, lines in cands.items():
                        if i < len(lines):
                            per_lang[lang] = lines[i]
                    best = pick_best(per_lang)
                    if best is not None:
                        merged.append(best)
                entry["halves"].append({"side": side, "lines": merged})
                # уменьшенная копия страницы для сверки
                try:
                    from PIL import Image
                    im = Image.fromarray(half)
                    im.thumbnail((1500, 1500))
                    im.save(os.path.join(pages_dir, f"{pno + 1:03d}{side}.jpg"), quality=72)
                except Exception as e:
                    print("  предупреждение: не удалось сохранить превью:", e, flush=True)
            pages_out.append(entry)
            print(f"[{book_id}] разворот {pno + 1}/{doc.page_count} "
                  f"({time.time() - started:.0f} с)", flush=True)
        doc.close()
        out_path = os.path.join(OUT_DIR, book_id + ".json")
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump({"book": book_id, "dpi": DPI, "pages": pages_out}, f, ensure_ascii=False)
        print("сохранено:", out_path, flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
