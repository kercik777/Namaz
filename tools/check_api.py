#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Статическая проверка проекта без компилятора (в песочнице нет JDK).

Что проверяет:
  1. Каждый вызов вида «МойКласс.член» — существует ли этот член в файле класса
     (поля, методы, константы, вложенные классы).
  2. Каждое обращение к вложенному классу («Widgets.Logo») — объявлен ли он.
  3. Импорты tm.akyda.namaz.* — существует ли такой класс.
  4. Простые имена своих классов, использованные в файле, но не импортированные
     (и не лежащие в том же пакете) — это ошибка компиляции.
  5. Ссылки на R.string / R.plurals / R.style / R.anim / R.xml — есть ли они в res.

Запуск:  python3 tools/check_api.py
"""
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "java")
PKG_ROOT = "tm.akyda.namaz"

# методы/поля из JDK и Android, которые можно встретить как «Класс.член» у своих классов
IGNORED_MEMBERS = {"class", "this", "super", "new", "length", "clone"}


def strip_code(text):
    """Убирает комментарии и строковые литералы (чтобы не было ложных срабатываний)."""
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\])*'", "''", text)
    return text


class Klass:
    def __init__(self, dotted, path, package):
        self.dotted = dotted          # Skin / Widgets.Logo / Book.Item
        self.path = path
        self.package = package
        self.tokens = set()           # все «словоподобные» члены, найденные в файле


def collect_classes():
    """Собирает свои классы: имя (с вложенными) -> файл, пакет, набор токенов файла."""
    classes = {}
    for path in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
        raw = open(path, encoding="utf-8").read()
        code = strip_code(raw)
        m = re.search(r"^\s*package\s+([\w.]+)\s*;", code, flags=re.M)
        package = m.group(1) if m else ""
        tokens = set(re.findall(r"[A-Za-z_][A-Za-z0-9_]*", code))
        stack = []
        depth = 0
        for m in re.finditer(r"[{}]|\b(?:class|interface|enum)\s+([A-Za-z_0-9]+)", code):
            tok = m.group(0)
            if tok == "{":
                depth += 1
            elif tok == "}":
                depth -= 1
                while stack and stack[-1][1] >= depth:
                    stack.pop()
            else:
                dotted = ".".join([s[0] for s in stack] + [m.group(1)])
                classes.setdefault(dotted, Klass(dotted, path, package))
                stack.append((m.group(1), depth))
        if not any(k.path == path for k in classes.values()):
            base = os.path.basename(path)[:-5]
            classes[base] = Klass(base, path, package)
        for k in list(classes.values()):
            if k.path == path:
                k.tokens |= tokens
    return classes



def check_xml_references(res_dir, problems):
    """Ссылки @drawable/... @style/... @color/... внутри XML и манифеста."""
    have = {}
    def names(pattern, strip_ext=True):
        out = set()
        for f in glob.glob(pattern):
            b = os.path.basename(f)
            out.add(b.rsplit(".", 1)[0] if strip_ext else b)
        return out
    have["drawable"] = names(os.path.join(res_dir, "drawable*", "*"))
    have["mipmap"] = names(os.path.join(res_dir, "mipmap*", "*"))
    have["anim"] = names(os.path.join(res_dir, "anim", "*"))
    have["xml"] = names(os.path.join(res_dir, "xml", "*"))
    have["raw"] = names(os.path.join(res_dir, "raw", "*"))
    for kind in ("colors", "styles", "strings", "bools", "integers", "arrays", "dimens"):
        have[kind] = set()
    for f in glob.glob(os.path.join(res_dir, "values*", "*.xml")):
        text = open(f, encoding="utf-8").read()
        for tag, target in (("string", "strings"), ("plurals", "strings"), ("color", "colors"),
                            ("style", "styles"), ("bool", "bools"), ("integer", "integers"),
                            ("string-array", "arrays"), ("integer-array", "arrays"),
                            ("array", "arrays"), ("dimen", "dimens")):
            for name in re.findall(r'<{}\s+name="([^"]+)"'.format(tag), text):
                have[target].add(name)
                if tag == "style":
                    have[target].add(name.replace(".", "_"))
    for target, kind in (("strings", "string"), ("plurals", "plurals"), ("colors", "color"),
                         ("styles", "style"), ("bools", "bool"), ("integers", "integer"),
                         ("arrays", "array"), ("dimens", "dimen")):
        have.setdefault(kind, have.get(target, set()))

    files = glob.glob(os.path.join(res_dir, "**", "*.xml"), recursive=True)
    files.append(os.path.join(ROOT, "app", "src", "main", "AndroidManifest.xml"))
    for f in files:
        text = open(f, encoding="utf-8").read()
        for m in re.finditer(r'"@(?!android:|\+|\*)([a-z]+)/([A-Za-z0-9_.]+)"', text):
            kind, name = m.group(1), m.group(2)
            table = have.get(kind)
            if table is None:
                continue
            if name not in table and name.replace(".", "_") not in table:
                problems.append("{}: ссылка на несуществующий ресурс @{}/{}".format(
                    os.path.relpath(f, ROOT), kind, name))


def check_manifest_classes(problems):
    """Классы из манифеста должны существовать."""
    path = os.path.join(ROOT, "app", "src", "main", "AndroidManifest.xml")
    if not os.path.exists(path):
        return
    text = open(path, encoding="utf-8").read()
    app_id = re.search(r'package="([^"]+)"', text)
    app_id = app_id.group(1) if app_id else PKG_ROOT
    for m in re.finditer(r'android:name="([^"]+)"', text):
        name = m.group(1)
        if not name.startswith(".") and not name.startswith(PKG_ROOT) and name != app_id:
            continue
        cls = (app_id + name) if name.startswith(".") else name
        cls = cls.rsplit(".", 1)[-1]
        found = any(os.path.basename(f)[:-5] == cls
                    for f in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True))
        if not found:
            problems.append("AndroidManifest.xml: нет класса {}".format(name))


def check_file_names(problems):
    """Имя файла должно совпадать с именем public-класса."""
    for path in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
        code = strip_code(open(path, encoding="utf-8").read())
        m = re.search(r"\bpublic\s+(?:final\s+|abstract\s+)?(?:class|interface|enum)\s+([A-Za-z_0-9]+)", code)
        if m and m.group(1) != os.path.basename(path)[:-5]:
            problems.append("{}: public-класс {} не совпадает с именем файла".format(
                os.path.relpath(path, ROOT), m.group(1)))


def main():
    classes = collect_classes()
    simple = {}
    for k in classes.values():
        if "." not in k.dotted:
            simple.setdefault(k.dotted, k)
    members_by_file = {}
    for k in classes.values():
        members_by_file.setdefault(k.path, set()).update(k.tokens)

    problems = []
    for path in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
        raw = open(path, encoding="utf-8").read()
        code = strip_code(raw)
        pkg = re.search(r"^\s*package\s+([\w.]+)\s*;", code, flags=re.M)
        pkg = pkg.group(1) if pkg else ""
        imports = set(re.findall(r"^\s*import\s+(?:static\s+)?([\w.]+)\s*;", code, flags=re.M))

        # 1-2. обращения «Класс.член»
        for m in re.finditer(r"\b([A-Z][A-Za-z0-9_]*(?:\.[A-Z][A-Za-z0-9_]*)?)\.([A-Za-z_][A-Za-z0-9_]*)", code):
            owner, member = m.group(1), m.group(2)
            if member in IGNORED_MEMBERS:
                continue
            target = None
            if owner in classes:
                target = classes[owner]
            elif owner.split(".")[0] in simple:
                outer = simple[owner.split(".")[0]]
                if owner.split(".")[-1] in {k.dotted.split(".")[-1] for k in classes.values() if k.path == outer.path}:
                    target = classes.get("{}.{}".format(outer.dotted, owner.split(".")[-1]))
            if target is None:
                continue
            # если в файле объявлена локальная переменная с таким же именем — это не наш класс
            if re.search(r"\b[A-Za-z_][\w<>\[\],.]*\s+{}\s*[=;)]".format(re.escape(owner.split(".")[0])), code):
                if owner.split(".")[0] not in {k for k in simple}:
                    continue
                # «P» в Ico.java — это локальный Path, а не класс P
                if re.search(r"\bPath\s+{}\b".format(re.escape(owner.split(".")[0])), code):
                    continue
            if member not in members_by_file.get(target.path, set()):
                problems.append("{}: {} -> нет члена «{}» в {}".format(
                    os.path.relpath(path, ROOT), owner + "." + member, member,
                    os.path.relpath(target.path, ROOT)))

        # 3. импорты своих классов
        for imp in imports:
            if imp.startswith(PKG_ROOT) and "." in imp[len(PKG_ROOT) + 1:]:
                tail = imp.rsplit(".", 1)[-1]
                if tail not in simple and imp not in classes:
                    problems.append("{}: импорт несуществующего класса {}".format(
                        os.path.relpath(path, ROOT), imp))

        # 4. свои классы, использованные без импорта
        for name, k in simple.items():
            if k.package == pkg:
                continue
            if not re.search(r"\b{}\b".format(re.escape(name)), code):
                continue
            if "." + name in code:
                continue  # используется полное имя
            imported_roots = {i.rsplit(".", 1)[-1] for i in imports}
            if name in imported_roots or any(i == name or i.endswith("." + name) for i in imports):
                continue
            # вложенный класс импортированного класса (например Book.Item)
            if any(k2.path == k.path for k2 in simple.values() if k2.dotted.split(".")[0] in imported_roots):
                continue
            problems.append("{}: класс {} используется без импорта".format(
                os.path.relpath(path, ROOT), name))

    # 5. ресурсы
    res_dir = os.path.join(ROOT, "app", "src", "main", "res")
    def res_keys(kind):
        keys = set()
        for f in glob.glob(os.path.join(res_dir, "values*", "*.xml")):
            text = open(f, encoding="utf-8").read()
            keys |= set(re.findall(r'<(?:\w+:)?(?:string|plurals|style|color|dimen|bool|string-array|integer)\s+name="([^"]+)"', text))
            if kind == "anim":
                keys |= {os.path.basename(f)[:-4]}
            if kind == "xml":
                keys |= {os.path.basename(f)[:-4]}
        return keys

    files_index = {
        "anim": {os.path.basename(f)[:-4] for f in glob.glob(os.path.join(res_dir, "anim", "*.xml"))},
        "xml": {os.path.basename(f)[:-4] for f in glob.glob(os.path.join(res_dir, "xml", "*.xml"))},
        "style": res_keys("values"),
        "mipmap": {os.path.basename(f).split(".")[0] for f in glob.glob(os.path.join(res_dir, "mipmap-*", "*"))},
        "drawable": {os.path.basename(f).split(".")[0] for f in glob.glob(os.path.join(res_dir, "drawable-*", "*"))},
        "string": set(), "plurals": set(),
    }
    for kind in ("string", "plurals"):
        for f in glob.glob(os.path.join(res_dir, "values*", "strings.xml")):
            files_index[kind] |= set(re.findall(
                r'<{}\s+name="([^"]+)"'.format("string" if kind == "string" else "plurals"),
                open(f, encoding="utf-8").read()))
    for path in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
        code = strip_code(open(path, encoding="utf-8").read())
        for m in re.finditer(r"\bR\.(\w+)\.(\w+)", code):
            kind, name = m.group(1), m.group(2)
            table = files_index.get(kind)
            if table is not None and kind in ("style", "color", "dimen", "string", "plurals", "bool", "integer", "array"):
                table = table | {t.replace(".", "_") for t in table}
            if table is not None and name not in table:
                problems.append("{}: нет ресурса R.{}.{}".format(
                    os.path.relpath(path, ROOT), kind, name))

    check_xml_references(res_dir, problems)
    check_manifest_classes(problems)
    check_file_names(problems)

    print("классов:", len({k.dotted for k in classes.values()}))
    if problems:
        seen = set()
        print("НАЙДЕНО ПРОБЛЕМ:", len(problems))
        for p in problems:
            if p not in seen:
                seen.add(p)
                print("  -", p)
        return 1
    print("проблем нет")
    return 0


if __name__ == "__main__":
    sys.exit(main())
