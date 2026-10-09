/**
 * Playwright global teardown for org-admin.
 *
 * Delegates to the shared harness so the pinned port is released the same way
 * for every app — a port left bound makes the next run look like a broken
 * harness rather than a busy socket.
 */

import { stopMicrocks } from '../../../e2e-harness/microcks-container';

export default async function globalTeardown() {
  await stopMicrocks();
}
