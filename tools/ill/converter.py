#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SVG (только path) -> Android VectorDrawable.

Поддерживается ровно то, что мы сами рисуем: <path d fill stroke stroke-width
stroke-linecap fill-opacity>, viewport 0 0 100 100.
"""
import re


def _color(c):
    if not c:
        return None
    c = c.strip()
    if c.startswith("#"):
        if len(c) == 4:
            return "#FF" + "".join(ch * 2 for ch in c[1:])
        if len(c) == 7:
            return "#FF" + c[1:].upper()
        if len(c) == 9:
            return "#" + c[1:].upper()
        return c.upper()
    return c


def _alpha(base, opacity):
    if not base or not opacity:
        return base
    try:
        a = int(round(float(opacity) * 255))
    except ValueError:
        return base
    if a >= 255:
        return base
    if len(base) == 9:
        old = int(base[1:3], 16)
        a = int(old * a / 255)
    return "#%02X%s" % (a, base[3:])


def to_vectordrawable(svg, name, size=100):
    """Возвращает текст VectorDrawable XML."""
    paths = re.findall(r"<path\s+([^/>]+)/>", svg)
    body = []
    for attrs in paths:
        d = re.search(r'd="([^"]+)"', attrs)
        if not d:
            continue
        d = d.group(1).strip()
        fill = re.search(r'fill="([^"]+)"', attrs)
        stroke = re.search(r'stroke="([^"]+)"', attrs)
        sw = re.search(r'stroke-width="([^"]+)"', attrs)
        cap = re.search(r'stroke-linecap="([^"]+)"', attrs)
        op = re.search(r'fill-opacity="([^"]+)"', attrs)
        sop = re.search(r'stroke-opacity="([^"]+)"', attrs)

        fill_c = _color(fill.group(1)) if fill else None
        stroke_c = _color(stroke.group(1)) if stroke else None
        if fill_c and op:
            fill_c = _alpha(fill_c, op.group(1))
        if stroke_c and sop:
            stroke_c = _alpha(stroke_c, sop.group(1))
        if fill_c == "none":
            fill_c = None

        attrs_out = ['android:pathData="%s"' % d.replace('"', "")]
        if fill_c and fill_c != "none":
            attrs_out.append('android:fillColor="%s"' % fill_c)
        else:
            attrs_out.append('android:fillColor="#00000000"')
        if stroke_c and stroke_c != "none":
            attrs_out.append('android:strokeColor="%s"' % stroke_c)
            attrs_out.append('android:strokeWidth="%s"' % (sw.group(1) if sw else "1"))
            if cap and cap.group(1) == "round":
                attrs_out.append('android:strokeLineCap="round"')
                attrs_out.append('android:strokeLineJoin="round"')
        body.append("    <path\n        %s />" % "\n        ".join(attrs_out))

    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Сгенерировано tools/ill/figures.py — править там, а не здесь. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:name="%s"\n'
            '    android:width="%ddp"\n'
            '    android:height="%ddp"\n'
            '    android:viewportWidth="%d"\n'
            '    android:viewportHeight="%d">\n%s\n</vector>\n'
            % (name, size, size, size, size, "\n".join(body)))


if __name__ == "__main__":
    import sys
    print(to_vectordrawable(open(sys.argv[1], encoding="utf-8").read(), "test"))
