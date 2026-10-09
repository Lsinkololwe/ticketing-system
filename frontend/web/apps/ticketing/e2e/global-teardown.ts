/**
 * Playwright global teardown for the ticketing app — releases the pinned port.
 */

import { stopMicrocks } from '../../../e2e-harness/microcks-container';

export default async function globalTeardown() {
  await stopMicrocks();
}
