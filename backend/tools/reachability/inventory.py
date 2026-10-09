#!/usr/bin/env python3
"""Reachability inventory for the backend modules.

Classifies every declared method, type and custom configuration key in src/main by
joining four sources of evidence:

  1. a name-based reference graph over src/main and src/test (comments stripped),
  2. framework entry points (DGS, REST, Temporal, Spring callbacks, SPI, SpEL),
  3. the names the specs declare (specs/**/spec.yaml: GraphQL operations,
     workflow activities, collections),
  4. JaCoCo method coverage, when target/site/jacoco/jacoco.xml exists
     (mvn -f backend -Pcoverage verify).

Name-based matching over-counts references (two methods sharing a name keep each other
alive), so DEAD is a lower bound: every row it reports has no caller by any spelling.

Usage:  python3 backend/tools/reachability/inventory.py [--out DIR]
Writes: inventory-methods.csv, inventory-types.csv, inventory-config.csv, summary.txt
"""
from __future__ import annotations

import argparse
import csv
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

import yaml

BACKEND = Path(__file__).resolve().parents[2]
REPO = BACKEND.parent
SERVICES = ["identity-service", "catalog-service", "booking-service", "api-gateway"]
MODULES = ["shared-library", *SERVICES, "keycloak-extensions"]

# Methods the framework calls by annotation rather than by name.
ENTRY_METHOD_ANNOTATIONS = {
    "DgsQuery", "DgsMutation", "DgsData", "DgsEntityFetcher", "DgsSubscription", "DgsScalar",
    "DgsDirective", "DgsTypeDefinitionRegistry", "DgsCodeRegistry", "DgsRuntimeWiring",
    "DgsDataLoader", "DgsEnableDataFetcherInstrumentation", "DgsExceptionHandler",
    "Bean", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping",
    "RequestMapping", "ExceptionHandler", "EventListener", "PostConstruct", "PreDestroy",
    "WorkflowMethod", "UpdateMethod", "UpdateValidatorMethod", "SignalMethod", "QueryMethod",
    "Around", "Before", "After", "AfterReturning", "AfterThrowing", "Pointcut",
    "JsonCreator", "JsonValue", "JsonProperty", "JsonAnySetter", "JsonAnyGetter",
    "ReadingConverter", "WritingConverter", "Scheduled", "ChangeSet", "Execution",
    "ReadOperation", "WriteOperation", "DeleteOperation",
}
ENTRY_TYPE_ANNOTATIONS = {
    "DgsComponent", "RestController", "Controller", "ControllerAdvice", "RestControllerAdvice",
    "Configuration", "AutoConfiguration", "WorkflowImpl", "ActivityImpl", "Aspect", "DgsScalar",
    "Endpoint", "SpringBootApplication",
}
STEREOTYPES = {"Component", "Service", "Repository"}
# Names the JDK or a framework interface calls on our overrides.
FRAMEWORK_METHOD_NAMES = {
    "toString", "equals", "hashCode", "compareTo", "apply", "accept", "test", "get", "run",
    "call", "main", "close", "filter", "configure", "customize", "convert", "handle", "health",
    "onApplicationEvent", "afterPropertiesSet", "destroy", "getOrder", "supports", "validate",
    "initialize", "invoke", "matches", "resolve", "serialize", "deserialize", "parseValue",
    "parseLiteral", "valueToLiteral", "create", "close", "init", "authenticate", "action",
    "requiresUser", "configuredFor", "setRequiredActions", "onEvent", "getId", "getDisplayType",
    "getHelpText", "getConfigProperties", "getRequirementChoices", "isConfigurable",
    "isUserSetupAllowed", "getReferenceCategory", "postInit", "decode", "encode",
}
KEYWORDS = {"return", "new", "else", "if", "for", "while", "switch", "catch", "throw", "case",
            "yield", "do", "try", "synchronized", "assert", "super", "this", "instanceof"}
FRAMEWORK_CONFIG_ROOTS = {"spring", "server", "management", "logging", "dgs", "resilience4j",
                          "springdoc", "info", "mongock", "debug", "trace", "eureka"}

COMMENT = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)
STRING = re.compile(r'"""(?:.|\n)*?"""|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])\'')
TYPE_DECL = re.compile(
    r"((?:@\w+(?:\.\w+)*(?:\s*\((?:[^()]|\([^()]*\))*\))?\s*)*)"
    r"(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed|strictfp)\s+)*"
    r"(class|interface|enum|record|@interface)\s+(\w+)")
METHOD_DECL = re.compile(
    r"(?<=[;{}])(\s*(?:@\w+(?:\.\w+)*(?:\s*\((?:[^()]|\([^()]*\))*\))?\s*)*)"
    r"((?:(?:public|protected|private|static|final|abstract|default|synchronized|native)\s+)*)"
    r"(?:<[^;{}()]*?>\s+)?"
    r"([\w.$]+(?:\s*<[^;{}()=]*?>)?(?:\s*\[\])*)\s+(\w+)\s*\(")
ANNOTATION = re.compile(r"@(\w+(?:\.\w+)*)")


def strip_comments(text: str) -> str:
    # Protect strings first so that "//" inside a URL literal is not treated as a comment.
    holders: list[str] = []

    def hold(m):
        holders.append(m.group(0))
        return f"\x00{len(holders) - 1}\x00"

    protected = STRING.sub(hold, text)
    protected = COMMENT.sub(lambda m: " " * len(m.group(0)) if "\n" not in m.group(0)
                            else "\n" * m.group(0).count("\n"), protected)
    return re.sub(r"\x00(\d+)\x00", lambda m: holders[int(m.group(1))], protected)


def blank_strings(text: str) -> str:
    return STRING.sub(lambda m: '""', text)


class Source:
    def __init__(self, module: str, path: Path, is_test: bool):
        self.module, self.path, self.is_test = module, path, is_test
        raw = path.read_text(encoding="utf-8", errors="replace")
        self.code = strip_comments(raw)          # strings kept: SpEL and @Value live in them
        self.shape = blank_strings(self.code)    # strings blanked: declarations only
        self.rel = str(path.relative_to(REPO))

    def line_of(self, pos: int) -> int:
        return self.shape.count("\n", 0, pos) + 1


def load_keep() -> dict[str, str]:
    keep = {}
    path = Path(__file__).with_name("keep.txt")
    for line in path.read_text().splitlines() if path.exists() else []:
        symbol, _, reason = line.partition("#")
        if symbol.strip():
            keep[symbol.strip()] = reason.strip()
    return keep


def load_sources() -> dict[str, dict[str, list[Source]]]:
    out: dict[str, dict[str, list[Source]]] = {}
    for module in MODULES:
        root = BACKEND / module / "src"
        out[module] = {
            "main": [Source(module, p, False) for p in sorted((root / "main" / "java").rglob("*.java"))
                     if p.name != "package-info.java"],
            "test": [Source(module, p, True) for p in sorted((root / "test" / "java").rglob("*.java"))],
        }
    return out


def spec_names() -> set[str]:
    names: set[str] = set()
    for spec in (REPO / "specs").rglob("spec.yaml"):
        if "_templates" in spec.parts:
            continue
        try:
            doc = yaml.safe_load(spec.read_text()) or {}
        except yaml.YAMLError:
            continue
        if doc.get("status") == "withdrawn":
            continue
        gql = doc.get("graphql") or {}
        for key in ("queries", "mutations", "extensions"):
            for entry in gql.get(key) or []:
                token = re.match(r"\s*(?:[\w]+\.)?(\w+)", str(entry))
                if token:
                    names.add(token.group(1))
        for wf in doc.get("workflows") or []:
            if not isinstance(wf, dict):
                names.add(str(wf).split()[0])
                continue
            names.add(str(wf.get("type", "")))
            for key in ("activities", "updates", "signals", "queries"):
                for a in wf.get(key) or []:
                    names.add(re.split(r"[\s(]", str(a).strip())[0])
        persistence = doc.get("persistence") or {}
        for c in persistence.get("collections") or []:
            names.add(str(c).split()[0])
    names.discard("")
    return names


def schema_fields(module: str) -> set[str]:
    fields: set[str] = set()
    for sdl in (BACKEND / module / "src/main/resources").rglob("*.graphqls"):
        text = re.sub(r'"""(?:.|\n)*?"""|#[^\n]*', "", sdl.read_text())
        fields.update(re.findall(r"^\s*(\w+)\s*[(:]", text, re.M))
    return fields


def jacoco(module: str) -> dict[tuple[str, str], bool] | None:
    """Method coverage for a module's classes, merged across every report that measured them.

    A service's report-aggregate (mvn -Pcoverage verify, then jacoco:report-aggregate in the
    service) measures shared-library classes under that service's tests, which is where most
    shared code actually runs.
    """
    reports = [BACKEND / module / "target/site/jacoco/jacoco.xml"]
    if module == "shared-library":
        reports += [BACKEND / m / "target/site/jacoco-aggregate/jacoco.xml" for m in SERVICES]
    reports = [r for r in reports if r.exists()]
    if not reports:
        return None
    prefix = "com/pml/shared/" if module == "shared-library" else ""
    covered: dict[tuple[str, str], bool] = {}
    for report in reports:
        for cls in ET.parse(report).iter("class"):
            name = cls.get("name")
            if prefix and not name.startswith(prefix):
                continue
            simple = name.rsplit("/", 1)[-1].split("$")[-1]
            for m in cls.iter("method"):
                hit = any(c.get("type") == "METHOD" and int(c.get("covered", 0)) > 0
                          for c in m.iter("counter"))
                key = (simple, m.get("name"))
                covered[key] = covered.get(key, False) or hit
    return covered


def enclosing_type(src: Source, pos: int, types: list[tuple[int, str, str, set[str]]]):
    best = None
    for tpos, kind, name, anns in types:
        if tpos < pos:
            best = (kind, name, anns)
        else:
            break
    return best or ("?", src.path.stem, set())


def scope_for(module: str) -> list[str]:
    if module == "shared-library":
        return MODULES
    return [module, "shared-library"] if module != "keycloak-extensions" else [module]


SUPER_CLAUSE = re.compile(
    r"(\b(?:class|interface|enum|record)\s+\w+(?:\s*<[^{;]*?>)?(?:\s*\((?:[^()]|\([^()]*\))*\))?)"
    r"(\s+(?:extends|implements|permits)\s[^{;]*)(?=\{)", re.S)


def usage_text(src: "Source") -> str:
    """The file's code without imports and without extends/implements clauses.

    Implementing an interface is not a use of it: an interface whose only mention is its
    own implementation's header has no caller.
    """
    text = re.sub(r"^\s*(?:import|package)\s[^;]*;", "", src.code, flags=re.M)
    return SUPER_CLAUSE.sub(lambda m: m.group(1) + " ", text)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(REPO / "docs/audits/inventory"))
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    sources = load_sources()
    specs = spec_names()
    keep = load_keep()
    by_rel = {s.rel: s for sets in sources.values() for files in sets.values() for s in files}

    call_re = re.compile(r"(?<![\w$])(\w+)\s*\(|::\s*(\w+)\b")
    word_re = re.compile(r"(?<![\w$])([A-Z]\w*)\b")
    file_calls: dict[str, Counter] = {}
    file_words: dict[str, Counter] = {}
    for rel, s in by_rel.items():
        text = usage_text(s)
        file_calls[rel] = Counter((m.group(1) or m.group(2)) for m in call_re.finditer(text))
        file_words[rel] = Counter(word_re.findall(text))

    resource_text = {}
    for mod in MODULES:
        res = BACKEND / mod / "src/main/resources/META-INF"
        resource_text[mod] = "\n".join(p.read_text(errors="replace") for p in res.rglob("*") if p.is_file()) \
            if res.exists() else ""

    # Declarations.
    methods, types = [], []
    for module, sets in sources.items():
        for s in sets["main"]:
            tdecls = []
            for m in TYPE_DECL.finditer(s.shape):
                anns = {a.split(".")[-1] for a in ANNOTATION.findall(m.group(1) or "")}
                tdecls.append((m.start(2), m.group(2), m.group(3), anns))
                tail = s.shape[m.end():m.end() + 400].split("{", 1)[0]
                types.append(dict(module=module, file=s.rel, line=s.line_of(m.start(2)),
                                  kind=m.group(2), name=m.group(3), anns=anns,
                                  supertypes=re.findall(r"[A-Z]\w+", tail),
                                  nested=Path(s.rel).stem != m.group(3)))
            for m in METHOD_DECL.finditer(s.shape):
                rtype, name = m.group(3), m.group(4)
                if rtype in KEYWORDS or name in KEYWORDS or rtype in ("class", "interface", "enum", "record"):
                    continue
                kind, owner, owner_anns = enclosing_type(s, m.start(4), tdecls)
                if name == owner:
                    continue
                anns = {a.split(".")[-1] for a in ANNOTATION.findall(m.group(1) or "")}
                after = s.shape[m.end():m.end() + 2000]
                depth, i = 1, 0
                while i < len(after) and depth:
                    depth += {"(": 1, ")": -1}.get(after[i], 0)
                    i += 1
                params = after[: i - 1].strip()
                methods.append(dict(module=module, file=s.rel, line=s.line_of(m.start(4)), owner=owner,
                                    owner_kind=kind, owner_anns=owner_anns, name=name, anns=anns,
                                    nparams=0 if not params else params.count(",") + 1))

    def scope_files(module: str, kind: str, excluded: set[str]) -> list[str]:
        mods = scope_for(module) if kind == "main" else list(dict.fromkeys(scope_for(module) + ["shared-library"]))
        return [s.rel for mod in mods for s in sources[mod][kind] if s.rel not in excluded]

    # Type reachability, iterated to a fixpoint: a file whose top-level type is dead does not
    # keep anything else alive.
    declared = defaultdict(list)
    for t in types:
        declared[t["name"]].append(t)

    def reaches_framework(sup: str, seen: set[str]) -> bool:
        """True when `sup`, or a type it extends, is declared outside our sources."""
        if sup in seen:
            return False
        seen.add(sup)
        if sup not in declared:
            return True
        return any(reaches_framework(x, seen) for t in declared[sup] for x in t["supertypes"] if x != sup)

    prev: dict[tuple[str, str], str] = {}

    def subtypes_alive(name: str) -> bool:
        return any(name in t["supertypes"] and t["name"] != name
                   and prev.get((t["file"], t["name"]), "LIVE") not in ("DEAD", "TEST_ONLY", "BEAN_UNINJECTED")
                   for t in types)

    dead_files: set[str] = set()
    for _ in range(20):
        type_rows = []
        for t in types:
            module, name = t["module"], t["name"]
            refs = 0
            for rel in scope_files(module, "main", dead_files):
                n = file_words[rel][name]
                if rel == t["file"]:
                    n = n - 1 if t["nested"] else 0
                refs += max(n, 0)
            bean = "@" + name[0].lower() + name[1:]
            if any(bean in by_rel[rel].code for rel in scope_files(module, "main", dead_files)):
                refs += 1   # SpEL, e.g. @PreAuthorize("@refundSecurityService.isOwner(...)")
            if re.search(r"\b" + name + r"\b", resource_text.get(module, "")):
                refs += 1   # META-INF/services, AutoConfiguration.imports
            test_refs = sum(file_words[rel][name] for rel in scope_files(module, "test", set()))
            if name in keep:
                verdict = "KEEP_MANUAL"
            elif t["anns"] & ENTRY_TYPE_ANNOTATIONS:
                verdict = "KEEP_ENTRY"
            elif refs > 0:
                verdict = "LIVE"
            elif t["kind"] == "class" and subtypes_alive(name):
                verdict = "LIVE"      # a base type whose subclasses are alive
            elif t["anns"] & STEREOTYPES and t["kind"] != "interface":
                live_super = False
                for sup in t["supertypes"]:
                    if sup == name:
                        continue
                    if reaches_framework(sup, set()):
                        live_super = True   # the container calls it through a framework contract
                        break
                    if sum(file_words[rel][sup] for rel in scope_files(module, "main", dead_files)
                           if rel != t["file"]) > 0:
                        live_super = True
                        break
                inner_components = "@Component" in by_rel[t["file"]].code.split("class " + name, 1)[-1]
                verdict = "KEEP_BEAN" if live_super or not t["supertypes"] and inner_components else "BEAN_UNINJECTED"
                if verdict == "BEAN_UNINJECTED" and not t["supertypes"]:
                    verdict = "BEAN_UNINJECTED"
            elif test_refs > 0:
                verdict = "TEST_ONLY"
            else:
                verdict = "DEAD"
            type_rows.append(dict(module=module, verdict=verdict, kind=t["kind"], type=name, file=t["file"],
                                  line=t["line"], main_refs=refs, test_refs=test_refs,
                                  annotations=" ".join(sorted(t["anns"]))))
        prev = {(r["file"], r["type"]): r["verdict"] for r in type_rows}
        now_dead = {r["file"] for r, t in zip(type_rows, types)
                    if not t["nested"] and r["verdict"] in ("DEAD", "TEST_ONLY", "BEAN_UNINJECTED")}
        if now_dead == dead_files:
            break
        dead_files = now_dead

    # Declaration counts, ignoring dead files.
    decl_count: Counter = Counter()
    non_override_decl: Counter = Counter()
    for md in methods:
        if md["file"] in dead_files:
            continue
        decl_count[(md["module"], md["name"])] += 1
        if "Override" not in md["anns"]:
            non_override_decl[(md["module"], md["name"])] += 1

    cov = {m: jacoco(m) for m in MODULES}
    schema = {m: schema_fields(m) for m in MODULES}
    type_names = {t["name"] for t in types}
    # A method implementing an annotated interface method (Temporal @UpdateValidatorMethod,
    # @QueryMethod, …) is reached through the annotation on the interface.
    entry_names = defaultdict(set)
    for md in methods:
        if md["anns"] & ENTRY_METHOD_ANNOTATIONS:
            entry_names[md["module"]].add(md["name"])
    # Names written in annotation strings: fallbackMethod = "x" and SpEL "@bean.x(...)".
    string_names = defaultdict(set)
    for mod in MODULES:
        for src in sources[mod]["main"]:
            string_names[mod].update(re.findall(r'fallbackMethod\s*=\s*"(\w+)"', src.code))
            for lit in re.findall(r'"([^"\n]*@\w+\.[^"\n]*)"', src.code):
                string_names[mod].update(re.findall(r"@\w+\.(\w+)\s*\(", lit))

    method_rows = []
    for md in methods:
        name, module = md["name"], md["module"]
        # A bean property: a getter takes nothing, a setter or wither one value.
        accessor = (bool(re.match(r"(get|is|has)[A-Z]", name)) and md["nparams"] == 0) or \
                   (bool(re.match(r"(set|with)[A-Z]", name)) and md["nparams"] == 1)
        reason = ""
        if name in type_names:
            continue  # a constructor the declaration pattern mistook for a method
        if md["anns"] & ENTRY_METHOD_ANNOTATIONS:
            reason = "entry:" + ",".join(sorted(md["anns"] & ENTRY_METHOD_ANNOTATIONS))
        elif any(name in entry_names[m] for m in scope_for(module)):
            reason = "entry:inherited"
        elif any(name in string_names[m] for m in scope_for(module)):
            reason = "entry:string-reference"
        elif "Override" in md["anns"] and sum(non_override_decl[(m, name)] for m in scope_for(module)) == 0:
            reason = "framework-override"
        elif name in FRAMEWORK_METHOD_NAMES:
            reason = "framework-name"
        main_refs = sum(file_calls[rel][name] for rel in scope_files(module, "main", dead_files))
        main_refs = max(main_refs - sum(decl_count[(m, name)] for m in scope_for(module)), 0)
        test_refs = sum(file_calls[rel][name] for rel in scope_files(module, "test", set()))
        spec_named = name in specs
        schema_named = name in schema.get(module, set())
        covmap = cov.get(module)
        covered = "" if covmap is None else ("yes" if covmap.get((md["owner"], name)) else "no")
        if f'{md["owner"]}#{name}' in keep or md["owner"] in keep:
            verdict = "KEEP_MANUAL"
        elif md["file"] in dead_files:
            verdict = "DEAD_WITH_TYPE"
        elif reason:
            verdict = "KEEP_ENTRY"
        elif accessor:
            verdict = "KEEP_ACCESSOR" if main_refs == 0 else "LIVE"
        elif main_refs > 0:
            verdict = "GAP" if covered == "no" else "LIVE"
        elif spec_named or schema_named:
            verdict = "KEEP_SPEC"
        elif test_refs > 0:
            verdict = "TEST_ONLY"
        else:
            verdict = "DEAD"
        method_rows.append(dict(module=module, verdict=verdict, owner=md["owner"], method=name,
                                file=md["file"], line=md["line"], main_refs=main_refs,
                                test_refs=test_refs, spec_named=spec_named, schema_field=schema_named,
                                covered=covered, entry=reason))

    config_rows = config_inventory(sources)

    write_csv(out / "inventory-methods.csv", method_rows)
    write_csv(out / "inventory-types.csv", type_rows)
    write_csv(out / "inventory-config.csv", config_rows)
    summary = [f"specs: {len(specs)} names; jacoco: " +
               ", ".join(f"{m}={'yes' if cov[m] is not None else 'no'}" for m in MODULES)]
    for label, rows in (("methods", method_rows), ("types", type_rows), ("config", config_rows)):
        tally = defaultdict(Counter)
        for r in rows:
            tally[r["module"]][r["verdict"]] += 1
        for module in MODULES:
            summary.append(f"{label:8} {module:20} " + "  ".join(f"{k}={v}" for k, v in sorted(tally[module].items())))
    (out / "summary.txt").write_text("\n".join(summary) + "\n")
    print("\n".join(summary))
    return 0


def flatten(node, prefix=""):
    if isinstance(node, dict):
        for k, v in node.items():
            key = f"{prefix}.{k}" if prefix else str(k)
            if isinstance(v, dict) and v:
                yield from flatten(v, key)
            else:
                yield key, v
    else:
        yield prefix, node


def kebab(s: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "-", s).lower()


def config_inventory(sources) -> list[dict]:
    rows = []
    for module in MODULES:
        res = BACKEND / module / "src/main/resources"
        ymls = sorted(res.glob("application*.yml")) + sorted(res.glob("application*.yaml"))
        if not ymls:
            continue
        code_files = sources[module]["main"] + (sources["shared-library"]["main"] if module != "shared-library" else [])
        code = "\n".join(s.code for s in code_files)
        yml_text = "\n".join(p.read_text() for p in ymls)
        placeholders = set(re.findall(r"\$\{([\w.\-\[\]]+)", code + "\n" + yml_text))
        # Keys read through Spring's Binder, e.g. Binder.get(env).bind("platform.security.public-paths", ...)
        placeholders |= set(re.findall(r'\.bind(?:OrCreate)?\(\s*"([\w.\-]+)"', code))
        # @ConfigurationProperties prefix -> the text of that class (and its nested types)
        props = {}
        for s in code_files:
            for m in re.finditer(r'@ConfigurationProperties\s*\(\s*(?:prefix\s*=\s*|value\s*=\s*)?"([\w.\-]+)"', s.code):
                props[m.group(1)] = s.code
        conditions = set()
        for m in re.finditer(r"@ConditionalOnProperty\s*\(([^)]*)\)", code):
            body = m.group(1)
            prefix = re.search(r'prefix\s*=\s*"([\w.\-]+)"', body)
            for n in re.findall(r'"([\w.\-]+)"', re.sub(r'prefix\s*=\s*"[^"]*"|havingValue\s*=\s*"[^"]*"', "", body)):
                conditions.add(f"{prefix.group(1)}.{n}" if prefix else n)
        for yml in ymls:
            for doc in yaml.safe_load_all(yml.read_text()):
                if not isinstance(doc, dict):
                    continue
                for key, _ in flatten(doc):
                    root = key.split(".")[0]
                    if root in FRAMEWORK_CONFIG_ROOTS:
                        continue
                    bound_by = ""
                    parts = key.split(".")
                    for i in range(len(parts), 0, -1):
                        k = ".".join(parts[:i])
                        if k in placeholders:
                            bound_by = "placeholder" if i == len(parts) else f"placeholder-parent:{k}"
                            break
                        if k in conditions:
                            bound_by = f"condition:{k}"
                            break
                        if k in props:
                            rest = parts[i:]
                            text = props[k]
                            # A field matches by its camelCase name, by an @Name alias, or as a Map's key.
                            names = set(re.findall(r'@Name\("([\w\-]+)"\)', text))
                            ok = all(p in names or re.search(r"\b" + re.escape(re.sub(r"-(\w)", lambda x: x.group(1).upper(), p)) + r"\b", text)
                                     for p in rest[:1]) and (len(rest) <= 1 or re.search(r"Map<", text) or all(
                                         p in names or re.search(r"\b" + re.escape(re.sub(r"-(\w)", lambda x: x.group(1).upper(), p)) + r"\b", text)
                                         for p in rest[1:]))
                            bound_by = f"properties:{k}" if ok else f"properties-unmatched:{k}"
                            break
                    verdict = "BOUND" if bound_by and "unmatched" not in bound_by else "ORPHAN"
                    rows.append(dict(module=module, verdict=verdict, file=str(yml.relative_to(REPO)),
                                     key=key, bound_by=bound_by))
    return rows


def write_csv(path: Path, rows: list[dict]):
    if not rows:
        path.write_text("")
        return
    with path.open("w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(sorted(rows, key=lambda r: (r["module"], r["verdict"], r.get("file", ""), str(r.get("line", "")))))


if __name__ == "__main__":
    sys.exit(main())
