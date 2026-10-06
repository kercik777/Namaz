#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Ставит рисунки (векторные иллюстрации) на места подписей «N-nji surat».

В книгах вместо фотографий стоят подписи вида «3-nji surat». Инструмент находит
такие подписи, смотрит на соседний текст (что именно делает человек) и вставляет
рядом блок с картинкой: приложение рисует её само.

    python3 tools/content/add_figs.py [--dry]
"""
import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "ill"))
import figures as F   # noqa: E402

ASSETS = "app/src/main/assets/content"
LABEL = re.compile(r"^\s*(\d{1,2})[- ]?(?:nji|njy|njy|nji)\s+surat\b", re.I)
MENTION = re.compile(r"(\d{1,2})-nji\s+suratl", re.I)

# порядок проверки: раньше — точнее
RULES = [
    ("tay_", ["teýemmüm", "toprak", "topraga", "epeli"]),
    ("abl_", ["täret", "taharat", "çaýkal", "ýuwul", "mesh", "barmaklar"]),
    ("salam", ["salam", "esselä", "essälem"]),
    ("takbir", ["tekbir", "tahrim", "gulaklary"]),
    ("ruku", ["ruku", "bili", "bil bagla", "rukuwa"]),
    ("sajda", ["sejde", "süjüt", "sežde"]),
    ("sit", ["otur", "tahyý", "täşehhüt", "oturyl"]),
    ("dua", ["doga", "dogalar", "elleri açyp"]),
    ("niet", ["niýet", "niet"]),
    ("stand", ["kyýam", "dik duru", "duruş", "dik"]),
]

# уточнения по ключевым словам действия
PARTS = [
    ("mouth", ["agzy", "agza", "agyz"]),
    ("nose", ["burun", "burna", "burny"]),
    ("ear", ["gulak"]),
    ("foot", ["aýak", "aýagy", "aýaklary"]),
    ("head", ["baş", "başa", "kellä"]),
    ("face", ["ýüz", "ýüzi", "ýüze"]),
    ("arm", ["gol", "goly", "tirsege", "tirsег", "elle"]),
    ("hands", ["el", "eller", "elleri", "elini", "owuj", "owuja"]),
    ("knees", ["dyz", "dyzlaryna"]),
]


# Точные таблицы: книга -> список разделов (по порядку), внутри раздела номер -> рисунок.
# Так рисунки соответствуют именно тому, что показано в печатном издании.
# Точные таблицы: книга -> разделы по порядку, внутри раздела «номер рисунка -> иллюстрация».
# Номера проверены по печатному изданию (страницы «N-nji surat»).
SECTIONS = {
    "namaz_kitaby": [
        {1: "pose_niet_m", 2: "abl_hands", 3: "abl_hands", 4: "abl_mouth", 5: "abl_nose",
         6: "abl_nose", 7: "abl_face", 8: "abl_arm", 9: "abl_arm", 10: "abl_arm",
         11: "abl_head", 12: "abl_head", 13: "abl_ear", 14: "abl_ear", 15: "abl_dua"},
        {1: "tay_intent", 2: "tay_hands", 3: "tay_hands", 4: "tay_face", 5: "tay_hands",
         6: "tay_arm", 7: "tay_arm"},
        {1: "pose_niet_m", 2: "pose_niet_w", 3: "pose_takbir_m", 4: "pose_takbir_w",
         5: "pose_stand_m", 6: "pose_stand_w", 7: "pose_ruku_m", 8: "pose_ruku_w",
         9: "pose_sajda_m", 10: "pose_sajda_w", 11: "pose_sit_m", 12: "pose_sit_w",
         13: "pose_sit_m", 14: "pose_sit_w", 15: "pose_salam_r", 16: "pose_salam_r_w",
         17: "pose_salam_l", 18: "pose_salam_l_w"},
    ],
    "namaz": [
        {19: "pose_dua_m", 20: "pose_dua_w"},
    ],
}

def gender_of(text):
    return "w" if re.search(r"aýal", text, re.I) else "m"


def pick(text, hint=""):
    """Подбирает имя иллюстрации по тексту рядом с подписью."""
    t = (text + " " + hint).lower()
    base = None
    for name, keys in RULES:
        if any(k in t for k in keys):
            base = name
            break
    if base is None:
        base = "stand"
    if base == "tay_":
        part = "intent"
        for p, keys in PARTS:
            if any(k in t for k in keys):
                part = {"hands": "hands", "face": "face", "arm": "arm", "head": "face",
                        "ear": "arm"}.get(p, "intent")
                break
        return "tay_" + part
    if base == "abl_":
        part = "hands"
        for p, keys in PARTS:
            if any(k in t for k in keys):
                part = {"head": "head", "ear": "ear", "face": "face", "arm": "arm",
                        "foot": "foot", "mouth": "mouth", "nose": "nose"}.get(p, "hands")
                break
        return "abl_" + part
    if base == "niet":
        return "pose_niet_" + gender_of(t)
    if base == "stand":
        return "pose_stand_" + gender_of(t)
    if base == "takbir":
        return "pose_takbir_" + gender_of(t)
    if base == "ruku":
        return "pose_ruku_" + gender_of(t)
    if base == "sajda":
        return "pose_sajda_" + gender_of(t)
    if base == "sit":
        return "pose_sit_" + gender_of(t)
    if base == "dua":
        return "pose_dua_" + gender_of(t)
    if base == "salam":
        return "pose_salam_" + ("r" if "sag" in t or "saga" in t else "l")
    return "pose_stand_" + gender_of(t)


def label_of(block):
    """Если блок — подпись «N-nji surat», вернуть (номер, текст подписи)."""
    if block.get("t") not in ("p", "note", "center", "h2"):
        return None
    x = (block.get("x") or "").strip()
    if len(x) > 60:
        return None
    m = LABEL.match(x)
    if not m:
        return None
    return int(m.group(1)), x


def labels_in(block):
    """Подписи «N-nji surat»: блок должен начинаться с подписи (их может быть две)."""
    if block.get("t") not in ("p", "note", "center", "h2"):
        return []
    x = (block.get("x") or "").strip()
    if not LABEL.match(x):
        return []
    return [int(m.group(1)) for m in LABEL.finditer(x)]


def split_label(text, nos):
    """Делит блок «N-nji surat + текст» на подпись рисунка и отдельный абзац."""
    t = text.strip()
    cut = 0
    for _ in nos:
        m = LABEL.match(t[cut:])
        if not m:
            break
        cut += m.end()
    rest = t[cut:].strip(" .:-—·")
    if len(rest) <= 25:
        return None, t
    return t[:cut].strip(), rest


def process(path, dry, book):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    if isinstance(data, list):
        data = data[0]
    blocks = [b for b in data.get("blocks", []) if not str(b.get("src", "")).startswith("ill:fig:")]
    figs = F.build()
    tables = SECTIONS.get(book, [])
    out = []
    added = []
    sec = -1
    last_no = 0
    for i, b in enumerate(blocks):
        nos = labels_in(b)
        if nos and b.get("x"):
            label, rest = split_label(b["x"], nos)
            if label:
                # подпись рисунка остаётся как в книге, текст продолжается отдельно
                b = dict(b)
                b["x"] = label
                out.append(b)
                cont = dict(b)
                cont["x"] = rest
                out.append(cont)
                nos = labels_in(b)
            else:
                out.append(b)
        else:
            out.append(b)
        if not nos:
            continue
        for no in nos:
            # новый раздел, когда нумерация рисунков начинается заново (с №1)
            if no == 1:
                sec += 1
                last_no = 0
            elif sec < 0:
                sec = 0
            names = []
            if 0 <= sec < len(tables):
                table = tables[sec]
                if no <= last_no:
                    continue          # подпись уже обработана (OCR отдал их не по порядку)
                for k in range(last_no + 1, no + 1):
                    if k in table:
                        names.append(table[k])
                last_no = no
            if not names:
                after = " ".join((blocks[j].get("x") or "")
                                 for j in range(i + 1, min(i + 3, len(blocks))))
                before = " ".join((blocks[j].get("x") or "") for j in range(max(0, i - 2), i))
                names = [pick((b.get("x") or "") + " " + after, before)]
            for name in names:
                if name not in figs:
                    print("   нет рисунка:", name, "—", (b.get("x") or "")[:40])
                    continue
                out.append({"t": "img", "src": "ill:fig:" + name, "cap": figs[name][0]})
                added.append((no, name))
    data["blocks"] = out
    if not dry:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    return added


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args()
    for book in ("namaz_kitaby", "namaz", "akyda"):
        path = os.path.join(ASSETS, book + ".json")
        if not os.path.exists(path):
            continue
        added = process(path, a.dry, book)
        print("%-14s рисунков: %d" % (book, len(added)))
        for no, name in added:
            print("     %2d-nji surat -> %s" % (no, name))


if __name__ == "__main__":
    main()
