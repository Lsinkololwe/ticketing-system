import path from 'node:path';
import fs from 'node:fs';

import { GenericContainer, Wait, type StartedTestContainer } from 'testcontainers';

/**
 * A container that speaks a real GraphQL schema, for surfaces Microcks cannot
 * serve. Track F0-4.
 *
 * <h2>What it is for</h2>
 * Microcks answers 500 to any query containing a fragment — measured by
 * `microcks-limits.spec.ts`, not assumed. Server Components issue plain queries
 * and are well served there; Apollo Client composes fragments as a matter of
 * course, so an Apollo-driven surface mocked through Microcks fails on transport
 * before reaching whatever the test meant to check, and fails identically to a
 * broken backend.
 *
 * <h2>Why it is not the hand-rolled stub the skill forbids</h2>
 * The container executes queries through `graphql-js` against the <b>SDL the
 * service publishes</b>. A query naming a field the schema lacks is rejected
 * here exactly as the real subgraph would reject it — proven in
 * `subgraph-fixture.spec.ts`. A stub encodes the author's belief about the
 * response shape, which is where beliefs are wrong; this cannot, because it
 * never sees a response shape, only a schema.
 *
 * <h2>Why the bundle is built rather than installed</h2>
 * The image is a bare `node`, and bind-mounting `node_modules` does not survive
 * pnpm: its layout is symlinks into `.pnpm`, which dangle once mounted. So
 * `graphql` and `@graphql-tools/mock` are bundled into a single file by
 * `subgraph/build.mjs`, and the container is handed that file and an SDL.
 */

const BUNDLE = path.join(__dirname, 'subgraph', 'dist', 'server.mjs');

/** The port inside the container. The host port is mapped, never assumed. */
const CONTAINER_PORT = 4001;

export interface StartedSubgraph {
  /** Full URL an app or a test should POST GraphQL to. */
  url: string;
  stop: () => Promise<void>;
}

/**
 * Starts the fixture with the given SDL.
 *
 * <p>Unlike the Microcks harness this does <b>not</b> pin a host port. Nothing
 * needs to know the address before the container exists: a test reads it from
 * the return value, and an app under test is handed it through the Playwright
 * `webServer` env once it is known. Pinning would import that harness's one real
 * drawback for no benefit.</p>
 *
 * @param sdlPath absolute path to the schema this fixture should serve
 */
export async function startSubgraph(sdlPath: string): Promise<StartedSubgraph> {
  if (!fs.existsSync(BUNDLE)) {
    throw new Error(
      `Subgraph fixture bundle is missing at ${BUNDLE}. ` +
        'Build it with: node e2e-harness/subgraph/build.mjs'
    );
  }
  if (!fs.existsSync(sdlPath)) {
    throw new Error(`Schema not found at ${sdlPath}`);
  }

  const container = await new GenericContainer('node:20-alpine')
    .withCopyFilesToContainer([
      { source: BUNDLE, target: '/app/server.mjs' },
      { source: sdlPath, target: '/schema/schema.graphql' },
    ])
    .withEnvironment({
      SDL_PATH: '/schema/schema.graphql',
      PORT: String(CONTAINER_PORT),
    })
    .withCommand(['node', '/app/server.mjs'])
    .withExposedPorts(CONTAINER_PORT)
    // Matches the line the server prints once it is actually listening. Waiting
    // on the port alone would return before the schema had been parsed, and a
    // malformed SDL would then surface as a connection reset mid-test rather
    // than as a startup failure.
    .withWaitStrategy(Wait.forLogMessage(/subgraph fixture listening on/))
    .start();

  return {
    url: `http://${container.getHost()}:${container.getMappedPort(CONTAINER_PORT)}`,
    stop: () => container.stop().then(() => undefined),
  };
}

export type { StartedTestContainer };
