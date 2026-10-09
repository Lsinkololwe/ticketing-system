import { harnessTest as test } from '../../../../e2e-harness/browser/playwright';
import { mockAll } from './mock-upstream';

/**
 * Prints computed geometry/type metrics of selectors for the prototype comparison (not an assertion
 * suite). MEASURE="route|role|sel1;;sel2;;..." e.g. MEASURE="/dashboard|SUPER_ADMIN|.m3-kpi;;.m3-kpi__label".
 */
test('measure', async ({ page, upstream, signInAs }) => {
  const spec = process.env.MEASURE;
  test.skip(!spec, 'MEASURE not set');
  const [route, role, sels] = spec!.split('|');
  mockAll(upstream);
  await signInAs({ roles: [role], displayName: 'Natasha Mulenga', accountId: 'staff-1' });
  await page.goto(route);
  await page.waitForLoadState('networkidle').catch(() => undefined);
  await page.waitForTimeout(500);
  const out = await page.evaluate((list) => list.map((s) => {
    const e = document.querySelector(s);
    if (!e) return [s, null];
    const c = getComputedStyle(e);
    const r = e.getBoundingClientRect();
    return [s, `${Math.round(r.x)},${Math.round(r.y)} ${Math.round(r.width)}x${Math.round(r.height)} fs${c.fontSize}/${c.lineHeight} fw${c.fontWeight} pad${c.padding} rad${c.borderRadius} bg${c.backgroundColor} fg${c.color} bd${c.border.slice(0, 40)} mg${c.margin}`];
  }), sels.split(';;'));
  for (const [s, v] of out) console.log('M', s, '=>', v);
});
