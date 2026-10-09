import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { m3Breakpoints } from './m3.tokens';

const read = (f: string) => fs.readFileSync(path.join(__dirname, f), 'utf8');
const tokensCss = read('m3.tokens.css');
const themesCss = read('m3.themes.css');
const componentsCss = read('m3.components.css');

function declarations(css: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const m of css.matchAll(/(--m3-[a-z0-9-]+)\s*:\s*([^;]+);/g)) out[m[1]] = m[2].trim();
  return out;
}
/** The block of themes.css for one selector (first level only). */
function block(css: string, selector: string): string {
  const start = css.indexOf(`${selector} {`);
  if (start < 0) throw new Error(`no block ${selector}`);
  return css.slice(start, css.indexOf('\n}', start));
}

const base = declarations(tokensCss);
const buyer = { ...base, ...declarations(block(themesCss, "[data-app='buyer']")) };

/**
 * Measurements transcribed from the prototype CSS (organizer.html / admin.html
 * "medium density" cascade, storefront index.html for the buyer).
 */
describe('medium density (organizer + platform admin) measurements', () => {
  it.each([
    ['--m3-btn-h', '34px'],
    ['--m3-btn-h-sm', '28px'],
    ['--m3-btn-pad-x', '14px'],
    ['--m3-pill-h', '20px'],
    ['--m3-field-h', '40px'],
    ['--m3-topbar-min-h', '60px'],
    ['--m3-rail-w', '76px'],
    ['--m3-fab', '44px'],
    ['--m3-drawer-w', '236px'],
    ['--m3-card-radius', '12px'],
    ['--m3-card-pad', '16px'],
    ['--m3-body-size', '14px'],
    ['--m3-dialog-w', '560px'],
    ['--m3-sheet-w', '560px'],
    ['--m3-tab-h', '40px'],
    ['--m3-switch-w', '44px'],
    ['--m3-switch-h', '24px'],
    ['--m3-seg-h', '32px'],
    ['--m3-chip-h', '28px'],
    ['--m3-pager-btn', '30px'],
    ['--m3-work-max-w', '1280px'],
    ['--m3-bottomnav-h', '76px'],
  ])('%s = %s', (name, value) => {
    expect(base[name]).toBe(value);
  });
});

describe('buyer storefront measurements', () => {
  it.each([
    ['--m3-btn-h', '48px'],
    ['--m3-btn-h-sm', '39px'], // measured on the prototype's .btn.sm (8px 14px, 14px type) in a ticket row
    ['--m3-btn-pad-x-sm', '14px'],
    ['--m3-btn-font-size-sm', '14px'],
    ['--m3-field-h', '48px'],
    ['--m3-chip-h', '36px'],
    ['--m3-icon-btn', '40px'],
    ['--m3-body-size', '15px'],
  ])('%s = %s', (name, value) => {
    expect(buyer[name]).toBe(value);
  });
});

describe('components consume tokens', () => {
  it.each([
    ['.m3-btn', 'height', 'var(--m3-btn-h)'],
    ['.m3-btn', 'padding', '0 var(--m3-btn-pad-x)'],
    ['.m3-btn', 'border-radius', 'var(--m3-btn-radius)'],
  ])('%s %s uses a token', (selector, prop, expected) => {
    const m = componentsCss.match(new RegExp(`^${selector.replace('.', '\\.')} \\{([^}]*)\\}`, 'm'));
    expect(m, selector).not.toBeNull();
    expect(m![1]).toContain(`${prop}: ${expected}`);
  });
});

describe('breakpoints agree with the stylesheet', () => {
  it('every TS breakpoint appears as a media query', () => {
    for (const px of Object.values(m3Breakpoints)) {
      expect(componentsCss, String(px)).toMatch(new RegExp(`max-width: ${px}px`));
    }
  });
});

describe('themes', () => {
  it('chart and heat ramps have five steps in both schemes', () => {
    for (const k of ['chart', 'heat']) {
      const hits = themesCss.match(new RegExp(`--m3-${k}-[1-5]:`, 'g')) ?? [];
      expect(hits.length).toBeGreaterThanOrEqual(10);
    }
  });
});
