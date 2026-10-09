import type { CodegenConfig } from '@graphql-codegen/cli';

/**
 * ============================================================================
 * GRAPHQL CODE GENERATOR - Introspection Only
 * ============================================================================
 *
 * Generates TypeScript types from the **composed supergraph SDL on disk**, not
 * from a running server. ET-PLT-004 BE-8.
 *
 * WHY A FILE AND NOT INTROSPECTION
 *
 * Introspection makes the generated types a function of whatever happened to be
 * running. Three services on slightly different branches produce a supergraph
 * that exists on no branch, and the output is committed — so the types can
 * change with no schema change at all, and a schema change can fail to appear
 * because one service was not restarted. It also means codegen cannot run in CI
 * or on a laptop without the whole stack up, which is how generated types drift
 * from the SDL that defines them.
 *
 * The file is produced by `compose-supergraph.sh --static`, which composes the
 * three on-disk `schema.graphqls` with a pinned federation version. Same inputs,
 * same output, every time and everywhere.
 *
 * THE ONE COST, STATED
 *
 * A supergraph carries federation's own machinery, so the output gains four
 * inert scalar aliases (`join__FieldSet`, `link__Import`, and two siblings) that
 * introspecting a router would have hidden. They are type aliases nothing
 * references. That is the whole price, and it buys types that can be generated
 * in CI, on a laptop with nothing running, and identically on both — which the
 * previous arrangement could not do at all.
 *
 * USAGE:
 *   npm run codegen            # from the committed supergraph
 *   npm run codegen:compose    # recompose from the subgraphs first
 *
 * ============================================================================
 */

import path from 'node:path';

/**
 * The composed supergraph, in the shared docker-resources checkout.
 *
 * <p>Overridable so CI can compose to a temporary path, and so a developer can
 * point at a live endpoint deliberately — but never by default, because the
 * default is what everyone actually runs.</p>
 */
const SUPERGRAPH =
  process.env.GRAPHQL_SCHEMA ||
  path.resolve(
    __dirname,
    '..', '..', '..',
    'docker-resources', 'apollo-router', 'ticketing', 'supergraph.graphql'
  );

// Shared scalar mappings
const sharedScalars = {
  ID: 'string',
  UUID: 'string',
  DateTime: 'string',
  LocalDateTime: 'string',
  LocalDate: 'string',
  LocalTime: 'string',
  BigDecimal: 'string',
  Long: 'number',
  Int: 'number',
  Float: 'number',
  Boolean: 'boolean',
  String: 'string',
  JSON: 'Record<string, unknown>',
  // Canonical E.164 phone string (validated/normalized server-side by the
  // PhoneNumber scalar; UI composes E.164 via libphonenumber-js).
  PhoneNumber: 'string',
};

const config: CodegenConfig = {
  overwrite: true,
  schema: SUPERGRAPH,
  ignoreNoDocuments: true,

  generates: {
    // Single output file for all apps to consume
    'libs/shared/src/types/graphql/index.ts': {
      documents: [
        'libs/shared/src/api/**/*.ts',
        'apps/*/src/**/*.ts',
        'apps/*/src/**/*.tsx',
        '!libs/shared/src/api/**/*.test.ts',
        '!apps/*/src/**/*.test.ts',
        '!apps/*/src/**/*.test.tsx',
      ],
      plugins: ['typescript', 'typescript-operations'],
      config: {
        avoidOptionals: { field: true, object: true, defaultValue: true, inputValue: false },
        maybeValue: 'T | null',
        enumsAsTypes: true,
        useTypeImports: true,
        constEnums: false,
        skipTypename: false,
        nonOptionalTypename: true,
        namingConvention: { enumValues: 'change-case#upperCase' },
        scalars: sharedScalars,
        preResolveTypes: true,
        comment: `
 * GraphQL Schema Types and Operation Types
 * Generated from the composed supergraph SDL (deterministic, no running services)
 *
 * DO NOT EDIT - Run 'npm run codegen' to regenerate
 `,
      },
    },
  },
};

export default config;
