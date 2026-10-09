import { harnessTest as test, expect } from '../../../../e2e-harness/browser/playwright';
import { as, baseFixtures, ownedOrg } from './fixtures';
import { shot } from './_kit';

const STATES = ['DRAFT', 'PENDING_REVIEW', 'CHANGES_REQUESTED', 'REJECTED'] as const;

test('login page: public, offers sign in', async ({ page }, info) => {
  await page.goto('/login');
  await page.waitForLoadState('networkidle').catch(() => undefined);
  await shot(page, 'login', info);
  await expect(page.getByRole('heading').first()).toBeVisible();
});

for (const st of STATES) {
  test(`onboarding ${st}: routed to the application flow`, async ({ page, upstream, signInAs }, info) => {
    upstream.gql({ ...baseFixtures({ status: st }), MyOrganization: { myOwnedOrganization: ownedOrg({ status: st, isApproved: false, kybStatus: st === 'DRAFT' ? 'NOT_STARTED' : st, rejectionReason: st === 'REJECTED' ? 'We could not verify the registration number.' : null }) } });
    upstream.rest('GET', '/api/v1/organizations/org-1/documents', { body: [] });
    await signInAs(as('OWNER'));
    await page.goto('/apply/status');
    await page.waitForLoadState('networkidle').catch(() => undefined);
    console.log(`ONB ${st} -> ${new URL(page.url()).pathname} unhandled=${JSON.stringify([...new Set(upstream.unhandled)])}`);
    await shot(page, `onboarding-${st.toLowerCase()}`, info);
  });
}
