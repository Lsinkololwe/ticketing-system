/**
 * Playwright global setup for the admin console: the shared Microcks harness, so all three apps
 * start the same container on the same pinned port. No dataset is seeded — a spec declares the
 * state it needs.
 */

import { startMicrocks } from '../../../e2e-harness/microcks-container';

export default async function globalSetup() {
  await startMicrocks();
}
