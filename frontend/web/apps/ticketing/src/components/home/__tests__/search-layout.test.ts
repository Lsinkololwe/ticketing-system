import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const css = readFileSync(join(__dirname, '../../../app/buyer.css'), 'utf8');

/** The rule body for a selector, or '' when there is none. */
function ruleFor(selector: string): string {
  const start = css.indexOf(`${selector} {`);
  if (start < 0) return '';
  return css.slice(start, css.indexOf('}', start));
}

describe('the search card under the sticky header', () => {
  it('does not lift itself unless a hero is above it', () => {
    const base = ruleFor('.buyer-search');
    expect(base).toContain('margin-top: var(--m3-sp-24)');
    expect(base).not.toMatch(/margin-top:\s*calc\(.*\* *-1\)/);
  });

  it('overlaps the hero only when the home page has one', () => {
    expect(ruleFor("[data-hero='true'] > .buyer-search")).toMatch(/margin-top:\s*calc\(var\(--m3-sp-28\) \* -1\)/);
  });
});
