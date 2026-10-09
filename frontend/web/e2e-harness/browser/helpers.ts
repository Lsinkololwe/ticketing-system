import type { BrowserContext, ConsoleMessage, Page, TestInfo } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

export const VIEWPORTS = {
  desktop: { width: 1440, height: 900 },
  phone: { width: 390, height: 844 },
} as const;

/** Where screenshots go: SHOTS_DIR or <repo>/e2e-harness/shots. */
export const shotsDir = () => process.env.SHOTS_DIR ?? path.resolve(__dirname, '..', 'shots');

/** Full-page screenshot named `<project>-<name>.png` (project = desktop 1440x900 or phone 390x844). */
export async function shot(page: Page, name: string, info: TestInfo) {
  const dir = path.join(shotsDir(), info.project.metadata?.app ?? 'app');
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, `${info.project.name}-${name}.png`);
  // caret: 'initial' — the default 'hide' injects an inline style that React reports as a hydration mismatch when a shot lands mid-hydration.
  await page.screenshot({ path: file, fullPage: true, caret: 'initial' });
  return file;
}

export interface ConsoleLog {
  errors: string[];
  warnings: string[];
  pageErrors: string[];
  failedRequests: string[];
}

/**
 * Collects console errors, uncaught page errors and failed (non-2xx/3xx, aborted) same-origin
 * requests. Call before navigation. Browser noise that is not the app's (favicon) is ignored.
 */
export function captureConsole(page: Page): ConsoleLog {
  const log: ConsoleLog = { errors: [], warnings: [], pageErrors: [], failedRequests: [] };
  page.on('console', (m: ConsoleMessage) => {
    const t = m.text();
    if (m.type() === 'error') log.errors.push(`${t} @ ${m.location().url}`);
    else if (m.type() === 'warning') log.warnings.push(t);
  });
  page.on('pageerror', (e) => log.pageErrors.push(String(e)));
  page.on('requestfailed', (r) => {
    if (!r.url().includes('favicon')) log.failedRequests.push(`${r.method()} ${r.url()} ${r.failure()?.errorText ?? ''}`);
  });
  return log;
}

/** Errors worth failing a test over: console errors and uncaught exceptions, minus expected ones. */
export function realErrors(log: ConsoleLog, allow: RegExp[] = []): string[] {
  return [...log.errors, ...log.pageErrors].filter((e) => !allow.some((a) => a.test(e)));
}

function axeSource(): string {
  const root = path.resolve(__dirname, '../../node_modules');
  const direct = path.join(root, 'axe-core/axe.min.js');
  if (fs.existsSync(direct)) return direct;
  const pnpm = path.join(root, '.pnpm');
  const hit = fs.existsSync(pnpm) ? fs.readdirSync(pnpm).find((d) => d.startsWith('axe-core@')) : undefined;
  if (hit) return path.join(pnpm, hit, 'node_modules/axe-core/axe.min.js');
  throw new Error('axe-core is not installed (pnpm add -D axe-core)');
}

export interface AxeViolation {
  id: string;
  impact: string | null;
  help: string;
  nodes: string[];
  /** axe's first failure summary, e.g. the measured contrast ratio. */
  detail: string;
}

/** Runs axe-core (WCAG 2.x A/AA tags) on the current page and returns the violations. */
export async function axe(page: Page, opts: { exclude?: string[] } = {}): Promise<AxeViolation[]> {
  if (!(await page.evaluate(() => 'axe' in window))) await page.addScriptTag({ path: axeSource() });
  return page.evaluate(async (exclude) => {
    const a = (window as unknown as { axe: { run: (ctx: unknown, o: unknown) => Promise<{ violations: Array<{ id: string; impact: string | null; help: string; nodes: Array<{ target: string[]; failureSummary?: string }> }> }> } }).axe;
    const r = await a.run({ exclude: exclude.map((e) => [e]) }, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } });
    return r.violations.map((v) => ({ id: v.id, impact: v.impact, help: v.help, nodes: v.nodes.map((n) => n.target.join(' ')), detail: (v.nodes[0]?.failureSummary ?? '').replace(/\s+/g, ' ').slice(0, 300) }));
  }, opts.exclude ?? []);
}

export interface CrawlEntry {
  kind: 'link' | 'tab' | 'button';
  label: string;
  /** For links: the href. */
  href: string | null;
  /** Where the browser ended up after the click (path + search), or null when the page stayed. */
  landed: string | null;
  /** True when a dialog, menu or panel opened or the DOM changed noticeably without navigating. */
  changed: boolean;
  error?: string;
}

/**
 * Clicks every visible link, tab and button in `scope` (default: the whole page), one at a time,
 * returning where each landed. After each click the page is sent back to `startUrl`. Buttons that
 * submit data are skipped by `skip` (matched on accessible name) so the crawl never mutates.
 * It records, it does not judge: the spec asserts on the table.
 */
export async function crawlClickables(page: Page, startUrl: string, opts: { scope?: string; skip?: RegExp; max?: number } = {}): Promise<CrawlEntry[]> {
  const scope = () => (opts.scope ? page.locator(opts.scope) : page.locator('body'));
  const sel = 'a[href], [role=tab], button, [role=button], [role=link]';
  await page.goto(startUrl);
  await page.waitForLoadState('networkidle').catch(() => undefined);
  const count = Math.min(await scope().locator(sel).count(), opts.max ?? 80);
  const out: CrawlEntry[] = [];
  for (let i = 0; i < count; i++) {
    const el = scope().locator(sel).nth(i);
    if (!(await el.isVisible().catch(() => false))) continue;
    const info = await el.evaluate((n) => ({
      tag: n.tagName.toLowerCase(),
      role: n.getAttribute('role'),
      label: (n.getAttribute('aria-label') || (n as HTMLElement).innerText || n.getAttribute('title') || '').trim().replace(/\s+/g, ' ').slice(0, 80),
      href: n.getAttribute('href'),
      disabled: (n as HTMLButtonElement).disabled || n.getAttribute('aria-disabled') === 'true',
    }));
    if (info.disabled || (opts.skip && opts.skip.test(info.label))) continue;
    const kind: CrawlEntry['kind'] = info.role === 'tab' ? 'tab' : info.tag === 'a' || info.role === 'link' ? 'link' : 'button';
    if (kind === 'link' && info.href && /^(mailto:|tel:|https?:\/\/(?!localhost))/.test(info.href)) {
      out.push({ kind, label: info.label, href: info.href, landed: null, changed: false });
      continue;
    }
    const before = page.url();
    const domBefore = await page.evaluate(() => document.body.innerHTML.length);
    const dialogsBefore = await page.locator('[role=dialog], [role=menu], [role=listbox]').count();
    try {
      await el.click({ timeout: 5000 });
      // A guarded or freshly compiled route can take a moment to redirect: wait for the URL to move, briefly.
      await page.waitForURL((u) => u.toString() !== before, { timeout: kind === 'link' ? 4000 : 700 }).catch(() => undefined);
      await page.waitForTimeout(250);
      await page.waitForLoadState('domcontentloaded').catch(() => undefined);
      const after = page.url();
      const dialogsAfter = await page.locator('[role=dialog], [role=menu], [role=listbox]').count();
      const domAfter = await page.evaluate(() => document.body.innerHTML.length);
      const u = new URL(after);
      out.push({
        kind,
        label: info.label,
        href: info.href,
        landed: after !== before ? u.pathname + u.search + u.hash : null,
        changed: dialogsAfter !== dialogsBefore || Math.abs(domAfter - domBefore) > 40,
      });
    } catch (e) {
      out.push({ kind, label: info.label, href: info.href, landed: null, changed: false, error: String(e).split('\n')[0] });
    }
    if (page.url() !== startUrl) {
      await page.goto(startUrl);
      await page.waitForLoadState('networkidle').catch(() => undefined);
    } else {
      await page.keyboard.press('Escape').catch(() => undefined);
    }
  }
  return out;
}

/** Adds a seeded session cookie to a context. */
export async function signIn(context: BrowserContext, cookie: { name: string; value: string; url: string }) {
  await context.addCookies([{ name: cookie.name, value: cookie.value, url: cookie.url, httpOnly: true, sameSite: 'Lax', secure: cookie.url.startsWith('https:') }]);
}

export interface Measured {
  w: number;
  h: number;
  fontSize: string;
  fontWeight: string;
  padding: string;
  radius: string;
  background: string;
  color: string;
}

/** Computed box and type metrics of the first match of each selector (null when absent): for comparing against a reference page. */
export async function measure(page: Page, selectors: string[]): Promise<Record<string, Measured | null>> {
  return page.evaluate((sels) => {
    const out: Record<string, unknown> = {};
    for (const s of sels) {
      const e = document.querySelector(s);
      if (!e) {
        out[s] = null;
        continue;
      }
      const c = getComputedStyle(e);
      const r = e.getBoundingClientRect();
      out[s] = { w: Math.round(r.width), h: Math.round(r.height), fontSize: c.fontSize, fontWeight: c.fontWeight, padding: c.padding, radius: c.borderRadius, background: c.backgroundColor, color: c.color };
    }
    return out as Record<string, Measured | null>;
  }, selectors);
}
