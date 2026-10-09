import fs from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * Codegen is the only source of GraphQL types.
 *
 * <h2>What a hand-written GraphQL type actually costs</h2>
 * It compiles forever. The schema renames a field, the query starts returning
 * `undefined` for it, and the interface still says `string` — so the type system
 * reports nothing, the UI renders a blank cell or `NaN`, and the first sign of
 * trouble is someone looking at a screen. A type derived from codegen turns the
 * same change into a build failure at the moment codegen runs.
 *
 * <p>Normalised view models are still allowed, and needed: the schema makes
 * every `PageInfo` field nullable and a table cannot render
 * `totalPages: number | null` without a default at every use. The rule is that
 * their **field names** come from the generated type via `Pick`, so the
 * normalisation survives a rename and the field set cannot silently diverge.</p>
 */

const SRC = path.resolve(__dirname, '..');
const GENERATED = path.join(SRC, 'types', 'graphql', 'index.ts');
const WEB_ROOT = path.resolve(SRC, '..', '..', '..');
const APP_ROOTS = ['admin', 'organization-admin', 'ticketing'].map((app) =>
  path.join(WEB_ROOT, 'apps', app, 'src')
);

/**
 * A GraphQL-shaped name whose **fields are written out by hand** — either an
 * `interface`, or a `type` assigned an object literal.
 *
 * <p>An alias (`type EventPageInfo = OffsetPageInfo;`) is deliberately not
 * matched. Renaming a derived type for a local surface costs nothing and breaks
 * nothing: the field set still comes from codegen through whatever it points at.
 * What matters is the brace — that is where a hand-maintained copy of the schema
 * begins.</p>
 */
const HAND_WRITTEN_SHAPE =
  /^(?:export\s+)?(?:interface\s+([A-Za-z0-9_]*(?:MutationResponse|Payload|Connection|Edge|PageInfo))\b|type\s+([A-Za-z0-9_]*(?:MutationResponse|Payload|Connection|Edge|PageInfo))\s*=\s*\{)/gm;

/** Files that may declare them, because they derive rather than re-declare. */
const DERIVES_FROM_CODEGEN = [
  path.join('types', 'pageInfo.ts'),
];

function sourceFiles(dir: string): string[] {
  const found: string[] = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules' || entry.name === 'dist' || entry.name === '.next') continue;
      found.push(...sourceFiles(full));
    } else if (/\.tsx?$/.test(entry.name) && !/\.(test|spec)\.tsx?$/.test(entry.name)) {
      found.push(full);
    }
  }
  return found;
}

/** libs/shared/src plus every app's src — where a hook or a hand type could live. */
function allProjectRoots(): string[] {
  return [SRC, ...APP_ROOTS];
}

/**
 * A `useQuery`/`useMutation` call whose type argument is an inline object
 * literal — `useQuery<{ foo: Bar }>(...)` — instead of a generated
 * `*Query`/`*Mutation` type.
 *
 * <p>This is the same hazard `HAND_WRITTEN_SHAPE` guards against, spelled at
 * the call site instead of as a named type: Apollo trusts whatever shape is
 * written here, so it does not catch a query whose selection no longer
 * matches. Passing the generated `<Op>Query, <Op>QueryVariables>` pair is the
 * only way a field the query stops selecting — or a field the schema drops —
 * turns into a compile error instead of `undefined` in production.</p>
 */
const INLINE_OPERATION_GENERIC = /\buse(?:Query|Mutation)\s*<\s*\{/g;

function findInlineOperationGenerics(contents: string): number {
  return [...contents.matchAll(INLINE_OPERATION_GENERIC)].length;
}

/** Every `type`/`interface` name codegen has already produced. */
function generatedTypeNames(): Set<string> {
  const generated = fs.readFileSync(GENERATED, 'utf8');
  const names = new Set<string>();
  for (const match of generated.matchAll(/^export (?:type|interface) ([A-Za-z0-9_]+)/gm)) {
    names.add(match[1]);
  }
  return names;
}

/**
 * Same brace test as {@link HAND_WRITTEN_SHAPE}, but captures *any* identifier
 * rather than a fixed suffix list, so it can be checked against the real,
 * current set of names codegen produced — a supergraph type, input or enum —
 * instead of a guessed pattern that only catches the suffixes someone thought
 * to list.
 */
const NAMED_OBJECT_SHAPE =
  /^(?:export\s+)?(?:interface\s+([A-Za-z0-9_]+)\b|type\s+([A-Za-z0-9_]+)\s*=\s*\{)/gm;

function findShapesNamedAfterSchema(contents: string, schemaNames: ReadonlySet<string>): string[] {
  const offenders: string[] = [];
  for (const match of contents.matchAll(NAMED_OBJECT_SHAPE)) {
    const name = match[1] ?? match[2];
    if (name && schemaNames.has(name)) offenders.push(name);
  }
  return offenders;
}

describe('codegen is the authority for GraphQL types', () => {
  it('the generated file exists and is substantial', () => {
    // Every assertion below is about what is NOT declared elsewhere. If codegen
    // had produced nothing, they would all pass while the apps had no types.
    const generated = fs.readFileSync(GENERATED, 'utf8');
    expect(generated.split('\n').length).toBeGreaterThan(1000);
    expect(generated).toContain('export type PageInfo');
  });

  it('no file re-declares a GraphQL shape by hand', () => {
    const offenders: string[] = [];

    for (const file of sourceFiles(SRC)) {
      const relative = path.relative(SRC, file);
      if (relative.startsWith(path.join('types', 'graphql'))) continue;
      if (DERIVES_FROM_CODEGEN.includes(relative)) continue;

      const contents = fs.readFileSync(file, 'utf8');
      for (const match of contents.matchAll(HAND_WRITTEN_SHAPE)) {
        offenders.push(`${relative} → ${match[1] ?? match[2]}`);
      }
    }

    expect(
      offenders,
      'declare these with Pick<> from the generated type — a hand-written copy keeps ' +
        'compiling after the schema drops the field, and the failure surfaces as a blank ' +
        'cell rather than a build error'
    ).toEqual([]);
  });

  it('no use*Query/use*Mutation hook takes an inline object-literal generic', () => {
    const offenders: string[] = [];

    for (const root of allProjectRoots()) {
      for (const file of sourceFiles(root)) {
        const relative = path.relative(WEB_ROOT, file);
        if (relative.includes(path.join('types', 'graphql'))) continue;

        const contents = fs.readFileSync(file, 'utf8');
        const count = findInlineOperationGenerics(contents);
        if (count > 0) offenders.push(`${relative} (${count})`);
      }
    }

    expect(
      offenders,
      'pass the generated <Op>Query/<Op>Mutation type (plus its Variables) instead of an ' +
        'inline object literal — Apollo cannot check a literal against the real response, so a ' +
        'field the query drops or the schema renames turns into a silent `undefined` at runtime ' +
        'rather than a build error'
    ).toEqual([]);
  });

  it('the inline-generic rule fires on a synthetic offender', () => {
    // Proves the regex actually matches a violation shaped like the real
    // thing, rather than passing only because nothing in the tree trips it.
    const offending = `
      export function useBrokenHook() {
        const { data } = useQuery<{ foo: string }>(SOME_QUERY);
        return data;
      }
    `;
    const offendingMutation = `
      const [mutate] = useMutation<{ createFoo: { id: string } }>(CREATE_FOO);
    `;
    const clean = `
      export function useOkHook() {
        const { data } = useQuery<SomeQuery, SomeQueryVariables>(SOME_QUERY);
        return data;
      }
    `;

    expect(findInlineOperationGenerics(offending)).toBe(1);
    expect(findInlineOperationGenerics(offendingMutation)).toBe(1);
    expect(findInlineOperationGenerics(clean)).toBe(0);
  });

  it('no interface or type-literal is named after a generated supergraph type, input or enum', () => {
    const schemaNames = generatedTypeNames();
    const offenders: string[] = [];

    for (const root of allProjectRoots()) {
      for (const file of sourceFiles(root)) {
        const relative = path.relative(WEB_ROOT, file);
        if (relative.includes(path.join('types', 'graphql'))) continue;
        if (relative.endsWith(path.join('types', 'pageInfo.ts'))) continue;

        const contents = fs.readFileSync(file, 'utf8');
        for (const name of findShapesNamedAfterSchema(contents, schemaNames)) {
          offenders.push(`${relative} → ${name}`);
        }
      }
    }

    expect(
      offenders,
      'a hand-written interface or type-literal reusing a real schema name is confusable with ' +
        'the generated type of the same name — rename it (a *Row/*VM/*Option suffix, or the ' +
        'REST-prefixed form other REST DTOs in this repo already use) so the two shapes cannot ' +
        'be mistaken for each other'
    ).toEqual([]);
  });

  it('the named-after-schema rule fires on a synthetic offender', () => {
    const schemaNames = generatedTypeNames();
    // A real generated entity name, so this proves the rule sees the actual
    // current name set rather than a hardcoded guess that happens to pass.
    const realName = 'Organization';
    expect(schemaNames.has(realName)).toBe(true);

    const offendingInterface = `export interface ${realName} {\n  id: string;\n}`;
    const offendingTypeLiteral = `type ${realName} = {\n  id: string;\n};`;
    // A derived alias is not a hand-written shape and must not be flagged.
    const cleanAlias = `export type ${realName}Row = SomeQuery['${realName.toLowerCase()}'];`;
    // A different, made-up name is not a schema name and must not be flagged.
    const cleanLocalName = `export interface ${realName}CardVM {\n  id: string;\n}`;

    expect(findShapesNamedAfterSchema(offendingInterface, schemaNames)).toEqual([realName]);
    expect(findShapesNamedAfterSchema(offendingTypeLiteral, schemaNames)).toEqual([realName]);
    expect(findShapesNamedAfterSchema(cleanAlias, schemaNames)).toEqual([]);
    expect(findShapesNamedAfterSchema(cleanLocalName, schemaNames)).toEqual([]);
  });

  it('the derived page types really are derived', () => {
    // Guards the exemption: `types/pageInfo.ts` is allowed to name these shapes
    // precisely because it Picks them from PageInfo. If it stopped doing that,
    // the exemption would be shielding exactly what this test forbids.
    const derived = fs.readFileSync(path.join(SRC, 'types', 'pageInfo.ts'), 'utf8');

    expect(derived).toContain("import type { PageInfo }");
    expect(derived).toMatch(/Pick<\s*PageInfo/);
  });

  it('codegen reads a file on disk, not a running server', () => {
    // Introspection makes the committed types a function of whatever happened
    // to be running, so they can change with no schema change and fail to change
    // with one. It also puts codegen out of reach of CI entirely.
    const config = fs.readFileSync(
      path.resolve(SRC, '..', '..', '..', 'codegen.ts'),
      'utf8'
    );

    expect(config).toContain('supergraph.graphql');
    expect(
      config,
      'a default pointing at localhost is a default nobody can run in CI'
    ).not.toMatch(/schema:\s*['"]http/);
  });
});
