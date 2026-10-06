#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Лист превью иллюстраций: фигуры рисуются так же, как их рисует Android.

Раньше здесь был ImageMagick, и он сильно искажал картинку (полупрозрачные
фигуры, потерянные цвета). Теперь SVG каждой фигуры растеризуется PyMuPDF —
это близко к тому, что показывают Android и сам файл drawable.

    python3 tools/ill/preview.py [--out docs/preview/suratlar.png] [--only täret]

Нужны пакеты: pymupdf, pillow.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import figures  # noqa: E402


def render(svg_text, size):
    import fitz  # pymupdf
    doc = fitz.open(stream=svg_text.encode("utf-8"), filetype="svg")
    page = doc[0]
    rect = page.rect
    scale = size / max(rect.width, rect.height)
    pix = page.get_pixmap(matrix=fitz.Matrix(scale, scale), alpha=False)
    return pix


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="docs/preview/suratlar.png")
    ap.add_argument("--only", default="")
    ap.add_argument("--cols", type=int, default=7)
    ap.add_argument("--thumb", type=int, default=190)
    a = ap.parse_args()

    from PIL import Image, ImageDraw, ImageFont

    font = None
    for cand in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                 "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
                 "/usr/share/fonts/dejavu/DejaVuSans.ttf"):
        if os.path.exists(cand):
            font = ImageFont.truetype(cand, 13)
            break

    figs = figures.build()
    names = [n for n in figs if not a.only or a.only in n]
    label = ("Kitapdaky suratlar — %d sany. " % len(names)
             + "Hemmesi programmanyň özünde wektor görnüşinde çyzylan.")

    tiles = []
    for n in names:
        s = figures.Svg()
        figs[n][1](s)
        pix = render(s.svg(), a.thumb)
        img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
        tiles.append((img, figs[n][0]))

    cols = a.cols
    pad, cap = 10, 22
    tw, th = a.thumb, a.thumb + cap
    rows = (len(tiles) + cols - 1) // cols
    W = pad + cols * (tw + pad)
    H = pad + 30 + rows * (th + pad)
    sheet = Image.new("RGB", (W, H), "#F4F1E8")
    d = ImageDraw.Draw(sheet)
    d.text((pad, 12), label, fill="#4A3B2E", font=font)

    for i, (img, title) in enumerate(tiles):
        c, r = i % cols, i // cols
        x = pad + c * (tw + pad)
        y = pad + 30 + r * (th + pad)
        sheet.paste(img, (x, y))
        d.rectangle([x - 1, y - 1, x + tw, y + tw], outline="#D9CDB4")
        d.text((x + 2, y + tw + 5), title[:36], fill="#6B5A46", font=font)

    out = os.path.abspath(a.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    for i, (_, title) in enumerate(tiles):
        print("  %2d. %s" % (i + 1, title))
    print("превью:", out, "плиток:", len(tiles))


if __name__ == "__main__":
    main()
