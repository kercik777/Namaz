#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Лист превью иллюстраций: рисует фигуры через ImageMagick и склеивает их.

    python3 tools/ill/preview.py [--out docs/preview/suratlar.png] [--only täret]
"""
import argparse
import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import figures  # noqa: E402


def q(color):
    """Цвет для MVG: none без кавычек, остальное в кавычках."""
    return "'%s'" % color if color else "none"


def mvg(svg):
    """Переводит собранные фигуры в формат Magick Vector Graphics."""
    out = ["fill '#FFFFFF' stroke none path 'M 0 0 H 100 V 100 H 0 Z'"]
    for kind, a in svg.parts:
        fill = a.get("fill")
        stroke = a.get("stroke")
        op = a.get("opacity")
        sw = a.get("sw") or 0
        seg = "fill %s stroke %s" % (q(fill), q(stroke))
        if stroke and sw:
            seg += " stroke-width %g" % sw
            cap = a.get("cap") or "round"
            seg += " stroke-linecap %s stroke-linejoin round" % cap
        if op:
            seg += " fill-opacity %g stroke-opacity %g" % (op, op)
        seg += " path '%s'" % a["d"]
        out.append(seg)
    return "\n".join(out)


def render(svg, path, size=200):
    with tempfile.NamedTemporaryFile("w", suffix=".mvg", delete=False) as f:
        f.write(mvg(svg))
        name = f.name
    subprocess.run(["convert", "-size", "100x100", "mvg:" + name, "-resize", "%dx%d" % (size, size),
                    path], check=True)
    os.unlink(name)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="docs/preview/suratlar.png")
    ap.add_argument("--only", default="")
    ap.add_argument("--cols", type=int, default=7)
    ap.add_argument("--thumb", type=int, default=170)
    a = ap.parse_args()

    figs = figures.build()
    names = [n for n in figs if not a.only or a.only in n]
    tmp = tempfile.mkdtemp()
    tiles = []
    for i, n in enumerate(names):
        svg = figures.Svg()
        figs[n][1](svg)
        p = os.path.join(tmp, "%02d.png" % i)
        render(svg, p, a.thumb)
        tiles.append((p, figs[n][0]))

    cols = a.cols
    out = os.path.abspath(a.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    subprocess.run(["montage"] + [t[0] for t in tiles]
                   + ["-tile", "%dx" % cols, "-geometry", "+6+6",
                      "-background", "#F4F1E8", out], check=True)
    for i, (_, label) in enumerate(tiles):
        print("  %2d. %s" % (i + 1, label))
    print("превью:", out, "плиток:", len(tiles))


if __name__ == "__main__":
    main()
