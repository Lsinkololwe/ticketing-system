/**
 * Contract: every operation document the buyer app ships is valid against the COMPOSED supergraph,
 * and the discovery filter the buyer sends is one the catalog actually runs.
 *
 * Why two checks: codegen validates documents against the schema, but `EventDiscoveryFilterInput`
 * declares fields the catalog deliberately refuses (ET-CAT-003 R4 admits exactly five filters),
 * so a document can be schema-valid and still fail at runtime with COMMAND_NOT_WELL_FORMED. That
 * is how the home page shipped sending `isFeatured`/`hasAvailableTickets`.
 *
 * Schema source, first that exists: $GRAPHQL_SCHEMA, the file `npm run codegen:local` composes from
 * the current subgraph SDL, the committed supergraph in docker-resources.
 */
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { buildASTSchema, parse, validate, Kind, specifiedRules, NoUnusedFragmentsRule, type DocumentNode } from 'graphql';

const WEB = path.resolve(__dirname, '../../../../../../..');
const candidates = [
  process.env.GRAPHQL_SCHEMA,
  path.join(WEB, 'node_modules/.cache/pml-supergraph.graphql'),
  path.resolve(WEB, '../../../docker-resources/apollo-router/ticketing/supergraph.graphql'),
].filter((p): p is string => !!p);

/** The only filters `EventDiscovery.check` lets through (catalog-service). */
const DISCOVERY_FILTERS = ['searchQuery', 'categoryId', 'cityId', 'startDate', 'endDate', 'minPrice', 'maxPrice'];

function walk(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const full = path.join(dir, name);
    if (statSync(full).isDirectory()) {
      if (name === '__tests__' || name === 'node_modules' || name === '.next') continue;
      walk(full, out);
    } else if (/\.(ts|tsx)$/.test(name) && !/\.(test|spec)\./.test(name)) out.push(full);
  }
  return out;
}

/** Every gql`...` template of the given roots, `${fragment}` interpolations removed (fragments are defined in-place). */
function documentsOf(roots: string[]): DocumentNode {
  const definitions = new Map<string, DocumentNode['definitions'][number]>();
  for (const root of roots) {
    for (const file of walk(root)) {
      const src = readFileSync(file, 'utf8');
      for (const match of src.matchAll(/\bgql`(\s*(?:\$\{|query|mutation|subscription|fragment)[\s\S]*?)`/g)) {
        const body = match[1].replace(/\$\{[^}]*\}/g, '');
        if (!body.trim()) continue;
        for (const def of parse(body).definitions) {
          const name = 'name' in def && def.name ? def.name.value : `${file}:${definitions.size}`;
          definitions.set(`${def.kind}:${name}`, def);
        }
      }
    }
  }
  return { kind: Kind.DOCUMENT, definitions: [...definitions.values()] };
}

describe('buyer operations vs the composed supergraph', () => {
  const schemaPath = candidates.find((p) => existsSync(p));

  it('has a composed schema to check against', () => {
    expect(schemaPath, `no supergraph found; tried ${candidates.join(', ')}`).toBeTruthy();
  });

  it('every buyer operation validates', () => {
    const schema = buildASTSchema(parse(readFileSync(schemaPath!, 'utf8')), { assumeValidSDL: true });
    const doc = documentsOf([
      // Fragments are shared across the graphql modules, so the whole API layer is the document set.
      path.join(WEB, 'libs/shared/src/api/graphql'),
      path.join(WEB, 'apps/ticketing/src'),
    ]);
    expect(doc.definitions.some((d) => d.kind === Kind.OPERATION_DEFINITION && d.name?.value === 'DiscoverEvents')).toBe(true);
    const rules = specifiedRules.filter((r) => r !== NoUnusedFragmentsRule);
    const errors = validate(schema, doc, rules).map((e) => e.message);
    expect(errors).toEqual([]);
  });

  it('the buyer discovery filter carries only filters the catalog runs', () => {
    const src = readFileSync(path.join(WEB, 'libs/shared/src/api/graphql/buyer/discover.ts'), 'utf8');
    const block = /export interface DiscoverFilter \{([\s\S]*?)\n\}/.exec(src)?.[1] ?? '';
    const keys = [...block.matchAll(/^\s{2}(\w+)\??:/gm)].map((m) => m[1]);
    expect(keys.length).toBeGreaterThan(0);
    expect(keys.filter((k) => !DISCOVERY_FILTERS.includes(k))).toEqual([]);
  });
});
