# -*- coding: utf-8 -*-
"""
Восстановление туркменской диакритики в тексте после OCR.

Идея: OCR-модель уверенно читает базовые латинские буквы, но часто теряет
туркменские диакритические знаки (ä, ň, ş, ý, ö, ü, ž). Мы восстанавливаем
их при помощи морфологического словаря туркменского языка (hunspell:

    nazartm/turkmen-spell-check-dictionary  -> tk_TM.dic + tk_TM.aff)

Алгоритм:
  1. Словарь содержит основы слов и списки разрешённых аффиксов (AF-алиасы),
     .aff содержит 4804 правила-суффикса (strip=0, condition=".").
  2. Строим индекс: "слово без диакритики" -> [(слово с диакритикой, правила)].
  3. Для OCR-токена перебираем возможные суффиксы, отрезаем их и ищем основу
     в индексе. Так получаем варианты восстановленного слова.
  4. Неоднозначности разрешаем по частотности слов в самом корпусе книги
     (термины повторяются многократно), затем -- по близости к OCR-форме.
  5. Для токенов, которые не нашлись, пробуем мягкие замены похожих букв
     (i/u/y, s/ş, n/ň, o/ö, u/ü, ...) -- типичные ошибки распознавания.
  6. Остаток выводится в отчёт для ручной вычитки.

Использование (CLI):
    python3 tkcorrect.py --text-file raw.txt --out fixed.txt --report rep.json
"""
from __future__ import annotations

import argparse
import collections
import json
import os
import re
import sys

DIA = "äňşýöüžÄŇŞÝÖÜŽçÇ"
DE_DIA = {
    "ä": "a", "ň": "n", "ş": "s", "ý": "y", "ö": "o", "ü": "u", "ž": "z",
    "Ä": "A", "Ň": "N", "Ş": "S", "Ý": "Y", "Ö": "O", "Ü": "U", "Ž": "Z",
    "ç": "c", "Ç": "C",
}

# Похожие буквы, которые OCR путает чаще всего (в базовом виде).
CONFUSIONS = [
    ("i", "u"), ("u", "i"), ("y", "i"), ("i", "y"), ("o", "a"), ("a", "o"),
    ("e", "a"), ("a", "e"), ("u", "y"), ("y", "u"), ("s", "n"), ("n", "s"),
    ("c", "s"), ("s", "c"), ("k", "g"), ("g", "k"), ("b", "h"), ("h", "b"),
    ("l", "i"), ("i", "l"), ("m", "n"), ("n", "m"), ("r", "n"), ("t", "f"),
    ("g", "s"), ("s", "g"), ("z", "s"), ("s", "z"), ("j", "i"), ("i", "j"),
]

WORD_RE = re.compile(r"[A-Za-z" + DIA + r"]+(?:['\u2019`\-][A-Za-z" + DIA + r"]+)*")


def dediac(s: str) -> str:
    """Убирает туркменские диакритические знаки и приводит к нижнему регистру."""
    return "".join(DE_DIA.get(ch, ch) for ch in s).lower()


def _case_like(reference: str, value: str) -> str:
    """Восстанавливает регистр по образцу (ВСЕ ЗАГЛАВНЫЕ, С заглавной, строчные)."""
    if not reference:
        return value
    letters = [c for c in reference if c.isalpha()]
    if not letters:
        return value
    if all(c.isupper() for c in letters):
        return value.upper()
    if letters[0].isupper() and all(c.islower() for c in letters[1:]):
        return value[:1].upper() + value[1:]
    if all(c.islower() for c in letters):
        return value[:1].lower() + value[1:]
    return value


class TurkmenCorrector:
    def __init__(self, dic_path: str, aff_path: str, extra_words=()):
        self.alias = {}          # номер алиаса -> [id правил]
        self.rules = {}          # id правила -> [суффиксы]
        self.stems = {}          # дедиакрит. основа -> [(основа, frozenset(rule_ids)|None)]
        self.suffix_index = collections.defaultdict(list)  # дедиакрит. суффикс -> [rule ids]
        self._parse_aff(aff_path)
        self._parse_dic(dic_path)
        for w in extra_words:
            self.stems.setdefault(dediac(w), []).append((w, None))
        self._build_suffix_index()
        self.cache = {}

    # ---------- разбор словаря ----------
    def _parse_aff(self, aff_path: str) -> None:
        with open(aff_path, encoding="utf-8", errors="ignore") as fh:
            for line in fh:
                line = line.strip()
                if line.startswith("SFX "):
                    parts = line.split()
                    if len(parts) == 5:            # SFX <id> <strip> <append> <cond>
                        rid, strip, append = parts[1], parts[2], parts[3]
                        if strip != "0":
                            continue               # в этом словаре таких нет
                        self.rules.setdefault(rid, []).append(append)
                elif line.startswith("AF "):
                    body = line[3:]
                    idx = None
                    if "#" in body:
                        body, _, tail = body.partition("#")
                        idx = tail.strip()
                    ids = [x.strip() for x in body.split(",") if x.strip()]
                    if idx is not None:
                        self.alias[idx] = ids

    def _parse_dic(self, dic_path: str) -> None:
        with open(dic_path, encoding="utf-8", errors="ignore") as fh:
            fh.readline()                          # первая строка -- количество
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                word, _, flags = line.partition("/")
                word = word.strip()
                if not word or not any(ch.isalpha() for ch in word):
                    continue
                allowed = None
                if flags:
                    ids = []
                    for flag in flags.split(","):
                        if flag in self.alias:
                            ids.extend(self.alias[flag])
                        elif flag in self.rules:
                            ids.append(flag)
                    allowed = frozenset(ids) if ids else None
                self.stems.setdefault(dediac(word), []).append((word, allowed))

    def _build_suffix_index(self) -> None:
        for rid, suffixes in self.rules.items():
            for sfx in suffixes:
                if sfx:
                    self.suffix_index[dediac(sfx)].append(rid)
        # индексируем по длине для быстрого поиска
        self._suffix_by_len = collections.defaultdict(set)
        for sfx in self.suffix_index:
            self._suffix_by_len[len(sfx)].add(sfx)
        self._max_suffix = max(self._suffix_by_len) if self._suffix_by_len else 0

    # ---------- поиск вариантов ----------
    def _lookup_stem(self, token_low: str):
        """Возвращает список форм-кандидатов для токена (в нижнем регистре)."""
        out = {}
        # 1) прямое совпадение с основой
        if token_low in self.stems:
            for word, _allowed in self.stems[token_low]:
                out[word] = out.get(word, 0) + 3
        # 2) основа + суффикс
        for ln in range(1, self._max_suffix + 1):
            if ln >= len(token_low):
                break
            tail = token_low[-ln:]
            if tail not in self.suffix_index:
                continue
            stem = token_low[:-ln]
            entries = self.stems.get(stem)
            if not entries:
                continue
            for rid in self.suffix_index[tail]:
                for word, allowed in entries:
                    if allowed is not None and rid not in allowed:
                        continue
                    for sfx in self.rules[rid]:
                        if sfx and dediac(sfx) == tail:
                            cand = word + sfx
                            out[cand] = out.get(cand, 0) + (2 if allowed is not None else 1)
        return out

    def _fuzzy_lookup(self, token_low: str, base: dict):
        """Мягкий поиск: одна-две замены похожих букв."""
        found = {}
        n = len(token_low)
        for i in range(n):
            ch = token_low[i]
            for a, b in CONFUSIONS:
                if ch != a:
                    continue
                trial = token_low[:i] + b + token_low[i + 1:]
                if trial in self.cache:
                    res = self.cache[trial]
                else:
                    res = self._lookup_stem(trial)
                for w, sc in res.items():
                    found[w] = max(found.get(w, 0), sc + 1)
            if found:
                break
        if not found:
            for i in range(n - 1):
                pair = token_low[i:i + 2]
                variants = []
                if pair[0] == pair[1]:
                    variants.append(token_low[:i] + pair[0] + token_low[i + 2:])
                else:
                    variants.append(token_low[:i] + token_low[i + 1] + token_low[i] + token_low[i + 2:])
                if len(pair) == 2 and pair[0] in "nmrl" and pair[1] in "nmrl":
                    variants.append(token_low[:i] + token_low[i + 1] + pair[0] + token_low[i + 2:])
                for trial in variants:
                    res = self._lookup_stem(trial)
                    for w, sc in res.items():
                        found[w] = max(found.get(w, 0), sc + 2)
        return found

    # ---------- публичный API ----------
    def candidates(self, token: str):
        key = (token,)
        if key in self.cache:
            return self.cache[key]
        low = dediac(token)
        res = self._lookup_stem(low)
        if not res:
            res = self._fuzzy_lookup(low, res)
        self.cache[key] = res
        return res

    def correct_word(self, token: str, freq: dict | None = None):
        """Возвращает (исправленное слово, уверенность) для одного токена."""
        if len(token) < 2 or not any(c.isalpha() for c in token):
            return token, "asis"
        # сохраняем апострофы/дефисы как разделители
        parts = re.split(r"(['’`\-])", token)
        rebuilt, status = [], "asis"
        for part in parts:
            if not part or not part[0].isalpha():
                rebuilt.append(part)
                continue
            cands = self.candidates(part)
            if not cands:
                rebuilt.append(part)
                status = "unknown"
                continue
            if len(cands) == 1:
                word = next(iter(cands))
                rebuilt.append(_case_like(part, word))
                if status != "unknown":
                    status = "fixed"
                continue
            # разрешаем неоднозначность
            scored = []
            for word, base in cands.items():
                score = base
                if freq:
                    score += min(3, freq.get(word, 0) / 5.0)
                # близость к OCR-форме: совпадение длины и позиций без диакритики
                score -= abs(len(dediac(word)) - len(dediac(part))) * 0.5
                scored.append((score, word))
            scored.sort(key=lambda x: (-x[0], x[1]))
            best = scored[0]
            if len(scored) > 1 and abs(best[0] - scored[1][0]) < 0.35:
                rebuilt.append(part)              # недостаточно уверенности -- оставляем как есть
                status = "doubt"
            else:
                rebuilt.append(_case_like(part, best[1]))
                if status != "unknown":
                    status = "fixed"
        return "".join(rebuilt), status

    def correct_text(self, text: str, freq: dict | None = None):
        stats = collections.Counter()
        unknown = collections.Counter()

        def repl(match):
            word = match.group(0)
            fixed, st = self.correct_word(word, freq)
            stats[st] += 1
            if st in ("unknown", "doubt"):
                unknown[dediac(word)] += 1
            return fixed

        out = WORD_RE.sub(repl, text)
        return out, stats, unknown

    def build_frequency(self, texts, min_len: int = 3, rounds: int = 1):
        """Частотный словарь форм, восстановленных однозначно (для разрешения
        неоднозначностей в следующем проходе)."""
        freq = collections.Counter()
        for text in texts:
            for token in WORD_RE.findall(text):
                cands = self.candidates(token)
                if len(cands) == 1:
                    freq[next(iter(cands))] += 1
        return freq


def load_texts(paths):
    texts = []
    for p in paths:
        with open(p, encoding="utf-8") as fh:
            texts.append(fh.read())
    return texts


def main(argv=None):
    ap = argparse.ArgumentParser(description="Восстановление туркменской диакритики после OCR")
    ap.add_argument("--dict-dir", default=os.path.join(os.path.dirname(__file__), "data"),
                    help="папка с tk_TM.dic и tk_TM.aff")
    ap.add_argument("--text-file", required=True, help="файл с OCR-текстом (или несколько через запятую)")
    ap.add_argument("--extra-words", default="", help="файл со словами из предметной области, по одному в строке")
    ap.add_argument("--out", help="куда сохранить исправленный текст")
    ap.add_argument("--report", help="json-отчёт: статистика и список непонятных слов")
    args = ap.parse_args(argv)

    dic = os.path.join(args.dict_dir, "tk_TM.dic")
    aff = os.path.join(args.dict_dir, "tk_TM.aff")
    extra = []
    if args.extra_words and os.path.exists(args.extra_words):
        extra = [l.strip() for l in open(args.extra_words, encoding="utf-8") if l.strip()]
    corr = TurkmenCorrector(dic, aff, extra_words=extra)

    paths = args.text_file.split(",")
    texts = load_texts(paths)
    freq = corr.build_frequency(texts)
    total_stats = collections.Counter()
    total_unknown = collections.Counter()
    fixed_texts = []
    for t in texts:
        fixed, st, unk = corr.correct_text(t, freq)
        fixed_texts.append(fixed)
        total_stats.update(st)
        total_unknown.update(unk)

    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write("\n\n".join(fixed_texts))
    report = {
        "stats": dict(total_stats),
        "unknown_top": total_unknown.most_common(400),
        "frequency_forms": len(freq),
    }
    if args.report:
        with open(args.report, "w", encoding="utf-8") as fh:
            json.dump(report, fh, ensure_ascii=False, indent=1)
    print(json.dumps(report["stats"], ensure_ascii=False), file=sys.stderr)
    print("непонятных словоформ:", sum(total_unknown.values()), file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
