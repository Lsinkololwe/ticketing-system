#!/usr/bin/env python3
"""Lists every /api/internal endpoint and who calls it.

inventory.py treats a REST mapping as an entry point, which is right for a public API and wrong
for an internal one: an internal endpoint exists only for another service, so one that no other
module and no frontend calls is dead however reachable it looks. This turns each mapped path
into a pattern — a {variable} matches anything — and looks for it in the string literals of
every other module, the frontend, and the scripts beside them.

A caller that builds the path from pieces ("/users/" + id + "/exists") is matched on the static
segments around the variables, so a NONE here is a candidate to confirm by reading, not proof.

Usage:  python3 backend/tools/reachability/endpoints.py
"""
from __future__ import annotations

import re
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[2]
REPO = BACKEND.parent
MODULES = ["identity-service", "catalog-service", "booking-service", "api-gateway",
           "keycloak-extensions", "shared-library"]
OTHER_ROOTS = [REPO / "frontend", REPO.parent / "docker-resources", BACKEND / "keycloak-extensions/scripts"]
OTHER_SUFFIXES = {".ts", ".tsx", ".js", ".sh", ".yml", ".yaml"}
SKIP_DIRS = {"node_modules", ".next", "target", "dist", "build"}
MAPPING = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping(?:\(\s*(?:value\s*=\s*|path\s*=\s*)?"([^"]*)")?')
BASE = re.compile(r'@RequestMapping\(\s*(?:value\s*=\s*|path\s*=\s*)?"([^"]+)"')
LITERAL = re.compile(r'"([^"\n]*)"|\'([^\'\n]*)\'|`([^`]*)`')


def literals(text: str) -> str:
    return "\n".join("".join(groups) for groups in LITERAL.findall(text))


def pieces(path: str) -> list[str]:
    """The static runs of a path template, in order: /a/{x}/b -> ['/a/', '/b']."""
    return [p for p in re.split(r"\{[^}]+\}", path) if p.strip("/")]


def called(path: str, text: str) -> bool:
    parts = pieces(path)
    if not parts:
        return False
    # The whole template with variables as wildcards, or its static pieces in order across
    # concatenated literals.
    whole = re.escape(parts[0]) + "".join(r"[^\s\"']*" + re.escape(p) for p in parts[1:])
    if re.search(whole, text):
        return True
    return all(p.rstrip("/") in text for p in parts) and len(parts) > 1


def main() -> int:
    java = {p: p.read_text() for m in MODULES for p in (BACKEND / m / "src/main").rglob("*.java")}
    others = {}
    for root in OTHER_ROOTS:
        if not root.exists():
            continue
        for p in root.rglob("*"):
            if p.is_file() and p.suffix in OTHER_SUFFIXES and not SKIP_DIRS.intersection(p.parts):
                try:
                    others[p] = p.read_text()
                except (UnicodeDecodeError, OSError):
                    continue
    lit = {p: literals(t) for p, t in {**java, **others}.items()}

    print("verdict\tmethod\tpath\towner\tcallers")
    for path, text in java.items():
        base = BASE.search(text)
        if not base or "/internal" not in base.group(1):
            continue
        owner = path.relative_to(BACKEND).parts[0]
        for m in MAPPING.finditer(text):
            full = base.group(1) + (m.group(2) or "")
            callers = sorted({
                (q.relative_to(BACKEND).parts[0] if q in java else q.relative_to(REPO.parent).parts[0])
                for q, t in lit.items()
                if q != path and not (q in java and q.relative_to(BACKEND).parts[0] == owner) and called(full, t)
            })
            print(f"{'OK' if callers else 'NONE'}\t{m.group(1).upper()}\t{full}\t{owner}\t{','.join(callers)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
