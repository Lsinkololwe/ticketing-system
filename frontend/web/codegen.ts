import type { CodegenConfig } from '@graphql-codegen/cli';

/**
 * ============================================================================
 * GRAPHQL CODE GENERATOR - Introspection Only
 * ============================================================================
 *
 * Generates TypeScript types from the federated supergraph via introspection.
 * All types go to libs/shared for all apps to consume.
 *
 * USAGE:
 *   npm run codegen    # Generate from running backend via API Gateway
 *
 * REQUIREMENTS:
 *   - Backend services must be running
 *   - API Gateway at localhost:8080 routes to Apollo Router at localhost:4000
 *
 * ============================================================================
 */

// Apollo Router endpoint (direct or via API Gateway)
// Use port 4001 for local router, 4000 for GraphOS router, 8080 for API Gateway
const GRAPHQL_ENDPOINT = process.env.GRAPHQL_ENDPOINT || 'http://localhost:4001';

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
  schema: GRAPHQL_ENDPOINT,
  ignoreNoDocuments: true,

  generates: {
    // Single output file for all apps to consume
    'libs/shared/src/types/graphql/index.ts': {
      documents: [
        'libs/shared/src/api/graphql/**/*.queryDefinitions.ts',
        'libs/shared/src/api/graphql/**/*.mutationDefinitions.ts',
        'libs/shared/src/api/graphql/**/*Definitions.ts',
      ],
      plugins: ['typescript', 'typescript-operations'],
      config: {
        avoidOptionals: true,
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
 * Generated from federated supergraph via API Gateway introspection
 *
 * DO NOT EDIT - Run 'npm run codegen' to regenerate
 `,
      },
    },
  },
};

export default config;
