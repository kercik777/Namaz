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
from xml.sax.saxutils import escape

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
CARD = "#F6EEDD"
CARD_D = "#EADFC7"
ARCH = "#EFE3CC"
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

    def line(self, x1, y1, x2, y2, w, color):
        return self.path("M %g %g L %g %g" % (x1, y1, x2, y2), stroke=color, sw=w)

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

class Pal:
    def __init__(self, gender="man"):
        self.gender = gender
        if gender == "man":
            self.body = WHITE
            self.body_sh = WHITE_SH
            self.legs = TROUSER
            self.legs_sh = TROUSER_SH
            self.headwear = CAP
            self.headwear_sh = CAP_SH
        else:
            self.body = DRESS
            self.body_sh = DRESS_SH
            self.legs = DRESS
            self.legs_sh = DRESS_SH
            self.headwear = SCARF
            self.headwear_sh = SCARF_SH


def head(s, x, y, r, pal, turn=0.0):
    """Голова: шея, лицо, глаза, уши, головной убор."""
    ex = x + turn
    # шея
    s.limb(x, y + r * 0.5, x, y + r + 3.0, r * 0.85, SKIN, outline=OUTLINE)
    if pal.gender == "woman":
        # платок вокруг лица
        s.ellipse(ex, y + 1.0, r + 3.4, r + 3.0, pal.headwear)
        s.path("M %g %g C %g %g %g %g %g %g L %g %g Z"
               % (x - r - 3.0, y + 1.4, x - r - 5.0, y + r + 12, x + r + 5.0, y + r + 12,
                  x + r + 3.0, y + 1.4, x + r + 3.0, y + 1.4), fill=pal.headwear_sh)
        s.disc(ex, y, r, SKIN, outline=OUTLINE)
        s.path("M %g %g A %g %g 0 0 1 %g %g Z"
               % (ex - r - 0.4, y - 0.6, r + 0.4, r + 0.6, ex + r + 0.4, y - 0.6), fill=pal.headwear)
        # обрамление платка у подбородка
        s.path("M %g %g C %g %g %g %g %g %g" % (ex - r - 3.2, y + 1.6, ex - r - 3.4, y + r + 10,
                                                ex + r + 3.4, y + r + 10, ex + r + 3.2, y + 1.6),
               fill=pal.headwear)
        s.circle(ex, y, r - 0.6, SKIN)
    else:
        s.disc(ex, y, r, SKIN, outline=OUTLINE)
        s.circle(ex - r * 0.9, y + r * 0.25, r * 0.28, SKIN_SH)   # ухо
        s.path("M %g %g A %g %g 0 0 1 %g %g Z"
               % (ex - r - 0.8, y - 0.6, r + 0.8, r + 0.8, ex + r + 0.8, y - 0.6), fill=CAP)
        s.path("M %g %g H %g V %g H %g Z" % (ex - r - 1.6, y - 1.0, ex + r + 1.6, y + 1.6,
                                             ex - r - 1.6), fill=CAP_SH)
    s.circle(ex - r * 0.36, y + 0.4, 0.7, "#2B2B2B")
    s.circle(ex + r * 0.36, y + 0.4, 0.7, "#2B2B2B")
    s.path("M %g %g C %g %g %g %g %g %g" % (ex - r * 0.3, y + r * 0.55, ex - r * 0.1, y + r * 0.7,
                                            ex + r * 0.1, y + r * 0.7, ex + r * 0.3, y + r * 0.55),
           stroke="#B07A5A", sw=0.7, cap="round")


def tunic(s, pal, hip, shoulder, w=18):
    """Верхняя одежда: широкий рукав-туника от бёдер к плечам."""
    s.limb(hip[0], hip[1], shoulder[0], shoulder[1], w, pal.body, outline=OUTLINE)
    s.limb(hip[0] + 2.5, hip[1], shoulder[0] + 2.5, shoulder[1], w * 0.35, pal.body_sh)


def legs_standing(s, pal, cx, hip_y, foot_y, spread=4.0):
    s.limb(cx - spread * 0.55, hip_y, cx - spread, foot_y, 9, pal.legs, outline=OUTLINE)
    s.limb(cx + spread * 0.55, hip_y, cx + spread, foot_y, 9, pal.legs, outline=OUTLINE)
    s.limb(cx - spread * 0.35, hip_y, cx - spread * 0.8, foot_y, 3, pal.legs_sh)
    # ступни
    s.path("M %g %g H %g L %g %g H %g Z" % (cx - spread - 4, foot_y + 4.5, cx - spread + 4,
                                            cx - spread + 3, foot_y - 0.5, cx - spread - 4),
           fill=GOLD)
    s.path("M %g %g H %g L %g %g H %g Z" % (cx + spread - 4, foot_y + 4.5, cx + spread + 4.5,
                                            cx + spread + 3.5, foot_y - 0.5, cx + spread - 4),
           fill=GOLD)


def arm(s, pal, from_, to, sleeve=True, hand_at=None, w=6.5):
    s.limb(from_[0], from_[1], to[0], to[1], w, pal.body if sleeve else SKIN,
           outline=OUTLINE)
    if hand_at:
        s.disc(hand_at[0], hand_at[1], 3.4, SKIN, outline=OUTLINE)


def palms_up(s, x1, y1, x2, y2):
    """Раскрытые ладони (две руки рядом или по бокам)."""
    s.ellipse(x1, y1, 5.0, 5.8, OUTLINE)
    s.ellipse(x1, y1, 4.4, 5.2, SKIN)
    s.ellipse(x2, y2, 5.0, 5.8, OUTLINE)
    s.ellipse(x2, y2, 4.4, 5.2, SKIN)
    s.path("M %g %g H %g" % (x1 - 3, y1 - 2.6, x1 + 3), stroke=SKIN_SH, sw=0.7, cap="round")
    s.path("M %g %g H %g" % (x2 - 3, y2 - 2.6, x2 + 3), stroke=SKIN_SH, sw=0.7, cap="round")


# ---------- позы намаза ----------

def pose_stand(s, pal, hands="down", arch=True):
    """Стоя (кыям)."""
    backdrop(s, arch=arch)
    shadow(s, 50, 84)
    legs_standing(s, pal, 50, 62, 80)
    tunic(s, pal, (50, 62), (50, 42), 18)
    if hands == "fold":
        arm(s, pal, (42, 45), (46, 55), hand_at=None)
        arm(s, pal, (58, 45), (54, 55), hand_at=None)
        s.circle(50, 56, 4.2, SKIN)
    elif hands == "down":
        arm(s, pal, (42, 45), (40, 60), hand_at=(40, 61))
        arm(s, pal, (58, 45), (60, 60), hand_at=(60, 61))
    elif hands == "open":
        arm(s, pal, (42, 45), (38, 56), hand_at=None)
        arm(s, pal, (58, 45), (62, 56), hand_at=None)
        palms_up(s, 38, 58, 62, 58)
    head(s, 50, 32, 8.2, pal)


def pose_takbir(s, pal):
    """Такбир: ладони у ушей."""
    backdrop(s)
    shadow(s, 50, 84)
    legs_standing(s, pal, 50, 62, 80)
    tunic(s, pal, (50, 62), (50, 42), 18)
    if pal.gender == "man":
        arm(s, pal, (42, 45), (33, 39))
        arm(s, pal, (58, 45), (67, 39))
        arm(s, pal, (33, 39), (40, 27), w=6.0, sleeve=False)
        arm(s, pal, (67, 39), (60, 27), w=6.0, sleeve=False)
    else:
        arm(s, pal, (42, 45), (34, 38))
        arm(s, pal, (58, 45), (66, 38))
        arm(s, pal, (34, 38), (41, 29), w=6.0, sleeve=False)
        arm(s, pal, (66, 38), (59, 29), w=6.0, sleeve=False)
    s.circle(41.5, 25.5, 3.6, SKIN)
    s.circle(58.5, 25.5, 3.6, SKIN)
    head(s, 50, 32, 8.2, pal)


def pose_ruku(s, pal):
    """Поясной поклон."""
    backdrop(s)
    s.limb(46, 62, 46, 80, 9, pal.legs)
    s.limb(56, 62, 56, 80, 9, pal.legs)
    s.path("M 38 84.5 H 51 L 50 79.5 H 38 Z", fill=GOLD)
    s.path("M 51 84.5 H 63 L 63 79.5 H 52 Z", fill=GOLD)
    # спина: от бёдер вперёд вверх
    s.limb(51, 60, 70, 46, 18, pal.body)
    s.limb(53, 61, 71, 48, 6, pal.body_sh)
    # руки к коленям
    arm(s, pal, (66, 48), (61, 60), hand_at=(60, 61))
    arm(s, pal, (69, 49), (64, 61), hand_at=(63, 62))
    head(s, 75, 44, 7.6, pal, turn=1.2)


def pose_sajda(s, pal):
    """Земной поклон."""
    backdrop(s)
    s.rect(30, 78, 34, 4.5, RUG_G, rx=2)
    # голени и ступни
    s.limb(58, 76, 66, 60, 11, pal.legs)
    s.limb(66, 60, 68, 74, 9, pal.legs)
    s.path("M 63 79 H 73 L 72 74 H 63 Z", fill=GOLD)
    # спина к полу
    s.limb(64, 62, 50, 70, 17, pal.body)
    s.limb(63, 64, 51, 71, 6, pal.body_sh)
    # руки на коврике
    arm(s, pal, (56, 68), (44, 76), hand_at=(43, 77), w=6.0)
    arm(s, pal, (58, 70), (47, 78), hand_at=(46, 79), w=6.0)
    head(s, 45, 74, 7.4, pal, turn=-1.0)


def pose_sit(s, pal, hands="knees", turn=0.0, arch=True):
    """Сидя на коленях (ташаххуд, дуа)."""
    backdrop(s, arch=arch)
    shadow(s, 50, 83)
    # сложенные ноги
    s.rect(36, 72, 30, 10, pal.legs, rx=3)
    s.rect(38, 66, 26, 9, pal.legs_sh, rx=3)
    s.path("M 34 82 H 47 L 46 77 H 34 Z", fill=GOLD)
    s.path("M 52 82 H 65 L 65 77 H 53 Z", fill=GOLD)
    tunic(s, pal, (51, 64), (51, 42), 18)
    if hands == "knees":
        arm(s, pal, (43, 46), (44, 62), hand_at=(45, 63))
        arm(s, pal, (59, 46), (58, 62), hand_at=(57, 63))
    elif hands == "up":
        arm(s, pal, (43, 46), (41, 58), hand_at=None)
        arm(s, pal, (59, 46), (61, 58), hand_at=None)
        palms_up(s, 41, 60, 61, 60)
    elif hands == "lap":
        arm(s, pal, (43, 46), (48, 60), hand_at=(49, 61))
        arm(s, pal, (59, 46), (54, 60), hand_at=(53, 61))
    head(s, 51, 32, 8.2, pal, turn=turn)


def pose_salam(s, pal, right=True):
    pose_sit(s, pal, hands="knees", turn=3.4 if right else -3.4, arch=False)


# ---------- омовение ----------

def abl_stand(s, pal):
    """Ният перед омовением: стоя, ладони раскрыты."""
    backdrop(s)
    legs_standing(s, pal, 50, 62, 80)
    tunic(s, pal, (50, 62), (50, 42), 18)
    arm(s, pal, (42, 45), (39, 56), hand_at=None)
    arm(s, pal, (58, 45), (61, 56), hand_at=None)
    palms_up(s, 39, 58, 61, 58)
    head(s, 50, 32, 8.2, pal)


def abl_sit(s, pal, part="hands", turn=0.0):
    """Омовение сидя: перед человеком кувшин, вода льётся на нужную часть тела."""
    backdrop(s, arch=True, rug=False)
    shadow(s, 44, 86)
    s.rect(24, 74, 44, 12, "#DCCFB4", rx=4)
    s.rect(26, 76, 40, 8, CARD_D, rx=3)
    jug(s, 78, 70, 1.1)
    # сложенные ноги
    s.rect(34, 70, 26, 9, pal.legs, rx=3)
    s.path("M 32 79 H 44 L 43 75 H 32 Z", fill=GOLD)
    tunic(s, pal, (47, 64), (47, 44), 17)
    head(s, 47, 34, 8.0, pal, turn=turn)

    hx, hy = 47, 44          # плечи
    if part == "hands":
        arm(s, pal, (hx - 8, hy + 2), (hx + 4, hy + 14))
        arm(s, pal, (hx + 8, hy + 2), (hx + 10, hy + 12))
        s.circle(hx + 6, hy + 17, 3.6, SKIN)
        s.circle(hx + 11, hy + 14, 3.6, SKIN)
        water(s, hx + 12, hy + 18, 3)
    elif part == "mouth":
        arm(s, pal, (hx + 8, hy + 2), (hx + 12, hy - 6), hand_at=(hx + 11, hy - 8))
        water(s, hx + 6, hy - 14, 2)
        drops(s, hx + 4, hy - 18, 3)
    elif part == "nose":
        arm(s, pal, (hx + 8, hy + 2), (hx + 13, hy - 4), hand_at=(hx + 12, hy - 6))
        water(s, hx + 6, hy - 10, 2)
        drops(s, hx + 4, hy - 14, 3)
    elif part == "face":
        arm(s, pal, (hx - 8, hy + 2), (hx - 2, hy - 6), hand_at=(hx, hy - 8))
        arm(s, pal, (hx + 8, hy + 2), (hx + 4, hy - 6), hand_at=(hx + 4, hy - 8))
        water(s, hx + 2, hy - 12, 3)
    elif part == "arm":
        arm(s, pal, (hx - 8, hy + 2), (hx + 8, hy + 6), w=6.0, sleeve=False)
        s.circle(hx + 10, hy + 7, 3.6, SKIN)
        arm(s, pal, (hx + 8, hy + 2), (hx + 12, hy + 10), hand_at=(hx + 12, hy + 11))
        water(s, hx + 12, hy + 12, 3)
    elif part == "head":
        arm(s, pal, (hx - 8, hy + 2), (hx - 4, hy - 10), hand_at=(hx - 3, hy - 12))
        arm(s, pal, (hx + 8, hy + 2), (hx + 4, hy - 10), hand_at=(hx + 3, hy - 12))
        water(s, hx, hy - 16, 2)
    elif part == "ear":
        arm(s, pal, (hx + 8, hy + 2), (hx + 11, hy - 10), hand_at=(hx + 11, hy - 12))
        water(s, hx + 8, hy - 16, 2)
    elif part == "foot":
        arm(s, pal, (hx + 8, hy + 2), (hx + 14, hy + 16), hand_at=(hx + 14, hy + 18))
        s.limb(hx + 6, 66, hx + 16, 74, 7, pal.legs)
        s.circle(hx + 19, 76, 3.6, SKIN)
        water(s, hx + 18, 78, 2)


def tay(s, pal, part="intent"):
    """Тейеммум: вместо воды — чистый песок/камень."""
    backdrop(s, arch=True, rug=False)
    shadow(s, 44, 87)
    s.rect(20, 76, 60, 11, STONE, rx=4)
    s.rect(23, 78, 54, 7, STONE_SH, rx=3)
    for i in range(5):
        s.circle(28 + i * 11, 81, 1.2, "#9C8E74")
    s.rect(32, 72, 30, 8, pal.legs, rx=3)
    tunic(s, pal, (47, 64), (47, 44), 17)
    head(s, 47, 34, 8.0, pal)
    hx, hy = 47, 44
    if part == "intent":
        arm(s, pal, (hx - 8, hy + 2), (hx - 11, hy + 14), hand_at=None)
        arm(s, pal, (hx + 8, hy + 2), (hx + 11, hy + 14), hand_at=None)
        palms_up(s, hx - 11, hy + 16, hx + 11, hy + 16)
    elif part == "hands":
        arm(s, pal, (hx - 8, hy + 2), (hx - 6, hy + 24), hand_at=(hx - 6, hy + 26))
        arm(s, pal, (hx + 8, hy + 2), (hx + 6, hy + 24), hand_at=(hx + 6, hy + 26))
        for i in range(3):
            s.circle(hx - 6 + i * 1.6, hy + 29 + (i % 2) * 1.6, 0.9, STONE_SH)
    elif part == "face":
        arm(s, pal, (hx - 8, hy + 2), (hx - 4, hy - 8), hand_at=(hx - 3, hy - 10))
        arm(s, pal, (hx + 8, hy + 2), (hx + 4, hy - 8), hand_at=(hx + 3, hy - 10))
    else:  # arm
        arm(s, pal, (hx + 8, hy + 2), (hx + 14, hy + 8), w=6.0, sleeve=False)
        arm(s, pal, (hx - 8, hy + 2), (hx - 4, hy + 12), hand_at=(hx - 4, hy + 14))
        s.circle(hx + 16, hy + 9, 3.6, SKIN)


# ---------- список всех иллюстраций ----------

def build():
    """name -> (заголовок по-туркменски, функция рисования)."""
    figs = {}
    man = Pal("man")
    woman = Pal("woman")

    figs["pose_niet_m"] = ("Niýet edildi — erkek kişi", lambda s: pose_stand(s, man, hands="down"))
    figs["pose_niet_w"] = ("Niýet edildi — aýal kişi", lambda s: pose_stand(s, woman, hands="down"))
    figs["pose_takbir_m"] = ("Tahrim tekbiri — erkek kişi", lambda s: pose_takbir(s, man))
    figs["pose_takbir_w"] = ("Tahrim tekbiri — aýal kişi", lambda s: pose_takbir(s, woman))
    figs["pose_stand_m"] = ("Kyýam — dik duruş", lambda s: pose_stand(s, man, hands="fold"))
    figs["pose_stand_w"] = ("Kyýam — aýal kişi", lambda s: pose_stand(s, woman, hands="fold"))
    figs["pose_ruku_m"] = ("Rukug — bil baglamak", lambda s: pose_ruku(s, man))
    figs["pose_ruku_w"] = ("Rukug — aýal kişi", lambda s: pose_ruku(s, woman))
    figs["pose_sajda_m"] = ("Säjdä — ýere baş goýmak", lambda s: pose_sajda(s, man))
    figs["pose_sajda_w"] = ("Säjdä — aýal kişi", lambda s: pose_sajda(s, woman))
    figs["pose_sit_m"] = ("Oturyş — täşehhüt", lambda s: pose_sit(s, man))
    figs["pose_sit_w"] = ("Oturyş — aýal kişi", lambda s: pose_sit(s, woman))
    figs["pose_dua_m"] = ("Doga — eller açyk", lambda s: pose_sit(s, man, hands="up"))
    figs["pose_dua_w"] = ("Doga — aýal kişi", lambda s: pose_sit(s, woman, hands="up"))
    figs["pose_salam_r"] = ("Salam — saga", lambda s: pose_salam(s, man, right=True))
    figs["pose_salam_l"] = ("Salam — sola", lambda s: pose_salam(s, man, right=False))
    figs["pose_salam_r_w"] = ("Salam — saga (aýal)", lambda s: pose_salam(s, woman, right=True))
    figs["pose_salam_l_w"] = ("Salam — sola (aýal)", lambda s: pose_salam(s, woman, right=False))

    figs["abl_intent"] = ("Täret: niýet", lambda s: abl_stand(s, man))
    figs["abl_intent_w"] = ("Täret: niýet (aýal)", lambda s: abl_stand(s, woman))
    figs["abl_hands"] = ("Täret: elleri ýuwmak", lambda s: abl_sit(s, man, "hands"))
    figs["abl_mouth"] = ("Täret: agzy çaýkamak", lambda s: abl_sit(s, man, "mouth"))
    figs["abl_nose"] = ("Täret: burny ýuwmak", lambda s: abl_sit(s, man, "nose"))
    figs["abl_face"] = ("Täret: ýüzi ýuwmak", lambda s: abl_sit(s, man, "face"))
    figs["abl_arm"] = ("Täret: goly ýuwmak", lambda s: abl_sit(s, man, "arm"))
    figs["abl_head"] = ("Täret: başa mesh etmek", lambda s: abl_sit(s, man, "head"))
    figs["abl_ear"] = ("Täret: gulaga mesh etmek", lambda s: abl_sit(s, man, "ear"))
    figs["abl_foot"] = ("Täret: aýagy ýuwmak", lambda s: abl_sit(s, man, "foot"))
    figs["abl_dua"] = ("Täretden soň doga", lambda s: abl_stand(s, man))

    figs["tay_intent"] = ("Teýemmüm: niýet", lambda s: tay(s, man, "intent"))
    figs["tay_hands"] = ("Teýemmüm: elleri topraga urmak", lambda s: tay(s, man, "hands"))
    figs["tay_face"] = ("Teýemmüm: ýüze mesh", lambda s: tay(s, man, "face"))
    figs["tay_arm"] = ("Teýemmüm: gola mesh", lambda s: tay(s, man, "arm"))

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
        import pymupdf
        from PIL import Image, ImageDraw
        cols, thumb, pad, lab = 7, 150, 6, 14
        rows = (len(names) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * (thumb + pad) + pad, rows * (thumb + lab + pad) + pad),
                          (246, 243, 236))
        d = ImageDraw.Draw(sheet)
        os.makedirs(os.path.join(os.path.dirname(__file__), "..", "preview"), exist_ok=True)
        for i, n in enumerate(names):
            s = Svg()
            figs[n][1](s)
            doc = pymupdf.open("svg", s.svg().encode("utf-8"))
            pix = doc[0].get_pixmap(matrix=pymupdf.Matrix(thumb / 100.0 * 2, thumb / 100.0 * 2))
            im = Image.frombytes("RGB", (pix.width, pix.height), pix.samples).resize((thumb, thumb),
                                                                                    Image.LANCZOS)
            x = pad + (i % cols) * (thumb + pad)
            y = pad + (i // cols) * (thumb + lab + pad)
            sheet.paste(im, (x, y))
            d.text((x + 2, y + thumb + 1), n, fill=(40, 40, 40))
        out = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "preview",
                                           "ill-preview.png"))
        sheet.save(out)
        print("превью:", out, sheet.size)


if __name__ == "__main__":
    main()
