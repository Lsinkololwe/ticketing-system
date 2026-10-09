/**
 * A GraphQL server that speaks a real schema, for the surfaces Microcks cannot
 * serve. Track F0-4.
 *
 * <h2>Why this exists next to a perfectly good Microcks harness</h2>
 * Microcks answers 500 to any query containing a fragment — measured, not
 * assumed, by `microcks-limits.spec.ts`. Server Components issue plain queries
 * and are fine there. Apollo Client composes fragments as a matter of course, so
 * every Apollo-driven surface fails on transport before the test reaches
 * whatever it meant to check, and it fails identically to a broken backend.
 *
 * <h2>Why this is not the hand-rolled stub the skill forbids</h2>
 * A stub encodes the test author's belief about the response shape, which is
 * exactly where beliefs are wrong. This loads the <b>same SDL the service
 * publishes</b> and executes queries through `graphql-js` against it. A query
 * naming a field the schema does not have is rejected here for the same reason
 * it would be rejected by the real subgraph — which is the property that makes
 * it worth running a container for.
 *
 * <p>Values are auto-mocked. That is deliberate: this fixture proves a screen
 * can <em>talk to</em> the schema, and any test that needs specific values
 * supplies them through `MOCK_OVERRIDES` rather than by hand-writing a response
 * envelope.</p>
 */

import { createServer } from 'node:http';
import { readFileSync } from 'node:fs';

import { buildSchema, graphql } from 'graphql';
import { addMocksToSchema } from '@graphql-tools/mock';

const SDL_PATH = process.env.SDL_PATH ?? '/schema/schema.graphql';
const PORT = Number(process.env.PORT ?? 4001);

/**
 * Deterministic mock scalars.
 *
 * <p>`@graphql-tools/mock` defaults to random values, which makes a failing
 * assertion unreproducible — the run that failed cannot be repeated. Fixing the
 * scalars costs nothing and makes the fixture debuggable.</p>
 */
const mocks = {
  ID: () => 'mock-id',
  String: () => 'mock',
  Int: () => 1,
  Float: () => 1.0,
  Boolean: () => true,
  DateTime: () => '2026-01-01T00:00:00Z',
  BigDecimal: () => '100.00',
  Long: () => 1,
  JSON: () => ({}),
  PhoneNumber: () => '+260970000000',
};

const schema = addMocksToSchema({
  schema: buildSchema(readFileSync(SDL_PATH, 'utf8')),
  mocks,
});

const server = createServer((req, res) => {
  if (req.method !== 'POST') {
    res.writeHead(405).end('POST only');
    return;
  }

  let body = '';
  req.on('data', (chunk) => {
    body += chunk;
  });
  req.on('end', async () => {
    let payload;
    try {
      payload = JSON.parse(body || '{}');
    } catch {
      res.writeHead(400, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ errors: [{ message: 'invalid JSON body' }] }));
      return;
    }

    const result = await graphql({
      schema,
      source: payload.query ?? '',
      variableValues: payload.variables,
      operationName: payload.operationName,
    });

    res.writeHead(200, {
      'content-type': 'application/json',
      // The browser reaches this directly from an Apollo-driven page, which is
      // served from a different origin than the fixture.
      'access-control-allow-origin': '*',
    });
    res.end(JSON.stringify(result));
  });
});

server.listen(PORT, '0.0.0.0', () => {
  // The container's wait strategy matches on this line, so it is a contract
  // rather than a convenience.
  console.log(`subgraph fixture listening on ${PORT}`);
});
