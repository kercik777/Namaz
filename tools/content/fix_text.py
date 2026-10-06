#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Вычитка распознанного текста книг: артефакты, переносы, слова, оглавление.

Каждая правка проверяется:
  * артефакты распознавания (ś, š, ς, ñ, ğ, ó, ²) разрешаются по словарю
    и по частоте слов в самой книге — берётся вариант, который в книге
    встречается чаще всего;
  * слова, которые в OCR-тексте искажены и в словаре их нет (например
    «njšet» вместо «niýet»), перечислены в WORDS — они сверены по смыслу
    предложения;
  * переносы слов на границе строк склеиваются;
  * «заголовки» из одних символов удаляются, остальные нормализуются.

    python3 tools/content/fix_text.py --all app/src/main/assets/content
    python3 tools/content/fix_text.py --all app/src/main/assets/content --dry
"""
from __future__ import annotations

import argparse
import collections
import glob
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import clean_book as CB  # noqa: E402

# --------------------------------------------------------------------------
# 1. Символы: в этих кодах OCR подставлял похожие буквы.
#    ñ -> ň можно менять всегда; ś, š, ς, ğ, ó разрешаются по словам ниже.
# --------------------------------------------------------------------------
SAFE_CHARS = [
    ("ñ", "ň"), ("Ñ", "Ň"), ("ń", "ň"), ("Ń", "Ň"),
    ("²", ""), ("³", ""), ("¹", ""), ("°", ""),
    ("\u00a0", " "), ("\u200b", ""), ("\u200e", ""), ("\u200f", ""),
    ("–", "—"), ("ʻ", "’"), ("ʼ", "’"), ("`", "’"),
    ("ã", "ä"), ("Ã", "Ä"), ("ı", "y"), ("İ", "I"), ("ſ", "s"),
]

# --------------------------------------------------------------------------
# 2. Артефакты, которые нельзя разрешить одной заменой: для каждого такого
#    символа перечислены возможные настоящие буквы. Вариант выбирается по
#    частоте в самой книге и по словарю.
# --------------------------------------------------------------------------
PRIOR = {"ý": 3, "ş": 1, "s": 1, "ç": 1, "g": 1, "ö": 1, "o": 0, "z": 0, "ň": 0, "w": 0, "u": 0}
PREFIXES = set()

VARIANTS = {
    "ś": "şýszç",
    "š": "şýsžç",
    "ς": "çşs",
    "ğ": "gýňw",
    "ó": "oöu",
}

# --------------------------------------------------------------------------
# 3. Слова, сверенные по смыслу (в словаре их нет или словарь ошибается).
# --------------------------------------------------------------------------
WORDS = {
    # «id...» — распознавание переставило «ди» в начале слова
    "idñe": "diňe", "idip": "diýip", "idilýär": "diýilýär", "idilýän": "diýilýän",
    "idšilýär": "diýilýär", "idšilýän": "diýilýän", "idšilyr": "diýilýär",
    "idğilýär": "diýilýär", "idśip": "diýip", "idšip": "diýip", "idóip": "diýip",
    "idślip": "diýlip", "idšlip": "diýlip", "idšen": "diýen", "idśer": "diýer",
    "idšýar": "diýär", "idśýar": "diýär", "idipdir": "diýipdir",
    "idómeli": "diýmeli", "idšilýär.": "diýilýär.",
    # слова, которые OCR исказил целиком
    "njšet": "niýet", "şon": "soň", "şora": "soňra", "şo": "soň",
    "şoñuna": "soňuna", "şoruna": "soňuna", "şiresi": "süresi",
    "şiresini": "süresini", "şiresiniň": "süresiniň", "şiresine": "süresine",
    "şiresin": "süresin", "şire": "süre", "şiresinde": "süresinde",
    "peride": "perişde", "peridäniň": "perişdäniň", "peridänin": "perişdäniň",
    "perideler": "perişdeler", "perideleri": "perişdeleri",
    "peridelerine": "perişdelerine", "peridelerini": "perişdelerini",
    "perideleriň": "perişdeleriň", "peridelerinden": "perişdelerinden",
    "peridesi": "perişdesi", "sejde": "säjde", "sejdesi": "säjdesi",
    "sejdäni": "säjdesini", "sejdede": "säjdede", "sejdeler": "säjdeler",
    "teyemmum": "teýemmüm", "teyemmümi": "teýemmümi", "bismilla": "bismillä",
    "bismillä": "bismillä", "allahumme": "Allahümme", "aleyhissalam": "aleýhissalam",
    "äleğhissalam": "aleýhissalam", "ğerinden": "ýerinden", "öšla": "öýle",
    "öğla": "öýle", "öšle": "öýle", "edñyz": "deňiz", "ümña": "şuňa",
    "häśwaňlaryn": "haýwanlaryň", "häýwaňlaryn": "haýwanlaryň",
    "häśwanlaryň": "haýwanlaryň", "reñik": "reňk", "reñki": "reňki",
    "gölšazmalar": "golýazmalar", "häsišetlerden": "häsiýetlerden",
    "hasiýetlerden": "häsiýetlerden", "mukaýšat": "mukayýat",
    "elleriñişi": "elleriňizi", "ädiñ": "ediň", "açšandygyna": "aşandygyna",
    "hz": "Hz.", "HZ": "Hz.",
    # проверено по смыслу предложения
    "tahyýšat": "tahyýat", "tabyśýat": "tahyýat", "tabyšýat": "tahyýat",
    "šerinden": "ýerinden", "šerindäki": "ýerindäki", "šerine": "ýerine",
    "šere": "ýere", "šäri": "ýeri", "šadyna": "ýadyna", "šokdugyna": "ýokdugyna",
    "šokdur": "ýokdur", "šetiryän": "ýetirýän", "šetiren": "ýetiren",
    "šetmez": "ýetmez", "šäten": "ýeten", "šetmegi": "ýetmegi",
    "šagsyrakdyr": "ýagşyrakdyr", "šeke": "ýeke", "šörite": "ýörite",
    "śollary": "ýollary", "śarysydyr": "ýarysydyr", "śyrtyklar": "ýyrtyklar",
    "śylda": "ýylda", "śasyna": "ýaşyna", "śiwanda": "ýuwanda",
    "śüklenyär": "ýüklenyär", "śuwmak": "ýuwmak", "śuwulýar": "ýuwulýar",
    "śuwulmagy": "ýuwulmagy", "śuwun": "suwun", "śagdaýy": "ýagdaýy",
    "śüwmek": "süwmek", "śüwär": "süwär", "śgirimi": "ýigrimi",
    "šušudan": "şu suwdan", "bilšaň": "bilýäň", "āhmišet": "ähmiýet",
    "āhmišetini": "ähmiýetini", "idśleňde": "diýilende", "ςygýan": "çykýan",
    "ςykarysa": "çykarysa", "ςykarylyar": "çykarylýar", "ςykanyndan": "çykanyndan",
    "Üς": "Üç", "üς": "üç",
    # арабская транслитерация: буквы, которые OCR перепутал
    "tahyśat": "tahyýat", "tahyýśat": "tahyýat", "tahyśýat": "tahyýat",
    "tabyśat": "tahyýat", "tabyśa": "tahyýat", "tabyśýśat": "tahyýat",
    "ettahyśyatü": "et-tahyýatü", "tabyśýetil": "tabyýetil",
    "njğet": "niýet", "nešśidinä": "neşşidinä", "kürsišyuhussemä": "kürsiýyühussemä",
    "haýše": "haýýe", "mekgeši": "mekgei", "lušäter": "luşäter",
    "lihaýśinä": "lihayýinä", "meýśitinä": "meýýitinä", "äheýśtehu": "ahyýtehu",
    "teweffeśtehü": "teweffeýtehü", "lideýśe": "lideýe", "merzyśýeb": "merzyýeb",
    "hiše": "hiýe", "käýśimeh": "käýýimeh", "luğüraw": "luğüraw",
    "śünfehu": "şünfehu", "šušyiratil": "şuşyiratil", "lušüzhirahu": "luşüzhirahu",
    "tekbirinišet": "tekbirini niýet",
}

# Заголовки: сверены по книге (в оглавлении они видны сразу, поэтому правятся точно).
HEADINGS = [
    ("KITABY", None),                                   # обрывок титула
    ("JRAAAAnn", None),                                 # мусор распознавания
    ("MANLYGYŇ ALLATAGALADANDYGYNA YNAN- MÄK",
     "MANLYGYŇ ALLATAGALADANDYGYNA YNANMAK"),
    ("MANLYGYŇ ALLATAGALADANDYGYNA YNAN-",
     "MANLYGYŇ ALLATAGALADANDYGYNA YNANMAK"),
    ("NAMAZYŇ OKALY DÜZGÜNI Ertir namazynyň okaly düzgüni",
     "NAMAZYŇ OKALYŞ DÜZGÜNI"),
    ("Ertir namazynyň okaly düzgüni", "Ertir namazynyň okalyş düzgüni"),
    ("Öýle namazyny okaly d", "Öýle namazynyň okalyş düzgüni"),
    ("Öýle namazynyň okaly d", "Öýle namazynyň okalyş düzgüni"),
    ("Agam namazynyň", "Agşam namazynyň okalyş düzgüni"),
    ("Agşam namazynyň okalyş d", "Agşam namazynyň okalyş düzgüni"),
    ("Täretin parzlary", "Täretiň parzlary"),
    ("Täretin alnyşy", "Täretiň alnyşy"),
    ("Namazlaryň wagtlary", "Namazyň wagtlary"),
    ("Namazyň görnüleri", "Namazyň görnüşleri"),
    ("Bä wagt namazyň rekagatlary", "Bäş wagt namazyň rekagatlary"),
    ("Yas ýerlerde okalýan", "Aýatlar we süreler"),
    ("Yas ýerlerinde okalýan aýat we süreler", "Aýatlar we süreler"),
    ("Çap nusgasy", None),
    ("MAZMUNY", None),
    ("TÜRKMENISTANYŇ MÜFTÜSINIŇ MÜDIRIÝETI NAMAZ KITABY", None),
    ("TÜRKMENISTANYŇ MÜFTÜSINIŇ MÜDIRIÝETI", None),
    ("IKINJI KITAP — dogalar we süreler", "IKINJI BÖLÜM — dogalar we süreler"),
    ("I BÖLÜM IMAN-YNANÇ ESASLARY", "I BÖLÜM. IMAN-YNANÇ ESASLARY"),
    ("II BÖLÜM TÄMIZLIK", "II BÖLÜM. TÄMIZLIK"),
    ("ILI BÖLÜM", "III BÖLÜM"),
    ("IL BÖLÜM", "II BÖLÜM"),
    ("KYRK PARZ Ýedisi imanda", "KYRK PARZ"),
    ("Ýedisi imanda", "Ýedisi imanda"),
]

# Заголовки-мусор со страниц арабской графики: слова из одной-двух букв.
JUNK_HEADING = re.compile(r"^([A-Za-zÇÄŇŞÝÜÖŽçäňşýüöž]{1,2}\s+){2,}")


# Фразы (арабская транслитерация), которые врозь не исправить.
PHRASES = [
    ("müňe-neštä:", "mineş-şäýtä:"), ("müne-neštä:", "mineş-şäýtä:"),
    ("neśtä:nir-raji:m", "şäýtä:nir-raji:m"),
    ("neštä:nir-raji:m", "şäýtä:nir-raji:m"),
    ("mineş-neštä:", "mineş-şäýtä:"),
    ("tä'tušehümül", "tä'tiýehümül"),
    ("idğilSejdede", "diýilse, Sejdede"),
    ("tahrime nj almaly", "tahrime niýetini almaly"),
]

RE_WORD = re.compile(r"[^\W\d_]+", re.UNICODE)
RE_HYPHEN_JOIN = re.compile(r"\b(\w{3,})-\s*$")

TR_LETTERS = set("aäbçde fghi jžklmno öprsştu üwyýz".replace(" ", ""))


def fix_phrases(t: str) -> str:
    for a, b in PHRASES:
        if a in t:
            t = t.replace(a, b)
    return t


def fix_chars(t: str) -> str:
    for a, b in SAFE_CHARS:
        if a in t:
            t = t.replace(a, b)
    return t


def strip_diacritics(w: str) -> str:
    return (w.lower().replace("ä", "a").replace("ö", "o").replace("ü", "u")
            .replace("ý", "y").replace("ň", "n").replace("ş", "s")
            .replace("ž", "z").replace("ç", "c"))


def build_frequency(blocks) -> collections.Counter:
    """Частота «чистых» слов книги — по ней проверяются варианты замен."""
    global PREFIXES
    freq = collections.Counter()
    for b in blocks:
        for txt in [b.get("x") or ""] + (b.get("items") or []):
            for w in RE_WORD.findall(txt):
                if any(ch in w for ch in VARIANTS):
                    continue
                w = w.lower()
                if len(w) > 2:
                    freq[w] += 1
                    if len(w) >= 5:
                        PREFIXES.add(w[:5])
    return freq


def variant_candidates(word: str):
    """Все варианты замены артефактных букв (не больше 24 штук)."""
    out = [word]
    for i, ch in enumerate(word):
        if ch.lower() in VARIANTS:
            reps = VARIANTS[ch.lower()]
            if ch.isupper():
                reps = reps.upper()
            out = [o[:i] + r + o[i + 1:] for o in out for r in reps]
            if len(out) > 24:
                break
    return out


DICT = None


def dict_words():
    """Слова из словаря туркменского языка — второй способ проверки варианта."""
    path = os.path.join(HERE, "..", "data", "tk_TM.dic")
    out = set()
    try:
        with open(path, encoding="utf-8", errors="ignore") as fh:
            for line in fh:
                w = line.split("/")[0].strip().lower()
                if w and w[:1].isalpha():
                    out.add(w)
    except OSError:
        pass
    return out


def score(cand, raw, freq, prefixes):
    """Насколько вариант похож на настоящее слово: частота в книге + словарь + приоритет буквы."""
    global DICT
    if DICT is None:
        DICT = dict_words()
    s = min(freq.get(cand, 0), 10) * 3.0
    if cand in DICT:
        s += 2.0
    if len(cand) >= 5 and cand[:5] in prefixes:
        s += 1.0
    for a, b in zip(raw, cand):        # приоритет буквы: сколько таких букв в языке
        if a != b:
            s += PRIOR.get(b, 0) * 0.5
    return s


def words_index():
    """Проверенные слова и в исходном написании, и после замены ñ -> ň и т. п."""
    out = {}
    for k, v in WORDS.items():
        out[k] = v
        out.setdefault(fix_chars(k), fix_chars(v))
    return out


WORDS_IDX = {}


def look_up(low: str):
    global WORDS_IDX
    if not WORDS_IDX:
        WORDS_IDX = words_index()
    return WORDS_IDX.get(low)


RE_TRANSLIT = re.compile(r"(?:[a-zäöüňşýžç]{1,4}:){2,}", re.IGNORECASE)


def is_transliteration(text: str) -> bool:
    """Арабская транслитерация: слова с двоеточиями (a:, ä:, i:) — их не «исправляем»."""
    return len(RE_TRANSLIT.findall(text)) >= 2


def resolve_conservative(word: str) -> str:
    """В транслитерации меняем артефакт на самую вероятную букву, без догадок."""
    out = []
    for ch in word:
        low = ch.lower()
        if low in VARIANTS:
            rep = VARIANTS[low][0]
            out.append(rep.upper() if ch.isupper() else rep)
        else:
            out.append(ch)
    return "".join(out)


def resolve_artifacts(text: str, freq: collections.Counter):
    """Заменяет ś, š, ς, ğ, ó на настоящие буквы по частоте слов в книге."""
    fixed = 0
    left = []
    translit = is_transliteration(text)

    def one(m):
        nonlocal fixed
        w = m.group(0)
        if not any(ch in w for ch in VARIANTS):
            return w
        low = w.lower()
        known = look_up(low)
        if known:
            fixed += 1
            return cap(known, w)
        if translit:
            fixed += 1
            return resolve_conservative(w)
        best, best_score = None, 0
        for cand in variant_candidates(low):
            if cand == low:
                continue
            sc = score(cand, low, freq, PREFIXES)
            if sc > best_score:
                best, best_score = cand, sc
        if best is None:
            left.append(w)
            return w
        fixed += 1
        return cap(best, w)

    out = RE_WORD.sub(one, text)
    return out, fixed, left


def cap(new: str, sample: str) -> str:
    if sample.isupper() and len(sample) > 1:
        return new.upper()
    if sample[:1].isupper():
        return new[:1].upper() + new[1:]
    return new


def apply_words(text: str):
    """Проверенные слова: с учётом знаков препинания и регистра."""
    n = 0

    def one(m):
        nonlocal n
        w = m.group(0)
        low = w.lower()
        known = look_up(low)
        if known and known != low:
            n += 1
            return cap(known, w)
        return w

    return RE_WORD.sub(one, text), n


DIA_GROUPS = [("a", "ä"), ("o", "ö"), ("u", "ü"), ("n", "ň"), ("s", "ş"),
              ("z", "ž"), ("c", "ç"), ("y", "ý")]


def dia_variants(word: str, limit=90):
    """Варианты написания с другими диакритическими знаками (не больше limit)."""
    out = [word]
    for i, ch in enumerate(word):
        for a, b in DIA_GROUPS:
            if ch == a or ch == b:
                other = b if ch == a else a
                out = [o[:i] + other + o[i + 1:] for o in out] + out
                break
        if len(out) > limit:
            return out[:limit]
    return out


def report_diacritics(blocks, freq):
    """Где буква есть, а знака нет: «namazyn» вместо «namazyň».

    Только отчёт: n/ň, s/ş и подобное меняют смысл слова, поэтому такие пары
    решает человек, а не частота слов в распознанном тексте.
    """
    changes = collections.Counter()
    if DICT is None:
        return changes
    for b in blocks:
        if b.get("t") in ("title", "center", "img", "ar"):
            continue
        for txt in [b.get("x") or ""] + (b.get("items") or []):
            if is_transliteration(txt):
                continue
            for m in RE_WORD.finditer(txt):
                low = m.group(0).lower()
                if len(low) < 4 or low in DICT or look_up(low):
                    continue
                if freq.get(low, 0) > 1:
                    continue
                for cand in dia_variants(low):
                    if cand != low and cand in DICT and freq.get(cand, 0) >= 2:
                        changes[(low, cand)] += 1
                        break
    return changes


# Родительный падеж в туркменском всегда пишется с «ň» на конце: -nyň / -ňyň.
# В распознанном тексте знак теряется («namazlarynyn»), поэтому правило точное.
RE_GENITIVE = re.compile(r"([a-zäöüçžşýň]{2,})nyn\b", re.IGNORECASE)
RE_DOUBLE_N = re.compile(r"nn(?=[yý])", re.IGNORECASE)


def fix_genitive(text: str):
    """«namazlarynyn» -> «namazlarynyň»: возвращаем «ň» в родительном падеже.

    Заодно убираем случайное удвоение «н» на стыке основы и окончания
    («halkynnyn» -> «halkynyň») — в туркменском таких удвоений не бывает.
    """
    text = RE_DOUBLE_N.sub("n", text)
    n = 0

    def one(m):
        nonlocal n
        n += 1
        return m.group(1) + "nyň"

    out = RE_GENITIVE.sub(one, text)
    return out, n


def join_broken(blocks):
    """Перенос слова на границе блоков: «ynan-» + «mäk» -> «ynanmäk»."""
    out = []
    for b in blocks:
        if out:
            prev = out[-1]
            px = prev.get("x", "") or ""
            bx = b.get("x", "") or ""
            if (prev.get("t") in ("p", "note", "h1", "h2") and b.get("t") in ("p", "note", "h2")
                    and RE_HYPHEN_JOIN.search(px) and bx[:1].isalpha() and bx[:1].islower()
                    and len(bx) <= 40):
                prev["x"] = px.rstrip()[:-1] + bx.lstrip()
                continue
        out.append(b)
    return out


def drop_noise_headings(blocks):
    """Убираем «заголовки»-мусор: без букв или из коротких обрывков."""
    out = []
    for b in blocks:
        if b.get("t") in ("h1", "h2"):
            x = (b.get("x") or "").strip()
            if not any(c.isalpha() for c in x):
                continue
            if JUNK_HEADING.match(x) and len(x) < 40:
                continue
            if len(KNOWN_WORDS(x)) == 0 and len(x) < 26:
                continue
        out.append(b)
    return out


def KNOWN_WORDS(text):
    words = [w.lower() for w in RE_WORD.findall(text)]
    return [w for w in words if len(w) >= 4]


def words_known(text, vocab):
    """Все ли слова заголовка есть в словаре или в самой книге."""
    global DICT
    if DICT is None:
        DICT = dict_words()
    words = [w.lower() for w in RE_WORD.findall(text) if len(w) >= 4]
    if not words:
        return False
    good = 0
    for w in words:
        if w in DICT or w in vocab or w.rstrip("larlerdanymň") in DICT:
            good += 1
    return good >= max(1, (len(words) + 1) // 2)


def fix_headings(blocks, vocab=()):
    """Правка заголовков по таблице: видно в оглавлении, поэтому точно."""
    out = []
    for b in blocks:
        if b.get("t") not in ("h1", "h2"):
            out.append(b)
            continue
        if not words_known((b.get("x") or "").strip(" .:"), vocab) and len((b.get("x") or "").strip()) < 34:
            continue
        x = (b.get("x") or "").strip()
        drop = False
        for src, dst in HEADINGS:
            if x == src or x.startswith(src):
                if dst is None:
                    if x == src:
                        drop = True
                        break
                    continue
                x = dst
                break
        if drop or not x:
            continue
        b = dict(b)
        b["x"] = x
        out.append(b)
    return out


def fix_case_headings(blocks):
    """«NAMAZYN PARZLARY» -> «NAMAZYŇ PARZLARY»: у заголовков чиним буквы."""
    for b in blocks:
        x = b.get("x") or ""
        if not x:
            continue
        x = x.replace("NAMAZYN", "NAMAZYŇ").replace("NAMAZYÑ", "NAMAZYŇ")
        x = x.replace("NAMAZYŇYŇ", "NAMAZYŇ")
        x = x.replace("MÜFTÜSININ", "MÜFTÜSINIŇ").replace("MÜFTÜSINIÑ", "MÜFTÜSINIŇ")
        x = x.replace("TÜRKМENISTANYŇ", "TÜRKMENISTANYŇ")
        x = x.replace("ILI BÖLÜM", "III BÖLÜM").replace("IL BÖLÜM", "II BÖLÜM")
        x = re.sub(r"\bSÜNNETLERI\b", "SÜNNETLERI", x)
        b["x"] = x
    return blocks


def process(path, freq_ref=None, verbose=True):
    data = json.load(open(path, encoding="utf-8"))
    if isinstance(data, list):
        data = data[0]
    blocks = data.get("blocks", [])
    before = (sum(len(b.get("x", "") or "") for b in blocks),
              sum(len(RE_WORD.findall((b.get("x") or "") + " " + " ".join(b.get("items") or [])))
                  for b in blocks))

    for b in blocks:  # символы и фразы
        if b.get("x"):
            b["x"] = fix_chars(fix_phrases(b["x"]))
        if b.get("items"):
            b["items"] = [fix_chars(fix_phrases(i)) for i in b["items"]]
    blocks = join_broken(blocks)

    freq = build_frequency(blocks)
    if freq_ref is not None:
        freq += freq_ref
    left = []
    for _pass in range(2):  # второй проход: частота уже с исправленными словами
        left = []
        for b in blocks:
            if b.get("x"):
                b["x"], _, l = resolve_artifacts(b["x"], freq)
                left += l
            if b.get("items"):
                items = []
                for it in b["items"]:
                    it2, _, l = resolve_artifacts(it, freq)
                    items.append(it2)
                    left += l
                b["items"] = items
        if _pass == 0:
            freq = build_frequency(blocks) + (freq_ref or collections.Counter())

    global DICT
    if DICT is None:
        DICT = dict_words()
    dia = report_diacritics(blocks, freq + (freq_ref or collections.Counter()))

    genitive_fixed = 0
    for b in blocks:
        if b.get("x"):
            b["x"], n = fix_genitive(b["x"])
            genitive_fixed += n
        if b.get("items"):
            items = []
            for it in b["items"]:
                it2, n = fix_genitive(it)
                genitive_fixed += n
                items.append(it2)
            b["items"] = items

    words_fixed = 0
    for b in blocks:
        if b.get("x"):
            b["x"], n = apply_words(b["x"])
            words_fixed += n
        if b.get("items"):
            items = []
            for it in b["items"]:
                it2, n = apply_words(it)
                words_fixed += n
                items.append(it2)
            b["items"] = items

    blocks = CB.drop_junk(blocks)
    blocks = CB.merge_headings(blocks)
    blocks = CB.demote_bad_headings(blocks)
    # словарь книги — только по обычному тексту: заголовки не должны
    # подтверждать сами себя (иначе мусорные заголовки остаются в оглавлении)
    vocab = set()
    for b in blocks:
        if b.get("t") not in ("p", "list", "note", "q", "a", "quote"):
            continue
        for w in RE_WORD.findall((b.get("x") or "") + " " + " ".join(b.get("items") or [])):
            if len(w) >= 4:
                vocab.add(w.lower())
    blocks = fix_headings(blocks, vocab)
    blocks = drop_noise_headings(blocks)
    blocks = CB.join_fragments(blocks)
    blocks = CB.drop_duplicates(blocks)
    blocks = fix_case_headings(blocks)
    data["blocks"] = blocks
    data["toc"] = CB.rebuild_toc(blocks, data.get("toc", []))

    after = (sum(len(b.get("x", "") or "") for b in blocks),
             sum(len(RE_WORD.findall((b.get("x") or "") + " " + " ".join(b.get("items") or [])))
                 for b in blocks))
    if verbose:
        print("%s: символов %d -> %d, слов %d -> %d, блоков %d, пунктов оглавления %d"
              % (os.path.basename(path), before[0], after[0], before[1], after[1],
                 len(blocks), len(data["toc"])))
        print("   проверенных слов: %d, родительный падеж: %d" % (words_fixed, genitive_fixed))
        if dia:
            print("   без знаков (%d): %s" % (sum(dia.values()),
                  ", ".join("%s→%s" % (a, b_) for (a, b_), n in dia.most_common(40))))
        if left:
            cnt = collections.Counter(left)
            print("   не разрешено (%d): %s" % (sum(cnt.values()),
                  ", ".join("%s×%d" % (w, n) for w, n in cnt.most_common(14))))
    return data, left, freq


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="*")
    ap.add_argument("--all", help="каталог content")
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args(argv)

    targets = list(a.paths)
    if a.all:
        targets += sorted(glob.glob(os.path.join(a.all, "*.json")))
        targets = [t for t in targets if not t.endswith("quotes.json")]
    if not targets:
        ap.error("укажите файл или --all <каталог>")

    freq = collections.Counter()
    for path in targets:  # частота по всем книгам сразу — так надёжнее
        data = json.load(open(path, encoding="utf-8"))
        if isinstance(data, list):
            data = data[0]
        freq += build_frequency(data.get("blocks", []))

    for path in targets:
        data, _, _ = process(path, freq_ref=freq)
        if not a.dry:
            with open(path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, separators=(",", ":"))


if __name__ == "__main__":
    main()
