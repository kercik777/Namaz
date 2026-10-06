# -*- coding: utf-8 -*-
"""
Шаг 1. Нарезка сканов: разворот -> две книжные страницы.

Что делает:
  * находит область бумаги на фотографии разворота;
  * вырезает левую и правую страницы по корешку;
  * выравнивает перекос (deskew) по тексту;
  * сохраняет два варианта:
      - webp   (для читалки, компактно; текст -- градации серого, фото -- цвет)
      - jpg    (300 dpi, для распознавания текста)
  * пишет meta.json c геометрией и оценкой цветности каждой страницы.

Запуск:
    python3 tools/build_pages.py --pdf "Namaz/1Namaz_compressed.pdf" --book b1 \
        --out-assets app/src/main/assets/pages/b1 --out-work /tmp/work/b1
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import numpy as np
import pymupdf  # PyMuPDF


def page_bounds(gray: np.ndarray, thr: int = 190):
    """Границы белой бумаги на развороте."""
    bright = gray > thr
    colfrac = bright.mean(axis=0)
    rowfrac = bright.mean(axis=1)
    cols = np.where(colfrac > 0.5)[0]
    rows = np.where(rowfrac > 0.5)[0]
    if len(cols) == 0 or len(rows) == 0:
        h, w = gray.shape
        return 0, w - 1, 0, h - 1
    return int(cols[0]), int(cols[-1]), int(rows[0]), int(rows[-1])


def split_spread(rgb: np.ndarray):
    """Делит разворот на левую/правую страницы. Возвращает (left, right, gutter_x)."""
    h, w, _ = rgb.shape
    gray = rgb.mean(axis=2)
    left, right, top, bottom = page_bounds(gray)
    paper_w = max(1, right - left)
    # корешок -- столбец с минимумом тёмных пикселей около центра бумаги
    dark = gray < 120
    coldark = dark.sum(axis=0)
    center = (left + right) // 2
    win = max(10, int(paper_w * 0.09))
    lo, hi = max(0, center - win), min(w, center + win)
    gutter = center
    if hi > lo:
        seg = coldark[lo:hi]
        # сглаживаем, чтобы не поймать случайную светлую точку внутри буквы
        k = max(3, win // 12)
        kernel = np.ones(k) / k
        smooth = np.convolve(seg.astype(float), kernel, mode="same")
        gutter = lo + int(np.argmin(smooth))
    # небольшой отступ от корешка, чтобы не тянуть тень переплёта
    pad = max(2, int(paper_w * 0.004))
    left_img = rgb[top:bottom + 1, left:max(left + 10, gutter - pad)]
    right_img = rgb[top:bottom + 1, min(w - 10, gutter + pad):right + 1]
    return left_img, right_img, gutter


def trim_white(rgb: np.ndarray, thr: int = 200, pad_frac: float = 0.012):
    """Оставляет только область бумаги: тёмный фон/обложка вокруг отрезаются."""
    gray = rgb.mean(axis=2)
    bright = gray > thr
    if bright.mean() < 0.5:
        return rgb                      # страница-фотография: поля не обрезаем
    colfrac = bright.mean(axis=0)
    rowfrac = bright.mean(axis=1)
    cols = np.where(colfrac > 0.35)[0]
    rows = np.where(rowfrac > 0.55)[0]
    if len(cols) < 5 or len(rows) < 5:
        return rgb
    ph, pw = rgb.shape[0], rgb.shape[1]
    pad_x = max(4, int(pw * pad_frac * 0.35))
    pad_y = max(4, int(ph * pad_frac * 0.35))
    x0 = max(0, int(cols[0]) - pad_x)
    x1 = min(pw, int(cols[-1]) + pad_x)
    y0 = max(0, int(rows[0]) - pad_y)
    y1 = min(ph, int(rows[-1]) + pad_y)
    return rgb[y0:y1, x0:x1]


def estimate_skew(gray: np.ndarray, max_deg: float = 3.0):
    """Оценка наклона текста по главному направлению тёмных пикселей."""
    try:
        import cv2
    except ImportError:
        return 0.0
    dark = (gray < 150).astype(np.uint8)
    if dark.sum() < 500:
        return 0.0
    coords = np.column_stack(np.nonzero(dark))
    if len(coords) > 40000:
        step = len(coords) // 40000 + 1
        coords = coords[::step]
    rect = cv2.minAreaRect(coords.astype(np.float32))
    angle = rect[-1]
    if angle > 45:
        angle -= 90
    elif angle < -45:
        angle += 90
    if abs(angle) > max_deg:
        return 0.0
    return float(angle)


def rotate(rgb: np.ndarray, angle: float):
    if abs(angle) < 0.12:
        return rgb
    try:
        import cv2
    except ImportError:
        return rgb
    h, w = rgb.shape[:2]
    m = cv2.getRotationMatrix2D((w / 2, h / 2), angle, 1.0)
    return cv2.warpAffine(rgb, m, (w, h), flags=cv2.INTER_CUBIC,
                          borderMode=cv2.BORDER_REPLICATE)


def trim_edges(rgb: np.ndarray, white: int = 190, max_drop_frac: float = 0.08):
    """Срезает тёмные/цветные полосы обложки по краям страницы.

    Ищем не первую светлую колонку, а первую *полосу* подряд светлых колонок --
    так не остаётся узкая тёмная кромка у самого края снимка.
    """
    gray = rgb.mean(axis=2)
    sat = rgb.max(axis=2).astype(np.int16) - rgb.min(axis=2).astype(np.int16)
    h, w = gray.shape
    run_x = max(10, int(w * 0.012))
    run_y = max(10, int(h * 0.012))

    col_ok = (gray.mean(axis=0) > white) & ((sat > 40).mean(axis=0) < 0.2)
    row_ok = (gray.mean(axis=1) > white) & ((sat > 40).mean(axis=1) < 0.2)

    def all_ok(flags, start, n):
        start = max(0, start)
        end = min(len(flags), start + n)
        return end - start == n and bool(flags[start:end].all())

    limit_x = int(w * max_drop_frac)
    limit_y = int(h * max_drop_frac)

    x0 = 0
    while x0 < limit_x and not all_ok(col_ok, x0, run_x):
        x0 += 1
    x1 = w
    while x1 > w - limit_x and not all_ok(col_ok, x1 - run_x, run_x):
        x1 -= 1
    y0 = 0
    while y0 < limit_y and not all_ok(row_ok, y0, run_y):
        y0 += 1
    y1 = h
    while y1 > h - limit_y and not all_ok(row_ok, y1 - run_y, run_y):
        y1 -= 1

    if (x1 - x0) < w * 0.5 or (y1 - y0) < h * 0.5:
        return rgb
    return rgb[y0:y1, x0:x1]


def colorfulness(rgb: np.ndarray) -> float:
    """Доля пикселей, которые явно цветные (фотографии)."""
    a = rgb.astype(np.int16)
    mx = a.max(axis=2)
    mn = a.min(axis=2)
    sat = mx - mn
    return float((sat > 45).mean())


def save_outputs(rgb: np.ndarray, webp_path: str, jpg_path: str, target_w: int = 1400):
    from PIL import Image
    img = Image.fromarray(rgb)
    # версия для распознавания
    img.save(jpg_path, "JPEG", quality=88, optimize=True, progressive=False)
    # версия для читалки
    scale = target_w / max(1, img.width)
    if scale < 1:
        img = img.resize((target_w, max(1, int(img.height * scale))), Image.LANCZOS)
    if img.mode != "L" and colorfulness(np.asarray(img)) < 0.0045:
        img = img.convert("L")
        img.save(webp_path, "WEBP", quality=64, method=4)
    else:
        img.save(webp_path, "WEBP", quality=74, method=4)


def process_pdf(pdf_path: str, book: str, out_assets: str, out_work: str,
                dpi: int = 300, target_w: int = 1400, first_spread=1, last_spread=None,
                skip_sides=(), progress=None):
    os.makedirs(out_assets, exist_ok=True)
    os.makedirs(out_work, exist_ok=True)
    doc = pymupdf.open(pdf_path)
    total = doc.page_count
    last_spread = last_spread or total
    meta = []
    for pno in range(first_spread, last_spread + 1):
        page = doc[pno - 1]
        pix = page.get_pixmap(dpi=dpi)
        rgb = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)[:, :, :3]
        left_img, right_img, gutter = split_spread(rgb)
        for side, img in (("L", left_img), ("R", right_img)):
            idx = (pno - first_spread) * 2 + (0 if side == "L" else 1) + 1
            name = f"{idx:04d}{side.lower()}"
            if name in skip_sides or idx in skip_sides:
                continue
            img = trim_white(img)
            img = trim_edges(img)
            ang = estimate_skew(img.mean(axis=2))
            img = rotate(img, -ang)
            jpg = os.path.join(out_work, f"{name}.jpg")
            webp = os.path.join(out_assets, f"{name}.webp")
            save_outputs(img, webp, jpg, target_w=target_w)
            meta.append({
                "name": name,
                "spread": pno,
                "side": side,
                "gutter": int(gutter),
                "skew_deg": round(ang, 3),
                "colorfulness": round(colorfulness(img), 4),
                "width": int(img.shape[1]),
                "height": int(img.shape[0]),
                "webp_bytes": os.path.getsize(webp),
                "jpg_bytes": os.path.getsize(jpg),
            })
            if progress:
                progress(len(meta))
    doc.close()
    with open(os.path.join(out_work, "meta.json"), "w", encoding="utf-8") as fh:
        json.dump({"book": book, "pdf": os.path.basename(pdf_path), "pages": meta},
                  fh, ensure_ascii=False, indent=1)
    return meta


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--pdf", required=True)
    ap.add_argument("--book", required=True)
    ap.add_argument("--out-assets", required=True)
    ap.add_argument("--out-work", required=True)
    ap.add_argument("--dpi", type=int, default=300)
    ap.add_argument("--target-w", type=int, default=1400)
    ap.add_argument("--first-spread", type=int, default=1)
    ap.add_argument("--last-spread", type=int, default=None)
    ap.add_argument("--skip-sides", default="", help="имена или номера страниц через запятую")
    args = ap.parse_args(argv)

    skip = tuple(x for x in args.skip_sides.split(",") if x)
    meta = process_pdf(args.pdf, args.book, args.out_assets, args.out_work,
                       dpi=args.dpi, target_w=args.target_w,
                       first_spread=args.first_spread, last_spread=args.last_spread,
                       skip_sides=skip)
    total = sum(m["webp_bytes"] for m in meta)
    print(f"Готово: {len(meta)} страниц, webp {total/1e6:.1f} MB", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
