/**
 * Playwright global teardown: stop the Microcks container.
 *
 * Testcontainers' Ryuk reaper would eventually collect it anyway, but stopping
 * explicitly frees the pinned host port immediately — otherwise a second run
 * started too soon fails to bind and looks like a broken harness.
 */

export default async function globalTeardown() {
  const container = global.__microcks;
  if (!container) return;

  await container.stop();
  global.__microcks = undefined;

  // eslint-disable-next-line no-console
  console.log('[microcks] stopped');
}
