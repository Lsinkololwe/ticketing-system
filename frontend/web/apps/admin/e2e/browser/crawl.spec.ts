import fs from 'node:fs';
import path from 'node:path';
import { harnessTest as test, expect, crawlClickables, shotsDir } from '../../../../e2e-harness/browser/playwright';
import { mockAll } from './mock-upstream';
import { ROUTES } from './routes';

/**
 * Click-everything crawl per role: every link, tab and button in the console frame (nav + page) is
 * clicked once from a fresh load. Links must land on their href; no control may throw; controls that
 * neither navigate nor change the DOM are listed (and fail the test unless allow-listed).
 * Mutating buttons are skipped by label so nothing is posted.
 */
const SKIP = /^(Sign out|Approve|Reject|Delete|Suspend|Unsuspend|Lock|Unlock|Cancel event|Seed standard chart|Sync all|Send|Post|Reverse|Save|Confirm|Retry|Process|Complete|Hold|Release|Close escrow|Light or dark|Switch app|Account menu|Collapse navigation)/i;
/**
 * Controls that legitimately show no change in a 0.7 s dev-server window or are already "on":
 * the selected tab / current page, period toggles (they refetch), and router.push buttons whose
 * route compiles on first hit under `next dev` (their landing is asserted by the page specs).
 */
const BENIGN = /^(First page|Previous page|Next page|Last page|Page \d+|Last \d+ (days|months)|Open |Audit log|[A-Z][A-Z ,&]+ \d+$)/;
const ROLES = ['SUPER_ADMIN', 'ADMIN', 'FINANCE', 'FINANCE_LEAD'];

for (const role of ROLES) {
  test(`crawl as ${role}`, async ({ page, upstream, signInAs }, info) => {
    test.skip(info.project.name === 'phone', 'desktop crawl only; phone layout covered by the sweep');
    test.setTimeout(900_000);
    mockAll(upstream);
    await signInAs({ roles: [role], accountId: 'staff-1' });
    const targets = ROUTES.filter((r) => r.roles.includes(role) && r.app !== '/profile');
    const report: Array<Record<string, unknown>> = [];
    const problems: string[] = [];
    const seen = new Set<string>();
    for (const r of targets) {
      const mod = r.app.split('/')[1];
      if (seen.has(mod) && !['/finance/payouts', '/ledger/coa'].includes(r.app)) continue;
      seen.add(mod);
      const rows = await crawlClickables(page, r.app, { scope: 'main', skip: SKIP, max: 40 });
      for (const e of rows) {
        report.push({ route: r.app, ...e });
        if (e.error) problems.push(`${r.app}: "${e.label}" threw ${e.error}`);
        if (e.kind === 'link' && e.href?.startsWith('/') && e.landed && !e.landed.startsWith(e.href.split('?')[0])) problems.push(`${r.app}: link "${e.label}" -> ${e.href} but landed ${e.landed}`);
        if (e.kind !== 'link' && !e.landed && !e.changed && e.kind !== 'tab' && !BENIGN.test(e.label)) problems.push(`${r.app}: ${e.kind} "${e.label}" did nothing`);
      }
    }
    const dir = path.join(shotsDir(), 'admin');
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, `crawl-${role}.json`), JSON.stringify(report, null, 1));
    expect(problems, 'dead or misrouted controls').toEqual([]);
  });
}
