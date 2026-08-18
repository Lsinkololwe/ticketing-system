/**
 * Playwright global setup: bring up Microcks and seed the mocks.
 *
 * ## Why the port is pinned
 *
 * Playwright starts `webServer` *before* `globalSetup`, so the Next.js dev
 * server's `GRAPHQL_ENDPOINT` has to be known when the config file is evaluated
 * — before a container with a random host port could possibly exist. Binding
 * Microcks to a fixed host port is what lets the app be configured up front.
 * The trade-off is that the port must be free; `MICROCKS_PORT` overrides it.
 *
 * ## Why a container rather than a hand-written stub
 *
 * The mocks are derived from the same artifacts the real API is described by, so
 * they cannot drift into agreeing with a test while disagreeing with the
 * backend — which is precisely the failure a hand-rolled fixture invites.
 */

import { MicrocksContainer, type StartedMicrocksContainer } from '@microcks/microcks-testcontainers';

import { setOnboardingState } from './microcks/client';

export const MICROCKS_PORT = Number(process.env.MICROCKS_PORT ?? 18080);
export const MICROCKS_URL = `http://localhost:${MICROCKS_PORT}`;

/** Kept on globalThis so teardown can reach it across module instances. */
declare global {
  // eslint-disable-next-line no-var
  var __microcks: StartedMicrocksContainer | undefined;
}

export default async function globalSetup() {
  // eslint-disable-next-line no-console
  console.log(`[microcks] starting on ${MICROCKS_URL} …`);

  const container = await new MicrocksContainer()
    // Fixed host port — see the docstring.
    .withExposedPorts({ container: 8080, host: MICROCKS_PORT })
    .start();

  global.__microcks = container;

  // Seed a known baseline so a spec that forgets to declare its state fails on
  // an assertion rather than on whatever the previous run happened to leave.
  await setOnboardingState(MICROCKS_URL, { organization: null, documents: [] });

  // eslint-disable-next-line no-console
  console.log('[microcks] ready');
}
