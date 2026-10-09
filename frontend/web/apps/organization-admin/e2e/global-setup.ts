/**
 * Playwright global setup for org-admin: bring up the shared Microcks harness
 * and seed the onboarding baseline.
 *
 * The container lifecycle — the pinned host port, the reuse check, the explicit
 * stop — lives in `e2e-harness/microcks-container.ts` so all three apps start
 * the same way. What stays here is the only part that is org-admin's: which
 * dataset the mocks begin with.
 */

import { startMicrocks, stopMicrocks, MICROCKS_PORT, MICROCKS_URL } from '../../../e2e-harness/microcks-container';

import { setOnboardingState } from './microcks/client';

export { MICROCKS_PORT, MICROCKS_URL, stopMicrocks };

export default async function globalSetup() {
  await startMicrocks(async (microcksUrl) => {
    // A known baseline, so a spec that forgets to declare its state fails on an
    // assertion rather than on whatever the previous run happened to leave.
    await setOnboardingState(microcksUrl, { organization: null, documents: [] });
  });
}
