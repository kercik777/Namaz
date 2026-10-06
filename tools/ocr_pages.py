# -*- coding: utf-8 -*-
"""
Шаг 2. Распознавание текста на страницах книг.

Для каждой страницы:
  * находит строки (детекция + распознавание RapidOCR);
  * собирает строки в абзацы с учётом отступов и межстрочных промежутков;
  * склеивает переносы слов («namaz-» + «lary» -> «namazlary»);
  * отличает заголовки от основного текста, отделяет сноски и колонтитулы;
  * помечает строки, распознанные с низкой уверенностью или содержащие
    арабскую графику (их показываем в режиме «Оригинал»);
  * сохраняет JSON на страницу: blocks[{type, text}].

Запуск:
    python3 tools/ocr_pages.py --dir /tmp/work/raw/b1 --out /tmp/work/text/b1 \
        --first 1 --last 92 --workers 2
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys
from concurrent.futures import ProcessPoolExecutor

import numpy as np

ALLOWED = set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
              "äňşýöüžçÄŇŞÝÖÜŽÇ"
              "0123456789 .,;:!?«»\"'()[]{}-–—/\\|+=*%&@#№°'’`~^<>_$€…"
              "⁰¹²³⁴⁵⁶⁷⁸⁹хХ")
SUPERSCRIPTS = {"⁰": "0", "¹": "1", "²": "2", "³": "3", "⁴": "4",
                "⁵": "5", "⁶": "6", "⁷": "7", "⁸": "8", "⁹": "9"}
GARBAGE_CHARS = set("《》〈〉「」【】†‡§¶ΩΔΣπµ×÷±∞≈≠≤≥√∫∑∏")

PAGE_NUM_RE = re.compile(r"^\s*[©(]?\s*\d{1,3}\s*[)]?\s*$")
LIST_RE = re.compile(r"^\s*(\d{1,3}|[a-zа-я])\s*[.)]\s+")
FOOTNOTE_RE = re.compile(r"^\s*\d{1,2}\s+[A-ZА-ЯÄŇŞÝÖÜŽ]")

TR = str.maketrans({"ä": "a", "ň": "n", "ş": "s", "ý": "y", "ö": "o", "ü": "u",
                    "ž": "z", "ç": "c", "Ä": "A", "Ň": "N", "Ş": "S", "Ý": "Y",
                    "Ö": "O", "Ü": "U", "Ž": "Z", "Ç": "C"})


def sanitize_text(text: str) -> str:
    """Убирает из строки «мусорные» токены (обычно это арабская вязь,
    которую модель не умеет читать) и нормализует пробелы."""
    if not text:
        return text
    text = "".join(GARBAGE_CHARS_LOOKUP.get(ch, ch) for ch in text)
    out = []
    for token in text.split(" "):
        if not token:
            continue
        allowed = sum(1 for ch in token if ch in ALLOWED)
        if allowed == 0:
            continue
        if allowed / len(token) < 0.6 and len(token) > 2:
            continue
        out.append(token)
    res = " ".join(out)
    res = re.sub(r"\s+", " ", res).strip()
    res = re.sub(r"\s+([,.;:!?»)\]])", r"\1", res)
    res = re.sub(r"([«(\[])\s+", r"\1", res)
    return res


GARBAGE_CHARS_LOOKUP = {ch: "…" for ch in GARBAGE_CHARS}


def clean_line(text: str) -> str:
    text = text.replace("|", "I").replace("¦", "I")
    text = text.replace("ﬁ", "fi").replace("ﬂ", "fl")
    text = re.sub(r"\s+", " ", text).strip()
    return text


def garbage_ratio(text: str) -> float:
    if not text:
        return 1.0
    bad = sum(1 for ch in text if ch not in ALLOWED)
    return bad / len(text)


class OcrEngine:
    """Ленивая инициализация RapidOCR (PP-OCRv6) внутри рабочего процесса.

    Модели PP-OCRv6 знают туркменские буквы (ä, ň, ş, ý, ö, ü), поэтому
    диакритика распознаётся сразу -- без последующего «дописывания».
    """

    _engine = None

    @classmethod
    def get(cls):
        if cls._engine is None:
            from rapidocr import RapidOCR
            cls._engine = RapidOCR(params={
                "Global.text_score": 0.40,
                "Global.log_level": "error",
                "Det.limit_side_len": 1280,
                "Det.limit_type": "min",
                "Det.unclip_ratio": 1.7,
            })
        return cls._engine


def detect_lines(img: np.ndarray, min_conf: float = 0.42):
    engine = OcrEngine.get()
    result = engine(img)
    lines = []
    boxes = result.boxes if result is not None else None
    texts = result.txts if result is not None else None
    scores = result.scores if result is not None else None
    if boxes is None or texts is None:
        return lines
    for box, text, conf in zip(boxes, texts, scores if scores is not None else [1.0] * len(texts)):
        conf = float(conf)
        if conf < min_conf:
            continue
        text = clean_line(text)
        if not text:
            continue
        xs = [p[0] for p in box]
        ys = [p[1] for p in box]
        lines.append({
            "text": text,
            "conf": round(conf, 3),
            "x0": float(min(xs)), "x1": float(max(xs)),
            "y0": float(min(ys)), "y1": float(max(ys)),
            "h": float(max(ys) - min(ys)),
            "cx": float((min(xs) + max(xs)) / 2.0),
        })
    lines.sort(key=lambda l: (l["y0"], l["x0"]))
    return lines


def merge_multiline(lines):
    """RapidOCR иногда разбивает одну строку на несколько боксов по горизонтали."""
    merged = []
    for ln in lines:
        if merged:
            prev = merged[-1]
            same_row = abs(ln["y0"] - prev["y0"]) < max(8.0, prev["h"] * 0.55)
            gap = ln["x0"] - prev["x1"]
            if same_row and -2 <= gap < prev["h"] * 1.4:
                prev["text"] = (prev["text"] + " " + ln["text"]).strip()
                prev["x1"] = max(prev["x1"], ln["x1"])
                prev["y1"] = max(prev["y1"], ln["y1"])
                prev["x0"] = min(prev["x0"], ln["x0"])
                prev["h"] = prev["y1"] - prev["y0"]
                prev["cx"] = (prev["x0"] + prev["x1"]) / 2
                prev["conf"] = min(prev["conf"], ln["conf"])
                continue
        merged.append(dict(ln))
    return merged


def median(values, default=0.0):
    if not values:
        return default
    s = sorted(values)
    return s[len(s) // 2]


def build_blocks(lines, page_width: float, page_height: float, min_conf: float):
    if not lines:
        return []

    body_h = median([l["h"] for l in lines if len(l["text"]) > 24], median([l["h"] for l in lines]))
    gaps = []
    for a, b in zip(lines, lines[1:]):
        gaps.append(b["y0"] - a["y1"])
    body_gap = median([g for g in gaps if g > 0], body_h * 0.4)

    left_edge = median([l["x0"] for l in lines if len(l["text"]) > 24], 0.0)
    right_edge = median([l["x1"] for l in lines if len(l["text"]) > 24], page_width)
    center = page_width / 2.0

    blocks = []
    current = None

    def is_footnote(ln, idx):
        # сноска -- у нижнего края страницы и заметно мельче основного текста
        near_bottom = ln["y0"] > page_height * 0.80
        small = ln["h"] <= body_h * 0.88
        return near_bottom and small

    def heading_level(ln):
        big = ln["h"] >= body_h * 1.16
        short = len(ln["text"]) <= 48
        centered = abs(ln["cx"] - center) < page_width * 0.10
        allcaps = ln["text"].upper() == ln["text"] and len(ln["text"]) > 2
        if (big and short) or (centered and short and (big or allcaps)):
            return 1 if ln["h"] >= body_h * 1.32 else 2
        return 0

    for i, ln in enumerate(lines):
        text = ln["text"]
        if PAGE_NUM_RE.match(text) and ln["y0"] > page_height * 0.9:
            continue
        if garbage_ratio(text) > 0.2:
            new_type = "arabic"
        elif is_footnote(ln, i):
            new_type = "footnote"
        else:
            new_type = None

        text = sanitize_text(text)
        if not text:
            continue

        if new_type is None:
            level = heading_level(ln)
            if level:
                new_type = "h%d" % level
            elif LIST_RE.match(text):
                new_type = "li"
            else:
                new_type = "p"

        if current is None:
            current = {"type": new_type, "text": text, "y0": ln["y0"], "conf": ln["conf"]}
            continue

        prev_y1 = current["_y1"] if "_y1" in current else current["y0"]
        gap = ln["y0"] - prev_y1
        indented = ln["x0"] > left_edge + max(6.0, (right_edge - left_edge) * 0.03)
        prev_ends_hyphen = current["text"].endswith("-") and len(current["text"]) > 2
        same_kind = (current["type"] == new_type)
        new_para = (
            new_type in ("h1", "h2", "footnote", "arabic", "li")
            or current["type"] in ("h1", "h2", "footnote", "arabic")
            or gap > body_gap * 1.5
            or (indented and not prev_ends_hyphen)
            or not same_kind
        )

        if new_para:
            current.pop("_y1", None)
            blocks.append(current)
            current = {"type": new_type, "text": text, "y0": ln["y0"], "conf": ln["conf"]}
        else:
            if prev_ends_hyphen:
                current["text"] = current["text"][:-1] + text.lstrip()
            else:
                current["text"] = current["text"] + " " + text
            current["conf"] = min(current["conf"], ln["conf"])
        current["_y1"] = ln["y1"]

    if current is not None:
        current.pop("_y1", None)
        blocks.append(current)

    blocks = join_hyphenated(blocks)

    # убираем остатки колонтитулов и пустые блоки
    clean = []
    for b in blocks:
        t = b["text"].strip()
        if not t:
            continue
        if len(t) <= 3 and PAGE_NUM_RE.match(t):
            continue
        b["text"] = t
        clean.append(b)
    return clean


_DICT = None


def dictionary_words():
    """Набор слов туркменского словаря -- нужен, чтобы отличать перенос слова
    ("namaz-" + "lary") от настоящего дефиса в составном слове ("parz-kyfaya")."""
    global _DICT
    if _DICT is None:
        _DICT = set()
        base = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
        dic = os.path.join(base, "tk_TM.dic")
        if os.path.exists(dic):
            with open(dic, encoding="utf-8", errors="ignore") as fh:
                fh.readline()
                for line in fh:
                    word = line.split("/")[0].strip().translate(TR).lower()
                    if word and word.isalpha():
                        _DICT.add(word)
    return _DICT


def join_hyphenated(blocks):
    """Склеивает блоки, если первый кончается переносом слова."""
    words = dictionary_words()
    out = []
    for b in blocks:
        text = b["text"].rstrip()
        if (out and text and out[-1]["text"].endswith("-")
                and text[:1].islower() and out[-1]["type"] == b["type"]):
            prev_text = out[-1]["text"][:-1]
            tail_match = re.match(r"^([A-Za-zäňşýöüžçÄŇŞÝÖÜŽÇ]+)", text)
            tail = tail_match.group(1).lower() if tail_match else ""
            head_match = re.search(r"([A-Za-zäňşýöüžçÄŇŞÝÖÜŽÇ]+)$", prev_text)
            head = head_match.group(1).lower() if head_match else ""
            joined_word = head + tail
            tail_is_word = tail in words and len(tail) > 2
            joined_is_word = joined_word in words
            if words and (joined_is_word or not tail_is_word):
                out[-1]["text"] = prev_text + text
                continue
        out.append(b)
    return out


def process_page(path: str, out_path: str, dpi_scale: float = 1.0, min_conf: float = 0.42):
    import pymupdf  # noqa: F401  (не используется, оставлено для совместимости импорта)
    from PIL import Image
    img = Image.open(path)
    arr = np.asarray(img.convert("RGB"))
    lines = merge_multiline(detect_lines(arr, min_conf))
    blocks = build_blocks(lines, float(arr.shape[1]), float(arr.shape[0]), min_conf)
    data = {
        "page": os.path.splitext(os.path.basename(path))[0],
        "width": int(arr.shape[1]),
        "height": int(arr.shape[0]),
        "blocks": blocks,
        "low_conf": [l["text"] for l in lines if l["conf"] < 0.6][:20],
    }
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=1)
    return len(blocks)


def worker(args):
    path, out_dir, min_conf = args
    name = os.path.splitext(os.path.basename(path))[0]
    out_path = os.path.join(out_dir, name + ".json")
    if os.path.exists(out_path):
        return name + " (уже готово)"
    try:
        n = process_page(path, out_path, min_conf=min_conf)
        return "%s: %d блоков" % (name, n)
    except Exception as exc:  # noqa: BLE001
        return "%s: ОШИБКА %s" % (name, exc)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", required=True, help="папка с JPG страницами")
    ap.add_argument("--out", required=True, help="папка для JSON")
    ap.add_argument("--workers", type=int, default=2)
    ap.add_argument("--min-conf", type=float, default=0.42)
    args = ap.parse_args(argv)

    os.makedirs(args.out, exist_ok=True)
    files = sorted(glob.glob(os.path.join(args.dir, "*.jpg")))
    if not files:
        print("нет файлов в " + args.dir, file=sys.stderr)
        return 1
    tasks = [(f, args.out, args.min_conf) for f in files]
    with ProcessPoolExecutor(max_workers=args.workers) as pool:
        for i, res in enumerate(pool.map(worker, tasks), 1):
            print("[%d/%d] %s" % (i, len(tasks), res), file=sys.stderr, flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
