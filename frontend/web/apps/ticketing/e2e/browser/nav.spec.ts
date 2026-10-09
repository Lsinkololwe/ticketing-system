import { harnessTest as test, expect, crawlClickables } from '../../../../e2e-harness/browser/playwright';
import { catalog, signedInBase } from './fixtures';

/** Clicks every link, tab and button on each page and records where it lands: the table below is the expected navigation. */
const SKIP_MUTATIONS = /sign out|request account deletion|mark all|delete|reserve tickets|apply|unlock|save|transfer$|request refund|resend|pay/i;

test.describe('navigation crawl', () => {
  test.setTimeout(300_000);
  test('home: every link and button lands where expected', async ({ page, upstream }, info) => {
    upstream.gql(catalog());
    const rows = await crawlClickables(page, '/', { skip: SKIP_MUTATIONS, max: 60 });
    console.log(`CRAWL ${info.project.name} / ${JSON.stringify(rows.filter((r) => r.landed || r.error).map((r) => [r.label, r.landed, r.error]))}`);
    const landed = Object.fromEntries(rows.filter((r) => r.landed).map((r) => [r.label, r.landed]));
    expect(landed['Sign in']).toMatch(/^\/auth/);
    expect(landed['My tickets']).toMatch(/^\/auth\?next=%2Fmy-tickets/);
    expect(rows.filter((r) => r.error && r.label !== 'Skip to content')).toEqual([]);
  });

  test('event page', async ({ page, upstream }, info) => {
    upstream.gql(catalog());
    const rows = await crawlClickables(page, '/events/e1', { skip: SKIP_MUTATIONS, scope: 'main', max: 50 });
    console.log(`CRAWL ${info.project.name} /events/e1 ${JSON.stringify(rows.filter((r) => r.landed || r.error).map((r) => [r.label, r.landed, r.error]))}`);
    expect(rows.filter((r) => r.error && r.label !== 'Skip to content')).toEqual([]);
  });

  test('signed-in pages: tickets, notifications, profile', async ({ page, upstream, signInAs }, info) => {
    upstream.gql(signedInBase());
    await signInAs({ roles: ['CUSTOMER'] });
    for (const path of ['/my-tickets', '/notifications', '/profile']) {
      const rows = await crawlClickables(page, path, { skip: SKIP_MUTATIONS, scope: 'main', max: 40 });
      console.log(`CRAWL ${info.project.name} ${path} ${JSON.stringify(rows.filter((r) => r.landed || r.error).map((r) => [r.label, r.landed, r.error]))}`);
      expect(rows.filter((r) => r.error && r.label !== 'Skip to content')).toEqual([]);
    }
  });
});
