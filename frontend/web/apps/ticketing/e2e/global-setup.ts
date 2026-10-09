/**
 * Playwright global setup for the ticketing app.
 *
 * Uses the same container harness as org-admin (`e2e-harness/microcks-container`),
 * so all three apps share one mock path and one pinned port rather than three
 * subtly different ones. No dataset is seeded here: this app's specs declare the
 * state they need, and seeding a baseline nobody asked for would make a spec
 * that forgets to do so pass for the wrong reason.
 */

import { startMicrocks } from '../../../e2e-harness/microcks-container';

export default async function globalSetup() {
  await startMicrocks();
}
