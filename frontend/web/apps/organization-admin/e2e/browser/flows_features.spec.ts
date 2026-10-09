import { harnessTest as test, expect } from '../../../../e2e-harness/browser/playwright';
import { EVENTS, eventRow, as, baseFixtures, eventsListFixtures, eventDetailFixtures, editorEvent, settingsFixtures, mediaFixtures, teamFixtures, PIXEL, ORG } from './fixtures';
import { shot } from './_kit';

const setup = async (up: any, signInAs: any, extra: Record<string, any> = {}, role: any = 'OWNER') => {
  up.gql({ ...baseFixtures({ role }), ...extra });
  await signInAs(as(role));
};

test('team event access: org-wide table, grant with an event, edit, revoke after confirming', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, {
    ...eventsListFixtures(), ...teamFixtures(),
    MyEvents: { myEvents: { content: EVENTS.map(eventRow), totalElements: EVENTS.length, totalPages: 1, hasNext: false } },
    OrganizerGrantAccess: { grantEventAccess: { id: 'g-new' } },
    OrganizerUpdateGrant: { updateEventAccess: { id: 'g-1' } },
    OrganizerRevokeGrant: { revokeEventAccess: { id: 'g-1' } },
  });
  await page.goto('/team?tab=access');
  await expect(page.getByText('Event access grants').first()).toBeVisible({ timeout: 45_000 });
  await expect(page.getByText('Joe Tembo').first()).toBeVisible();
  await shot(page, 'team-access-orgwide', info);
  await page.getByRole('button', { name: 'Grant access' }).first().click();
  const dlg = page.getByRole('dialog');
  await expect(dlg.getByLabel('Event', { exact: true })).toBeVisible();
  await dlg.getByRole('button', { name: 'Grant access' }).click(); // empty: validation, no request
  await page.waitForTimeout(300);
  expect(upstream.calls('OrganizerGrantAccess').length).toBe(0);
  await shot(page, 'team-access-grant-invalid', info);
  await dlg.getByLabel('Event', { exact: true }).selectOption({ index: 1 });
  await dlg.getByLabel('Team member').selectOption({ index: 1 });
  await dlg.getByLabel('Reason').fill('Runs the gate');
  await dlg.getByRole('button', { name: 'Grant access' }).click();
  await expect.poll(() => upstream.calls('OrganizerGrantAccess').length).toBe(1);
  await page.getByRole('button', { name: 'Edit' }).first().click();
  await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click();
  await expect.poll(() => upstream.calls('OrganizerUpdateGrant').length).toBe(1);
  await page.getByRole('button', { name: 'Revoke' }).first().click();
  await expect(page.getByRole('alertdialog')).toBeVisible();
  expect(upstream.calls('OrganizerRevokeGrant').length).toBe(0);
  await page.getByRole('alertdialog').getByRole('button', { name: 'Revoke' }).click();
  await expect.poll(() => upstream.calls('OrganizerRevokeGrant').length).toBe(1);
});

test('rich text: toolbar formatting is saved as sanitised HTML; injected markup never reaches the form', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, {
    ...eventsListFixtures(), ...eventDetailFixtures('kgn', 'DRAFT'), ...editorEvent('kgn', 'DRAFT'),
    EditorUpdateEvent: { updateEvent: { id: 'kgn', status: 'DRAFT' } }, EditorUpdateAccessibility: { updateEventAccessibility: { id: 'kgn' } },
  });
  await page.goto('/events/kgn/edit');
  const box = page.getByRole('textbox', { name: 'Full description' });
  await expect(box).toBeVisible({ timeout: 45_000 });
  await box.click();
  await page.keyboard.press('Control+A');
  await page.keyboard.type('Gospel night with the choir');
  await page.keyboard.press('Control+A');
  await page.getByRole('button', { name: 'Bold' }).click();
  await page.getByRole('button', { name: 'Preview', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Edit' })).toBeVisible();
  await shot(page, 'rich-preview', info);
  await page.getByRole('button', { name: 'Edit' }).click();
  // Hostile markup written straight into the surface is reduced to the allowed subset.
  await page.getByRole('textbox', { name: 'Full description' }).evaluate((el) => {
    el.innerHTML = '<p onclick="x()">Safe <b>bold</b></p><script>window.__pwned=1</script><img src=x onerror="window.__pwned=1">';
    el.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await shot(page, 'rich-edit', info);
  await page.getByRole('button', { name: 'Save changes' }).click({ timeout: 10_000 }).catch(() => undefined);
  await page.getByRole('button', { name: /save draft|save changes/i }).first().click().catch(() => undefined);
  await expect.poll(() => upstream.calls('EditorUpdateEvent').length).toBeGreaterThan(0);
  const input = (upstream.calls('EditorUpdateEvent')[0].variables as any).input;
  expect(input.description).toBe('<p>Safe <b>bold</b></p>');
  expect(await page.evaluate(() => (window as any).__pwned)).toBeUndefined();
});

test('logo and banner: choose from the media library, upload a new image, save', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, {
    ...settingsFixtures(), ...mediaFixtures(2),
    SettingsUpdateOrganization: { updateOrganization: { id: ORG.id } },
    OrganizerUploadMedia: { uploadMedia: { id: 'med-new', url: PIXEL.replace('logo', 'new'), fileName: 'banner.png', contentType: 'image/png', sizeBytes: 3, title: null, altText: null, eventId: null, status: 'ACTIVE', createdAt: '2026-10-05T10:00:00Z' } },
  });
  await page.goto('/settings');
  await expect(page.getByRole('button', { name: 'Choose logo' })).toBeVisible({ timeout: 45_000 });
  await shot(page, 'settings-media-fields', info);
  await page.getByRole('button', { name: 'Choose logo' }).click();
  await expect(page.getByRole('dialog')).toContainText('Choose an image');
  await shot(page, 'settings-media-picker', info);
  await page.getByRole('dialog').getByRole('button', { name: /^Use / }).first().click();
  await expect(page.getByRole('button', { name: 'Change logo' })).toBeVisible();
  await page.getByRole('button', { name: 'Choose banner' }).click();
  await page.getByLabel('Upload an image').setInputFiles({ name: 'banner.png', mimeType: 'image/png', buffer: Buffer.from('png') });
  await expect.poll(() => upstream.calls('OrganizerUploadMedia').length).toBe(1);
  await expect(page.getByRole('button', { name: 'Change banner' })).toBeVisible();
  await page.getByRole('button', { name: /save changes/i }).click();
  await expect.poll(() => upstream.calls('SettingsUpdateOrganization').length).toBe(1);
  const v = upstream.calls('SettingsUpdateOrganization')[0].variables as any;
  expect(v.input.logoUrl).toBe(PIXEL);
  expect(String(v.input.bannerUrl)).toContain('new');
});

test('duplicate a past event: header button, choose event, name the copy, land in the editor', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, {
    ...eventsListFixtures(), ...editorEvent('dup-1', 'DRAFT'),
    OrgDuplicateEvent: { duplicateEvent: { id: 'dup-1', title: 'Independence Eve Concert (copy)', status: 'DRAFT' } },
  });
  await page.goto('/events');
  await page.getByRole('button', { name: 'Duplicate a past event' }).click({ timeout: 45_000 });
  const dlg = page.getByRole('dialog');
  await expect(dlg).toContainText('Duplicate a past event');
  await shot(page, 'events-duplicate-past', info);
  await dlg.getByLabel('Past event').selectOption({ label: /Independence Eve Concert/ as never }).catch(async () => dlg.getByLabel('Past event').selectOption({ index: 1 }));
  await dlg.getByRole('button', { name: 'Continue' }).click();
  await expect(page.getByRole('dialog')).toContainText('Duplicate “');
  await page.getByRole('dialog').getByRole('button', { name: 'Duplicate', exact: true }).click();
  await expect.poll(() => upstream.calls('OrgDuplicateEvent').length).toBe(1);
  await expect(page).toHaveURL(/\/events\/dup-1\/edit/);
});
