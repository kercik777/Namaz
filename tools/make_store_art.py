#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Арт для Google Play: значок 512×512 и графический баннер 1024×500.
Использует ту же эмблему, что и иконка приложения (tools/make_icons.py).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw  # noqa: E402

import make_icons as MI  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "play")


def icon_512():
    size = 512
    img = Image.new("RGB", (size, size), MI.EMERALD_TOP)
    base = MI.gradient_vertical(size, MI.EMERALD_TOP, MI.EMERALD_BOT)
    mask = MI.rounded_mask(size, int(size * 0.22))
    img.paste(base, (0, 0), mask)
    emb = MI.emblem(size, scale=0.62)
    img.paste(emb, (0, 0), emb)
    img.save(os.path.join(OUT, "icon-512.png"))
    print("icon-512.png готов")


def feature_graphic():
    w, h = 1024, 500
    base = MI.gradient_vertical(h, MI.EMERALD_TOP, MI.EMERALD_BOT).resize((w, h))
    img = Image.new("RGB", (w, h), MI.EMERALD_TOP)
    img.paste(base, (0, 0))
    d = ImageDraw.Draw(img, "RGBA")
    # лёгкое золотое свечение справа
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for i in range(28):
        r = 330 - i * 10
        a = int(5 + i * 1.2)
        gd.ellipse([w - 120 - r // 2, h // 2 - r // 2, w - 120 + r // 2, h // 2 + r // 2],
                   fill=(240, 217, 160, a))
    img.paste(Image.alpha_composite(img.convert("RGBA"), glow).convert("RGB"), (0, 0))
    d = ImageDraw.Draw(img, "RGBA")

    emb = MI.emblem(300, scale=0.5)
    img.paste(emb, (72, (h - 300) // 2), emb)

    try:
        from PIL import ImageFont
        serif = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
        sans = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        f_title = ImageFont.truetype(serif, 62) if os.path.exists(serif) else None
        f_tag = ImageFont.truetype(sans, 30) if os.path.exists(sans) else None
        f_small = ImageFont.truetype(sans, 24) if os.path.exists(sans) else None
    except Exception:  # noqa: BLE001
        f_title = f_tag = f_small = None

    tx = 412
    if f_title:
        d.text((tx, 170), "Akyda we Namaz", font=f_title, fill=(247, 241, 227))
        d.line([tx + 2, 258, tx + 230, 258], fill=(224, 190, 108, 220), width=4)
        if f_tag:
            d.text((tx, 286), "Namaz kitaby • Akyda", font=f_tag, fill=(224, 190, 108))
            d.text((tx, 330), "Doly tekst • Asyl skanlar • Of-laýn", font=f_small,
                   fill=(198, 210, 203))
    else:
        d.text((tx, 200), "Akyda we Namaz", fill=(247, 241, 227))
    img.save(os.path.join(OUT, "feature-graphic.png"))
    print("feature-graphic.png готов")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    icon_512()
    feature_graphic()
