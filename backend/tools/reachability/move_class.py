#!/usr/bin/env python3
"""Moves Java classes between packages and rewrites every reference in the backend.

usage: move_class.py BACKEND_DIR old.pkg.Name new.pkg [new.pkg.NewName ...]
       (pairs: OLD_FQCN NEW_PACKAGE_OR_FQCN, repeated)
A destination ending in a capitalised segment renames the class as well.
"""
import re
import sys
from pathlib import Path

backend = Path(sys.argv[1])
pairs = list(zip(sys.argv[2::2], sys.argv[3::2]))

JAVA = [p for p in backend.glob("*/src/**/*.java")]


def split(fqcn):
    pkg, _, name = fqcn.rpartition(".")
    return pkg, name


def strip(text):
    return re.sub(r"//[^\n]*|/\*.*?\*/|\"(?:\\.|[^\"\\\n])*\"", " ", text, flags=re.S)


def package_of(text):
    m = re.search(r"^package ([\w.]+);", text, re.M)
    return m.group(1) if m else ""


def add_import(text, fqcn):
    if re.search(r"^import " + re.escape(fqcn) + r";", text, re.M):
        return text
    m = re.search(r"^import ", text, re.M)
    if m:
        return text[:m.start()] + f"import {fqcn};\n" + text[m.start():]
    return re.sub(r"^(package [\w.]+;\n)", r"\1\nimport " + fqcn + ";\n", text, count=1, flags=re.M)


def source_path(fqcn):
    pkg, name = split(fqcn)
    rel = Path(*pkg.split(".")) / f"{name}.java"
    for p in JAVA:
        if str(p).endswith(str(rel)) and "/src/main/java/" in str(p):
            return p
    raise SystemExit(f"not found: {fqcn}")


for old, dest in pairs:
    old_pkg, old_name = split(old)
    if dest.rsplit(".", 1)[-1][:1].isupper():
        new_pkg, new_name = split(dest)
    else:
        new_pkg, new_name = dest, old_name
    new = f"{new_pkg}.{new_name}"
    src = source_path(old)
    text = src.read_text()

    # Same-package neighbours the moved class used without an import now need one.
    old_dir_classes = {p.stem for p in src.parent.glob("*.java") if p.stem not in ("package-info", old_name)}
    body = strip(text)
    for neighbour in sorted(old_dir_classes):
        if re.search(r"(?<![\w.$])" + neighbour + r"\b", body):
            text = add_import(text, f"{old_pkg}.{neighbour}")
    text = re.sub(r"^package [\w.]+;", f"package {new_pkg};", text, count=1, flags=re.M)
    if new_name != old_name:
        text = re.sub(r"\b" + old_name + r"\b", new_name, text)
    # An import of a class now in the same package is redundant.
    text = re.sub(r"^import " + re.escape(new_pkg) + r"\.\w+;\n", "", text, flags=re.M)
    dst = src.parent
    for _ in old_pkg.split("."):
        dst = dst.parent
    dst = dst.joinpath(*new_pkg.split(".")) / f"{new_name}.java"
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(text)
    src.unlink()
    JAVA = [p for p in JAVA if p != src] + [dst]

    for p in JAVA:
        if p == dst:
            continue
        t = p.read_text()
        o = t
        t = t.replace(f"import {old};", f"import {new};")
        t = t.replace(f"import static {old}.", f"import static {new}.")
        t = re.sub(re.escape(old) + r"\b", new, t)
        pkg = package_of(t)
        uses = re.search(r"(?<![\w.$])" + old_name + r"\b", strip(t))
        if new_name != old_name and uses:
            if f"import {new};" in t or pkg == old_pkg or pkg == new_pkg:
                t = re.sub(r"(?<![\w.$])" + old_name + r"\b", new_name, t)
        if pkg == old_pkg and uses and pkg != new_pkg:
            t = add_import(t, new)
        if pkg == new_pkg:
            t = t.replace(f"import {new};\n", "")
        if t != o:
            p.write_text(t)
    print(f"moved {old} -> {new}")
