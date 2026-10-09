import { MicrocksContainer, type StartedMicrocksContainer } from '@microcks/microcks-testcontainers';

/**
 * One container-backed mock path, shared by all three apps. Track F0-3.
 *
 * <h2>Why the host port is pinned rather than random</h2>
 * Playwright starts `webServer` <b>before</b> `globalSetup`, so each app's
 * `GRAPHQL_ENDPOINT` has to be known while the config file is being evaluated —
 * earlier than a container with a random host port could possibly exist.
 * Binding to a fixed port is what lets the app be configured up front. The
 * trade-off is that the port must be free, so `MICROCKS_PORT` overrides it.
 *
 * <h2>Why a container rather than a hand-written stub</h2>
 * The mocks are derived from the same artifacts the real API is described by,
 * so they cannot drift into agreeing with a test while disagreeing with the
 * backend. A stub proves the stub matches the test's belief about the response,
 * which is exactly where beliefs are wrong.
 *
 * <h2>What this cannot do</h2>
 * Microcks answers <b>500</b> to any GraphQL query containing a fragment. That
 * is survivable for Server Components, which issue plain queries, and fatal for
 * Apollo Client, which composes fragments as a matter of course. Apollo-driven
 * surfaces need a container speaking the real schema instead — see F0-4. Reach
 * for this harness for server-rendered surfaces and REST, and do not try to
 * stretch it over the rest.
 */

/** Kept on globalThis so teardown reaches it across module instances. */
declare global {
  // eslint-disable-next-line no-var
  var __microcks: StartedMicrocksContainer | undefined;
}

export const MICROCKS_PORT = Number(process.env.MICROCKS_PORT ?? 18080);
export const MICROCKS_URL = `http://localhost:${MICROCKS_PORT}`;

/**
 * Starts Microcks on the pinned port, or reuses one already running.
 *
 * @param seed optional callback to load a baseline dataset. Seeding a known
 *   state matters more than it looks: without it a spec that forgets to declare
 *   what it needs passes or fails on whatever the previous run happened to
 *   leave, which is the kind of flake that gets blamed on the container.
 */
export async function startMicrocks(
  seed?: (microcksUrl: string) => Promise<void>
): Promise<StartedMicrocksContainer> {
  if (global.__microcks) {
    return global.__microcks;
  }

  // eslint-disable-next-line no-console
  console.log(`[microcks] starting on ${MICROCKS_URL} …`);

  const container = await new MicrocksContainer()
    .withExposedPorts({ container: 8080, host: MICROCKS_PORT })
    .start();

  global.__microcks = container;

  if (seed) {
    await seed(MICROCKS_URL);
  }

  // eslint-disable-next-line no-console
  console.log('[microcks] ready');
  return container;
}

/**
 * Stops the container and frees the pinned port.
 *
 * <p>Testcontainers' Ryuk reaper would collect it eventually, but stopping
 * explicitly releases the port immediately — otherwise a second run started too
 * soon cannot bind and the failure looks like a broken harness rather than a
 * busy port.</p>
 */
export async function stopMicrocks(): Promise<void> {
  const container = global.__microcks;
  if (!container) return;

  await container.stop();
  global.__microcks = undefined;

  // eslint-disable-next-line no-console
  console.log('[microcks] stopped');
}

/** Whether something is already answering on the pinned port. */
export async function microcksIsRunning(microcksUrl = MICROCKS_URL): Promise<boolean> {
  try {
    const response = await fetch(`${microcksUrl}/api/services`);
    return response.ok;
  } catch {
    return false;
  }
}
