#!/usr/bin/env python3
"""Проверка API: сверяет ссылки из скомпилированных классов с исходниками Paper.

В constant pool каждого .class лежат Methodref/Fieldref с точным дескриптором.
Если дескриптор не совпадает с настоящим API Paper, в рантайме будет
NoSuchMethodError / NoSuchFieldError. Скрипт скачивает настоящие исходники
Paper 1.21.11 с GitHub, разбирает объявления и сравнивает сигнатуры.

    python3 tools/verify_api.py build/classes /tmp/paperapi
"""
import os
import re
import struct
import sys
import urllib.parse
import urllib.request

BRANCH = "ver/1.21.11"
API_ROOT = "https://api.github.com/repos/PaperMC/Paper/contents/paper-api/src/main/java/"
CACHE = {}
PRIM = {"B": "byte", "C": "char", "D": "double", "F": "float",
        "I": "int", "J": "long", "S": "short", "Z": "boolean", "V": "void"}
JAVA_LANG = {"String", "Object", "Integer", "Long", "Double", "Float", "Boolean",
             "Byte", "Short", "Character", "Number", "Class", "Enum", "Iterable",
             "Comparable", "Runnable", "Thread", "Throwable", "Exception",
             "RuntimeException", "IllegalArgumentException", "IllegalStateException",
             "UnsupportedOperationException", "StringBuilder", "CharSequence", "Math", "System"}


# ---------- разбор .class ----------
def parse_class(path):
    data = open(path, "rb").read()
    assert data[:4] == b"\xca\xfe\xba\xbe"
    pos, count = 8, struct.unpack(">H", data[8:10])[0]
    pos = 10
    pool = [None] * count
    i = 1
    while i < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            ln = struct.unpack(">H", data[pos:pos + 2])[0]
            pos += 2
            pool[i] = ("utf8", data[pos:pos + ln].decode("utf-8", "replace"))
            pos += ln
        elif tag in (7, 8, 16, 19, 20):
            pool[i] = (tag, struct.unpack(">H", data[pos:pos + 2])[0])
            pos += 2
        elif tag == 15:
            pool[i] = (tag, struct.unpack(">BH", data[pos:pos + 3])[0:3])
            pos += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pool[i] = (tag, struct.unpack(">HH", data[pos:pos + 4]))
            pos += 4
        elif tag in (5, 6):
            pool[i] = (tag, None)
            i += 1
            pos += 8
        else:
            raise ValueError("unknown tag %d in %s" % (tag, path))
        i += 1

    def utf(idx):
        return pool[idx][1]

    refs = []
    for entry in pool:
        if entry and entry[0] in (9, 10, 11):
            _, (class_idx, nat_idx) = entry
            cls = utf(pool[class_idx][1]).replace("/", ".")
            n, d = pool[nat_idx][1]
            refs.append((entry[0], cls, utf(n), utf(d)))
    return refs


def normalize_type(t):
    t = re.sub(r"\bfinal\b", "", t).strip()
    t = t.replace("...", "[]")
    t = strip_generics(t)
    return t


def strip_generics(t):
    out, depth = "", 0
    for ch in t:
        if ch == "<":
            depth += 1
            continue
        if ch == ">":
            depth -= 1
            continue
        if depth == 0:
            out += ch
    return out.strip()


def same_type(real, needle):
    """Сравнение типов: тип-переменная в настоящем API подходит к любому ссылочному."""
    if real == needle:
        return True
    if re.fullmatch(r"[A-Z]{1,2}", real.split(".")[-1]):
        return not needle.startswith(("[", "int", "long", "double", "float", "boolean", "byte", "short", "char", "void"))
    return False


def descriptor_type(desc):
    """Разбор одного типа из дескриптора: 'Ljava/util/List;' -> java.util.List"""
    i = 0
    arr = ""
    while desc[i] == "[":
        arr += "[]"
        i += 1
    ch = desc[i]
    if ch == "L":
        j = desc.index(";", i)
        return desc[i + 1:j].replace("/", ".") + arr
    return PRIM[ch] + arr


def descriptor_types(desc):
    body = desc[1:desc.rindex(")")]
    out, i = [], 0
    while i < len(body):
        ch = body[i]
        if ch == "[":
            start = i
            while body[i] == "[":
                i += 1
            if body[i] == "L":
                j = body.index(";", i)
                out.append(body[i:j].replace("/", ".") + "[]")
                i = j + 1
            else:
                out.append(body[start:i + 1] + PRIM[body[i]])
                i += 1
        elif ch == "L":
            j = body.index(";", i)
            out.append(body[i + 1:j].replace("/", "."))
            i = j + 1
        else:
            out.append(PRIM[ch])
            i += 1
    return out


# ---------- исходники Paper ----------
def fetch(fqn, cache_dir):
    if fqn in CACHE:
        return CACHE[fqn]
    path = os.path.join(cache_dir, fqn.replace(".", "_") + ".java")
    if os.path.exists(path):
        CACHE[fqn] = open(path, encoding="utf-8", errors="ignore").read()
        return CACHE[fqn]
    url = API_ROOT + fqn.replace(".", "/") + ".java?ref=" + urllib.parse.quote(BRANCH, safe="")
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github.raw", "User-Agent": "x"})
    try:
        text = urllib.request.urlopen(req, timeout=40).read().decode("utf-8", "replace")
    except Exception:
        text = None
    if text is not None:
        os.makedirs(cache_dir, exist_ok=True)
        open(path, "w", encoding="utf-8").write(text)
    CACHE[fqn] = text
    return text


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def split_top(text, sep=","):
    out, depth, cur = [], 0, ""
    for ch in text:
        if ch in "<([{":
            depth += 1
        elif ch in ">)]}":
            depth -= 1
        elif ch == sep and depth == 0:
            out.append(cur)
            cur = ""
            continue
        cur += ch
    if cur.strip():
        out.append(cur)
    return out


def parse_source(fqn, text):
    text = strip_comments(text)
    imports = {}
    for m in re.finditer(r"import\s+([\w.]+);", text):
        fq = m.group(1)
        imports[fq.split(".")[-1]] = fq
    pkg = fqn.rsplit(".", 1)[0]
    simple = fqn.rsplit(".", 1)[1]

    def resolve(raw):
        t = raw.strip()
        t = re.sub(r"@[\w.]+(\([^)]*\))?", "", t).strip()
        t = re.sub(r"\bfinal\b", "", t).strip()
        t = re.sub(r"\b(this|super)\b", "", t).strip()
        generics = ""
        if "<" in t:
            idx = t.index("<")
            generics = t[idx:]
            t = t[:idx].strip()
        arr = ""
        while t.endswith("[]") or t.endswith("..."):
            arr += "[]"
            t = t[:-2] if t.endswith("[]") else t[:-3]
            t = t.strip()
        if t in ("int", "long", "double", "float", "boolean", "byte", "short", "char", "void"):
            return t + arr
        if t in imports:
            return imports[t] + arr + generics
        if t in JAVA_LANG:
            return "java.lang." + t + arr + generics
        if t.startswith("java.") or t.startswith("net.") or t.startswith("io."):
            return t + arr + generics
        if "." in t:
            return t + arr + generics
        return pkg + "." + t + arr + generics

    m = re.search(r"\b(?:class|interface|enum|record)\s+" + re.escape(simple) + r"\b([^{]*)\{", text)
    parents, is_enum = [], False
    if m:
        is_enum = bool(re.search(r"\benum\s+" + re.escape(simple) + r"\b", text[:m.end()]))
        head = m.group(1)
        e = re.search(r"\bextends\s+([\w.,\s<>?]+?)(?:\bimplements\b|$)", head)
        if e:
            parents += [resolve(p) for p in split_top(e.group(1)) if p.strip()]
        im = re.search(r"\bimplements\s+([\w.,\s<>?]+)$", head)
        if im:
            parents += [resolve(p) for p in split_top(im.group(1)) if p.strip()]

    body_start = m.end() if m else 0
    methods, fields = scan_body(text, body_start, resolve)
    if is_enum:
        fields.add("values")
        fields.add("valueOf")
        for extra in (("values", (), fqn + "[]"), ("valueOf", ("java.lang.String",), fqn),
                      ("name", (), "java.lang.String"), ("ordinal", (), "int"),
                      ("toString", (), "java.lang.String"),
                      ("equals", ("java.lang.Object",), "boolean"), ("hashCode", (), "int")):
            methods.add(extra)
        # константы перечислений
        for name, _ in scan_enum_constants(text, body_start):
            fields.add(name)
    return parents, methods, fields


def scan_enum_constants(text, start):
    """Константы enum: NAME, NAME(args), NAME { ... } до первой ;"""
    depth, i, out = 1, start, []
    buf = ""
    while i < len(text):
        ch = text[i]
        if ch == "{":
            depth += 1
            if depth == 2:
                i += 1
                continue
        elif ch == "}":
            depth -= 1
            if depth == 0:
                break
        elif ch == ";" and depth == 1:
            break
        elif ch == "," and depth == 1:
            out.append(buf)
            buf = ""
        elif depth == 1:
            buf += ch
        i += 1
    out.append(buf)
    result = []
    for chunk in out:
        mm = re.match(r"\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*([A-Za-z_]\w*)", chunk)
        if mm:
            result.append((mm.group(1), chunk))
    return result


KEYWORDS = {"if", "for", "while", "switch", "catch", "return", "new", "throw",
            "else", "do", "try", "synchronized", "assert", "case", "instanceof"}


def param_type(raw, resolve=None):
    p = re.sub(r"@[\w.]+(\([^)]*\))?", "", raw.split("=")[0]).strip()
    p = re.sub(r"\bfinal\b", "", p).strip()
    if not p:
        return ""
    parts = p.split()
    if len(parts) > 1:
        p = " ".join(parts[:-1])
    return p


def scan_body(text, start, resolve):
    """Идёт по телу класса (глубина 1) и собирает объявления."""
    methods, fields = set(), set()
    depth, i, stmt_start = 1, start, start
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == "{":
            depth += 1
            if depth == 1:
                stmt_start = i + 1
            i += 1
            continue
        if ch == "}":
            depth -= 1
            if depth == 0:
                break
            if depth == 1:
                stmt_start = i + 1
            i += 1
            continue
        if ch == ";" and depth == 1:
            stmt = text[stmt_start:i]
            add_field(stmt, resolve, fields)
            stmt_start = i + 1
            i += 1
            continue
        if ch == "(" and depth == 1:
            prefix = re.sub(r"@[\w.]+(\([^)]*\))?", "", text[stmt_start:i])
            prefix = re.sub(r"^\s*<[^<>]*>\s*", "", prefix)
            mm = re.match(r"\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*"
                          r"((?:(?:public|protected|private|static|final|abstract|default|synchronized|native|strictfp)\s+)*)"
                          r"([\w.$<>\[\], ?]+?)\s+([A-Za-z_]\w*)\s*$", prefix)
            if mm and mm.group(3) not in KEYWORDS and not prefix.rstrip().endswith("."):
                # найти закрывающую скобку параметров
                j, pdepth = i, 0
                while j < n:
                    if text[j] == "(":
                        pdepth += 1
                    elif text[j] == ")":
                        pdepth -= 1
                        if pdepth == 0:
                            break
                    j += 1
                params = text[i + 1:j]
                after = text[j + 1:j + 60].lstrip()
                if after.startswith("{") or after.startswith(";") or after.startswith("throws"):
                    if after.startswith("throws"):
                        k = text.find("{", j)
                        semi = text.find(";", j)
                        after = "{"
                        if semi != -1 and (k == -1 or semi < k):
                            after = ";"
                    name = mm.group(3)
                    if name not in KEYWORDS:
                        plist = tuple(resolve(param_type(p)) for p in split_top(params) if p.strip())
                        rtype = re.sub(r"@[\w.]+(\([^)]*\))?", "", mm.group(2)).strip()
                        rtype = re.sub(r"\b(public|protected|private|static|final|abstract|default|synchronized|native|strictfp)\b", "", rtype).strip()
                        rtype = resolve(rtype) if rtype else "void"
                        methods.add((name, plist, rtype))
                    i = j + 1
                    continue
        i += 1
    return methods, fields


def add_field(stmt, resolve, fields):
    s = stmt.strip()
    if "(" in s or "=" in s and s.split("=")[0].count(" ") > 1:
        pass
    s = re.sub(r"@[\w.]+(\([^)]*\))?", "", s).strip()
    s = re.sub(r"^(?:(?:public|protected|private|static|final|transient|volatile)\s+)+", "", s)
    if "(" in s.split("=")[0]:
        return
    left = s.split("=")[0].strip()
    if not left:
        return
    mm = re.match(r"([\w.$<>\[\], ?]+?)\s+([A-Za-z_]\w*)$", left)
    if not mm:
        return
    typ, name = mm.group(1), mm.group(2)
    if typ in KEYWORDS or typ in ("return", "new"):
        return
    fields.add(name)


INDEX = {}


def api(fqn, cache_dir, seen=None):
    if fqn in INDEX:
        return INDEX[fqn]
    if seen is None:
        seen = set()
    if fqn in seen or not (fqn.startswith("org.bukkit.") or fqn.startswith("io.papermc.paper.")):
        return None
    seen.add(fqn)
    text = fetch(fqn, cache_dir)
    if text is None:
        INDEX[fqn] = None
        return None
    parents, methods, fields = parse_source(fqn, text)
    methods = {(n, tuple(normalize_type(p) for p in ps), normalize_type(r)) for n, ps, r in methods}
    all_m, all_f = set(methods), set(fields)
    for p in parents:
        pdata = api(p.split("<")[0], cache_dir, seen)
        if pdata:
            all_m |= pdata[0]
            all_f |= pdata[1]
    INDEX[fqn] = (all_m, all_f, parents)
    return INDEX[fqn]


def main():
    classes_dir, cache_dir = sys.argv[1], sys.argv[2]
    problems, seen, checked = [], set(), 0
    for root, _, files in os.walk(classes_dir):
        for f in files:
            if not f.endswith(".class"):
                continue
            for kind, owner, name, desc in parse_class(os.path.join(root, f)):
                if not (owner.startswith("org.bukkit.") or owner.startswith("io.papermc.paper.")):
                    continue
                data = api(owner, cache_dir)
                if data is None:
                    continue
                methods, fields, _ = data
                checked += 1
                if kind == 9:
                    if name not in fields:
                        key = ("FIELD", owner, name, desc)
                        if key not in seen:
                            seen.add(key)
                            problems.append(key)
                    continue
                params = tuple(normalize_type(x) for x in descriptor_types(desc))
                if name in ("<init>", "<clinit>"):
                    continue
                rtype = normalize_type(descriptor_type(desc[desc.rindex(")") + 1:]))
                if any(m[0] == name and m[1] == params and same_type(m[2], rtype) for m in methods):
                    continue
                same = sorted(m for m in methods if m[0] == name)
                key = ("METHOD", owner, name, desc,
                       "; ".join("%s(%s)%s" % (m[0], ", ".join(x.split(".")[-1] for x in m[1]), "" if m[2] == "void" else ":" + m[2].split(".")[-1]) for m in same[:5]))
                if key[:4] not in {k[:4] for k in seen}:
                    seen.add(key)
                    problems.append(key)
    for p in problems:
        if p[0] == "FIELD":
            print("НЕТ ПОЛЯ   %s.%s" % (p[1], p[2]))
        else:
            print("НЕТ МЕТОДА %s.%s%s\n           есть: %s" % (p[1], p[2], p[3], p[4]))
    print("\nпроверено ссылок: %d, проблем: %d" % (checked, len(problems)))


if __name__ == "__main__":
    main()
