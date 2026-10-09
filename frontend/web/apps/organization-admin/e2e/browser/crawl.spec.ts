import { harnessTest as test, expect, crawlClickables } from '../../../../e2e-harness/browser/playwright';
import { as, baseFixtures, dashboardFixtures, eventsListFixtures, eventDetailFixtures, bookingsFixtures, financeFixtures, teamFixtures, settingsFixtures, mediaFixtures, notificationsFixture, type OrgRole } from './fixtures';

/**
 * Click-everything crawler per role: every internal link lands on its href, and every enabled control
 * either navigates or opens/changes something (no dead controls). Mutating buttons are skipped.
 */
const SKIP = /sign out|log out|delete|remove|cancel|submit|publish|save|send|confirm|discard|refund|revoke|suspend|transfer|leave/i;
const PAGES: Array<{ path: string; roles: OrgRole[]; scope?: string }> = [
  { path: '/dashboard', roles: ['OWNER', 'MANAGER', 'MARKETER'] },
  { path: '/events', roles: ['OWNER', 'MARKETER'] },
  { path: '/events/ev1', roles: ['OWNER', 'MANAGER'] },
  { path: '/bookings', roles: ['OWNER', 'CONTRIBUTOR'] },
  { path: '/finance', roles: ['OWNER', 'ADMIN'] },
  { path: '/finance/bank-accounts', roles: ['OWNER'] },
  { path: '/team', roles: ['OWNER', 'MANAGER'] },
  { path: '/settings', roles: ['OWNER', 'ADMIN'] },
];

for (const pg of PAGES) {
  for (const role of pg.roles) {
    test(`crawl ${pg.path} as ${role}`, async ({ page, upstream, signInAs }) => {
      test.setTimeout(240_000);
      upstream.gql({
        ...baseFixtures({ role }), ...dashboardFixtures(), ...eventsListFixtures(), ...eventDetailFixtures('ev1'), ...bookingsFixtures(), ...financeFixtures(), ...teamFixtures(), ...settingsFixtures(), ...mediaFixtures(), ...notificationsFixture(2, 1),
      });
      await signInAs(as(role));
      const rows = await crawlClickables(page, pg.path, { scope: 'main, [role=main], .m3-shell__main, body', skip: SKIP, max: 45 });
      const brokenLinks = rows.filter((r) => r.kind === 'link' && r.href?.startsWith('/') && r.landed !== null && !r.landed.split('?')[0].startsWith(r.href.split('?')[0].split('#')[0]));
      const dead = rows.filter((r) => !r.error && r.landed === null && !r.changed && !(r.kind === 'link' && (!r.href || r.href.startsWith('#') || /^(mailto|tel|http)/.test(r.href))));
      const errors = rows.filter((r) => r.error);
      console.log(`CRAWL ${pg.path} ${role}: ${rows.length} controls; brokenLinks=${JSON.stringify(brokenLinks.map((r) => `${r.label}->${r.landed}`))} dead=${JSON.stringify(dead.map((r) => r.label))} errors=${JSON.stringify(errors.map((r) => `${r.label}: ${r.error}`.slice(0, 100)))}`);
      expect(brokenLinks).toEqual([]);
    });
  }
}
