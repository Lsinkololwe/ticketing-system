import { defineHarnessConfig } from '../../e2e-harness/browser/playwright';

/**
 * Browser specs for the buyer app: real `next dev` + real Redis + a fake upstream, in system Chrome.
 * See e2e-harness/README.md.
 *
 *   PW_CHANNEL=chrome npx playwright test -c apps/ticketing/playwright.browser.config.ts
 */
export default defineHarnessConfig({ app: 'ticketing', testDir: './e2e/browser' });
