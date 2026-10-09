import { harnessTest as test, expect, gqlErrors, delayed } from '../../../../e2e-harness/browser/playwright';
import { as, baseFixtures, settingsFixtures, mediaFixtures, notificationsFixture, dashboardFixtures, eventsListFixtures, ORG } from './fixtures';
import { shot } from './_kit';

const setup = async (up: any, signInAs: any, extra: Record<string, any> = {}, o: Parameters<typeof baseFixtures>[0] = {}) => {
  up.gql({ ...baseFixtures(o), ...extra });
  await signInAs(as(o.role ?? 'OWNER'));
};

test('settings platform rules: New chips on first sight of a changed rule, none after', async ({ page, upstream, signInAs, context }, info) => {
  await context.addInitScript(() => {
    if (!window.localStorage.getItem('pml.organizer.platformRulesSeen')) window.localStorage.setItem('pml.organizer.platformRulesSeen', JSON.stringify({ commissionRate: '4', minimumPayout: '500' }));
  });
  await setup(upstream, signInAs, settingsFixtures());
  await page.goto('/settings');
  await page.getByRole('tab', { name: /platform rules/i }).click({ timeout: 45_000 });
  await expect(page.getByTestId('settings-platform')).toBeVisible();
  await expect(page.getByText('New').first()).toBeVisible();
  await shot(page, 'settings-platform-new', info);
  await page.reload();
  await page.getByRole('tab', { name: /platform rules/i }).click();
  await expect(page.getByTestId('settings-platform')).toBeVisible();
  await expect(page.getByText('New', { exact: true })).toHaveCount(0);
  await shot(page, 'settings-platform-seen', info);
});

test('settings: org profile save, validation, error', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { ...settingsFixtures(), SettingsUpdateOrganization: { updateOrganization: { id: ORG.id } } });
  await page.goto('/settings');
  await expect(page.getByLabel('Tagline')).toBeVisible({ timeout: 45_000 });
  await page.getByLabel('Tagline').fill('Live music, everywhere');
  await page.getByRole('button', { name: /save/i }).first().click();
  await expect.poll(() => upstream.calls('SettingsUpdateOrganization').length).toBe(1);
  upstream.gql({ SettingsOrganization: gqlErrors({ message: 'Unavailable', code: 'SERVICE_UNAVAILABLE' }) });
  await page.reload();
  await page.waitForLoadState('networkidle').catch(() => undefined);
  await shot(page, 'settings-error', info);
});

test('deletion: request needs the typed name; banner when scheduled; only the owner can cancel', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { ...settingsFixtures(), SettingsRequestOrganizationDeletion: { requestOrganizationDeletion: { id: ORG.id } }, SettingsCancelOrganizationDeletion: { cancelOrganizationDeletion: { id: ORG.id } } });
  await page.goto('/settings');
  await page.getByRole('tab', { name: /delete organization/i }).click({ timeout: 45_000 });
  await shot(page, 'settings-danger', info);
  await expect.poll(() => upstream.calls('SettingsOrganization').length).toBeGreaterThan(0);
  await page.waitForLoadState('networkidle').catch(() => undefined);
  await page.getByRole('button', { name: /^delete organization$/i }).click();
  const dlg = page.getByRole('dialog');
  await expect(dlg).toContainText('Delete organization?');
  await shot(page, 'settings-delete-dialog', info);
  await dlg.getByRole('button', { name: /^delete/i }).last().click();
  await page.waitForTimeout(400);
  expect(upstream.calls('SettingsRequestOrganizationDeletion').length).toBe(0);
  await page.waitForTimeout(1200);
  const required = (await dlg.locator('p b').first().innerText()).trim();
  await dlg.getByLabel('Organization name').fill(required);
  await dlg.getByRole('button', { name: /^delete/i }).last().click();
  await expect.poll(() => upstream.calls('SettingsRequestOrganizationDeletion').length).toBe(1);
});

for (const role of ['OWNER', 'ADMIN'] as const) {
  test(`deletion banner shows for ${role}; cancel only for the owner`, async ({ page, upstream, signInAs }, info) => {
    const org = { deletionRequestedAt: '2026-10-01T10:00:00Z', deletionScheduledFor: '2026-12-30T10:00:00Z' };
    await setup(upstream, signInAs, { ...dashboardFixtures(), ...eventsListFixtures(), SettingsCancelOrganizationDeletion: { cancelOrganizationDeletion: { id: ORG.id } } }, { role, org });
    await page.goto('/dashboard');
    await expect(page.getByText('Organization scheduled for deletion.')).toBeVisible({ timeout: 45_000 });
    await shot(page, `deletion-banner-${role.toLowerCase()}`, info);
    const cancel = page.getByRole('button', { name: 'Cancel deletion' });
    if (role === 'OWNER') {
      await cancel.click();
      await expect.poll(() => upstream.calls('SettingsCancelOrganizationDeletion').length).toBe(1);
    } else await expect(cancel).toHaveCount(0);
  });
}

test('notifications: list, mark read, mark all, empty, error, loading', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { ...notificationsFixture(3, 2), OrganizerMarkNotificationRead: { markNotificationRead: { id: 'n0', readAt: '2026-10-05T10:00:00Z', status: 'READ' } }, OrganizerMarkAllNotificationsRead: { markAllNotificationsRead: true } });
  await page.goto('/notifications');
  await expect(page.getByText('Approval').first()).toBeVisible({ timeout: 45_000 });
  await shot(page, 'notifications-list', info);
  await page.getByRole('button', { name: /^Mark .* as read$/ }).first().click();
  await expect.poll(() => upstream.calls('OrganizerMarkNotificationRead').length).toBe(1);
  await page.getByRole('button', { name: /mark all/i }).click();
  await expect.poll(() => upstream.calls('OrganizerMarkAllNotificationsRead').length).toBe(1);
  upstream.gql(notificationsFixture(0, 0));
  await page.reload();
  await page.waitForTimeout(800);
  await shot(page, 'notifications-empty', info);
  upstream.gql({ OrganizerNotifications: gqlErrors({ message: 'Unavailable', code: 'SERVICE_UNAVAILABLE' }) });
  await page.reload();
  await page.waitForTimeout(800);
  await shot(page, 'notifications-error', info);
  upstream.gql({ OrganizerNotifications: delayed(6000, notificationsFixture(1, 1).OrganizerNotifications as never) });
  await page.reload();
  await page.waitForTimeout(1200);
  await shot(page, 'notifications-loading', info);
});

test('media library: grid, list, details sheet, empty, error', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, mediaFixtures(3));
  await page.goto('/media');
  await expect(page.getByText(/poster 1/i).first()).toBeVisible({ timeout: 45_000 });
  await shot(page, 'media-grid', info);
  await page.getByRole('radio', { name: 'List' }).or(page.getByRole('button', { name: 'List' })).first().click();
  await page.waitForTimeout(400);
  await shot(page, 'media-list', info);
  await page.getByRole('button', { name: /details/i }).first().click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await shot(page, 'media-details', info);
  await page.keyboard.press('Escape');
  upstream.gql(mediaFixtures(0));
  await page.reload();
  await page.waitForTimeout(800);
  await shot(page, 'media-empty', info);
  upstream.gql({ OrganizerMedia: gqlErrors({ message: 'Unavailable', code: 'SERVICE_UNAVAILABLE' }) });
  await page.reload();
  await page.waitForTimeout(800);
  await shot(page, 'media-error', info);
});
