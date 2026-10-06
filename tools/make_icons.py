#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Генератор иконок приложения «Akyda we Namaz».
Рисует эмблему (полумесяц + открытая книга) и сохраняет все нужные PNG
в ресурсы Android (mipmap-*/ic_launcher.png, ic_launcher_round.png,
drawable-xxxhdpi/ic_launcher_foreground.png).

Запуск:  python3 tools/make_icons.py
"""
import math
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

EMERALD_TOP = (16, 74, 54)
EMERALD_BOT = (4, 22, 16)
GOLD_HI = (245, 222, 155)
GOLD_LO = (186, 141, 56)
IVORY = (247, 241, 227)
IVORY_SHADE = (226, 214, 190)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def gradient(size, top, bottom):
    img = Image.new("RGB", (1, size), top)
    px = img.load()
    for y in range(size):
        px[0, y] = lerp(top, bottom, y / max(1, size - 1))
    return img.resize((size, size), Image.BILINEAR)


def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return m


def circle_mask(size, cx, cy, r):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    return m


def star_points(cx, cy, outer, inner, points=5, rotate=-math.pi / 2):
    pts = []
    for i in range(points * 2):
        r = outer if i % 2 == 0 else inner
        a = rotate + i * math.pi / points
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def draw_gold_shape(size, draw_fn, mask_fn):
    """Рисует фигуру золотым градиентом по маске."""
    grad = gradient(size, GOLD_HI, GOLD_LO)
    layer = Image.new("RGB", (size, size), (0, 0, 0))
    d = ImageDraw.Draw(layer)
    draw_fn(d)
    layer.putalpha(mask_fn())
    return layer


def emblem(size, scale=1.0):
    """Эмблема: полумесяц со звездой над раскрытой книгой. Возвращает RGBA."""
    S = size
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    cx = S / 2.0
    k = (S / 1024.0) * scale

    # --- книга (нижняя часть) ---
    book_top = S * 0.60
    book_bot = S * 0.82
    half_w = S * 0.30
    spine = S * 0.012
    d = ImageDraw.Draw(img)
    # левая страница
    d.polygon(
        [(cx - half_w, book_bot), (cx - half_w * 0.94, book_top + S * 0.012),
         (cx - spine, book_top), (cx - spine, book_bot - S * 0.008)],
        fill=IVORY)
    # правая страница
    d.polygon(
        [(cx + half_w, book_bot), (cx + half_w * 0.94, book_top + S * 0.012),
         (cx + spine, book_top), (cx + spine, book_bot - S * 0.008)],
        fill=IVORY_SHADE)
    # обложка/корешок золотом
    d.polygon(
        [(cx - half_w, book_bot), (cx - half_w * 0.97, book_bot - S * 0.028),
         (cx - spine, book_bot - S * 0.020), (cx - spine, book_bot - S * 0.008)],
        fill=GOLD_LO)
    d.polygon(
        [(cx + half_w, book_bot), (cx + half_w * 0.97, book_bot - S * 0.028),
         (cx + spine, book_bot - S * 0.020), (cx + spine, book_bot - S * 0.008)],
        fill=(150, 112, 42))
    d.rectangle([cx - spine * 0.9, book_top, cx + spine * 0.9, book_bot], fill=GOLD_LO)
    # тонкие линии строк на страницах
    for i in range(4):
        y = book_top + S * 0.055 + i * S * 0.038
        off = S * 0.012
        d.line([(cx - half_w * 0.9 + off, y + S * 0.01), (cx - spine * 2.0, y)],
               fill=(206, 192, 166), width=max(1, int(2 * k)))
        d.line([(cx + spine * 2.0, y), (cx + half_w * 0.9 - off, y + S * 0.01)],
               fill=(206, 192, 166), width=max(1, int(2 * k)))

    # --- полумесяц ---
    r = S * 0.215
    mcx, mcy = cx - S * 0.015, S * 0.325
    moon = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    md = ImageDraw.Draw(moon)
    md.ellipse([mcx - r, mcy - r, mcx + r, mcy + r], fill=GOLD_HI)
    inner_r = r * 0.86
    cut = S * 0.085
    md.ellipse([mcx - inner_r + cut, mcy - inner_r, mcx + inner_r + cut, mcy + inner_r],
               fill=(0, 0, 0, 0))
    img.alpha_composite(moon)

    # --- звезда ---
    star = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    sd = ImageDraw.Draw(star)
    sd.polygon(star_points(cx + S * 0.175, S * 0.300, S * 0.052, S * 0.021), fill=GOLD_HI)
    img.alpha_composite(star)
    return img


def gradient_vertical(size, top, bottom):
    img = Image.new("RGBA", (size, size))
    one = Image.new("RGB", (1, size))
    px = one.load()
    for y in range(size):
        px[0, y] = lerp(top, bottom, y / max(1, size - 1))
    return one.resize((size, size), Image.BILINEAR).convert("RGBA")


def make_legacy(size):
    ss = 4
    S = size * ss
    bg = gradient_vertical(S, EMERALD_TOP, EMERALD_BOT)
    # лёгкое свечение сверху
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([S * 0.05, -S * 0.45, S * 0.95, S * 0.55], fill=(30, 120, 88, 70))
    bg.alpha_composite(glow)
    bg.alpha_composite(emblem(S, scale=0.94))
    bg.putalpha(rounded_mask(S, int(S * 0.225)))
    return bg.resize((size, size), Image.LANCZOS)


def make_round(size):
    ss = 4
    S = size * ss
    bg = gradient_vertical(S, EMERALD_TOP, EMERALD_BOT)
    bg.alpha_composite(emblem(S, scale=0.86))
    bg.putalpha(circle_mask(S, S / 2, S / 2, S / 2 - 1))
    return bg.resize((size, size), Image.LANCZOS)


def make_foreground(size=432):
    ss = 4
    S = size * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    img.alpha_composite(emblem(S, scale=0.62))
    return img.resize((size, size), Image.LANCZOS)


def make_mono(size=432):
    ss = 4
    S = size * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    e = emblem(S, scale=0.62)
    # всё белым по альфе
    white = Image.new("RGBA", (S, S), (255, 255, 255, 255))
    white.putalpha(e.split()[3])
    img.alpha_composite(white)
    return img.resize((size, size), Image.LANCZOS)


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

if __name__ == "__main__":
    for d, size in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + d)
        os.makedirs(folder, exist_ok=True)
        make_legacy(size).save(os.path.join(folder, "ic_launcher.png"))
        make_round(size).save(os.path.join(folder, "ic_launcher_round.png"))
    os.makedirs(os.path.join(RES, "drawable-xxxhdpi"), exist_ok=True)
    make_foreground().save(os.path.join(RES, "drawable-xxxhdpi", "ic_launcher_foreground.png"))
    make_mono().save(os.path.join(RES, "drawable-xxxhdpi", "ic_launcher_mono.png"))
    print("Иконки готовы:", ", ".join(DENSITIES))
