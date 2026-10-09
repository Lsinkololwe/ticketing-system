import { harnessTest as test, expect, captureConsole, gqlErrors } from '../../../../e2e-harness/browser/playwright';
import { CONTACTS, ME, signedInBase } from './fixtures';
import { settle, shot } from './_kit';

test.describe('profile and settings', () => {
  test('populated: details, preferences, sign-in contacts, account actions', async ({ page, upstream, signInAs }, info) => {
    upstream.gql({
      ...signedInBase(),
      BuyerUpdateMyProfile: (vars) => ({ updateMyProfile: { ...ME, ...(vars.input as object) } }),
    });
    await signInAs({ roles: ['CUSTOMER'] });
    const log = captureConsole(page);
    await page.goto('/profile');
    await expect(page.getByRole('heading', { level: 1, name: 'Profile and settings' })).toBeVisible();
    await expect(page.getByLabel('First name')).toHaveValue('Mwila');
    await expect(page.getByText('+260 97 *** 4567')).toBeVisible();
    await shot(page, 'profile', info);
    await page.getByLabel('First name').fill('Chanda');
    await page.getByRole('button', { name: /save/i }).first().click();
    await expect.poll(() => upstream.calls('BuyerUpdateMyProfile').length).toBe(1);
    expect(upstream.calls('BuyerUpdateMyProfile')[0].variables).toMatchObject({ input: { firstName: 'Chanda', lastName: 'Banda' } });
    await page.getByRole('button', { name: 'Sign out' }).click();
    await expect(page.getByRole('alertdialog', { name: 'Sign out?' })).toBeVisible();
    await shot(page, 'profile-signout', info);
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: 'Request account deletion' }).click();
    await expect(page.getByRole('alertdialog', { name: 'Request account deletion?' })).toBeVisible();
    await shot(page, 'profile-delete', info);
    await settle(page, upstream, log, { axeExclude: ['[role=dialog]'] });
  });

  test('contacts: add a contact opens the verification flow', async ({ page, upstream, signInAs }, info) => {
    upstream.gql({
      ...signedInBase(),
      MyContacts: { myContacts: { ...CONTACTS.myContacts, contacts: CONTACTS.myContacts.contacts.slice(0, 1) } },
      RequestContactAdd: { requestContactAdd: { __typename: 'ContactChallenge', challengeId: 'chal-0001', contactType: 'EMAIL', maskedContact: 'n***@example.test', expiresInSeconds: 300, resendAfterSeconds: 30 } },
    });
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/profile');
    await page.getByRole('button', { name: 'Add an email' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await shot(page, 'profile-contact-add', info);
  });

  test('error: profile query fails', async ({ page, upstream, signInAs }, info) => {
    upstream.gql({ ...signedInBase(), Me: gqlErrors({ message: 'boom', extensions: { errorCode: 'INTERNAL_ERROR', classification: 'INTERNAL', retryable: true } }) });
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/profile');
    await expect(page.getByTestId('error-state').first()).toBeVisible();
    await shot(page, 'profile-error', info);
  });

  test('deletion pending shows the cancel option', async ({ page, upstream, signInAs }, info) => {
    upstream.gql({ ...signedInBase(), Me: { me: { ...ME, deletionRequestedAt: new Date().toISOString(), deletionScheduledFor: new Date(Date.now() + 14 * 86_400_000).toISOString() } } });
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/profile');
    await expect(page.getByRole('button', { name: 'Cancel deletion request' }).first()).toBeVisible();
    await shot(page, 'profile-deletion-pending', info);
  });
});
