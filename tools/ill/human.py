#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Фигуры людей для иллюстраций: аккуратные, одного стиля, с лицом и одеждой.

Приём простой и надёжный: каждая часть тела — «толстая линия с круглым концом»
(сначала тёмный контур, сверху цвет), туловище и одежда — одна плавная форма.
Никаких наложенных кругов, поэтому силуэт остаётся чистым.
"""

# ---------- палитра ----------

INK = "#4A3B2E"          # тёплый контур
SKIN = "#EBB98A"
SKIN_D = "#D49E6C"
BEARD = "#493728"
KAMIS = "#FBF7ED"        # мужская рубаха
KAMIS_D = "#DCD2BE"
TROUSER = "#3E4E5E"
TROUSER_D = "#33414F"
CAP = "#F2EBD9"
CAP_D = "#D9CEB4"
DRESS = "#3F7089"        # женское платье
DRESS_D = "#335C71"
SCARF = "#BC5740"        # платок
SCARF_D = "#9A4230"
SHOE = "#8A5A3B"
SHOE_D = "#6E462A"
GOLD = "#C9A24A"


# ---------- примитивы ----------

def seg(s, a, b, w, color, ink=INK, sw=1.0):
    """Толстая линия с круглыми концами: контур + цвет."""
    s.path("M %g %g L %g %g" % (a[0], a[1], b[0], b[1]), stroke=ink, sw=w + sw * 1.7)
    return s.path("M %g %g L %g %g" % (a[0], a[1], b[0], b[1]), stroke=color, sw=w)


def strokes(s, parts, ink=INK, sw=1.6):
    """Группа линий: сначала все контуры, потом все цвета — суставы остаются чистыми.

    parts — список (точка, точка, толщина, цвет).
    """
    for (a, b, w, _c) in parts:
        s.path("M %g %g L %g %g" % (a[0], a[1], b[0], b[1]), stroke=ink, sw=w + sw)
    for (a, b, w, c) in parts:
        s.path("M %g %g L %g %g" % (a[0], a[1], b[0], b[1]), stroke=c, sw=w)


def poly(s, pts, color, ink=INK, sw=1.1):
    """Замкнутая форма по точкам с контуром."""
    d = "M %g %g" % pts[0]
    for p in pts[1:]:
        d += " L %g %g" % p
    d += " Z"
    if ink:
        s.path(d, stroke=ink, sw=sw * 1.7)
    return s.path(d, fill=color, stroke=ink, sw=sw)


def smooth(s, pts, color, ink=INK, sw=1.1):
    """Замкнутая форма, плавно проходящая через все точки (сплайн Катмулла—Рома)."""
    n = len(pts)
    d = "M %g %g" % pts[0]
    for i in range(n):
        p0 = pts[(i - 1) % n]
        p1 = pts[i]
        p2 = pts[(i + 1) % n]
        p3 = pts[(i + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6.0, p1[1] + (p2[1] - p0[1]) / 6.0)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6.0, p2[1] - (p3[1] - p1[1]) / 6.0)
        d += " C %g %g %g %g %g %g" % (c1[0], c1[1], c2[0], c2[1], p2[0], p2[1])
    d += " Z"
    if ink:
        s.path(d, stroke=ink, sw=sw * 1.7)
    return s.path(d, fill=color, stroke=ink, sw=sw)


def dot(s, c, r, color, ink=None, sw=0.9):
    if ink:
        s.circle(c[0], c[1], r + sw * 0.8, ink)
    return s.circle(c[0], c[1], r, color)


def hand(s, c, r=2.0, palm=True, fill=SKIN):
    dot(s, c, r, fill, ink=INK, sw=0.35)
    if palm:
        s.path("M %g %g L %g %g" % (c[0] - r * 0.5, c[1] - r * 0.2, c[0] - r * 0.5, c[1] + r * 0.7),
               stroke=SKIN_D, sw=0.4)
        s.path("M %g %g L %g %g" % (c[0] + r * 0.1, c[1] - r * 0.4, c[0] + r * 0.1, c[1] + r * 0.7),
               stroke=SKIN_D, sw=0.4)


def limb(s, a, b, c, w, color, w2=None):
    """Двухзвенная конечность: плечо/бедро — локоть/колено — кисть/стопа."""
    w2 = w2 or w * 0.9
    seg(s, a, b, w, color)
    seg(s, b, c, w2, color)
    dot(s, b, w * 0.5, color, ink=INK, sw=0.75)


def shoe(s, ankle, toe):
    """Стопа-обувь: мягкий клин."""
    heel = (ankle[0] - (toe[0] - ankle[0]) * 0.28, ankle[1] - 0.4)
    pts = [(heel[0], heel[1] - 1.6), (toe[0] - 1.0, toe[1] - 1.4),
           (toe[0], toe[1] + 0.6), (heel[0], heel[1] + 2.0)]
    smooth(s, pts, SHOE, sw=1.0)
    s.path("M %g %g L %g %g" % (heel[0], heel[1] + 1.6, toe[0] - 0.4, toe[1] + 0.2),
           stroke=SHOE_D, sw=1.0)


# ---------- голова ----------

def _face(s, cx, cy, r, face=1.0, smile=True):
    """Глаза, брови, нос и рот."""
    ex = cx + face * r * 0.10
    for k in (-1, 1):
        dot(s, (ex + k * r * 0.34, cy - r * 0.06), 0.48, "#2A2018")
    for k in (-1, 1):
        s.path("M %g %g q %g %g %g 0" % (ex + k * r * 0.34 - r * 0.16, cy - r * 0.34,
                                          r * 0.16, -r * 0.12, r * 0.32),
               stroke="#2A2018", sw=0.5)
    s.path("M %g %g q %g %g %g %g" % (cx + face * r * 0.02, cy + r * 0.10,
                                      face * r * 0.10, r * 0.22, face * r * 0.16, r * 0.30),
           stroke=SKIN_D, sw=0.55)
    if smile:
        s.path("M %g %g q %g %g %g 0" % (cx - r * 0.18, cy + r * 0.52, r * 0.18, r * 0.14, r * 0.36),
               stroke="#B5764F", sw=0.55)


def head_man(s, c, r=6.8, face=1.0, beard=True, cap=True, tilt=0.0):
    """Голова мужчины: короткая шея, борода, тюбетейка."""
    x, y = c
    tx = x + tilt
    seg(s, (x, y + r * 0.8), (x + tilt * 0.4, y + r * 1.7), r * 0.42, SKIN)
    smooth(s, [(tx - r * 0.86, y + r * 0.30), (tx - r, y - r * 0.2), (tx - r * 0.72, y - r * 0.86),
               (tx, y - r * 1.06), (tx + r * 0.72, y - r * 0.86), (tx + r, y - r * 0.2),
               (tx + r * 0.86, y + r * 0.30), (tx + r * 0.5, y + r * 0.9),
               (tx, y + r * 1.04), (tx - r * 0.5, y + r * 0.9)],
           SKIN, sw=1.0)
    dot(s, (tx - face * r * 0.92, y + r * 0.1), r * 0.24, SKIN, ink=INK, sw=0.6)
    if beard:
        smooth(s, [(tx - r * 0.88, y + r * 0.26), (tx - r * 0.82, y + r * 1.12),
                   (tx, y + r * 1.34), (tx + r * 0.82, y + r * 1.12),
                   (tx + r * 0.88, y + r * 0.26), (tx + r * 0.66, y + r * 0.62),
                   (tx, y + r * 0.86), (tx - r * 0.66, y + r * 0.62)],
               BEARD, sw=0.7)
        s.path("M %g %g q %g %g %g 0" % (tx - r * 0.34, y + r * 0.44, r * 0.34, r * 0.2, r * 0.68),
               stroke=BEARD, sw=1.2)
    _face(s, tx, y, r, face=face)
    if cap:
        smooth(s, [(tx - r * 1.04, y - r * 0.26), (tx - r * 1.04, y - r * 1.02),
                   (tx, y - r * 1.34), (tx + r * 1.04, y - r * 1.02),
                   (tx + r * 1.04, y - r * 0.26)],
               CAP, sw=1.0)
        s.path("M %g %g H %g V %g H %g Z" % (tx - r * 1.08, y - r * 0.42, tx + r * 1.08,
                                             y - r * 0.06, tx - r * 1.08),
               fill=CAP_D, stroke=INK, sw=0.85)


def head_woman(s, c, r=6.6, face=1.0, tilt=0.0):
    """Голова женщины в платке, который ложится на плечи."""
    x, y = c
    tx = x + tilt
    seg(s, (x, y + r * 0.85), (x + tilt * 0.4, y + r * 1.75), r * 0.40, SKIN)
    # платок: широкий мягкий контур
    smooth(s, [(tx - r * 1.30, y + r * 0.5), (tx - r * 1.42, y - r * 0.9),
               (tx - r * 0.7, y - r * 1.5), (tx + r * 0.7, y - r * 1.5),
               (tx + r * 1.42, y - r * 0.9), (tx + r * 1.30, y + r * 0.5),
               (tx + r * 1.05, y + r * 1.55), (tx, y + r * 1.6),
               (tx - r * 1.05, y + r * 1.55)],
           SCARF, sw=1.0)
    # лицо
    smooth(s, [(tx - r * 0.86, y + r * 0.36), (tx - r * 0.92, y - r * 0.36),
               (tx - r * 0.54, y - r * 1.0), (tx, y - r * 1.12),
               (tx + r * 0.54, y - r * 1.0), (tx + r * 0.92, y - r * 0.36),
               (tx + r * 0.86, y + r * 0.36), (tx + r * 0.46, y + r * 0.98),
               (tx, y + r * 1.06), (tx - r * 0.46, y + r * 0.98)],
           SKIN, sw=0.95)
    # кромка платка надо лбом и складки
    s.path("M %g %g C %g %g %g %g %g %g" % (tx - r * 0.92, y - r * 0.66,
                                            tx - r * 0.44, y - r * 1.3,
                                            tx + r * 0.44, y - r * 1.3,
                                            tx + r * 0.92, y - r * 0.66),
           fill=SCARF, stroke=INK, sw=0.85)
    for k in (-1, 1):
        s.path("M %g %g C %g %g %g %g %g %g" % (tx + k * r * 0.95, y - r * 0.1,
                                                tx + k * r * 1.18, y + r * 0.7,
                                                tx + k * r * 1.0, y + r * 1.3,
                                                tx + k * r * 0.72, y + r * 1.62),
               stroke=SCARF_D, sw=0.8)
    _face(s, tx, y, r * 0.88, face=face, smile=True)


# ---------- туловище ----------

def body_man(s, neck, sh1, sh2, hem_y, hem_w=6.4):
    """Рубаха-камис: плечи, рукава, подол."""
    smooth(s, [(sh1[0] - 2.6, sh1[1] - 1.4), (sh1[0] - 5.2, sh1[1] + 12), (sh1[0] - hem_w, hem_y),
               (sh2[0] + hem_w, hem_y), (sh2[0] + 5.2, sh2[1] + 12), (sh2[0] + 2.6, sh2[1] - 1.4),
               (sh2[0] - 1.6, sh2[1] - 2.6), (neck[0], neck[1] + 1.2), (sh1[0] + 1.6, sh1[1] - 2.6)],
           KAMIS, sw=1.15)
    s.path("M %g %g C %g %g %g %g %g %g" % (sh1[0] + 1.4, sh1[1] - 1.2, neck[0] - 3.0, neck[1] + 1.0,
                                            neck[0] + 3.0, neck[1] + 1.0, sh2[0] - 1.4, sh2[1] - 1.2),
           stroke=KAMIS_D, sw=1.0)
    s.path("M %g %g Q %g %g %g %g" % (sh2[0] + 1.6, sh2[1] + 6, sh2[0] + 5.0, hem_y - 14,
                                      sh2[0] + hem_w - 1.6, hem_y - 1.2),
           stroke=KAMIS_D, sw=1.4)
    s.path("M %g %g Q %g %g %g %g" % (sh1[0] - 1.6, sh1[1] + 7, sh1[0] - 4.0, hem_y - 13,
                                      sh1[0] - hem_w + 1.4, hem_y - 1.4),
           stroke=KAMIS_D, sw=0.9)


def body_woman(s, neck, sh1, sh2, hem_y, hem_w=8.0):
    """Платье: мягкие плечи и расширяющийся подол."""
    smooth(s, [(sh1[0] - 1.6, sh1[1] - 1.0), (sh1[0] - 4.0, sh1[1] + 14), (sh1[0] - hem_w, hem_y),
               (sh2[0] + hem_w, hem_y), (sh2[0] + 4.0, sh2[1] + 14), (sh2[0] + 1.6, sh2[1] - 1.0),
               (sh2[0] - 1.4, sh2[1] - 2.4), (neck[0], neck[1] + 1.0), (sh1[0] + 1.4, sh1[1] - 2.4)],
           DRESS, sw=1.15)
    s.path("M %g %g C %g %g %g %g %g %g" % (sh1[0] + 1.4, sh1[1] - 0.8, neck[0] - 2.6, neck[1] + 0.8,
                                            neck[0] + 2.6, neck[1] + 0.8, sh2[0] - 1.4, sh2[1] - 0.8),
           stroke=DRESS_D, sw=1.0)
    for dx in (-6.0, -1.5, 3.0):
        s.path("M %g %g Q %g %g %g %g" % (neck[0] + dx * 0.35, sh1[1] + 12,
                                          neck[0] + dx * 0.9, hem_y - 12,
                                          neck[0] + dx * 1.6, hem_y - 1.6),
               stroke=DRESS_D, sw=0.85)


# ---------- скелет ----------

def skeleton(hip=44.0, man=True):
    """Точки скелета стоящего человека (взрослые пропорции, ~7 голов)."""
    return {
        "man": man,
        "neck": (50.0, 26.4),
        "sh1": (42.6, 29.0), "sh2": (57.4, 29.0),
        "hip1": (45.4, hip), "hip2": (54.6, hip),
        "knee1": (45.8, 61.5), "knee2": (54.2, 61.5),
        "ankle1": (46.0, 79.5), "ankle2": (54.0, 79.5),
        "toe1": (41.0, 82.0), "toe2": (59.0, 82.0),
    }


def draw_body(s, b, hem_y):
    if b["man"]:
        body_man(s, b["neck"], b["sh1"], b["sh2"], hem_y)
    else:
        body_woman(s, b["neck"], b["sh1"], b["sh2"], hem_y)


def draw_head(s, b, tilt=0.0, face=1.0, prefix=0.0, r=5.2):
    c = (b["neck"][0], b["neck"][1] - 7.0 + prefix)
    if b["man"]:
        head_man(s, c, r=r, face=face, tilt=tilt)
    else:
        head_woman(s, c, r=r * 0.97, face=face, tilt=tilt)


def draw_stand(s, man=True, hands="fold", hip=44.0):
    """Стоящий человек."""
    b = skeleton(hip, man)
    leg = TROUSER if man else DRESS
    cloth = KAMIS if man else DRESS
    hem = hip + (6.0 if man else 32.0)
    s.shadow_fig(50, 81.5, rx=13, ry=2.2)

    if hands == "fold":
        wy = 45.5 if man else 36.0
        far_arm = [(b["sh2"], (57.0, 39.5), 3.7, cloth), ((57.0, 39.5), (52.4, wy), 3.0, SKIN)]
        near_arm = [(b["sh1"], (43.0, 39.5), 3.7, cloth), ((43.0, 39.5), (47.6, wy + 1.0), 3.0, SKIN)]
    elif hands == "down":
        far_arm = [(b["sh2"], (58.6, 40.0), 3.7, cloth), ((58.6, 40.0), (58.4, 51.0), 2.9, SKIN)]
        near_arm = [(b["sh1"], (41.4, 40.0), 3.7, cloth), ((41.4, 40.0), (41.6, 51.0), 2.9, SKIN)]
    elif hands == "raise":            # такбир: ладони у ушей
        far_arm = [(b["sh2"], (61.4, 34.0), 3.7, cloth), ((61.4, 34.0), (56.4, 24.6), 2.9, SKIN)]
        near_arm = [(b["sh1"], (38.6, 34.0), 3.7, cloth), ((38.6, 34.0), (43.6, 24.6), 2.9, SKIN)]
    elif hands == "palms":            # ладони раскрыты перед грудью
        far_arm = [(b["sh2"], (60.0, 37.0), 3.7, cloth), ((60.0, 37.0), (55.6, 45.4), 2.9, SKIN)]
        near_arm = [(b["sh1"], (40.0, 37.0), 3.7, cloth), ((40.0, 37.0), (44.4, 45.4), 2.9, SKIN)]

    far = [(b["hip2"], b["knee2"], 4.4, leg), (b["knee2"], b["ankle2"], 3.8, leg)] + far_arm
    near = [(b["hip1"], b["knee1"], 4.4, leg), (b["knee1"], b["ankle1"], 3.8, leg)] + near_arm
    strokes(s, far)
    shoe(s, b["ankle2"], b["toe2"])
    draw_body(s, b, hem)
    strokes(s, near)
    shoe(s, b["ankle1"], b["toe1"])

    if hands == "fold":
        wy = 45.5 if man else 36.0
        s.path("M %g %g a %g %g 0 1 0 %g 0 a %g %g 0 1 0 -%g 0 Z"
               % (47.0, wy + 0.4, 2.3, 2.7, 4.6, 2.3, 2.7, 4.6),
               fill=SKIN, stroke=INK, sw=0.8)
        s.path("M %g %g L %g %g" % (47.4, wy + 1.0, 52.6, wy + 0.2), stroke=SKIN_D, sw=0.45)
    elif hands == "down":
        hand(s, (58.4, 52.0), 1.9)
        hand(s, (41.6, 52.0), 1.9)
    elif hands == "raise":
        hand(s, (56.4, 24.0), 1.85, palm=False)
        hand(s, (43.6, 24.0), 1.85, palm=False)
    elif hands == "palms":
        for (x, y) in ((44.4, 45.6), (55.6, 45.6)):
            s.path("M %g %g a %g %g 0 1 0 %g 0 a %g %g 0 1 0 -%g 0 Z"
                   % (x - 2.2, y, 2.2, 3.0, 4.4, 2.2, 3.0, 4.4),
                   fill=SKIN, stroke=INK, sw=0.8)
            for k in (-1, 0, 1):
                s.path("M %g %g L %g %g" % (x + k * 0.95, y - 1.8, x + k * 0.95, y + 1.0),
                       stroke=SKIN_D, sw=0.4)
    draw_head(s, b)
    return b


def draw_ruku(s, man=True):
    """Поясной поклон: спина горизонтально, ладони на коленях."""
    cloth = KAMIS if man else DRESS
    leg = TROUSER if man else DRESS
    s.shadow_fig(50, 81.5, rx=16, ry=2.2)
    strokes(s, [
        ((51.6, 47.0), (52.4, 62.0), 4.4, leg), ((52.4, 62.0), (52.4, 79.4), 3.8, leg),
        ((64.6, 35.4), (62.0, 49.0), 3.8, cloth), ((62.0, 49.0), (53.4, 60.4), 2.9, SKIN),
        ((47.4, 47.0), (47.6, 62.0), 4.4, leg), ((47.6, 62.0), (47.4, 79.4), 3.8, leg),
        ((62.0, 36.6), (59.0, 50.0), 3.8, cloth), ((59.0, 50.0), (50.4, 61.4), 3.0, SKIN),
    ])
    shoe(s, (52.4, 79.4), (58.0, 81.6))
    shoe(s, (47.4, 79.4), (41.8, 81.6))
    smooth(s, [(47.4, 47.0), (49.4, 40.4), (55.0, 36.0), (62.0, 35.2), (65.4, 38.0),
               (63.4, 45.0), (55.0, 48.0), (50.4, 48.6)],
           cloth, sw=1.15)
    hand(s, (49.8, 62.2), 1.9)
    hand(s, (53.0, 61.2), 1.9)
    b = {"man": man, "neck": (65.0, 34.0)}
    draw_head(s, b, prefix=0.6)


def draw_sajda(s, man=True):
    """Земной поклон: лоб на коврике, ладони рядом с головой."""
    cloth = KAMIS if man else DRESS
    leg = TROUSER if man else DRESS
    s.shadow_fig(52, 81.0, rx=20, ry=2.2)
    strokes(s, [
        ((60.0, 66.0), (62.4, 76.0), 4.4, leg), ((62.4, 76.0), (66.0, 79.0), 3.8, leg),
        ((53.0, 60.4), (49.6, 70.0), 3.9, cloth), ((49.6, 70.0), (47.6, 78.4), 3.0, SKIN),
    ])
    shoe(s, (62.4, 76.0), (66.6, 74.4))
    shoe(s, (66.0, 79.0), (70.6, 77.6))
    # бёдра и спина: от колен вверх и вперёд к голове
    smooth(s, [(57.6, 68.0), (55.0, 59.0), (58.0, 54.6), (63.4, 55.0),
               (66.4, 59.0), (64.6, 69.0), (60.6, 70.0)],
           cloth, sw=1.15)
    smooth(s, [(63.0, 55.4), (55.4, 56.2), (49.0, 60.0), (45.6, 64.6),
               (48.0, 68.4), (54.6, 66.6), (60.0, 71.0), (62.6, 62.0)],
           cloth, sw=1.15)
    hand(s, (43.4, 79.4), 1.9)
    hand(s, (47.2, 80.0), 1.9)
    b = {"man": man, "neck": (44.6, 68.0)}
    draw_head(s, b, prefix=1.6)


def draw_sit(s, man=True, hands="knees", turn=0.0):
    """Сидя на коленях: ташаххуд, дуа, салам."""
    cloth = KAMIS if man else DRESS
    leg = TROUSER if man else DRESS
    s.shadow_fig(50, 81.5, rx=18, ry=2.2)
    # сложенные ноги: бедро вправо, стопа под собой
    strokes(s, [((52.0, 62.0), (57.0, 70.0), 4.8, leg), ((57.0, 70.0), (52.0, 74.0), 4.2, leg)])
    shoe(s, (52.0, 74.0), (60.0, 78.6))
    smooth(s, [(42.4, 78.6), (43.4, 72.0), (48.0, 68.6), (54.0, 69.0),
               (58.4, 72.0), (58.0, 78.0), (50.0, 79.4), (45.0, 79.4)],
           cloth, sw=1.15)
    b = {"man": man, "neck": (49.8, 37.0),
         "sh1": (43.0, 39.6), "sh2": (56.6, 39.6)}
    draw_body(s, b, man and 66.0 or 78.0)
    if hands == "knees":
        strokes(s, [
            (b["sh2"], (56.2, 50.0), 3.9, cloth), ((56.2, 50.0), (56.6, 58.0), 3.0, SKIN),
            (b["sh1"], (43.4, 50.0), 3.9, cloth), ((43.4, 50.0), (49.6, 58.4), 3.0, SKIN),
        ])
        hand(s, (57.0, 59.0), 1.9)
        hand(s, (50.4, 59.4), 1.9)
    elif hands == "up":
        strokes(s, [
            (b["sh2"], (56.4, 49.6), 3.9, cloth), ((56.4, 49.6), (54.4, 52.6), 3.0, SKIN),
            (b["sh1"], (43.2, 49.6), 3.9, cloth), ((43.2, 49.6), (45.2, 52.6), 3.0, SKIN),
        ])
        for x in (45.2, 54.4):
            s.path("M %g %g a %g %g 0 1 0 %g 0 a %g %g 0 1 0 -%g 0 Z"
                   % (x - 2.2, 52.8, 2.2, 3.0, 4.4, 2.2, 3.0, 4.4),
                   fill=SKIN, stroke=INK, sw=0.8)
            for k in (-1, 0, 1):
                s.path("M %g %g L %g %g" % (x + k * 0.95, 51.0, x + k * 0.95, 53.8),
                       stroke=SKIN_D, sw=0.4)
    draw_head(s, b, tilt=turn * 1.0, face=1.0 if turn >= 0 else -1.0)


# ---------- вода, чаша, кувшин ----------

def basin(s, x, y, w=30.0, h=9.0):
    smooth(s, [(x - w / 2, y), (x - w / 2 + 3, y + h), (x + w / 2 - 3, y + h), (x + w / 2, y),
               (x + w / 2 - 3, y + 2.4), (x - w / 2 + 3, y + 2.4)], "#E2D6BC", sw=1.0)
    s.path("M %g %g L %g %g" % (x - w / 2 + 4, y + 1.2, x + w / 2 - 4, y + 1.2),
           stroke="#8FC0DC", sw=2.2)


def jug(s, x, y, k=1.0):
    smooth(s, [(x - 5 * k, y), (x - 6.4 * k, y - 6 * k), (x - 2.6 * k, y - 10 * k),
               (x, y - 10.6 * k), (x + 2.6 * k, y - 10 * k), (x + 6.4 * k, y - 6 * k),
               (x + 5 * k, y)], "#CBD0D6", sw=1.0)
    s.path("M %g %g C %g %g %g %g %g %g" % (x - 5.4 * k, y - 1.4 * k, x - 12 * k, y - 2 * k,
                                            x - 11 * k, y - 8 * k, x - 5.6 * k, y - 7.4 * k),
           stroke=INK, sw=1.3)
    s.path("M %g %g L %g %g" % (x - 3.4 * k, y - 7 * k, x - 2.6 * k, y - 1.4 * k),
           stroke="#B3B9C0", sw=1.6)


def water(s, x, y1, y2, w=2.2):
    s.path("M %g %g C %g %g %g %g %g %g" % (x, y1, x - 1.2, (y1 + y2) / 2, x + 0.8,
                                            (y1 + y2) / 2 + 1, x, y2),
           stroke="#63AFD8", sw=w)
    s.path("M %g %g C %g %g %g %g %g %g" % (x - 0.4, y1 + 1.5, x - 0.8, (y1 + y2) / 2,
                                            x + 0.3, (y1 + y2) / 2 + 1, x - 0.3, y2 - 1),
           stroke="#B6DDF1", sw=w * 0.45)


def drops(s, x, y, n=3, spread=6.0):
    for i in range(n):
        dx = (i - (n - 1) / 2.0) * (spread / max(1, n - 1)) if n > 1 else 0
        dy = abs(i - (n - 1) / 2.0) * 1.4
        s.path("M %g %g C %g %g %g %g %g %g Z"
               % (x + dx, y + dy, x + dx + 1.4, y + 2.2 + dy, x + dx + 0.8,
                  y + 3.6 + dy, x + dx, y + 4.0 + dy),
               fill="#63AFD8")


def cupped_hands(s, c, w=7.0, h=4.4):
    """Сложенные лодочкой ладони — под струёй воды."""
    smooth(s, [(c[0] - w / 2, c[1] - h / 4), (c[0] - w / 3, c[1] + h / 2),
               (c[0] + w / 3, c[1] + h / 2), (c[0] + w / 2, c[1] - h / 4),
               (c[0], c[1] - h / 3)],
           SKIN, sw=0.85)
    for k in (-1, 0, 1):
        s.path("M %g %g L %g %g" % (c[0] + k * 1.5, c[1] - h * 0.2, c[0] + k * 1.5, c[1] + h * 0.3),
               stroke=SKIN_D, sw=0.4)


def sparkle(s, x, y, r=1.0, color="#7FC3E4"):
    for (dx, dy, k) in ((0, 0, 1.0), (2.4, -1.6, 0.6), (-2.2, 1.4, 0.5)):
        s.circle(x + dx, y + dy, r * k, color)
