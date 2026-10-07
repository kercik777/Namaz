#!/usr/bin/env python3
"""
Better parser: each line is a fragment; merge into paragraphs based on
heuristics (no blank lines preserved in the user's txt).
"""
import json, re, os, pymupdf

ROOT = "/home/user/Namaz/app/src/main/assets/content"

RE_PAGE_NUM = re.compile(r"^\s*(\d{1,3})\s*$")
RE_PART = re.compile(r"^([IVX]+)\s*BÖLÜM\.?", re.IGNORECASE)
RE_NUM_HEAD = re.compile(r"^(\d+)\.\s+([A-ZÝÇŞĞÖÜŇÄŽ][A-ZÝÇŞĞÖÜŇÄŽ\s\-–—]{3,80})$")
RE_LIST = re.compile(r"^(\d+)\.\s+(.+)$")
RE_SENTENCE_END = re.compile(r"[.!?…:»\"']$")
RE_H2_TITLE = re.compile(
    r"^(Ýedisi|Bäşisi|On ikisi|Dördüsi|Üçüsi|Ikisi|Biri|Kyrk|On|Täretde|Gusulda|Teýemmümde|Namazda)\b",
    re.IGNORECASE,
)

def is_continued(previous, current):
    p = previous.rstrip()
    c = current.strip()
    if not c:
        return False
    if RE_PAGE_NUM.match(c):
        return False
    if RE_LIST.match(c):
        return False
    if RE_PART.match(c):
        return False
    if RE_NUM_HEAD.match(c):
        return False
    if c == c.upper() and len(c) < 80 and re.search(r"[A-ZÝÇŞĞÖÜŇÄŽ]", c):
        words = c.split()
        if len(words) <= 2 and not RE_SENTENCE_END.search(p):
            return True
        return False
    if RE_SENTENCE_END.search(p):
        return False
    if c[0].islower() or c[0] in "«\"'(":
        return True
    return False

def classify_paragraph(p):
    if p.startswith("__PAGE__"):
        return ("page", p[8:].strip())
    s = p.strip()
    if not s:
        return None
    # Drop TOC paragraphs that look like "IBÖLÜM. Iman-ynanç esaslary"
    if re.match(r"^[IVX]+\s*BÖLÜM\.?\s+[A-Za-zçğıöşüýäžň\-]+\s+esaslary\s*$", s, re.IGNORECASE):
        return None
    if RE_PART.match(s):
        return ("part", s)
    if RE_NUM_HEAD.match(s):
        return ("h1", s)
    if s == s.upper() and len(s) < 80 and re.search(r"[A-ZÝÇŞĞÖÜŇÄŽ]", s) and len(s.split()) >= 2:
        return ("h1", s)
    if RE_H2_TITLE.match(s) and len(s) < 80:
        return ("h2", s)
    if RE_LIST.match(s):
        m = RE_LIST.match(s)
        rest = m.group(2)
        if rest == rest.upper() and len(rest) < 80 and re.search(r"[A-ZÝÇŞĞÖÜŇÄŽ]", rest) and len(rest.split()) <= 12:
            return ("h1", s)
        return ("list", rest)
    return ("p", s)

def parse_text_file(path):
    with open(path, encoding="utf-8") as f:
        text = f.read()
    # If "MAZMUNY" appears, drop everything from that line onward (table of
    # contents of the second half — it has many small page numbers that confuse
    # the parser).
    idx = text.find("MAZMUNY")
    if idx > 0:
        text = text[:idx]
    raw = text.split("\n")
    lines = [ln.strip() for ln in raw]
    paragraphs = []
    cur = ""
    for ln in lines:
        if ln == "":
            if cur.strip():
                paragraphs.append(cur.strip())
            cur = ""
            continue
        if RE_PAGE_NUM.match(ln):
            if cur.strip():
                paragraphs.append(cur.strip())
            cur = ""
            paragraphs.append("__PAGE__" + ln.strip())
            continue
        if not cur:
            cur = ln
            continue
        if is_continued(cur, ln):
            if cur.endswith("-"):
                cur = cur[:-1] + ln.lstrip()
            else:
                cur = cur + " " + ln.lstrip()
        else:
            paragraphs.append(cur.strip())
            cur = ln
    if cur.strip():
        paragraphs.append(cur.strip())
    return paragraphs

def build_book_blocks(file1, file2):
    raw_blocks = []
    seen_first_namaz = False
    for path in [file1, file2]:
        for p in parse_text_file(path):
            cls = classify_paragraph(p)
            if cls is None:
                continue
            t, x = cls
            if t == "page":
                raw_blocks.append(("page", int(x)))
            else:
                raw_blocks.append((t, x))

    blocks = []
    pending_pages = []
    for t, x in raw_blocks:
        if t == "page":
            pending_pages.append(x)
            continue
        if pending_pages:
            blocks.append({"t": "page", "n": pending_pages[0]})
            pending_pages = []
        # If a paragraph starts with "I BÖLÜM IMAN-YNANÇ ESASLARY ..." (long),
        # split it into a part and the rest.
        m = re.match(r"^([IVX]+\s*BÖLÜM\.?\s+[A-ZÝÇŞĞÖÜŇÄŽ][A-ZÝÇŞĞÖÜŇÄŽ\s\-–—]{2,30})\s*\.?\s*(.*)$", x, re.IGNORECASE | re.DOTALL)
        if m:
            part_title = m.group(1).strip()
            rest = m.group(2).strip()
            blocks.append({"t": "part", "x": part_title, "items": []})
            if rest:
                blocks.append({"t": "p", "x": rest, "items": []})
            continue
        # If a paragraph is just "IBÖLÜM. Iman-ynanç esaslary" (table of contents),
        # drop it.
        if t == "p" and re.match(r"^[IVX]+\s*BÖLÜM\.?\s+[A-Za-zçğıöşüýäžň\-]+\s+esaslary\s*$", x, re.IGNORECASE):
            continue
        if t == "part":
            words = x.split()
            if len(words) > 6:
                t = "p"
            else:
                x = " ".join(words[:4])
        if t == "h1" and "NAMAZ KITABY" in x.upper() and not seen_first_namaz:
            if x.upper().count("NAMAZ") >= 2:
                x = "NAMAZ KITABY"
            seen_first_namaz = True
        blocks.append({"t": t, "x": x, "items": []})
    if pending_pages:
        blocks.append({"t": "page", "n": pending_pages[0]})
    return blocks

def build_akyda_blocks(pdf_path):
    doc = pymupdf.open(pdf_path)
    full = "\n\n".join(p.get_text("text") for p in doc)
    doc.close()
    pages = full.split("\n\n")
    blocks = []
    for p in pages:
        p = p.strip()
        if not p:
            continue
        for line in p.split("\n"):
            line = line.strip()
            if not line:
                continue
            line = line.replace("MUSILMAN", "MUSULMAN")
            line = line.replace("PIGAMBER", "PYGAMBER")
            line = line.replace("IÝMAM", "IÝMAN")
            line = line.replace("FERIŞTELERÖŇ", "FERIŞTELERIŇ")
            line = line.replace("IÝBEREN", "IBEREN")
            line = line.replace("IÝLÇILERI", "ILÇILERI")
            line = line.replace("IÝLÇISI", "ILÇISI")
            line = line.replace("DOKGYZYNJY", "DOKUZYNJY")
            line = line.replace("AWAWKYSY", "AWALKYSY")
            line = line.replace("AWWALKYSY", "AWALKYSY")
            line = line.replace("ONYNJY", "ONUNJY")
            line = line.replace("SEKGIZINJI", "SEKIZINJI")
            line = line.replace("HHSAN", "IHSAN")
            line = line.replace("SBUTI", "SUBUTY")
            line = line.replace("SYFALARY", "SYPATLARY")
            line = line.replace("SYFATLRY", "SYPATLARY")
            line = re.sub(r"\s{2,}", " ", line).strip()
            if RE_PAGE_NUM.match(line):
                blocks.append({"t": "page", "n": int(line)})
                continue
            if line.startswith("S :") or line.startswith("S:"):
                rest = re.sub(r"^S\s*:\s*", "", line)
                blocks.append({"t": "q", "x": rest})
                continue
            if line.startswith("J :") or line.startswith("J:"):
                rest = re.sub(r"^J\s*:\s*", "", line)
                blocks.append({"t": "a", "x": rest})
                continue
            if "BISMILL" in line.upper():
                blocks.append({"t": "center", "x": "BISMILLÄHIR-RAHMÄNIR-RAHYM"})
                continue
            if re.match(r"^(BIRINJI|IKINJI|ÜÇÜNJI|DÖRDINJI|BÄŞINJI|ALTYNJY|ÝEDINJI|SEKIZINJI|DOKUZYNJY|ONUNJY|ON BIRINJI)\s+DERS", line, re.IGNORECASE):
                blocks.append({"t": "h1", "x": line})
                continue
            if re.match(r"^\d+\.\s", line):
                rest = re.sub(r"^\d+\.\s*", "", line)
                if rest == rest.upper() and len(rest) < 60 and re.search(r"[A-ZÝÇŞĞÖÜŇÄŽ]", rest):
                    blocks.append({"t": "h2", "x": rest})
                else:
                    blocks.append({"t": "list", "x": rest})
                continue
            if re.match(r"^[A-ZÝÇŞĞÖÜŇÄŽА-ЯЁ][A-ZÝÇŞĞÖÜŇÄŽА-ЯЁ\s,\.\?\!]{3,}$", line) and len(line) < 70 and line.strip() == line.strip().upper():
                blocks.append({"t": "h2", "x": line})
                continue
            blocks.append({"t": "p", "x": line})
    return blocks

def main():
    book_blocks = build_book_blocks("/tmp/txt_user/1.txt", "/tmp/txt_user/2.txt")
    book = {
        "v": 1, "id": "namaz_kitaby", "script": "latin",
        "title": {"ru": "Намаз китабы", "tk": "Namaz kitaby"},
        "subtitle": {"tk": "Doly tekst: birinji we ikinji bölüm", "ru": "Полный текст: первая и вторая части"},
        "author": {"ru": "По изданию «Namaz kitaby»", "tk": "«Namaz kitaby» neşirine görä"},
        "edition": {"ru": "Туркменистан", "tk": "Türkmenistan"},
        "blocks": book_blocks,
    }
    toc = []
    for i, b in enumerate(book_blocks):
        if b.get("t") == "part":
            toc.append({"level": 0, "x": b["x"], "b": i})
        elif b.get("t") == "h1":
            toc.append({"level": 1, "x": b["x"], "b": i})
        elif b.get("t") == "h2":
            toc.append({"level": 2, "x": b["x"], "b": i})
    book["toc"] = toc
    out = os.path.join(ROOT, "namaz_kitaby.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(book, f, ensure_ascii=False, indent=1)
    print(f"WROTE {out}: {len(book_blocks)} blocks, {len(toc)} toc")

    akyda_blocks = build_akyda_blocks("/home/user/Namaz/aqyda bölümi.pdf")
    akyda = {
        "v": 1, "id": "akyda", "script": "latin", "has_scan": False, "scan_pages": 0, "accent": 4279126582,
        "title": {"ru": "Акыда", "tk": "Akyda"},
        "subtitle": {"ru": "Основы вероучения — одиннадцать уроков", "tk": "Iman esaslary — on bir ders"},
        "author": {"ru": "По изданию «Namaz kitaby»", "tk": "«Namaz kitaby» neşirine görä"},
        "edition": {"tk": "Türkmenistanyň Müftüsiniň Müdirligi", "ru": "Türkmenistanyň Müftüsiniň Müdirligi"},
        "blocks": akyda_blocks,
    }
    out2 = os.path.join(ROOT, "akyda.json")
    with open(out2, "w", encoding="utf-8") as f:
        json.dump(akyda, f, ensure_ascii=False, indent=1)
    print(f"WROTE {out2}: {len(akyda_blocks)} blocks")

if __name__ == "__main__":
    main()
