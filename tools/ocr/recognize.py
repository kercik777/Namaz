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


def _find_enums():
    """Ищет enum-типы RapidOCR в разных версиях библиотеки."""
    found = {}
    names = ("LangDet", "LangRec", "ModelType", "OCRVersion")
    try:
        import rapidocr
        for n in names:
            if hasattr(rapidocr, n):
                found[n] = getattr(rapidocr, n)
    except Exception:  # noqa: BLE001
        pass
    for mod in ("rapidocr.utils.typings", "rapidocr.utils.typings_enum",
                "rapidocr.utils.parse_parameters", "rapidocr.typings"):
        try:
            m = __import__(mod, fromlist=["*"])
        except Exception:  # noqa: BLE001
            continue
        for n in names:
            if n not in found and hasattr(m, n):
                found[n] = getattr(m, n)
    return found


def _pick(enum_cls, *names):
    for n in names:
        if hasattr(enum_cls, n):
            return getattr(enum_cls, n)
    return None


def _lang_value(enum_cls, lang):
    if lang == "latin":
        return _pick(enum_cls, "LATIN", "latin", "EN", "en")
    if lang == "cyrillic":
        return _pick(enum_cls, "CYRILLIC", "cyrillic", "ESLAV", "east_slavic")
    return _pick(enum_cls, "ARABIC", "arabic", "AR")


class Rec:
    """Распознавание строк: сначала пытаемся только распознавание, затем полный конвейер."""

    def __init__(self, engine, rec_only=True):
        self.engine = engine
        self.rec_only = rec_only

    def __call__(self, img):
        return self.run(img)

    def run(self, img):
        if self.rec_only:
            try:
                return self.engine(img, use_det=False, use_cls=False, use_rec=True)
            except TypeError:
                pass
            except Exception:  # noqa: BLE001
                return None
        try:
            return self.engine(img)
        except Exception:  # noqa: BLE001
            return None


def _params(kind, lang=None, enums=None, use_enums=True):
    p = {"Global.log_level": "error", "Global.text_score": 0.25}
    if kind == "det":
        p["Det.limit_side_len"] = 1600
        p["Det.limit_type"] = "min"
        p["Det.unclip_ratio"] = 1.7
    else:
        p["Global.text_score"] = 0.0
        p["Global.use_cls"] = False
        p["Rec.rec_img_shape"] = [3, 48, 320]
    if not use_enums:
        if kind == "det":
            p["Det.lang_type"] = "ch"
            p["Det.model_type"] = "mobile"
            p["Det.ocr_version"] = "PP-OCRv5"
        else:
            p["Rec.lang_type"] = lang
            p["Rec.model_type"] = "mobile"
            p["Rec.ocr_version"] = "PP-OCRv5"
        return p
    enums = enums or {}
    mt = _pick(enums.get("ModelType"), "MOBILE", "mobile") if enums.get("ModelType") else None
    ov = _pick(enums.get("OCRVersion"), "PPOCRV5", "PP_OCRv5", "v5") if enums.get("OCRVersion") else None
    if kind == "det":
        lt = _pick(enums.get("LangDet"), "CH", "ch", "MULTI", "multi") if enums.get("LangDet") else None
        if mt is not None:
            p["Det.model_type"] = mt
        if ov is not None:
            p["Det.ocr_version"] = ov
        if lt is not None:
            p["Det.lang_type"] = lt
    else:
        lt = _lang_value(enums.get("LangRec"), lang) if enums.get("LangRec") else None
        if mt is not None:
            p["Rec.model_type"] = mt
        if ov is not None:
            p["Rec.ocr_version"] = ov
        if lt is not None:
            p["Rec.lang_type"] = lt
    return p


def engines():
    """Детектор (один) + распознаватели по языкам. Подстраивается под версию RapidOCR."""
    global _ENGINES
    if _ENGINES is not None:
        return _ENGINES
    from rapidocr import RapidOCR

    enums = _find_enums()
    print("  найдены enum-типы RapidOCR:", sorted(enums.keys()), flush=True)

    def build(kind, lang=None):
        last = None
        for use_enums in (True, False):
            if use_enums and not enums:
                continue
            for rec_only in ((True, False) if kind == "rec" else (False,)):
                params = _params(kind, lang, enums, use_enums)
                if rec_only:
                    params["Global.use_det"] = False
                try:
                    eng = RapidOCR(params=params)
                    print("  модель", kind, lang or "", "готова (enums=%s, rec_only=%s)"
                          % (use_enums, rec_only), flush=True)
                    return Rec(eng, rec_only=rec_only)
                except Exception as e:  # noqa: BLE001
                    last = e
        print("  ВНИМАНИЕ: не удалось настроить", kind, lang, "->", last, flush=True)
        return None

    det = build("det")
    recs = {}
    for lang in ("latin", "cyrillic", "arabic"):
        r = build("rec", lang)
        if r is not None:
            recs[lang] = r
    if det is None:
        det = RapidOCR(params={"Global.log_level": "error"})
    if not recs:
        print("  ВНИМАНИЕ: языковые модели недоступны, используется модель по умолчанию", flush=True)
        base = RapidOCR(params={"Global.log_level": "error"})
        for lang in ("latin", "cyrillic", "arabic"):
            recs[lang] = Rec(base, rec_only=False)
    _ENGINES = (det, recs)
    return _ENGINES


def rec_text(model, img):
    if isinstance(model, Rec):
        res = model.run(img)
    else:
        res = model.run(img) if hasattr(model, "run") else model(img)
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
    lines = OP.detect_lines(img, min_conf=0.0)
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
