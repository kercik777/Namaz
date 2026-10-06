#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Собирает «Günüň sözi» — цитаты дословно из книг, с точным местом.

Важно: цитата — это настоящая фраза из книги (символ в символ), поэтому
нажатие в приложении открывает ровно ту страницу, где она напечатана.
Для каждой цитаты записаны книга, глава, блок и страница оригинала.

    python3 tools/content/make_quotes.py [--dry]
"""
import argparse
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "content")
OUT = os.path.join(ASSETS, "quotes.json")

DIC_PATH = os.path.join(ROOT, "tools", "data", "tk_TM.dic")
BOOKS = ["akyda", "namaz_kitaby"]
COUNT = 14
MIN_LEN, MAX_LEN = 95, 210
KEYWORDS = ("Allah", "Allatagala", "pygamber", "namaz", "täret", "iman", "kalp", "kalby",
            "musulman", "ahyre", "Gurhan", "säjde", "oraza", "dogа", "doga", "ynsan",
            "sabyr", "şükür", "ybadat", "tämizlik", "halal", "haram")
BAD = re.compile(r"[0-9\[\]{}<>|_~^`\\/@#*+=]")
OK_SHORT = {"we", "hem", "bilen", "ýa", "bu", "ol", "şu", "bir", "öz", "on", "diňe", "iň",
            "üçin", "içinde", "ýok", "bar", "has", "örän", "kän", "şeýle", "diýip", "ýene",
            "soň", "öň", "öňe", "ýokary", "aşak", "sag", "çep", "iki", "bäş", "dört", "alty"}


def stray_words(s):
    """Слова из 1–2 букв вне нормального туркменского текста — след распознавания."""
    return [w for w in WORD.findall(s) if len(w) <= 2 and w.lower() not in OK_SHORT]
SENT = re.compile(r"(?<=[.!?»])\s+")
WORD = re.compile(r"[^\W\d_]+", re.UNICODE)


def dictionary():
    words = set()
    try:
        with open(DIC_PATH, encoding="utf-8", errors="ignore") as f:
            for line in f:
                w = line.split("/")[0].strip().lower()
                if len(w) >= 3:
                    words.add(w)
    except OSError:
        pass
    return words


DIC = set()


def known_ratio(s, vocab):
    """Доля слов, которые есть в словаре или в самой книге."""
    global DIC
    if not DIC:
        DIC = dictionary()
    words = [w.lower() for w in WORD.findall(s)]
    if not words:
        return 0.0
    good = 0
    for w in words:
        if w in DIC or w in vocab or w.rstrip("larlerdanym") in DIC:
            good += 1
    return good / len(words)


def sentences(text):
    text = re.sub(r"\s+", " ", text or "").strip()
    for s in SENT.split(text):
        s = s.strip()
        if s:
            yield s


def translit_heavy(s):
    """Строки арабской транслитерации («ä:heýśtehu:») в цитаты не годятся."""
    return len(re.findall(r"\w:", s)) > 1


def score(s, vocab=()):
    sc = 0
    if s.isupper():
        return -99                      # капс из «Akyda» в карточке читается плохо
    if stray_words(s):
        return -99
    ratio = known_ratio(s, vocab)
    if ratio < 0.75:
        return -99
    sc += int(ratio * 3)
    if 110 <= len(s) <= 190:
        sc += 3
    if any(k in s for k in KEYWORDS):
        sc += 3
    if s.endswith((".", "!", "?")):
        sc += 1
    if s.count("«") == s.count("»"):
        sc += 1
    if re.search(r"\b(şeýle|bolýar|borçdur|parzdyr|sünnetdir|haramdyr|halaldyr|lazymdyr)\b", s):
        sc += 2
    words = WORD.findall(s)
    if words and sum(1 for w in words if len(w) <= 3) / len(words) > 0.45:
        sc -= 3
    if s[:1].islower():
        sc -= 2
    return sc


def quotes_of(book_id):
    path = os.path.join(ASSETS, book_id + ".json")
    data = json.load(open(path, encoding="utf-8"))
    if isinstance(data, list):
        data = data[0]
    title = (data.get("title") or {}).get("tk", book_id)
    toc = data.get("toc", [])
    vocab = set()
    for b in data.get("blocks", []):
        for w in WORD.findall((b.get("x") or "") + " " + " ".join(b.get("items") or [])):
            if len(w) >= 4:
                vocab.add(w.lower())
    page = 0
    chapter = -1
    found = []
    for i, b in enumerate(data.get("blocks", [])):
        t = b.get("t")
        if t == "page":
            page = int(b.get("n", page))
            continue
        if t in ("h1", "h2"):
            for k, e in enumerate(toc):
                if e.get("b") == i:
                    chapter = k
                    break
            continue
        if t not in ("p", "q", "a", "quote"):
            continue
        if len(b.get("x", "")) < MIN_LEN:
            continue
        if translit_heavy(b.get("x", "")):
            continue
        for s in sentences(b.get("x")):
            if not (MIN_LEN <= len(s) <= MAX_LEN):
                continue
            if BAD.search(s) or translit_heavy(s):
                continue
            src = toc[chapter]["x"] if 0 <= chapter < len(toc) else title
            if len([w for w in WORD.findall(src) if len(w) <= 2]) > 2 or len(src) < 4:
                src = title
            found.append({"tk": s, "src": src,
                          "book": book_id, "chapter": max(0, chapter), "block": i, "page": page,
                          "score": score(s, vocab)})
    return found


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args()

    all_q = []
    for b in BOOKS:
        list_b = [q for q in quotes_of(b) if q["score"] > 0]
        list_b.sort(key=lambda q: (-q["score"], q["page"]))
        print("%s: подходящих фраз %d" % (b, len(list_b)))
        all_q.append(list_b)

    # берём лучшее из каждой книги и распределяем по всей книге равномерно
    out = []
    per_book = max(4, COUNT // max(1, len(BOOKS)))
    for book_id, lst in zip(BOOKS, all_q):
        if not lst:
            continue
        pages = [q["page"] for q in lst]
        lo, hi = min(pages), max(pages)
        buckets = {}
        for q in lst:
            k = int((q["page"] - lo) / max(1e-9, (hi - lo) / per_book))
            k = min(per_book - 1, k)
            if k not in buckets or q["score"] > buckets[k]["score"]:
                buckets[k] = q
        out.extend(sorted(buckets.values(), key=lambda q: q["page"]))

    for q in out:
        q.pop("score", None)
        q["ru"] = ""      # приложение только на туркменском
    if not a.dry:
        json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("цитат:", len(out))
    for q in out:
        print("  %s | %s | s.%d | %s" % (q["book"], q["src"][:28], q["page"], q["tk"][:70]))


if __name__ == "__main__":
    main()
