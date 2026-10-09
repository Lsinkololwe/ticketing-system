import fs from 'node:fs';
import path from 'node:path';

import { buildSchema, parse, validate, type GraphQLSchema } from 'graphql';
import { describe, expect, it } from 'vitest';

/**
 * Every `gql` document is valid against the composed schema.
 *
 * <h2>Why this exists separately from codegen</h2>
 * `graphql-codegen` exits 0 on a document selecting a field the schema does not
 * have. It generates types for what it *can* resolve and moves on, so a
 * selection removed from the backend produces a green codegen run, green
 * TypeScript, and a query that fails at runtime against the real router — in
 * production, on the first user who opens that screen.
 *
 * <p>TypeScript cannot see inside a template literal, and the generated types
 * are derived from the same documents, so neither of the two checks a developer
 * would expect to catch this can. Parsing the documents and validating them
 * against the schema is the only thing that does.</p>
 */

const HERE = path.resolve(__dirname);
/**
 * GRAPHQL_SCHEMA overrides the schema file (same variable codegen.ts reads); `pnpm codegen:local`
 * leaves its composed supergraph at node_modules/.cache/pml-supergraph.graphql, which is
 * preferred over the sibling docker-resources checkout when present.
 */
const LOCAL_SUPERGRAPH = path.resolve(HERE, '..', '..', '..', '..', 'node_modules', '.cache', 'pml-supergraph.graphql');
const SUPERGRAPH =
  process.env.GRAPHQL_SCHEMA ||
  (fs.existsSync(LOCAL_SUPERGRAPH)
    ? LOCAL_SUPERGRAPH
    : path.resolve(
        HERE, '..', '..', '..', '..', '..', '..', '..',
        'docker-resources', 'apollo-router', 'ticketing', 'supergraph.graphql'
      ));

/**
 * Documents known to be invalid against the schema. Frozen, and may only
 * shrink.
 *
 * <h2>Why a ratchet rather than a fix or a skip</h2>
 * Each of these calls an operation or field the schema does not have, so the
 * screen behind it is already broken at runtime — they are findings, not
 * regressions, and fixing them means adding backend operations that have not
 * been designed. Deleting the check instead would hide these along with every
 * future addition.
 *
 * <p>Remove an entry when its operation lands; the test fails if the list is longer than reality, so it cannot rot
 * into permission.</p>
 */
const KNOWN_BROKEN: string[] = [];

/** Roots holding executable documents. */
const DOCUMENT_ROOTS = [
  path.resolve(HERE),
  path.resolve(HERE, '..', '..', '..', '..', 'apps'),
];

function loadSchema(): GraphQLSchema {
  // assumeValidSDL: the supergraph carries federation's own directives, which
  // are declared in it but are not part of the GraphQL specification's built-in set.
  return buildSchema(fs.readFileSync(SUPERGRAPH, 'utf8'), { assumeValidSDL: true });
}

function sourceFiles(dir: string): string[] {
  if (!fs.existsSync(dir)) return [];
  const found: string[] = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules' || entry.name === '.next' || entry.name === 'dist') {
        continue;
      }
      found.push(...sourceFiles(full));
    } else if (/\.tsx?$/.test(entry.name) && !/\.(test|spec)\.tsx?$/.test(entry.name)) {
      found.push(full);
    }
  }
  return found;
}

/** Every `gql\`…\`` body in a file, with no interpolation. */
function documentsIn(file: string): { text: string; line: number }[] {
  const contents = fs.readFileSync(file, 'utf8');
  const found: { text: string; line: number }[] = [];
  const pattern = /gql`([\s\S]*?)`/g;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(contents)) !== null) {
    const body = match[1];
    // A document assembled from fragments via ${…} cannot be validated in
    // isolation; it is checked wherever the composed document is defined.
    if (body.includes('${')) continue;
    // `gql` also appears inside prose ("hand-authored `gql` documents"), so the
    // body must actually open an operation before it is treated as one.
    if (!/^\s*(query|mutation|subscription|fragment)\b/.test(body)) continue;
    found.push({
      text: body,
      line: contents.slice(0, match.index).split('\n').length,
    });
  }
  return found;
}

describe('every GraphQL document matches the composed schema', () => {
  const schema = loadSchema();

  it('the supergraph is present and is a real schema', () => {
    // Without this, a missing file would make the sweep below validate nothing
    // and report success.
    expect(schema.getQueryType(), 'no Query type — the supergraph did not parse').toBeTruthy();
    expect(schema.getMutationType()).toBeTruthy();
  });

  it('finds documents to check', () => {
    const total = DOCUMENT_ROOTS
      .flatMap(sourceFiles)
      .reduce((n, file) => n + documentsIn(file).length, 0);

    expect(total, 'no gql documents found — the roots are wrong').toBeGreaterThan(20);
  });

  it('no document selects a field the schema does not have', () => {
    const failures: string[] = [];

    for (const root of DOCUMENT_ROOTS) {
      for (const file of sourceFiles(root)) {
        for (const { text, line } of documentsIn(file)) {
          let document;
          try {
            document = parse(text);
          } catch (error) {
            failures.push(
              `${path.relative(HERE, file)}:${line} — unparseable: ${(error as Error).message}`
            );
            continue;
          }
          // A file defining only fragments is composed into a document
          // elsewhere via `${…}`. Validating it alone reports every fragment as
          // unused, which is true in isolation and false in the query that uses
          // it — so the rule is skipped rather than the file.
          const fragmentsOnly = document.definitions.every(
            (definition) => definition.kind === 'FragmentDefinition'
          );

          for (const error of validate(schema, document)) {
            if (fragmentsOnly && error.message.includes('is never used')) continue;
            failures.push(`${path.relative(HERE, file)}:${line} — ${error.message}`);
          }
        }
      }
    }

    const regressions = failures.filter(
      (failure) => !KNOWN_BROKEN.some((known) => failure.startsWith(known))
    );

    expect(
      regressions,
      'these documents would fail against the running router. codegen does not ' +
        'catch this: it emits types for whatever it can resolve and exits 0.'
    ).toEqual([]);
  });

  it('the known-broken list has not outlived its entries', () => {
    // A stale allowlist is worse than none: it reads as "these are handled" and
    // silently forgives whatever later lands on the same line.
    const failures: string[] = [];
    for (const root of DOCUMENT_ROOTS) {
      for (const file of sourceFiles(root)) {
        for (const { text, line } of documentsIn(file)) {
          try {
            const document = parse(text);
            const fragmentsOnly = document.definitions.every(
              (definition) => definition.kind === 'FragmentDefinition'
            );
            for (const error of validate(schema, document)) {
              if (fragmentsOnly && error.message.includes('is never used')) continue;
              failures.push(`${path.relative(HERE, file)}:${line}`);
            }
          } catch {
            failures.push(`${path.relative(HERE, file)}:${line}`);
          }
        }
      }
    }

    const fixed = KNOWN_BROKEN.filter((known) => !failures.includes(known));
    expect(fixed, 'these now validate — remove them from KNOWN_BROKEN').toEqual([]);
  });
});
