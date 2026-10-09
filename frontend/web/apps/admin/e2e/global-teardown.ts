/**
 * Playwright global teardown for the admin console — releases the pinned port.
 */

import { stopMicrocks } from '../../../e2e-harness/microcks-container';

export default async function globalTeardown() {
  await stopMicrocks();
}
