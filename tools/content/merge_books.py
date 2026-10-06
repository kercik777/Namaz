#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Собирает «Namaz kitaby» из двух распознанных частей в одну книгу.

Вторая книга (арабица и кириллица) — продолжение первой: рисунки в ней
продолжают нумерацию (19, 20). Поэтому она становится вторым разделом,
а не отдельной книгой. Заодно чистится оглавление от мусора распознавания.

    python3 tools/content/merge_books.py [--dry]
"""
import argparse
import json
import os
import re

ASSETS = "app/src/main/assets/content"
DIC = os.path.join(os.path.dirname(__file__), "..", "data", "tk_TM.dic")
VOWELS = "aäeioöuüyýaeiouАЕЁИОУЫЭЮЯаеёиоуыэюя"

PART2_TITLE = "IKINJI KITAP — dogalar we süreler"


def tokens(text):
    return [t for t in re.split(r"[\s\W_]+", text, flags=re.UNICODE) if t]


def load_dictionary():
    words = set()
    try:
        with open(DIC, encoding="utf-8", errors="ignore") as f:
            for line in f:
                w = line.strip().split("/")[0].strip().lower()
                if len(w) >= 3:
                    words.add(w)
    except Exception as e:  # noqa: BLE001
        print("   словарь не прочитан:", e)
    return words


def body_vocabulary(blocks):
    """Слова, которые реально встречаются в тексте книг (чаще двух раз)."""
    from collections import Counter
    cnt = Counter()
    for b in blocks:
        text = (b.get("x") or "") + " " + " ".join(b.get("items") or [])
        for w in tokens(text):
            if len(w) >= 4:
                cnt[w.lower()] += 1
    return {w for w, c in cnt.items() if c >= 2}


def good_heading(text, dic=None, vocab=None):
    """Годится ли строка в оглавление (или это мусор распознавания)."""
    t = (text or "").strip()
    if len(t) < 3 or len(t) > 90:
        return False
    if t[0] in ",.;:!?«»-—–":
        return False                       # обрывок строки
    low = t.lower()
    if re.search(r"[a-zçäňöşüýž]{1,2}\d|\d[a-zçäňöşüýž]{1,2}", low):
        return False                       # «ilk6», «ga1g»
    if re.search(r"[^\w\s\.\,\-\—\:\;\?\(\)\«\»\/ʼ'’İı]", t, flags=re.UNICODE):
        return False                       # случайные знаки
    if re.search(r"\w:", t):
        return False                       # транслитерация: «mä:», «halfehum. Welä:»
    toks = tokens(t)
    if not toks:
        return False
    keep_short = {"i", "v", "a", "we", "hem", "ýa", "w", "ýe", "bir", "su", "en", "el"}
    shorts = [w for w in toks if len(w) <= 2 and w.lower() not in keep_short]
    if len(shorts) > len(toks) * 0.4:
        return False
    solid = [w for w in toks if len(w) >= 3 and any(c in VOWELS for c in w.lower())]
    if len(solid) < max(1, (len(toks) + 1) // 2):
        return False
    # заголовок — не целая фраза из текста
    if len(toks) >= 8:
        return False
    if len(toks) >= 5 and re.search(r"\w+,\s+\w+", t):
        return False
    if t[0].islower() and len(toks) >= 4:
        return False
    if len(toks) >= 4 and (re.search(r"\w,\s*$", t) or t.rstrip().endswith(":")):
        return False
    # проверка по словарю и по самому тексту книг
    if dic and vocab:
        words = [w.lower() for w in toks if len(w) >= 3]
        if words:
            known = 0
            for w in words:
                if w in dic or w in vocab:
                    known += 1
                    continue
                stem = w.rstrip("larlerdan")
                if len(stem) >= 4 and (stem in dic or stem in vocab):
                    known += 1
            if known == 0:
                # слова нет в словаре — пропускаем только «заголовочные» строки
                looks_like_title = (len(toks) <= 4
                                    and all(any(c in VOWELS for c in w.lower()) for w in toks)
                                    and (t.isupper() or t[0].isdigit()))
                if not looks_like_title:
                    return False
    return True


def merge(kitaby, second, dry=False):
    blocks = list(kitaby.get("blocks", []))
    toc = []
    dic = load_dictionary()
    vocab = body_vocabulary(list(kitaby.get("blocks", [])) + list(second.get("blocks", [])))
    print("   словарь: %d слов, словарь книги: %d слов" % (len(dic), len(vocab)))

    # оглавление первой части
    for t in kitaby.get("toc", []):
        if good_heading(t.get("x", ""), dic, vocab):
            toc.append({"level": t.get("level", 1), "x": t["x"].strip(), "b": t.get("b", 0)})
        else:
            print("   мусор в оглавлении 1: %r" % t.get("x", "")[:60])

    # разделитель второй части
    offset = len(blocks)
    blocks.append({"t": "h1", "x": PART2_TITLE})
    blocks.append({"t": "center", "x": "Namazyň okalyş düzgüni we dogalar"})
    toc.append({"level": 0, "x": PART2_TITLE, "b": offset})

    # блоки второй книги без её заголовка и подзаголовка
    body = [b for b in second.get("blocks", [])
            if b.get("t") not in ("title", "center") or (b.get("x") or "").strip().lower().startswith("namaz kitaby")]
    skip = 0
    for b in second.get("blocks", []):
        if b.get("t") == "title" or (b.get("t") == "center" and skip == 0):
            skip += 1
            continue
        break
    body = second.get("blocks", [])[skip:]

    base = len(blocks)
    blocks.extend(body)

    for t in second.get("toc", []):
        if not good_heading(t.get("x", ""), dic, vocab):
            print("   мусор в оглавлении 2: %r" % t.get("x", "")[:60])
            continue
        b = t.get("b", 0)
        if b < len(second.get("blocks", [])):
            pass
        toc.append({"level": max(1, t.get("level", 2)), "x": t["x"].strip(), "b": base + b})

    title = dict(kitaby.get("title", {}))
    title["tk"] = "Namaz kitaby"
    title["ru"] = "Намаз китабы"
    sub = {"tk": "Doly tekst: birinji we ikinji bölüm",
           "ru": "Полный текст: первая и вторая части"}
    out = {
        "v": 1,
        "id": kitaby.get("id", "namaz_kitaby"),
        "script": "latin",
        "accent": kitaby.get("accent", 0xFF0B3B2C),
        "title": title,
        "subtitle": sub,
        "author": kitaby.get("author", {"tk": "Türkmenistanyň Müftüsiniň Müdirligi",
                                        "ru": "Управление муфтия Туркменистана"}),
        "edition": kitaby.get("edition", {"tk": "Aşgabat, 2016", "ru": "Ашхабад, 2016"}),
        "blocks": blocks,
        "toc": toc,
    }
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args()
    p1 = os.path.join(ASSETS, "namaz_kitaby.json")
    p2 = os.path.join(ASSETS, "namaz.json")
    if not (os.path.exists(p1) and os.path.exists(p2)):
        print("нет второго тома — объединять нечего")
        return
    with open(p1, encoding="utf-8") as f:
        kitaby = json.load(f)
    with open(p2, encoding="utf-8") as f:
        second = json.load(f)
    if isinstance(kitaby, list):
        kitaby = kitaby[0]
    if isinstance(second, list):
        second = second[0]

    out = merge(kitaby, second, a.dry)
    print("было: %d + %d блоков, стало %d; пунктов оглавления %d"
          % (len(kitaby.get("blocks", [])), len(second.get("blocks", [])),
             len(out["blocks"]), len(out["toc"])))
    if not a.dry:
        with open(p1, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
        os.remove(p2)
        print("готово:", p1)


if __name__ == "__main__":
    main()
