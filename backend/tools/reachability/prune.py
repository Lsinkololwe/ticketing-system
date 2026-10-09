#!/usr/bin/env python3
"""Deletes what inventory.py classified as dead, and nothing else.

Reads the inventory CSVs and removes:
  - files whose top-level type is DEAD or BEAN_UNINJECTED (and drops that type from the
    extends/implements clauses of the classes that named it),
  - nested types that are DEAD,
  - methods that are DEAD, together with their Javadoc and annotations,
  - imports that no longer resolve to anything used in the file.

Run inventory.py, then this, then compile; repeat until inventory reports no DEAD rows —
deleting a method can leave the private helper it alone called with no caller.

Usage:  python3 backend/tools/reachability/prune.py INVENTORY_DIR [--module M ...] [--dry-run]
"""
from __future__ import annotations

import argparse
import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
DELETABLE_TYPES = {"DEAD", "BEAN_UNINJECTED"}


def mask(text: str) -> str:
    """Comments and literals blanked to spaces, newlines kept: offsets stay valid."""
    out = list(text)
    i, n = 0, len(text)

    def blank(a, b):
        for k in range(a, b):
            if out[k] != "\n":
                out[k] = " "

    while i < n:
        c = text[i]
        if text.startswith("//", i):
            j = text.find("\n", i)
            j = n if j < 0 else j
            blank(i, j)
            i = j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            blank(i, j)
            i = j
        elif text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            blank(i + 1, j - 1)
            i = j
        elif c in "\"'":
            j = i + 1
            while j < n and text[j] != c:
                j += 2 if text[j] == "\\" else 1
            blank(i + 1, j)
            i = j + 1
        else:
            i += 1
    return "".join(out)


def match_close(m: str, start: int, open_ch: str, close_ch: str) -> int:
    depth = 0
    for k in range(start, len(m)):
        if m[k] == open_ch:
            depth += 1
        elif m[k] == close_ch:
            depth -= 1
            if depth == 0:
                return k
    raise ValueError("unbalanced")


def member_span(text: str, m: str, name_pos: int, is_type: bool) -> tuple[int, int]:
    """[start, end) of the member declared at name_pos, including its Javadoc and annotations."""
    # Backward to the previous member terminator at paren depth 0.
    depth, k = 0, name_pos - 1
    while k >= 0:
        ch = m[k]
        if ch == ")":
            depth += 1
        elif ch == "(":
            depth -= 1
        elif depth == 0 and ch in ";{}":
            break
        k -= 1
    start = k + 1
    # Keep the terminator's line: start at the next line.
    nl = text.find("\n", start - 1 if start > 0 else 0)
    if nl != -1 and text[start:nl].strip() == "":
        start = nl + 1
    # Javadoc sits in the masked gap as spaces; the raw text still holds it, which is what we cut.
    if is_type:
        brace = m.find("{", name_pos)
        semi = m.find(";", name_pos)
        # records declare components in parentheses before the body
        paren = m.find("(", name_pos)
        if paren != -1 and paren < brace and (semi == -1 or paren < semi):
            brace = m.find("{", match_close(m, paren, "(", ")"))
        end = match_close(m, brace, "{", "}") + 1
    else:
        paren = m.find("(", name_pos)
        close = match_close(m, paren, "(", ")")
        k = close + 1
        while k < len(m) and m[k] not in "{;":
            k += 1
        end = k + 1 if m[k] == ";" else match_close(m, k, "{", "}") + 1
    # Swallow the rest of the closing line.
    nl = text.find("\n", end)
    if nl != -1 and text[end:nl].strip() == "":
        end = nl + 1
    return start, end


def decl_pos(text: str, m: str, line: int, name: str, is_type: bool) -> int | None:
    lines = text.split("\n")
    offset = sum(len(x) + 1 for x in lines[: line - 1])
    pattern = (r"\b(?:class|interface|enum|record)\s+" + re.escape(name) + r"\b") if is_type \
        else (r"\b" + re.escape(name) + r"\s*\(")
    hit = re.compile(pattern).search(m, offset, offset + len(lines[line - 1]) + 1) if line <= len(lines) else None
    if not hit:
        return None
    return hit.start() if not is_type else hit.start() + hit.group(0).rfind(name)


def tidy_imports(text: str) -> str:
    body_start = 0
    imports = list(re.finditer(r"^import\s+(static\s+)?([\w.]+)(\.\*)?\s*;\s*\n", text, re.M))
    if not imports:
        return text
    body_start = imports[-1].end()
    body = mask(text[body_start:])
    drop = []
    for imp in imports:
        if imp.group(3):
            continue
        simple = imp.group(2).rsplit(".", 1)[-1]
        if not re.search(r"(?<![\w$])" + re.escape(simple) + r"\b", body):
            drop.append(imp)
    for imp in reversed(drop):
        text = text[: imp.start()] + text[imp.end():]
    return text


def collapse_blank_lines(text: str) -> str:
    return re.sub(r"\n{3,}", "\n\n", text)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("inventory")
    ap.add_argument("--module", action="append")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--member", action="append", default=[],
                    help="FILE:NAME, a reviewed deletion the inventory did not classify DEAD")
    args = ap.parse_args()
    if args.member:
        return delete_members(args.member, args.dry_run)
    inv = Path(args.inventory)

    def wanted(row):
        return not args.module or row["module"] in args.module

    types = [r for r in csv.DictReader((inv / "inventory-types.csv").open()) if wanted(r)]
    methods = [r for r in csv.DictReader((inv / "inventory-methods.csv").open()) if wanted(r)]

    dead_files, dead_type_names, nested = set(), set(), defaultdict(list)
    for r in types:
        if r["verdict"] not in DELETABLE_TYPES:
            continue
        if Path(r["file"]).stem == r["type"]:
            dead_files.add(r["file"])
            dead_type_names.add(r["type"])
        else:
            nested[r["file"]].append((int(r["line"]), r["type"], True))

    edits = defaultdict(list)
    for file, items in nested.items():
        edits[file].extend(items)
    for r in methods:
        if r["verdict"] == "DEAD" and r["file"] not in dead_files:
            edits[r["file"]].append((int(r["line"]), r["method"], False))

    report = []
    for file in sorted(dead_files):
        report.append(f"delete file  {file}")
        if not args.dry_run:
            (REPO / file).unlink()

    # Strip deleted types from supertype clauses across every module's main sources.
    if dead_type_names:
        for path in (REPO / "backend").glob("*/src/main/java/**/*.java"):
            rel = str(path.relative_to(REPO))
            if rel in dead_files:
                continue
            text = path.read_text()
            new = text
            for name in dead_type_names:
                ref = name + r"(?:\s*<[^<>{};]*(?:<[^<>{};]*>[^<>{};]*)*>)?"
                new = re.sub(r"(\bimplements\s+)" + ref + r"\s*,\s*", r"\1", new)
                new = re.sub(r",\s*" + ref + r"(?=[^;{]*\{)", "", new)
                new = re.sub(r"\s+implements\s+" + ref + r"\s*(?=\{)", " ", new)
            if any(re.search(r"^import\s+[\w.]+\." + n + r"\s*;", new, re.M) for n in dead_type_names):
                edits.setdefault(rel, [])   # a dangling import: tidied below
            if new != text:
                report.append(f"supertype    {rel}")
                edits.setdefault(rel, [])
                if not args.dry_run:
                    path.write_text(new)

    for file, items in sorted(edits.items()):
        path = REPO / file
        if not path.exists():
            continue
        text = path.read_text()
        spans = []
        for line, name, is_type in items:
            m = mask(text)
            pos = decl_pos(text, m, line, name, is_type)
            if pos is None:
                report.append(f"NOT FOUND    {file}:{line} {name}")
                continue
            spans.append((*member_span(text, m, pos, is_type), name))
        # Delete bottom-up so earlier offsets stay valid; skip spans inside a larger one.
        spans.sort(key=lambda s: (s[0], -s[1]))
        merged = []
        for s in spans:
            if merged and s[0] < merged[-1][1]:
                continue
            merged.append(s)
        for start, end, name in reversed(merged):
            report.append(f"delete       {file}: {name}")
            text = text[:start] + text[end:]
        text = collapse_blank_lines(tidy_imports(text))
        if not args.dry_run:
            path.write_text(text)

    print("\n".join(report))
    print(f"\n{len(dead_files)} files, {sum(1 for r in report if r.startswith('delete   '))} members", file=sys.stderr)
    return 0


def delete_members(specs: list[str], dry_run: bool) -> int:
    by_file = defaultdict(list)
    for spec in specs:
        file, _, name = spec.rpartition(":")
        by_file[file].append(name)
    for file, names in by_file.items():
        path = REPO / file
        text = path.read_text()
        for name in names:
            m = mask(text)
            is_type = bool(re.search(r"\b(?:class|interface|enum|record)\s+" + re.escape(name) + r"\b", m))
            pattern = (r"\b(?:class|interface|enum|record)\s+(" + re.escape(name) + r")\b") if is_type \
                else (r"(?<![\w.])(" + re.escape(name) + r")\s*\((?=[^;{}]*\)\s*(?:throws[^{;]*)?[{;])")
            hits = list(re.finditer(pattern, m))
            if not hits:
                print(f"NOT FOUND    {file}: {name}")
                continue
            # Every overload of the name goes; delete bottom-up.
            for hit in reversed(hits):
                start, end = member_span(text, mask(text), hit.start(1), is_type)
                text = text[:start] + text[end:]
            print(f"delete       {file}: {name} x{len(hits)}")
        if not dry_run:
            path.write_text(collapse_blank_lines(tidy_imports(text)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
