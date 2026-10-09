import { harnessTest as test, expect, captureConsole, gqlErrors } from '../../../../e2e-harness/browser/playwright';
import { catalog, RULES } from './fixtures';
import { settle, shot } from './_kit';

test.describe('static and help pages', () => {
  test('help: topics and refund policies from publicPlatformRules, signed out', async ({ page, upstream }, info) => {
    upstream.gql(catalog());
    const log = captureConsole(page);
    await page.goto('/help');
    await expect(page.getByRole('heading', { level: 1, name: 'Need help with your booking?' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Refund policies' })).toBeVisible();
    for (const p of RULES.refundPolicies ?? []) await expect(page.getByText(String(p?.summary))).toBeVisible();
    await expect(page.getByText(/Refund requests close 2 days before the event/)).toBeVisible();
    await expect(page.getByText(/Sign in to see the refund polic/)).toHaveCount(0);
    await shot(page, 'help', info);
    await settle(page, upstream, log);
  });

  test('help: rules unavailable shows an honest message', async ({ page, upstream }, info) => {
    upstream.gql({ ...catalog(), BuyerPlatformRules: gqlErrors({ message: 'down', extensions: { errorCode: 'INTERNAL_ERROR', classification: 'INTERNAL', retryable: true } }) });
    await page.goto('/help');
    await expect(page.getByTestId('error-state')).toBeVisible();
    await shot(page, 'help-rules-down', info);
  });

  test('refund-policies page', async ({ page, upstream }, info) => {
    upstream.gql(catalog());
    const log = captureConsole(page);
    await page.goto('/refund-policies');
    await expect(page.getByText('All sales are final.')).toBeVisible();
    await shot(page, 'refund-policies', info);
    await settle(page, upstream, log);
  });

  for (const path of ['terms', 'privacy']) {
    test(`${path}`, async ({ page, upstream }, info) => {
      upstream.gql(catalog());
      const log = captureConsole(page);
      await page.goto(`/${path}`);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      await shot(page, path, info);
      await settle(page, upstream, log);
    });
  }

  test('sign-in page', async ({ page, upstream }, info) => {
    upstream.gql(catalog());
    const log = captureConsole(page);
    await page.goto('/auth');
    await expect(page.getByRole('heading', { name: 'Sign in to Showstop' })).toBeVisible();
    await shot(page, 'auth', info);
    await settle(page, upstream, log);
  });

  test('not found', async ({ page, upstream }, info) => {
    upstream.gql(catalog());
    const res = await page.goto('/no-such-page');
    expect(res?.status()).toBe(404);
    await shot(page, 'not-found', info);
  });

  test('invitation: preview, expired and missing token', async ({ page, upstream, signInAs }, info) => {
    await signInAs({ roles: ['CUSTOMER'] });
    upstream.gql({ ...catalog(), InvitationByToken: { invitationByToken: { __typename: 'InvitationPreview', organizationName: 'Fixture Organizer', organizationLogoUrl: null, proposedRole: 'EVENT_MANAGER', inviterDisplayName: 'Mwila B.', expiresAt: new Date(Date.now() + 86_400_000).toISOString() } } });
    await page.goto('/invitations/accept?token=tok-ok');
    await shot(page, 'invite', info);
    upstream.gql({ ...catalog(), InvitationByToken: gqlErrors({ message: 'expired', extensions: { errorCode: 'INVITATION_EXPIRED', classification: 'CONFLICT' } }) });
    await page.goto('/invitations/accept?token=tok-old');
    await shot(page, 'invite-expired', info);
  });
});
