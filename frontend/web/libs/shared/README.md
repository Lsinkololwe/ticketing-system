# @pml.tickets/shared

This library was generated with [Nx](https://nx.dev).

## Running unit tests

Run `nx test @pml.tickets/shared` to execute the unit tests via [Jest](https://jestjs.io).

## GraphQL codegen from the current backend schemas

`pnpm codegen:local` (from `frontend/web`) regenerates `src/types/graphql/index.ts` without the sibling `docker-resources` checkout: `scripts/codegen-local.sh` prepends the shared `@auth` SDL to the identity, catalog and booking `schema.graphqls` files under `backend/`, composes them into a throw-away supergraph with Apollo Rover (federation version pinned, override with `FEDERATION_VERSION`; backend path override with `BACKEND_DIR`), and runs `graphql-codegen` against it through the `GRAPHQL_SCHEMA` variable that `codegen.ts` already honours. The composed supergraph is also cached at `node_modules/.cache/pml-supergraph.graphql`, which `documentValidity.test.ts` prefers over docker-resources, so documents are validated against the same schema the types came from. If subgraphs disagree on an enum's values (backend drift), `scripts/harmonize-enums.mjs` unions the values in the temp copies only and prints a warning; fix the drift in the backend. Re-run it after any backend schema or gql document change; every operation name must be unique across the repo, and hooks must use the generated `<Op>Query`/`<Op>Mutation` types rather than inline generics (enforced by `codegenAuthority.test.ts`).
