// Usage: node harmonize-enums.mjs a.graphqls b.graphqls ...
// Enums defined in several subgraphs must agree for federation composition. When backend
// subgraphs are mid-change they can drift; for LOCAL codegen only, append the union of values to
// every subgraph copy (`extend enum`) and warn, so types can still be generated. Never touches
// the real backend files (callers pass temp copies).
import { readFileSync, appendFileSync } from 'node:fs';
import { parse, Kind } from 'graphql';

const files = process.argv.slice(2);
const byEnum = new Map(); // name -> Map(file -> Set(values))
for (const f of files) {
  for (const d of parse(readFileSync(f, 'utf8')).definitions) {
    if (d.kind !== Kind.ENUM_TYPE_DEFINITION && d.kind !== Kind.ENUM_TYPE_EXTENSION) continue;
    const perFile = byEnum.get(d.name.value) ?? new Map();
    const set = perFile.get(f) ?? new Set();
    for (const v of d.values ?? []) set.add(v.name.value);
    perFile.set(f, set);
    byEnum.set(d.name.value, perFile);
  }
}
for (const [name, perFile] of byEnum) {
  if (perFile.size < 2) continue;
  const union = new Set([...perFile.values()].flatMap((s) => [...s]));
  for (const [f, set] of perFile) {
    const missing = [...union].filter((v) => !set.has(v));
    if (!missing.length) continue;
    console.warn(`codegen:local WARNING enum ${name} drifts between subgraphs; adding ${missing.join(', ')} to ${f.split('/').pop()} (fix in backend)`);
    appendFileSync(f, `\nextend enum ${name} {\n  ${missing.join('\n  ')}\n}\n`);
  }
}
