#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Рисует иллюстрации для книг и превращает их в Android VectorDrawable.

Стиль: мягкий «мультяшный» рисунок — фигура человека, коврик, арка мечети.
Всё рисуется кодом, никаких картинок из PDF.

    python3 tools/ill/figures.py --preview      # лист превью (PNG) в tools/preview
    python3 tools/ill/figures.py --write        # готовые drawable/ill_*.xml в проект
"""
import argparse
import os
import sys
from xml.sax.saxutils import escape

import human as H

# ---------- палитра ----------

SKIN = "#E7B189"
SKIN_SH = "#CF9569"
HAIR = "#3A2A20"
WHITE = "#FBF8F1"
WHITE_SH = "#E4DCCB"
TROUSER = "#33414E"
TROUSER_SH = "#26313B"
CAP = "#F7F3E8"
CAP_SH = "#DED6C2"
DRESS = "#2F6076"          # женское платье
DRESS_SH = "#24495A"
SCARF = "#B2523C"          # платок
SCARF_SH = "#8E3F2E"
GOLD = "#C9A24A"
GOLD_L = "#E4CD8E"
GREEN = "#0F5A41"
GREEN_D = "#0A3F2D"
RUG = "#A63A32"
RUG_D = "#8A2C27"
RUG_G = "#D8B675"
WATER = "#5AA8D6"
WATER_L = "#9FD3EC"
CARD = "#F1E6D2"
CARD_D = "#E4D6BB"
ARCH = "#E9DCC1"
JUG = "#C9CDD2"
JUG_SH = "#A9AFB6"
OUTLINE = "#C9B79A"
STONE = "#CBBFA8"
STONE_SH = "#B3A58C"


class Svg:
    """Копилка фигур: элементы пишутся в порядке добавления (важен порядок слоёв)."""

    def __init__(self, w=100, h=100):
        self.w = w
        self.h = h
        self.parts = []

    # --- примитивы ---
    def path(self, d, fill=None, stroke=None, sw=0, cap="round", opacity=None):
        self.parts.append(("path", {"d": d, "fill": fill, "stroke": stroke,
                                    "sw": sw, "cap": cap, "opacity": opacity}))
        return self

    def circle(self, cx, cy, r, fill, opacity=None):
        d = ("M %g %g a %g %g 0 1 0 %g 0 a %g %g 0 1 0 -%g 0 Z"
             % (cx - r, cy, r, r, 2 * r, r, r, 2 * r))
        return self.path(d, fill=fill, opacity=opacity)

    def ellipse(self, cx, cy, rx, ry, fill, opacity=None):
        d = ("M %g %g a %g %g 0 1 0 %g 0 a %g %g 0 1 0 -%g 0 Z"
             % (cx - rx, cy, rx, ry, 2 * rx, rx, ry, 2 * rx))
        return self.path(d, fill=fill, opacity=opacity)

    def rect(self, x, y, w, h, fill, rx=0, opacity=None):
        if rx <= 0:
            d = "M %g %g H %g V %g H %g Z" % (x, y, x + w, y + h, x)
        else:
            d = ("M %g %g H %g A %g %g 0 0 1 %g %g V %g A %g %g 0 0 1 %g %g H %g "
                 "A %g %g 0 0 1 %g %g V %g A %g %g 0 0 1 %g %g Z"
                 % (x + rx, y, x + w - rx, rx, rx, x + w, y + rx, y + h - rx,
                    rx, rx, x + w - rx, y + h, x + rx,
                    rx, rx, x, y + h - rx, y + rx,
                    rx, rx, x + rx, y))
        return self.path(d, fill=fill, opacity=opacity)

    def limb(self, x1, y1, x2, y2, w, color, outline=None):
        if outline:
            self.path("M %g %g L %g %g" % (x1, y1, x2, y2), stroke=outline, sw=w + 1.8)
        return self.path("M %g %g L %g %g" % (x1, y1, x2, y2), stroke=color, sw=w)

    def disc(self, cx, cy, r, fill, outline=None):
        if outline:
            self.circle(cx, cy, r + 0.9, outline)
        return self.circle(cx, cy, r, fill)

    def line(self, x1, y1, x2, y2, w, color, cap="round"):
        return self.path("M %g %g L %g %g" % (x1, y1, x2, y2), stroke=color, sw=w, cap=cap)

    def shadow_fig(self, cx, cy, rx=20.0, ry=2.6, opacity=0.16):
        """Мягкая тень под человеком."""
        self.ellipse(cx, cy, rx, ry, "#6B5A46", opacity=opacity)

    # --- вывод ---
    def svg(self):
        out = ['<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" '
               'viewBox="0 0 %d %d">' % (self.w, self.h, self.w, self.h)]
        for kind, a in self.parts:
            attrs = ['d="%s"' % a["d"]]
            if a.get("fill"):
                attrs.append('fill="%s"' % a["fill"])
            else:
                attrs.append('fill="none"')
            if a.get("stroke"):
                attrs.append('stroke="%s"' % a["stroke"])
                attrs.append('stroke-width="%g"' % a["sw"])
                if a.get("cap"):
                    attrs.append('stroke-linecap="%s"' % a["cap"])
                    attrs.append('stroke-linejoin="round"')
            if a.get("opacity"):
                attrs.append('fill-opacity="%g" stroke-opacity="%g"' % (a["opacity"], a["opacity"]))
            out.append("<path %s/>" % " ".join(attrs))
        out.append("</svg>")
        return "\n".join(out)


# ---------- фон: карточка, арка и коврик ----------

def backdrop(s, arch=True, rug=True, floor=True):
    """Фон: светлая карточка, арка мечети, коврик и полоса пола."""
    s.rect(0, 0, 100, 100, CARD)
    if arch:
        # арка мечети: стрельчатый свод
        s.path("M 12 74 V 40 A 38 30 0 0 1 88 40 V 74 Z", fill=ARCH)
        s.path("M 18 74 V 41 A 32 25 0 0 1 82 41 V 74 Z", fill=CARD_D)
        s.path("M 22 74 V 42 A 28 21 0 0 1 78 42 V 74 Z", fill=CARD)
    if floor:
        s.rect(0, 82, 100, 18, "#E7DCC4")
    if rug:
        s.rect(10, 80, 80, 17, RUG, rx=4)
        s.rect(13, 82.5, 74, 12, RUG_D, rx=3)
        for i in range(4):
            x = 22 + i * 18
            s.path("M %g 85 L %g 88.5 L %g 92 L %g 88.5 Z" % (x, x + 6, x, x - 6), fill=RUG_G)
            s.path("M %g 86.5 L %g 88.5 L %g 90.5 L %g 88.5 Z" % (x, x + 3, x, x - 3), fill=RUG_D)
        s.path("M 10 80 H 90", stroke=GOLD_L, sw=0.8)


def shadow(s, cx, cy, rx=22, ry=3.0):
    s.ellipse(cx, cy, rx, ry, "#000000", opacity=0.07)


def jug(s, x, y, scale=1.0):
    """Кувшин для омовения."""
    s.rect(x - 4 * scale, y - 8 * scale, 8 * scale, 11 * scale, JUG, rx=3 * scale)
    s.path("M %g %g L %g %g L %g %g Z" % (x - 4 * scale, y - 8 * scale,
                                          x - 11 * scale, y - 15 * scale,
                                          x - 3 * scale, y - 1 * scale), fill=JUG_SH)
    s.ellipse(x, y + 3.4 * scale, 4.5 * scale, 1.8 * scale, JUG_SH)
    s.path("M %g %g C %g %g %g %g %g %g" % (x + 4 * scale, y - 6 * scale,
                                            x + 9 * scale, y - 5 * scale,
                                            x + 9 * scale, y, x + 4.6 * scale, y + 0.6 * scale),
           stroke=JUG_SH, sw=1.6 * scale)


def water(s, x, y, n=3, down=True):
    """Струйки воды из кувшина."""
    for i in range(n):
        dx = (i - (n - 1) / 2.0) * 2.2
        if down:
            s.path("M %g %g C %g %g %g %g %g %g"
                   % (x + dx, y, x + dx + 0.6, y + 4, x + dx - 0.6, y + 7, x + dx, y + 11),
                   stroke=WATER, sw=1.4, cap="round")
        else:
            s.path("M %g %g C %g %g %g %g %g %g"
                   % (x, y + dx, x - 4, y + dx + 0.6, x - 7, y + dx - 0.6, x - 11, y + dx),
                   stroke=WATER, sw=1.4, cap="round")
    s.circle(x, y + 13, 2.0, WATER_L)


def drops(s, x, y, n=4):
    for i in range(n):
        s.circle(x + (i % 2) * 3.2 - 1.6, y + i * 3.2, 1.1, WATER)


# ---------- фигура человека ----------

# ---------- позы: рисует движок human.py ----------

def cloth(man):
    return H.KAMIS if man else H.DRESS


def arm_at(s, sh, elbow, wrist, man, hand_size=2.7, palm=True):
    """Рука по трём точкам: короткий рукав у плеча, предплечье и кисть.

    Рукав рисуем более тёмным тоном и узким контуром — иначе на светлом халате
    остаётся пустой «овал» и рука не читается.
    """
    sleeve = H.SLEEVE if man else H.DRESS_D
    H.seg(s, sh, elbow, 3.2, sleeve, sw=0.6)
    H.seg(s, elbow, wrist, 2.6, H.SKIN, sw=0.6)
    H.hand(s, wrist, hand_size, palm)


def pose_niyet(s, man=True):
    """Ният перед намазом: стоим спокойно, руки опущены."""
    backdrop(s)
    H.draw_stand(s, man, hands="down")


def pose_takbir(s, man=True):
    """Вступительный такбир: ладони у ушей."""
    backdrop(s)
    H.draw_stand(s, man, hands="raise")


def pose_qiyam(s, man=True):
    """Кыям: руки сложены (мужчина ниже пупка, женщина на груди)."""
    backdrop(s)
    H.draw_stand(s, man, hands="fold")


def pose_ruku(s, man=True):
    """Поясной поклон."""
    backdrop(s)
    H.draw_ruku(s, man)


def pose_sajda(s, man=True):
    """Земной поклон."""
    backdrop(s)
    s.rect(28, 80, 44, 3.2, RUG_G, rx=1.6)
    H.draw_sajda(s, man)


def pose_sit(s, man=True, hands="knees", turn=0.0):
    """Сидя на коленях: ташаххуд, дуа, салам."""
    backdrop(s)
    H.draw_sit(s, man, hands=hands, turn=turn)


def abl_scene(s, man=True):
    """Общая сцена омовения: коврик, чаша и кувшин."""
    backdrop(s, arch=True, rug=True)
    H.basin(s, 72, 66, w=30, h=8)
    H.jug(s, 78, 62, 1.05)


def wudu(s, man=True, part="hands"):
    """Омовение: человек стоит у чаши, вода льётся на нужную часть тела."""
    abl_scene(s, man)
    b = H.draw_stand(s, man, hands="none")   # руки рисуем сами: по одной нужной
    sh1, sh2, neck = b["sh1"], b["sh2"], b["neck"]
    hip = b["hip1"][1]
    if part == "hands":
        arm_at(s, sh2, (sh2[0] + 4.5, sh2[1] + 8.0), (60.4, 55.0), man, 2.0, palm=False)
        arm_at(s, sh1, (sh1[0] + 3.5, sh1[1] + 9.0), (55.6, 57.0), man, 2.0, palm=False)
        H.cupped_hands(s, (58.4, 57.6))
        H.jug(s, 76, 50, 0.95)
        H.water(s, 70.0, 52.0, 55.6)
        H.drops(s, 62.0, 61.6, 3, spread=5.0)
    elif part == "mouth":
        arm_at(s, sh2, (sh2[0] - 0.6, sh2[1] + 6.4), (neck[0] + 1.6, neck[1] - 9.4), man, 2.4)
        H.water(s, neck[0] + 3.0, neck[1] - 12.0, neck[1] - 8.6, 2.0)
        H.sparkle(s, neck[0] + 2.4, neck[1] - 6.0)
    elif part == "nose":
        arm_at(s, sh2, (sh2[0] - 0.6, sh2[1] + 5.6), (neck[0] + 1.2, neck[1] - 11.0), man, 2.4)
        H.water(s, neck[0] + 3.4, neck[1] - 13.0, neck[1] - 10.4, 1.8)
        H.sparkle(s, neck[0] + 2.0, neck[1] - 8.4)
    elif part == "face":
        arm_at(s, sh1, (sh1[0] - 0.6, sh1[1] + 6.4), (neck[0] - 2.0, neck[1] - 10.6), man, 2.4)
        arm_at(s, sh2, (sh2[0] + 0.4, sh2[1] + 6.4), (neck[0] + 2.0, neck[1] - 10.6), man, 2.4)
        H.water(s, neck[0] + 4.0, neck[1] - 13.6, neck[1] - 9.0, 1.8)
        H.sparkle(s, neck[0] - 2.6, neck[1] - 8.0)
    elif part == "arm":
        # правый рукав засучен, вода льётся на предплечье
        arm_at(s, sh2, (sh2[0] + 5.0, sh2[1] + 8.0), (64.0, 55.0), man, 2.6)
        s.path("M %g %g L %g %g" % (sh2[0] + 3.6, sh2[1] + 6.4, 60.4, 53.4),
               stroke=H.KAMIS_D, sw=1.4)
        H.water(s, 64.0, 50.0, 55.0, 2.2)
        H.drops(s, 64.0, 58.0, 3, spread=5.0)
    elif part == "head":
        arm_at(s, sh1, (sh1[0] - 0.4, sh1[1] + 5.6), (neck[0] - 3.0, neck[1] - 13.0), man, 2.4)
        arm_at(s, sh2, (sh2[0] + 0.4, sh2[1] + 5.6), (neck[0] + 3.0, neck[1] - 13.0), man, 2.4)
        H.water(s, neck[0] + 4.4, neck[1] - 16.0, neck[1] - 12.4, 1.8)
        H.sparkle(s, neck[0] + 2.0, neck[1] - 10.0)
    elif part == "ear":
        arm_at(s, sh2, (sh2[0] - 0.4, sh2[1] + 4.6), (neck[0] + 6.0, neck[1] - 8.0), man, 2.4)
        H.water(s, neck[0] + 8.0, neck[1] - 12.0, neck[1] - 9.0, 1.8)
        H.sparkle(s, neck[0] + 6.4, neck[1] - 6.6)
    elif part == "foot":
        # сидя на скамье: правая стопа на краю чаши, вода льётся на неё
        s.rect(28, 72, 32, 5, "#C9B99A", rx=2)
        s.rect(30, 77, 4, 6, "#B5A488", rx=1)
        s.rect(54, 77, 4, 6, "#B5A488", rx=1)
        H.draw_sit(s, man, hands="none")
        arm_at(s, (56.0, 42.0), (60.0, 50.0), (64.0, 58.0), man, 2.0)
        H.shoe(s, (62.0, 62.0), (68.0, 59.0))
        H.water(s, 66.0, 50.0, 57.0, 2.2)
        H.drops(s, 66.0, 63.0, 3, spread=5.0)
    H.sparkle(s, 74.0, 46.0, 0.8, "#A9D6EE")


def tayammum(s, man=True, part="intent"):
    """Тейеммум: вместо воды — чистый песок."""
    backdrop(s, arch=True, rug=False)
    s.shadow_fig(46, 87, rx=30)
    s.path("M 18 88 C 22 78 40 74 58 76 C 72 77 80 82 82 88 Z", fill="#E4D3AE", stroke=H.INK, sw=1.0)
    s.path("M 24 86 C 30 80 44 78 58 80" , stroke="#D2BE93", sw=1.0)
    for i in range(7):
        s.circle(26 + i * 8, 84 - (i % 2) * 1.6, 0.9, "#CDB98E")
    if part == "intent":
        H.draw_stand(s, man, hands="palms")
    elif part == "hands":
        H.draw_sit(s, man, hands="none")
        sh1, sh2 = (42.6, 53.0), (56.6, 53.0)
        arm_at(s, sh1, (sh1[0] - 3.0, sh1[1] + 12.0), (44.0, 80.0), man, 2.5, palm=False)
        arm_at(s, sh2, (sh2[0] - 1.0, sh1[1] + 13.0), (54.0, 81.4), man, 2.5, palm=False)
        for x in (44.0, 54.0):
            for k in (-1, 0, 1):
                s.line(x + k * 1.4, 82.6, x + k * 1.4, 84.4, 0.6, "#C6B183")
    elif part == "face":
        H.draw_sit(s, man, hands="none")
        sh1, sh2, neck = (42.6, 53.0), (56.6, 53.0), (49.8, 49.8)
        arm_at(s, sh1, (sh1[0] + 2.0, sh1[1] + 8.0), (neck[0] - 2.2, neck[1] - 10.0), man, 2.4)
        arm_at(s, sh2, (sh2[0] - 1.0, sh2[1] + 8.0), (neck[0] + 2.2, neck[1] - 10.0), man, 2.4)
        for (dx, dy) in ((-3.2, -11.0), (2.6, -12.0), (0.4, -9.0), (-1.6, -13.4)):
            s.circle(neck[0] + dx, neck[1] + dy, 0.75, "#D8C79B")
    else:   # arm
        H.draw_sit(s, man, hands="none")
        sh1, sh2 = (42.6, 53.0), (56.6, 53.0)
        arm_at(s, sh2, (sh2[0] + 3.0, sh2[1] + 10.0), (60.0, 74.0), man, 2.5, palm=False)
        s.path("M %g %g L %g %g" % (sh2[0] + 5.0, sh2[1] + 8.4, 58.0, 72.0), stroke=H.KAMIS_D, sw=1.4)
        arm_at(s, sh1, (sh1[0] + 4.0, sh1[1] + 12.0), (56.0, 70.0), man, 2.5)
        for (dx, dy) in ((0, -3), (2.4, -1.6), (-2.0, -2.4)):
            s.circle(58.0 + dx, 71.0 + dy, 0.7, "#D8C79B")


# ---------- список всех иллюстраций ----------

def build():
    """name -> (заголовок по-туркменски, функция рисования)."""
    figs = {}
    figs["pose_niet_m"] = ("Niýet edildi — erkek kişi", lambda s: pose_niyet(s, True))
    figs["pose_niet_w"] = ("Niýet edildi — aýal kişi", lambda s: pose_niyet(s, False))
    figs["pose_takbir_m"] = ("Tahrim tekbiri — erkek kişi", lambda s: pose_takbir(s, True))
    figs["pose_takbir_w"] = ("Tahrim tekbiri — aýal kişi", lambda s: pose_takbir(s, False))
    figs["pose_stand_m"] = ("Kyýam — erkek kişi", lambda s: pose_qiyam(s, True))
    figs["pose_stand_w"] = ("Kyýam — aýal kişi", lambda s: pose_qiyam(s, False))
    figs["pose_ruku_m"] = ("Rukug — erkek kişi", lambda s: pose_ruku(s, True))
    figs["pose_ruku_w"] = ("Rukug — aýal kişi", lambda s: pose_ruku(s, False))
    figs["pose_sajda_m"] = ("Säjdä — erkek kişi", lambda s: pose_sajda(s, True))
    figs["pose_sajda_w"] = ("Säjdä — aýal kişi", lambda s: pose_sajda(s, False))
    figs["pose_sit_m"] = ("Oturyş (täşehhüt) — erkek kişi", lambda s: pose_sit(s, True))
    figs["pose_sit_w"] = ("Oturyş (täşehhüt) — aýal kişi", lambda s: pose_sit(s, False))
    figs["pose_dua_m"] = ("Doga — erkek kişi", lambda s: pose_sit(s, True, hands="up"))
    figs["pose_dua_w"] = ("Doga — aýal kişi", lambda s: pose_sit(s, False, hands="up"))
    figs["pose_salam_r"] = ("Salam — saga", lambda s: pose_sit(s, True, turn=1.0))
    figs["pose_salam_l"] = ("Salam — sola", lambda s: pose_sit(s, True, turn=-1.0))
    figs["pose_salam_r_w"] = ("Salam — saga (aýal)", lambda s: pose_sit(s, False, turn=1.0))
    figs["pose_salam_l_w"] = ("Salam — sola (aýal)", lambda s: pose_sit(s, False, turn=-1.0))

    figs["abl_intent"] = ("Täret: niýet", lambda s: pose_niyet(s, True))
    figs["abl_intent_w"] = ("Täret: niýet (aýal)", lambda s: pose_niyet(s, False))
    figs["abl_hands"] = ("Täret: elleri ýuwmak", lambda s: wudu(s, True, "hands"))
    figs["abl_mouth"] = ("Täret: agzy çaýkamak", lambda s: wudu(s, True, "mouth"))
    figs["abl_nose"] = ("Täret: burny ýuwmak", lambda s: wudu(s, True, "nose"))
    figs["abl_face"] = ("Täret: ýüzi ýuwmak", lambda s: wudu(s, True, "face"))
    figs["abl_arm"] = ("Täret: goly ýuwmak", lambda s: wudu(s, True, "arm"))
    figs["abl_head"] = ("Täret: başa mesh etmek", lambda s: wudu(s, True, "head"))
    figs["abl_ear"] = ("Täret: gulaga mesh etmek", lambda s: wudu(s, True, "ear"))
    figs["abl_foot"] = ("Täret: aýagy ýuwmak", lambda s: wudu(s, True, "foot"))
    figs["abl_dua"] = ("Täretden soň doga", lambda s: pose_sit(s, True, hands="up"))

    figs["tay_intent"] = ("Teýemmüm: niýet", lambda s: tayammum(s, True, "intent"))
    figs["tay_hands"] = ("Teýemmüm: elleri topraga urmak", lambda s: tayammum(s, True, "hands"))
    figs["tay_face"] = ("Teýemmüm: ýüze mesh", lambda s: tayammum(s, True, "face"))
    figs["tay_arm"] = ("Teýemmüm: gola mesh", lambda s: tayammum(s, True, "arm"))

    return figs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--only", default="")
    a = ap.parse_args()
    figs = build()
    names = [n for n in figs if not a.only or a.only in n]
    print("иллюстраций:", len(names))

    if a.write:
        import converter
        out_dir = os.path.join(os.path.dirname(__file__), "..", "..",
                               "app", "src", "main", "res", "drawable")
        out_dir = os.path.abspath(out_dir)
        os.makedirs(out_dir, exist_ok=True)
        for n in names:
            s = Svg()
            figs[n][1](s)
            path = os.path.join(out_dir, "ill_%s.xml" % n)
            with open(path, "w", encoding="utf-8") as f:
                f.write(converter.to_vectordrawable(s.svg(), "ill_%s" % n, 100))
        print("записано в", out_dir)

    if a.preview:
        import subprocess
        out = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "preview", "suratlar.png")
        subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), "preview.py"),
                        "--out", os.path.abspath(out)], check=True)


if __name__ == "__main__":
    main()
