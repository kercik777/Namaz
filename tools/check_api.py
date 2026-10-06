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


# Часто используемые классы платформы: если встречаются, должны быть импортированы
JAVA_LANG_OK = {"String", "StringBuilder", "StringBuffer", "Math", "System", "Runnable",
                 "Thread", "Integer", "Long", "Double", "Float", "Boolean", "Character",
                 "Object", "Number", "Iterable", "Comparable", "Exception", "RuntimeException",
                 "Throwable", "Class", "Void", "Byte", "Short", "Deprecated", "Override"}

PLATFORM_CLASSES = """
Intent Uri Bundle View ViewGroup MotionEvent Gravity Canvas Paint Path Rect RectF Bitmap BitmapFactory
BitmapShader Shader LinearGradient Typeface StaticLayout TextPaint Layout SpannableString Spannable
ForegroundColorSpan StyleSpan RelativeSizeSpan AlignmentSpan ClipData ClipboardManager Vibrator
SharedPreferences Context ContextWrapper Resources Configuration DisplayMetrics Handler Looper Runnable
Activity Application Fragment Dialog AlertDialog Toast Notification NotificationManager PendingIntent
Drawable ColorDrawable GradientDrawable RippleDrawable ColorStateList StateListDrawable
ScrollView LinearLayout FrameLayout RelativeLayout TextView ImageView Button EditText ProgressBar
SeekBar Switch CheckBox RadioButton Space Toolbar ViewPager RecyclerView ListView GridView
ScaleGestureDetector GestureDetector ValueAnimator ObjectAnimator Animator Interpolator
SimpleDateFormat Date Calendar Locale ArrayList HashMap HashSet List Map Set Collections Arrays
Math System String Integer Long Float Double Boolean Character StringBuilder Thread IOException
File FileOutputStream FileInputStream InputStream OutputStream BufferedReader InputStreamReader
ActivityNotFoundException JSONObject JSONArray IPackageManager InputMethodManager Window WindowManager
""".split()


def check_platform_imports(problems):
    for path in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
        raw = open(path, encoding="utf-8").read()
        code = strip_code(raw)
        imports = set(re.findall(r"^\s*import\s+([\w.]+)\s*;", code, flags=re.M))
        imported = {i.rsplit(".", 1)[-1] for i in imports}
        for name in PLATFORM_CLASSES:
            if name in imported or name in JAVA_LANG_OK:
                continue
            # используем как тип или как Класс.метод
            if re.search(r"(?:^|[^\w.\"]){}(?:\s+[a-zA-Z_][\w]*|\s*\.)".format(name), code):
                problems.append("{}: класс {} не импортирован".format(os.path.relpath(path, ROOT), name))



def class_fields(code):
    """Имена полей класса (объявления с модификаторами или без них)."""
    names = set()
    for line in code.split("\n"):
        m = re.match(r"\s*(?:public|protected|private|static|final|volatile|transient|\s)+"
                     r"[\w<>\[\],.\s]+?\s+([a-zA-Z_][\w]*)\s*(?:=|;)", line)
        if m:
            names.add(m.group(1))
        else:
            m2 = re.match(r"^\s{4,8}([\w<>\[\],.]+)\s+([a-zA-Z_][\w]*)\s*(?:=|;)", line)
            if m2:
                names.add(m2.group(2))
    return names


def check_member_access(classes, problems):
    """Обращение к полю своей переменной: var.field, где field не объявлен."""
    by_name = {}
    for k in classes.values():
        if "." not in k.dotted:
            by_name.setdefault(k.dotted, k)
    fields = {}
    for name, k in by_name.items():
        fields[name] = class_fields(strip_code(open(k.path, encoding="utf-8").read()))
    for path in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
        code = strip_code(open(path, encoding="utf-8").read())
        decls = {}
        for m in re.finditer(r"\b(" + "|".join(re.escape(n) for n in by_name) + r")\s+([a-zA-Z_][\w]*)\s*(?:=|;|\))", code):
            decls.setdefault(m.group(2), set()).add(m.group(1))
        for var, clss in decls.items():
            for m in re.finditer(r"\b{}\s*\.\s*([a-zA-Z_][\w]*)".format(re.escape(var)), code):
                member = m.group(1)
                tail = code[m.end():m.end() + 1]
                if tail == "(":
                    continue  # вызов метода
                if not any(member in fields.get(c, set()) for c in clss):
                    problems.append("{}: {}.{} — поле не найдено в {}".format(
                        os.path.relpath(path, ROOT), var, member, " / ".join(sorted(clss))))


# Методы платформы, которые вызываются без квалификатора внутри Activity/View
FRAMEWORK_CALLS = set("""
getString getResources getApplicationContext getApplication getSystemService getSharedPreferences
getPackageName getPackageManager setContentView findViewById finish startActivity startActivityForResult
onBackPressed setResult getIntent getWindow getLayoutInflater getAssets getWindowManager getContentResolver
isFinishing recreate setTitle overridePendingTransition onNewIntent onSaveInstanceState onRestoreInstanceState
onCreate onStart onStop onDestroy onResume onPause onRestart onPostCreate onPostResume onActivityResult
onRequestPermissionsResult onWindowFocusChanged onConfigurationChanged onKeyDown onTouchEvent onInterceptTouchEvent
invalidate postInvalidate requestLayout post postDelayed removeCallbacks getWidth getHeight getMeasuredWidth
setMeasuredDimension setLayoutParams setTheme getWindowManagerOf setContentView
getMeasuredHeight getPaddingLeft getPaddingRight getPaddingTop getPaddingBottom setPadding getContext notify
wait equals hashCode toString getClass clone isShown getVisibility setVisibility getId animate setAlpha
setTranslationX setTranslationY setTranslationZ setScaleX setScaleY setRotation setRotationX setRotationY
setTag getTag setOnClickListener setOnTouchListener setBackground setBackgroundColor setForeground
clearAnimation removeAllViews addView removeView removeViewAt getChildCount getChildAt indexOfChild
bringToFront callOnClick performClick getRootView getParent getLayoutParams getAlpha setEnabled setSelected
setClickable setLongClickable setFocusable setWillNotDraw scrollTo smoothScrollTo setX setY getX getY
setPivotX setPivotY setLayerType setCameraDistance getLocationInWindow getLocationOnScreen offsetTopAndBottom
computeScroll draw dispatchDraw onDraw onMeasure onLayout onSizeChanged onAttachedToWindow onDetachedFromWindow
notifyDataSetChanged notifyItemChanged getItemCount getItemViewType onCreateViewHolder onBindViewHolder
getItemId isEmpty size get length clear put get remove contains keySet values entrySet add addAll iterator
next hasNext append insert delete replace indexOf lastIndexOf substring trim split toCharArray toUpperCase
toLowerCase startsWith endsWith contains charAt format valueOf toStringOf join sort reverse copyOf copyOfRange
asList fill binarySearch setAll removeIf forEach stream map filter collect parseInt parseFloat parseLong
max min abs round floor ceil sqrt pow random signum toHexString equalsIgnoreCase compareTo compare matches
toIntExact isEmptyOf intern getBytes concat padStart repeat strip lines chars codePoints of
""".split())


def declared_methods(path, code):
    """Имена методов и конструкторов, объявленных в файле (построчный разбор)."""
    names = set()
    mods = r"(?:(?:public|private|protected|static|final|synchronized|abstract|native|default|strictfp)\s+)*"
    with_type = re.compile(r"^\s*" + mods + r"([\w.$<>\[\],]+(?:\s*<[^;{]*?>)?(?:\[\])?)\s+([a-zA-Z_]\w*)\s*\(")
    ctor = re.compile(r"^\s*(?:(?:public|private|protected)\s+)([A-Za-z_]\w*)\s*\(")
    for line in code.split("\n"):
        line = re.sub(r"^\s*(?:@\w+(?:\([^)]*\))?\s*)+", "", line)
        m = with_type.match(line)
        if m:
            names.add(m.group(2))
        m = ctor.match(line)
        if m:
            names.add(m.group(1))
    for m in re.finditer(r"\b(?:class|interface|enum)\s+([A-Za-z_0-9]+)", code):
        names.add(m.group(1))
        names.add("new " + m.group(1))
    return names


def super_chain(path, code, by_path):
    """Методы методов-предков по цепочке extends (только свои классы)."""
    names = set()
    seen = {path}
    cur_path, cur_code = path, code
    while True:
        names |= declared_methods(cur_path, cur_code)
        m = re.search(r"\bclass\s+[A-Za-z_0-9]+\s+extends\s+([A-Za-z_0-9.]+)", cur_code)
        if not m:
            break
        base = m.group(1).rsplit(".", 1)[-1]
        nxt = by_path.get(base)
        if not nxt or nxt in seen:
            break
        seen.add(nxt)
        cur_path = nxt
        cur_code = strip_code(open(nxt, encoding="utf-8").read())
    return names


def check_self_calls(classes, problems):
    """Неквалифицированные вызовы методов: определены ли они в классе или в предках."""
    by_path = {k.dotted: k.path for k in classes.values() if "." not in k.dotted}
    by_path = {os.path.basename(p)[:-5]: p for p in set(by_path.values())}
    known_classes = {k.rsplit(".", 1)[-1] for k in by_path}
    known_classes |= set(PLATFORM_CLASSES) | JAVA_LANG_OK
    for path in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
        raw = open(path, encoding="utf-8").read()
        code = strip_code(raw)
        if not re.search(r"\bclass\b", code):
            continue
        known = super_chain(path, code, by_path) | FRAMEWORK_CALLS
        imported = {i.rsplit(".", 1)[-1]
                    for i in re.findall(r"^\s*import\s+(?:static\s+)?([\w.]+)\s*;", code, flags=re.M)}
        known_classes = (known_classes - {os.path.basename(path)[:-5]}) | imported
        for m in re.finditer(r"(?<![\w.])([a-zA-Z_]\w*)\s*\(", code):
            name = m.group(1)
            before = code[:m.start()].rstrip()
            if (before.endswith("new") or before.endswith(".") or before.endswith(">")
                    or before.endswith("@")):
                continue
            if name in known or name in RESERVED or name in known_classes:
                continue
            line = code[:m.start()].count("\n") + 1
            problems.append("{}:{}: вызов {}() — метод не объявлен".format(
                os.path.relpath(path, ROOT), line, name))


RESERVED = {"if", "for", "while", "switch", "catch", "return", "new", "synchronized", "super",
            "this", "throw", "assert", "case", "do", "else", "try", "instanceof", "final", "static"}


def project_interfaces():
    """Имя интерфейса -> имена его методов (в т.ч. по цепочке extends)."""
    ifaces = {}
    extra = []
    for path in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
        code = strip_code(open(path, encoding="utf-8").read())
        for m in re.finditer(r"\binterface\s+(\w+)\s*(?:extends\s+([\w.]+))?\s*\{", code):
            name = m.group(1)
            body = brace_body(code, m.end() - 1)
            methods = {x for x in declared_methods(path, body)}
            methods.discard(name)
            methods = {x for x in methods if not x.startswith("new ")}
            ifaces[name] = (methods, m.group(2))
            if m.group(2):
                extra.append((name, m.group(2).rsplit(".", 1)[-1]))
    for name, base in extra:
        if name in ifaces and base in ifaces:
            ifaces[name] = (ifaces[name][0] | ifaces[base][0], None)
    return {k: v[0] for k, v in ifaces.items()}


def brace_body(code, at):
    """Тело блока, начиная с позиции «{»."""
    depth = 0
    j = at
    while j < len(code):
        if code[j] == "{":
            depth += 1
        elif code[j] == "}":
            depth -= 1
            if depth == 0:
                return code[at + 1:j]
        j += 1
    return code[at + 1:]


def check_anonymous_impls(problems):
    """Анонимный класс по своему интерфейсу должен реализовать все его методы."""
    ifaces = project_interfaces()
    for path in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
        code = strip_code(open(path, encoding="utf-8").read())
        for m in re.finditer(r"new\s+([A-Z][\w.]*)\s*\(\s*\)\s*\{", code):
            owner = m.group(1).rsplit(".", 1)[-1]
            if owner not in ifaces:
                continue
            body = brace_body(code, m.end() - 1)
            for meth in sorted(ifaces[owner]):
                if not re.search(r"\b{}\s*\(".format(re.escape(meth)), body):
                    line = code[:m.start()].count("\n") + 1
                    problems.append("{}:{}: в new {}() нет метода {}()".format(
                        os.path.relpath(path, ROOT), line, m.group(1), meth))


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
        "drawable": {os.path.basename(f).split(".")[0]
                     for f in glob.glob(os.path.join(res_dir, "drawable", "*"))
                     + glob.glob(os.path.join(res_dir, "drawable-*", "*"))},
        "string": set(), "plurals": set(),
    }
    # дубликаты имён внутри одного файла строк -- AAPT падает
    for f in glob.glob(os.path.join(res_dir, "values*", "strings.xml")):
        text = open(f, encoding="utf-8").read()
        names = re.findall(r'<(string|plurals)\s+name="([^"]+)"', text)
        seen = set()
        for tag, name in names:
            if (tag, name) in seen:
                problems.append("{}: повторяется {} {}".format(
                    os.path.relpath(f, ROOT), tag, name))
            seen.add((tag, name))

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

    check_member_access(classes, problems)
    check_self_calls(classes, problems)
    check_anonymous_impls(problems)
    check_platform_imports(problems)
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
