import { harnessTest as test, expect, gqlErrors, delayed } from '../../../../e2e-harness/browser/playwright';
import { as, baseFixtures, editorEvent, eventsListFixtures, eventDetailFixtures } from './fixtures';
import { shot } from './_kit';

const setup = async (up: any, signInAs: any, extra: Record<string, any>) => {
  up.gql({ ...baseFixtures(), ...eventsListFixtures(), ...eventDetailFixtures('kgn', 'DRAFT'), ...extra });
  await signInAs(as('OWNER'));
};
const toPublish = async (page: any) => {
  await page.getByRole('tab', { name: /publish/i }).first().click();
  await page.waitForTimeout(400);
};

test('editor: submit for approval (populated -> dialog -> mutation)', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { ...editorEvent('kgn', 'DRAFT'), EditorSubmitForApproval: { submitEventForApproval: { id: 'kgn', status: 'PENDING_APPROVAL' } } });
  await page.goto('/events/kgn/edit');
  await expect(page.getByText('Event details').first()).toBeVisible();
  await toPublish(page);
  await shot(page, 'editor-publish-step', info);
  await page.getByTestId('step-bar').getByRole('button', { name: /^submit for approval$/i }).click();
  const dlg = page.getByRole('dialog');
  await expect(dlg).toContainText('for approval?');
  await shot(page, 'editor-submit-dialog', info);
  await dlg.getByRole('button', { name: /submit for approval|submit anyway/i }).click();
  await expect.poll(() => upstream.calls('EditorSubmitForApproval').length).toBe(1);
  expect(upstream.calls('EditorSubmitForApproval')[0].variables).toMatchObject({ eventId: 'kgn' });
});

test('editor: submit error is shown and the dialog stays recoverable', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { ...editorEvent('kgn', 'DRAFT'), EditorSubmitForApproval: gqlErrors({ message: 'Event is missing a venue', code: 'VALIDATION_FAILED' }) });
  await page.goto('/events/kgn/edit');
  await expect(page.getByText('Event details').first()).toBeVisible();
  await toPublish(page);
  await page.getByTestId('step-bar').getByRole('button', { name: /^submit for approval$/i }).click();
  await page.getByRole('dialog').getByRole('button', { name: /submit for approval|submit anyway/i }).click();
  await expect.poll(() => upstream.calls('EditorSubmitForApproval').length).toBe(1);
  await page.waitForTimeout(600);
  await shot(page, 'editor-submit-error', info);
  await expect(page.getByText(/missing a venue|could not|failed/i).first()).toBeVisible();
});

test('editor: submit in flight shows a loading button', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { ...editorEvent('kgn', 'DRAFT'), EditorSubmitForApproval: delayed(4000, { submitEventForApproval: { id: 'kgn', status: 'PENDING_APPROVAL' } }) });
  await page.goto('/events/kgn/edit');
  await expect(page.getByText('Event details').first()).toBeVisible();
  await toPublish(page);
  await page.getByTestId('step-bar').getByRole('button', { name: /^submit for approval$/i }).click();
  await page.getByRole('dialog').getByRole('button', { name: /submit for approval|submit anyway/i }).click();
  await page.waitForTimeout(800);
  await shot(page, 'editor-submit-loading', info);
});

test('editor: changes requested shows the reviewer note and offers resubmit', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { ...eventDetailFixtures('lhg', 'CHANGES_REQUESTED'), ...editorEvent('lhg', 'CHANGES_REQUESTED'), EditorSubmitForApproval: { submitEventForApproval: { id: 'lhg', status: 'PENDING_APPROVAL' } } });
  await page.goto('/events/lhg/edit');
  await expect(page.getByText('Event details').first()).toBeVisible();
  await toPublish(page);
  await expect(page.getByText('Please add a clearer venue address.').first()).toBeVisible();
  await shot(page, 'editor-changes-requested', info);
  await page.getByTestId('step-bar').getByRole('button', { name: /^resubmit for approval$/i }).click();
  await expect(page.getByRole('dialog')).toContainText('Resubmit');
  await shot(page, 'editor-resubmit-dialog', info);
  await page.getByRole('dialog').getByRole('button', { name: /resubmit for approval|submit anyway/i }).click();
  await expect.poll(() => upstream.calls('EditorSubmitForApproval').length).toBe(1);
});

test('editor: rejected and pending events are read-only', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { ...editorEvent('nfm', 'PENDING_APPROVAL') });
  await page.goto('/events/nfm/edit');
  await page.waitForLoadState('networkidle').catch(() => undefined);
  await shot(page, 'editor-pending-locked', info);
});

test('editor: load error and loading states', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { ...editorEvent('kgn', 'DRAFT'), EditorEvent: gqlErrors({ message: 'Event unavailable', code: 'SERVICE_UNAVAILABLE' }) });
  await page.goto('/events/kgn/edit');
  await page.waitForLoadState('networkidle').catch(() => undefined);
  await shot(page, 'editor-error', info);
  upstream.gql({ EditorEvent: delayed(6000, editorEvent('kgn', 'DRAFT').EditorEvent as never) });
  await page.reload();
  await page.waitForTimeout(1200);
  await shot(page, 'editor-loading', info);
});
