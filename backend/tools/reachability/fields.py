#!/usr/bin/env python3
"""Finds fields nothing reads, including the ones PMD cannot judge.

PMD skips any class carrying a Lombok annotation, because a generated getter, builder or
record accessor can read a field without its name appearing in that class. This looks
for the generated names instead. A field counts as read when any of these holds:

  - its own class reads it: the bare name outside its declaration, `this.x = x`
    assignments and constructor signatures;
  - any main source calls `getX(`, `isX(`, `.x()` or references `::getX` / `::x`;
  - the module's GraphQL schema declares a field of that name, since DGS reads the
    property reflectively;
  - a string literal or configuration key names it (Criteria, @Query, @Field, YAML).

What is left is classified:
  UNUSED        the name appears nowhere but its declaration;
  WRITE_ONLY    something sets it (setter, builder, constructor) and nothing reads it;
  PERSISTED     it lives in a @Document class: the database is the reader, so it is
                reported, never deleted by this tool;
  PERSISTED_UNWRITTEN  the same, but nothing in the code sets it either: only data written
                before the code changed can hold a value;
  WIRE          the class carries @Json* annotations or sits in a client/gateway/dto
                package: a remote party may read or send it, so check its contract first.

Usage:  python3 backend/tools/reachability/fields.py [--module M ...] > fields.tsv
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from prune import mask  # noqa: E402

BACKEND = Path(__file__).resolve().parents[2]
MODULES = ["shared-library", "identity-service", "catalog-service", "booking-service",
           "api-gateway", "keycloak-extensions"]
MODIFIERS = r"(?:(?:public|protected|private|static|final|transient|volatile)\s+)*"
MODIFIERS_LINE = r"(?:(?:public|protected|private|static|final|transient|volatile)[ \t]+)*"
FIELD = re.compile(r"^[ \t]*" + MODIFIERS_LINE
                   + r"([\w.$]+(?:<[^;=(){}\n]*>)?(?:\[\])*)[ \t]+(\w+)[ \t]*(?:=[^;]*)?;", re.M)
TYPE_DECL = re.compile(r"\b(class|record|enum|interface)\s+(\w+)")
WIRE_PACKAGES = ("/client/", "/gateway/", "/infrastructure/", "/messaging/")


def cap(name: str) -> str:
    return name[:1].upper() + name[1:]


def scope(module: str) -> list[str]:
    if module == "shared-library":
        return MODULES
    return [module, "shared-library"]


def sources(module: str) -> list[Path]:
    return sorted((BACKEND / module / "src/main/java").rglob("*.java"))


def member_fields(text: str, masked: str):
    """Yields (type_name, field_name, decl_start, decl_end, class_annotations, is_record)."""
    for tm in TYPE_DECL.finditer(masked):
        kind, tname = tm.groups()
        if kind in ("interface",):
            continue
        header_start = masked.rfind("\n\n", 0, tm.start())
        header = text[max(header_start, 0):tm.start()]
        if kind == "record":
            open_paren = masked.find("(", tm.end())
            depth, j = 0, open_paren
            while True:
                depth += {"(": 1, ")": -1}.get(masked[j], 0)
                if depth == 0:
                    break
                j += 1
            params = masked[open_paren + 1:j]
            flat_params = re.sub(r"<[^<>]*>", "", re.sub(r"<[^<>]*>", "", params))
            for comp in re.findall(r"(\w+)\s*(?:,|$)", flat_params):
                at = re.search(r"\b" + comp + r"\s*(?:,|$)", params)
                start = open_paren + 1 + at.start()
                yield tname, comp, start, start + len(comp), header, True
        body = masked.find("{", tm.end())
        depth, i = 0, body
        while i < len(masked):
            c = masked[i]
            if c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        # Only statements directly in the type body: blank out nested blocks.
        inner = list(masked[body + 1:i])
        d = 0
        for k, c in enumerate(inner):
            if c == "{":
                d += 1
            if d > 0 and c != "\n":
                inner[k] = " "
            if c == "}":
                d -= 1
        flat = "".join(inner)
        if kind == "enum":
            semi = flat.find(";")
            flat = " " * (semi + 1) + flat[semi + 1:] if semi >= 0 else ""
        for fm in FIELD.finditer(flat):
            stmt = fm.group(0)
            if " static " in f" {stmt} " or "(" in stmt.split("=")[0] or fm.group(1).strip() in (
                    "return", "throw", "package", "import"):
                continue
            yield (tname, fm.group(2), body + 1 + fm.start(), body + 1 + fm.end(),
                   header, False)


def tokens(masked: str, raw: str, config: str):
    """Names the corpus reads, names it writes, and words that appear in literals or config."""
    reads = set(re.findall(r"\b((?:get|is)[A-Z]\w*)\s*\(", masked))
    reads |= set(re.findall(r"::\s*(\w+)", masked))
    reads |= set(re.findall(r"\.\s*(\w+)\s*\(\s*\)", masked))
    writes = set(re.findall(r"\b(set[A-Z]\w*)\s*\(", masked))
    writes |= set(re.findall(r"\.\s*(\w+)\s*\(", masked))
    literals = set()
    for lit in re.findall(r'"((?:[^"\\\n]|\\.)*)"', raw):
        literals |= set(re.findall(r"\w+", lit))
    for word in re.findall(r"[\w-]+", config):
        literals.add(word)
        literals.add(re.sub(r"-(\w)", lambda m: m.group(1).upper(), word))
    return reads, writes, literals


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--module", action="append")
    args = ap.parse_args()
    modules = args.module or MODULES

    corpus_masked, corpus_raw, schema, config = {}, {}, {}, {}
    for m in MODULES:
        texts = [(p, p.read_text()) for p in sources(m)]
        corpus_raw[m] = texts
        corpus_masked[m] = "\n".join(mask(t) for _, t in texts)
        schema[m] = set(re.findall(r"^\s*(\w+)\s*(?:\(|:)", "\n".join(
            p.read_text() for p in (BACKEND / m / "src/main/resources").rglob("*.graphqls")), re.M))
        config[m] = "\n".join(p.read_text() for p in (BACKEND / m / "src/main/resources").rglob("*.y*ml"))

    index = {}
    for m in MODULES:
        index[m] = tokens(corpus_masked[m], "\n".join(t for _, t in corpus_raw[m]), config[m])

    print("module\tfile\tline\tclass\tfield\tverdict")
    for module in modules:
        reads_any, writes_any, literal_words = set(), set(), set()
        for s in scope(module):
            r, w, l = index[s]
            reads_any |= r
            writes_any |= w
            literal_words |= l
        wide_schema = set().union(*(schema[s] for s in scope(module)))
        for path, text in corpus_raw[module]:
            masked = mask(text)
            seen = set()
            for tname, name, a, b, header, is_record in member_fields(text, masked):
                if (tname, name) in seen or name == "serialVersionUID":
                    continue
                seen.add((tname, name))
                C = cap(name)
                getter = name if re.match(r"is[A-Z]", name) else f"is{C}"
                own = masked[:a] + " " * (b - a) + masked[b:]
                own = re.sub(r"this\s*\.\s*" + name + r"\s*=", " ", own)
                own = re.sub(r"\b" + re.escape(tname) + r"\s*\([^)]*\)\s*\{", " ", own)
                if re.search(r"(?<![\w$])" + name + r"(?![\w$(])", own) or re.search(
                        r"\.\s*" + name + r"\s*\(\s*\)", own):
                    continue
                if {f"get{C}", getter, name} & reads_any:
                    continue
                if name in wide_schema or name in literal_words:
                    continue
                writes = (f"set{C}" in writes_any or name in writes_any or is_record
                          or re.search(r"this\s*\.\s*" + name + r"\s*=", masked) or "=" in masked[a:b])
                rel = str(path.relative_to(BACKEND))
                if "@Document" in text:
                    verdict = "PERSISTED" if writes else "PERSISTED_UNWRITTEN"
                elif "@Json" in text or any(w in rel for w in WIRE_PACKAGES) or "/dto/" in rel:
                    verdict = "WIRE"
                else:
                    verdict = "WRITE_ONLY" if writes else "UNUSED"
                print(f"{module}\t{rel}\t{text.count(chr(10), 0, a) + 1}\t{tname}\t{name}\t{verdict}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
