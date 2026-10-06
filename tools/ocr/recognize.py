#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Распознавание текста на страницах книг с выбором языковой модели.

Использует конвейер tools/ocr_pages.py (сборка строк в абзацы, заголовки,
сноски, склейка переносов), но распознаёт каждую строку тремя моделями
PP-OCRv5 (латиница / кириллица / арабица) и выбирает лучший вариант.

Запуск:
    python3 tools/ocr/recognize.py --work work/b1 --book b1 \
        --out content/ocr/b1.json --pages work/pages/b1 --workers 2
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from concurrent.futures import ProcessPoolExecutor

import numpy as np

TOOLS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, TOOLS)
import ocr_pages as OP  # noqa: E402

ARABIC = re.compile(r"[\u0600-\u06FF\u0750-\u077F\uFB50-\uFDFF\uFE70-\uFEFF]")
CYRILLIC = re.compile(r"[\u0400-\u04FF]")
LATIN = re.compile(r"[A-Za-zäňşýöüžçÄŇŞÝÖÜŽÇ]")

_ENGINES = None


def engines():
    """Детектор (один) + распознаватели по языкам."""
    global _ENGINES
    if _ENGINES is not None:
        return _ENGINES
    from rapidocr import RapidOCR

    det = RapidOCR(params={
        "Global.text_score": 0.25,
        "Global.log_level": "error",
        "Det.lang_type": "ch",
        "Det.model_type": "mobile",
        "Det.ocr_version": "PP-OCRv5",
        "Det.limit_side_len": 1600,
        "Det.limit_type": "min",
        "Det.unclip_ratio": 1.7,
        "Rec.lang_type": "latin",
        "Rec.model_type": "mobile",
        "Rec.ocr_version": "PP-OCRv5",
    })
    recs = {}
    for lang in ("latin", "cyrillic", "arabic"):
        try:
            recs[lang] = RapidOCR(params={
                "Global.text_score": 0.0,
                "Global.use_det": False,
                "Global.use_cls": False,
                "Global.log_level": "error",
                "Rec.lang_type": lang,
                "Rec.model_type": "mobile",
                "Rec.ocr_version": "PP-OCRv5",
                "Rec.rec_img_shape": [3, 48, 320],
            })
            print("  модель распознавания готова:", lang, flush=True)
        except Exception as e:  # noqa: BLE001
            print("  ВНИМАНИЕ: модель", lang, "недоступна:", e, flush=True)
    _ENGINES = (det, recs)
    return _ENGINES


def rec_text(model, img):
    try:
        res = model(img, use_det=False, use_cls=False, use_rec=True)
    except TypeError:
        res = model(img)
    except Exception:  # noqa: BLE001
        return None
    if res is None:
        return None
    txts = getattr(res, "txts", None)
    scores = getattr(res, "scores", None)
    if txts is None:
        return None
    best = None
    for i, t in enumerate(txts):
        if not t:
            continue
        sc = float(scores[i]) if scores is not None and i < len(scores) else 0.0
        if best is None or sc > best[0]:
            best = (sc, t)
    return best


def pick(crop, recs, langs):
    """Лучший вариант строки среди выбранных языковых моделей."""
    best = None
    for lang in langs:
        model = recs.get(lang)
        if model is None:
            continue
        got = rec_text(model, crop)
        if got is None:
            continue
        score, text = got
        text = OP.clean_line(text)
        if not text:
            continue
        s = score
        if lang == "arabic" and ARABIC.search(text):
            s += 0.30
        if lang == "cyrillic" and CYRILLIC.search(text):
            s += 0.22
        if lang == "latin" and LATIN.search(text) and not ARABIC.search(text):
            s += 0.10
        if best is None or s > best[0]:
            best = (s, text, score, lang)
    if best is None:
        return None
    return best[1], round(best[2], 3), best[3]


def page_script(lines):
    """Какая графика на странице — по тексту, который дала латинская модель."""
    ar = cy = 0
    total = 0
    for ln in lines:
        t = ln["text"]
        if len(t) < 2:
            continue
        total += 1
        if ARABIC.search(t):
            ar += 1
        elif CYRILLIC.search(t):
            cy += 1
    if total == 0:
        return 0.0, 0.0
    return ar / total, cy / total


def process_page(job):
    path, name, min_conf = job
    from PIL import Image

    det, recs = engines()
    img = np.array(Image.open(path).convert("RGB"))
    h, w = img.shape[:2]
    OP.OcrEngine._engine = det
    lines = OP.detect_lines(img, min_conf=0.20)
    ar_frac, cy_frac = page_script(lines)
    langs = ["latin"]
    if ar_frac > 0.15:
        langs.append("arabic")
    if cy_frac > 0.15:
        langs.append("cyrillic")
    if len(langs) == 1 and max(ar_frac, cy_frac) > 0.05:
        langs.extend(["arabic", "cyrillic"])

    for ln in lines:
        crop = img[max(0, int(ln["y0"]) - 2):int(ln["y1"]) + 2,
                   max(0, int(ln["x0"]) - 2):int(ln["x1"]) + 2]
        if crop.size == 0:
            continue
        got = pick(np.ascontiguousarray(crop), recs, langs)
        if got is not None:
            ln["text"], ln["conf"], ln["lang"] = got[0], got[1], got[2]

    lines = OP.merge_multiline([l for l in lines if l["conf"] >= min_conf])
    blocks = OP.build_blocks(lines, float(w), float(h), min_conf)
    return {
        "name": name,
        "width": int(w),
        "height": int(h),
        "arabic_frac": round(ar_frac, 3),
        "cyrillic_frac": round(cy_frac, 3),
        "blocks": [{"type": b["type"], "text": b["text"]} for b in blocks],
    }


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", required=True, help="папка с jpg и meta.json")
    ap.add_argument("--pages", help="папка с jpg (если отличается от --work)")
    ap.add_argument("--book", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--workers", type=int, default=2)
    ap.add_argument("--min-conf", type=float, default=0.35)
    ap.add_argument("--limit", type=int, default=0, help="обработать только N страниц (отладка)")
    args = ap.parse_args(argv)

    meta_path = os.path.join(args.work, "meta.json")
    meta = {"pages": []}
    if os.path.exists(meta_path):
        meta = json.load(open(meta_path, encoding="utf-8"))
    pages_dir = args.pages or args.work
    tasks = []
    for m in meta.get("pages", []):
        jpg = os.path.join(pages_dir, m["name"] + ".jpg")
        if os.path.exists(jpg):
            tasks.append((jpg, m["name"], args.min_conf))
    if args.limit:
        tasks = tasks[:args.limit]
    if not tasks:
        print("нет страниц для распознавания в", pages_dir, file=sys.stderr)
        return 1

    print("страниц:", len(tasks), "потоков:", args.workers, flush=True)
    engines()  # прогреваем модели в главном процессе (чтобы видеть ошибки сразу)
    out_pages = []
    done = 0
    with ProcessPoolExecutor(max_workers=max(1, args.workers)) as pool:
        for res in pool.map(process_page, tasks, chunksize=1):
            out_pages.append(res)
            done += 1
            print("[%d/%d] %s: блоков %d" % (done, len(tasks), res["name"],
                                             len(res["blocks"])), flush=True)

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump({"book": args.book, "pages": out_pages}, fh, ensure_ascii=False)
    chars = sum(len(b["text"]) for p in out_pages for b in p["blocks"])
    print("сохранено:", args.out, "страниц:", len(out_pages), "символов:", chars, flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
