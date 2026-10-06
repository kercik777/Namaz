#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Расставляет в книгах собственные векторные иллюстрации приложения (Ill.java).

Картинки из PDF не используются совсем: в блоки добавляется src = "ill:<имя>",
а приложение рисует их само (см. Ill.java). Инструмент идемпотентен.

    python3 tools/content/add_ills.py                 # все книги
    python3 tools/content/add_ills.py --dry           # только показать план
"""
import argparse
import json
import os

ASSETS = "app/src/main/assets/content"

# (книга, подстрока заголовка, иллюстрация, позиция)
PLAN = [
    # --- Namaz kitaby ---
    ("namaz_kitaby", "SÖZBAŞY", "mosque", "before"),
    ("namaz_kitaby", "IMAN-YNANÇ ESASLARY", "crescent", "after"),
    ("namaz_kitaby", "PERIŞDELERE IMAN", "star", "after"),
    ("namaz_kitaby", "KITAPLARA IMAN", "quran", "after"),
    ("namaz_kitaby", "AHYRETE IMAN", "night", "after"),
    ("namaz_kitaby", "TÄMIZLIK", "water", "after"),
    ("namaz_kitaby", "TÄRET", "ablution", "after"),
    ("namaz_kitaby", "SUSUL", "water", "after"),
    ("namaz_kitaby", "TEYEMMÜM", "water", "after"),
    ("namaz_kitaby", "Namazlaryň wagtlary", "sunset", "after"),
    ("namaz_kitaby", "Bä wagt namazyň rekagatlary", "rug", "after"),
    ("namaz_kitaby", "NAMAZYN PARZLARY", "stand", "after"),
    ("namaz_kitaby", "NAMAZYN WAJYPLARY", "ruku", "after"),
    ("namaz_kitaby", "NAMAZYÑ SÜNNETLERI", "sajda", "after"),
    ("namaz_kitaby", "NAMAZYN MUSTAHAPLARY", "tasbih", "after"),
    ("namaz_kitaby", "NAMAZYN OKALY DÜZGÜNI", "sit", "after"),
    # --- Namaz (ikinji kitap) ---
    ("namaz", "Saparda namaz okamak", "kaaba", "after"),
    ("namaz", "JUMA NAMAZY", "mosque", "after"),
    ("namaz", "TARAWA NAMAZY", "night", "after"),
    ("namaz", "JYNAZA NAMAZY", "dua", "after"),
    ("namaz", "SEPIL NAMAZLAR", "sunset", "after"),
    ("namaz", "Tehejjut", "night", "after"),
    ("namaz", "Hajat namazy", "dua", "after"),
    ("namaz", "NAMAZDA OKALÝAN DOGALAR", "dua", "after"),
    # --- Akyda ---
    ("akyda", "BIRINJI DERS", "crescent", "after"),
    ("akyda", "ÜÇÜNJI DERS", "quran", "after"),
    ("akyda", "BÄŞINJI DERS", "kaaba", "after"),
    ("akyda", "ÝEDINJI DERS", "mosque", "after"),
    ("akyda", "DOKUZYNJY DERS", "star", "after"),
]

CAPS = {
    "mosque": "Metjit — Yslamyň mukaddes jaýy",
    "kaaba": "Kâbe — musulmanlaryň kyblasy",
    "quran": "Gurhany Kerim",
    "rug": "Namazlyk (jigaz)",
    "crescent": "Iman — kalbyň nury",
    "ablution": "Täret almak",
    "tasbih": "Tesbih — Allany ýatlamak",
    "stand": "Kyýam — namazda duruş",
    "ruku": "Ruku — bil baglamak",
    "sajda": "Süjüt — ýere baş goýmak",
    "sit": "Otyryş — namazyň bir bölegi",
    "takbir": "Tekbir — namaza başlamak",
    "dua": "Doga — Alla ýalbarmak",
    "minaret": "Minara — azanyň sesi",
    "sunset": "Gün ýaşanda — agşam namazynyň wagty",
    "night": "Gije — humarly wagt",
    "water": "Arassa suw — täretiň şerti",
    "star": "Ýyldyzlar — Gijäniň yşygy",
}


def cap(ill):
    return CAPS.get(ill, "")


def apply_book(path, plan, dry):
    """Возвращает (сколько добавлено, список ненайденных правил)."""
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    if isinstance(data, list):
        data = data[0]
    blocks = data.get("blocks", [])
    has_ill = any(str(b.get("src", "")).startswith("ill:") for b in blocks)
    # прошлые иллюстрации пересобираем заново — план всегда источник истины
    blocks = [b for b in blocks if not str(b.get("src", "")).startswith("ill:")]
    out = []
    used = set()          # ключ — подстрока заголовка
    added = 0

    def hit(b, needle):
        return (needle.lower() in (b.get("x") or "").lower()
                and b.get("t") in ("h1", "h2", "title", "center"))

    for b in blocks:
        for _book, needle, ill, where in plan:
            if needle in used or where != "before":
                continue
            if hit(b, needle):
                out.append(img_block(ill))
                used.add(needle)
                added += 1
        out.append(b)
        for _book, needle, ill, where in plan:
            if needle in used or where != "after":
                continue
            if hit(b, needle):
                out.append(img_block(ill))
                used.add(needle)
                added += 1

    missed = [p[1] for p in plan if p[1] not in used]
    data["blocks"] = out
    if not dry:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    return added, missed, has_ill


def img_block(ill):
    return {"t": "img", "src": "ill:" + ill, "cap": cap(ill)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args()
    for book in ("namaz_kitaby", "namaz", "akyda"):
        path = os.path.join(ASSETS, book + ".json")
        plan = [p for p in PLAN if p[0] == book]
        added, missed, was = apply_book(path, plan, a.dry)
        print("%-14s добавлено иллюстраций: %d%s%s" % (book, added,
              "  (были и раньше)" if was and not added else "",
              ("   не найдено: " + ", ".join(missed)) if missed else ""))


if __name__ == "__main__":
    main()
