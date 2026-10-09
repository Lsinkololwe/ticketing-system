import { harnessTest as test, expect, captureConsole, realErrors, axe } from '../../../../e2e-harness/browser/playwright';
import type { Page } from '@playwright/test';
import { as, baseFixtures, dashboardFixtures, eventsListFixtures, eventDetailFixtures, financeFixtures, teamFixtures, settingsFixtures, mediaFixtures, checkinFixtures, editorEvent, editorRef, notificationsFixture } from './fixtures';
import { shot } from './_kit';

type Step = (page: Page) => Promise<void>;
interface Case { name: string; path: string; fx: () => Record<string, any>; wait: string | RegExp; steps?: Array<[string, Step]> }
const click = (role: 'tab' | 'button' | 'link', name: RegExp | string): Step => async (p) => {
  await p.getByRole(role, { name }).first().click({ timeout: 5000 });
  await p.waitForTimeout(500);
};

const CASES: Case[] = [
  { name: 'finance', path: '/finance', fx: () => financeFixtures(), wait: 'PO-2051', steps: [['payout-sheet', click('button', /view|details|open/i)], ] },
  { name: 'finance-banks', path: '/finance/bank-accounts', fx: () => financeFixtures(), wait: 'Zanaco', steps: [['add-bank', click('button', /add account/i)]] },
  { name: 'finance-tx', path: '/finance/transactions', fx: () => financeFixtures(), wait: /BK-2026-00003462|Payout PO-2053/ },
  { name: 'team-members', path: '/team', fx: () => teamFixtures(), wait: 'Chanda Mwape', steps: [['invites', click('tab', /invitations/i)], ['access', click('tab', /event access/i)], ['owner', click('tab', /ownership/i)], ['roles', click('tab', /roles/i)]] },
  { name: 'settings', path: '/settings', fx: () => settingsFixtures(), wait: /Organization profile/i, steps: [['org', click('tab', /organization settings/i)], ['notif', click('tab', /notifications/i)], ['platform', click('tab', /platform rules/i)], ['me', click('tab', /my profile/i)], ['danger', click('tab', /delete organization/i)]] },
  { name: 'media', path: '/media', fx: () => mediaFixtures(), wait: /poster/i },
  { name: 'notifications', path: '/notifications', fx: () => notificationsFixture(3, 2), wait: /Approval/ },
  { name: 'editor-new', path: '/events/new', fx: () => editorRef(), wait: /Basics/i },
  { name: 'editor-draft', path: '/events/kgn/edit', fx: () => editorEvent('kgn', 'DRAFT'), wait: /Basics/i, steps: [['when', click('tab', /date/i)], ['venue', click('tab', /venue/i)], ['tiers', click('tab', /ticket tiers/i)], ['policy', click('tab', /polic/i)], ['publish', click('tab', /publish/i)]] },
  { name: 'editor-changes', path: '/events/lhg/edit', fx: () => editorEvent('lhg', 'CHANGES_REQUESTED'), wait: /Basics/i },
  { name: 'checkin', path: '/events/ev1/check-in', fx: () => checkinFixtures(), wait: /gate|check-in/i, steps: [['attendees', click('tab', /attendees/i)]] },
];

for (const c of CASES) {
  test(`page ${c.name}`, async ({ page, upstream, signInAs }, info) => {
    upstream.gql({ ...baseFixtures(), ...dashboardFixtures(), ...eventsListFixtures(), ...eventDetailFixtures('ev1'), ...c.fx() });
    await signInAs(as('OWNER'));
    const log = captureConsole(page);
    await page.goto(c.path);
    await expect(page.getByText(c.wait).first()).toBeVisible({ timeout: 30_000 });
    await page.waitForLoadState('networkidle').catch(() => undefined);
    await shot(page, c.name, info);
    for (const [n, step] of c.steps ?? []) {
      try {
        await step(page);
        await shot(page, `${c.name}-${n}`, info);
      } catch (e) {
        console.log(`STEPFAIL ${c.name}/${n}: ${String(e).slice(0, 120)}`);
      }
    }
    const bad = (await axe(page)).filter((v) => v.impact === 'serious' || v.impact === 'critical').map((v) => `${v.id}: ${v.nodes.slice(0, 3).join(' | ')} :: ${v.detail}`);
    console.log(`DIAG ${c.name} unhandled`, JSON.stringify([...new Set(upstream.unhandled)]), 'missing', JSON.stringify([...new Set(upstream.missing)].filter((m) => !/imageUrl|logoUrl|bannerUrl|myOwnedOrganization/.test(m)).slice(0, 10)), 'errors', JSON.stringify(realErrors(log, [/Content Security Policy/]).slice(0, 3)).slice(0, 400), 'axe', JSON.stringify(bad));
  });
}
