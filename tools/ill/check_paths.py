#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Проверяет, что все пути в иллюстрациях корректны (как их читает Android)."""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import figures  # noqa: E402

NEED = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7,
        "m": 2, "l": 2, "h": 1, "v": 1, "c": 6, "s": 4, "q": 4, "t": 2, "a": 7}


def check(d):
    """Возвращает текст ошибки или пусто."""
    toks = re.findall(r"[MmLlHhVvCcSsQqTtAaZz]|-?\d*\.?\d+(?:e-?\d+)?", d)
    i = 0
    while i < len(toks):
        if toks[i] in "Zz":
            i += 1
            continue
        cmd = toks[i]
        if cmd not in NEED:
            return "неизвестная команда %r" % cmd
        need = NEED[cmd]
        i += 1
        count = 0
        while i < len(toks) and toks[i] not in "MmLlHhVvCcSsQqTtAaZz":
            i += 1
            count += 1
        if count == 0 or count % need:
            return "команда %s: %d чисел (нужно кратно %d)" % (cmd, count, need)
    return ""


def main():
    bad = 0
    for name, (title, draw) in figures.build().items():
        s = figures.Svg()
        draw(s)
        for k, (kind, a) in enumerate(s.parts):
            err = check(a["d"])
            if err:
                bad += 1
                print("%-16s часть %-3d %s\n    %s" % (name, k, err, a["d"][:110]))
    print("проблем:", bad)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
