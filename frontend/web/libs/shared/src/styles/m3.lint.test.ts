import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

const SRC = path.resolve(__dirname, '..');

function walk(dir: string): string[] {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) return e.name === 'node_modules' || e.name === '__tests__' ? [] : walk(p);
    return [p];
  });
}
const stripComments = (s: string) => s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1');

const RAW_PX = /(?<![\w.-])-?\d*\.?\d+px\b/;
const RAW_HEX = /#[0-9a-fA-F]{3,8}\b/;

const designFiles = walk(SRC).filter(
  (f) =>
    /\/(components\/m3|layouts)\/[^/]+\.tsx?$/.test(f) && !/\.test\.tsx?$/.test(f)
);
const cssFiles = ['m3.components.css', 'm3.extra.css'].map((f) => path.join(SRC, 'styles', f));

describe('token lint: no raw px or hex outside the token files', () => {
  it('finds the files it is supposed to lint', () => {
    expect(designFiles.length).toBeGreaterThan(10);
  });
  it.each(designFiles.map((f) => [path.relative(SRC, f), f]))('%s has no raw px/hex', (_n, f) => {
    const code = stripComments(fs.readFileSync(f, 'utf8'));
    expect(code.match(RAW_PX)?.[0]).toBeUndefined();
    expect(code.match(RAW_HEX)?.[0]).toBeUndefined();
  });
  it.each(cssFiles.map((f) => [path.basename(f), f]))('%s uses var(--m3-*) only (media queries excepted)', (_n, f) => {
    const css = stripComments(fs.readFileSync(f, 'utf8'))
      .split('\n')
      .filter((l) => !l.trim().startsWith('@media'))
      .join('\n');
    expect(css.match(RAW_PX)?.[0]).toBeUndefined();
    expect(css.match(RAW_HEX)?.[0]).toBeUndefined();
  });
  it('does not import removed visual libraries', () => {
    const all = walk(SRC).filter((f) => /\.(tsx?|css)$/.test(f) && !/\.test\./.test(f));
    for (const f of all) {
      const s = fs.readFileSync(f, 'utf8');
      expect(s, f).not.toMatch(/@radix-ui|material-tailwind|design-tokens\.css/);
    }
  });
});
