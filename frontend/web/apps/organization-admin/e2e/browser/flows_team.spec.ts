import { harnessTest as test, expect, gqlErrors } from '../../../../e2e-harness/browser/playwright';
import { as, baseFixtures, teamFixtures } from './fixtures';
import { shot } from './_kit';

const setup = async (up: any, signInAs: any, o: Parameters<typeof teamFixtures>[0] = {}, extra: Record<string, any> = {}, role: any = 'OWNER') => {
  up.gql({ ...baseFixtures({ role }), ...teamFixtures(o), ...extra });
  await signInAs(as(role));
};
const openInvites = async (page: any) => {
  await page.goto('/team');
  await expect(page.getByText('Chanda Mwape').first()).toBeVisible({ timeout: 45_000 });
  await page.getByRole('tab', { name: /invitations/i }).click();
};

test('invite by email: dialog, send, mutation variables', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, {}, { OrganizerInvite: { inviteTeamMember: { id: 'inv-9', status: 'PENDING' } } });
  await openInvites(page);
  await page.getByRole('button', { name: /invite one person/i }).click();
  const dlg = page.getByRole('dialog');
  await expect(dlg).toContainText('Invite a team member');
  await shot(page, 'team-invite-dialog', info);
  await dlg.getByLabel('Name').fill('Mwila Tembo');
  await dlg.getByLabel('Email').fill('mwila@example.com');
  await dlg.getByRole('button', { name: 'Send invitation' }).click();
  await expect.poll(() => upstream.calls('OrganizerInvite').length).toBe(1);
  expect(upstream.calls('OrganizerInvite')[0].variables).toMatchObject({ input: { email: 'mwila@example.com', role: 'MANAGER' } });
});

test('invite by phone only', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, {}, { OrganizerInvite: { inviteTeamMember: { id: 'inv-10', status: 'PENDING' } } });
  await openInvites(page);
  await page.getByRole('button', { name: /invite one person/i }).click();
  const dlg = page.getByRole('dialog');
  await dlg.getByLabel('Name').fill('Kunda Zulu');
  await dlg.getByLabel('WhatsApp number').fill('0971234567');
  await dlg.getByRole('button', { name: 'Send invitation' }).click();
  await expect.poll(() => upstream.calls('OrganizerInvite').length).toBe(1);
  const input = (upstream.calls('OrganizerInvite')[0].variables as any).input;
  expect(JSON.stringify(input)).toMatch(/\+?260971234567|971234567/);
});

test('invite: validation and server refusal', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, {}, { OrganizerInvite: gqlErrors({ message: 'That person is already a member', code: 'ALREADY_MEMBER' }) });
  await openInvites(page);
  await page.getByRole('button', { name: /invite one person/i }).click();
  const dlg = page.getByRole('dialog');
  await dlg.getByRole('button', { name: 'Send invitation' }).click();
  await page.waitForTimeout(400);
  await shot(page, 'team-invite-invalid', info);
  expect(upstream.calls('OrganizerInvite').length).toBe(0);
  await dlg.getByLabel('Name').fill('Mwila Tembo');
  await dlg.getByLabel('Email').fill('mwila@example.com');
  await dlg.getByRole('button', { name: 'Send invitation' }).click();
  await expect.poll(() => upstream.calls('OrganizerInvite').length).toBe(1);
  await page.waitForTimeout(600);
  await shot(page, 'team-invite-refused', info);
  await expect(page.getByRole('dialog')).toBeVisible();
});

test('invitations: resend and revoke; empty list', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, {}, { OrganizerResendInvite: { resendInvitation: { id: 'inv-1', status: 'PENDING' } }, OrganizerRevokeInvite: { revokeInvitation: { id: 'inv-1', status: 'REVOKED' } } });
  await openInvites(page);
  await page.getByRole('button', { name: 'Resend' }).first().click();
  await expect.poll(() => upstream.calls('OrganizerResendInvite').length).toBe(1);
  await page.getByRole('button', { name: 'Revoke' }).first().click();
  await page.waitForTimeout(400);
  const confirm = page.getByRole('dialog').getByRole('button', { name: /revoke|confirm/i });
  if (await confirm.count()) await confirm.last().click();
  await expect.poll(() => upstream.calls('OrganizerRevokeInvite').length).toBe(1);
  upstream.gql({ OrganizerInvitations: { pendingInvitations: { content: [] } } });
  await page.reload();
  await page.getByRole('tab', { name: /invitations/i }).click();
  await page.waitForTimeout(800);
  await shot(page, 'team-invites-empty', info);
});

test('ownership transfer: nominate an admin, then cancel the pending transfer', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, {}, { OrganizerInitiateTransfer: { initiateOwnershipTransfer: { id: 'tr-1', status: 'PENDING' } }, OrganizerCancelTransfer: { cancelOwnershipTransfer: true } });
  await page.goto('/team');
  await expect(page.getByText('Chanda Mwape').first()).toBeVisible({ timeout: 45_000 });
  await page.getByRole('tab', { name: /ownership/i }).click();
  await expect(page.getByText('Transfer ownership').first()).toBeVisible();
  await shot(page, 'team-ownership', info);
  await page.getByLabel('New owner').selectOption({ label: 'Chanda Mwape' });
  await page.getByRole('button', { name: 'Start transfer' }).click();
  await page.waitForTimeout(500);
  const confirm = page.getByRole('dialog').getByRole('button', { name: /start transfer|confirm|transfer/i });
  if (await confirm.count()) { await shot(page, 'team-ownership-confirm', info); await confirm.last().click(); }
  await expect.poll(() => upstream.calls('OrganizerInitiateTransfer').length).toBe(1);
  expect(upstream.calls('OrganizerInitiateTransfer')[0].variables).toMatchObject({ newOwnerId: 'u-chanda' });
  upstream.gql({ OrganizerPendingTransfer: { pendingOwnershipTransfer: { id: 'tr-1', newOwnerId: 'u-chanda', newOwner: { id: 'u-chanda', fullName: 'Chanda Mwape' }, status: 'PENDING', expiresAt: '2026-10-12T00:00:00Z', initiatedAt: '2026-10-05T00:00:00Z' } } });
  await page.reload();
  await page.getByRole('tab', { name: /ownership/i }).click();
  await expect(page.getByText('Ownership transfer pending')).toBeVisible();
  await shot(page, 'team-ownership-pending', info);
  await page.getByRole('button', { name: 'Cancel transfer' }).click();
  await page.waitForTimeout(400);
  const c2 = page.getByRole('dialog').getByRole('button', { name: /cancel transfer|confirm|yes/i });
  if (await c2.count()) await c2.last().click();
  await expect.poll(() => upstream.calls('OrganizerCancelTransfer').length).toBe(1);
});

test('ownership: non-owner sees the notice; incoming offer can be accepted', async ({ page, upstream, signInAs }, info) => {
  const offer = { id: 'tr-9', status: 'PENDING', expiresAt: '2026-10-12T00:00:00Z', reason: null, organization: { id: 'org-1', name: 'Copperbelt Live' }, currentOwner: { id: 'u-mutinta', fullName: 'Mutinta Banda' } };
  await setup(upstream, signInAs, { transfer: offer }, { OrganizerRequestTransferCode: { requestOwnershipTransferCode: true }, OrganizerAcceptTransfer: { acceptOwnershipTransfer: { id: 'tr-9', status: 'COMPLETED' } } }, 'ADMIN');
  await page.goto('/team');
  await expect(page.getByText('Chanda Mwape').first()).toBeVisible({ timeout: 45_000 });
  await page.getByRole('tab', { name: /ownership/i }).click();
  await expect(page.getByText('Only the owner can transfer ownership.')).toBeVisible();
  await expect(page.getByText('Ownership offered to you')).toBeVisible();
  await shot(page, 'team-ownership-incoming', info);
  await page.getByLabel('Transfer token').fill('tok-12345');
  await page.getByLabel('One-time code').fill('123456');
  await page.getByRole('button', { name: 'Accept ownership' }).click();
  await expect.poll(() => upstream.calls('OrganizerAcceptTransfer').length).toBe(1);
});

test('team: roles tab, members empty and error', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { members: [] });
  await page.goto('/team');
  await page.waitForLoadState('networkidle').catch(() => undefined);
  await shot(page, 'team-members-empty', info);
  await page.getByRole('tab', { name: /roles and access/i }).click();
  await page.waitForTimeout(500);
  await shot(page, 'team-roles', info);
  upstream.gql({ OrganizerRoster: gqlErrors({ message: 'Roster unavailable', code: 'SERVICE_UNAVAILABLE' }) });
  await page.reload();
  await page.waitForLoadState('networkidle').catch(() => undefined);
  await shot(page, 'team-members-error', info);
});
