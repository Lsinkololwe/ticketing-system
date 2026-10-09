import { harnessTest as test, expect, captureConsole, shot, gqlErrors } from '../../../../e2e-harness/browser/playwright';
import { mockAll } from './mock-upstream';
import { settleAdmin } from './_kit';

const pendingOrgs = { 'Organization.status': 'PENDING_REVIEW', 'Organization.name': 'Chipata Youth Arts' };

test.describe('approvals', () => {
  test('organizers: review sheet, rejection needs a written reason, then calls the mutation', async ({ page, upstream, signInAs }, info) => {
    mockAll(upstream, { fields: pendingOrgs, listSize: 2 });
    upstream.gql({ RejectOrganization: { rejectOrganization: { id: 'o1', status: 'REJECTED' } } });
    await signInAs({ roles: ['ADMIN'], accountId: 'staff-1' });
    const log = captureConsole(page);
    await page.goto('/approvals/orgs');
    await expect(page.getByRole('heading', { level: 3, name: 'Organizer applications' })).toBeVisible();
    await page.getByRole('button', { name: 'Review' }).first().click();
    await expect(page.getByRole('button', { name: 'Request changes' })).toBeVisible();
    await shot(page, 'approvals-org-sheet', info);
    await page.getByRole('button', { name: 'Reject', exact: true }).first().click();
    const dlg = page.getByRole('dialog').last();
    await dlg.getByRole('button', { name: 'Reject application' }).click();
    await expect(dlg.getByText(/at least 5 characters/)).toBeVisible();
    expect(upstream.calls('RejectOrganization')).toHaveLength(0);
    await shot(page, 'approvals-reject-required', info);
    await dlg.getByLabel('Reason').fill('Registration number does not match the certificate');
    await dlg.getByRole('button', { name: 'Reject application' }).click();
    await expect.poll(() => upstream.calls('RejectOrganization').length).toBe(1);
    expect(JSON.stringify(upstream.calls('RejectOrganization')[0].variables)).toContain('Registration number');
    await settleAdmin(page, upstream, log, { a11y: false });
  });

  test('organizers: request changes also needs a message', async ({ page, upstream, signInAs }) => {
    mockAll(upstream, { fields: pendingOrgs, listSize: 2 });
    await signInAs({ roles: ['SUPER_ADMIN'], accountId: 'staff-1' });
    await page.goto('/approvals/orgs');
    await page.getByRole('button', { name: 'Review' }).first().click();
    await page.getByRole('button', { name: 'Request changes' }).click();
    const dlg = page.getByRole('dialog').last();
    await dlg.getByRole('button', { name: 'Send request' }).click();
    await expect(dlg.getByText(/at least 5 characters/)).toBeVisible();
  });

  for (const tab of ['events', 'docs'] as const) {
    test(`${tab} tab renders populated, empty and error states`, async ({ page, upstream, signInAs }, info) => {
      await signInAs({ roles: ['ADMIN'], accountId: 'staff-1' });
      mockAll(upstream);
      await page.goto(`/approvals/${tab}`);
      await expect(page.getByRole('tab', { selected: true })).toContainText(tab === 'events' ? 'Events' : 'Documents');
      await shot(page, `approvals-${tab}`, info);
      mockAll(upstream, { listSize: 0 });
      await page.goto(`/approvals/${tab}`);
      await page.waitForLoadState('networkidle');
      await shot(page, `approvals-${tab}-empty`, info);
      upstream.gql({ PendingApprovalEvents: gqlErrors({ message: 'down', extensions: { errorCode: 'INTERNAL_ERROR', retryable: true } }) });
      await page.goto(`/approvals/${tab}`);
      await page.waitForLoadState('networkidle');
      await shot(page, `approvals-${tab}-error`, info);
    });
  }
});
