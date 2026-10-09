import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

/** The services cap a page at 100 rows and answer a larger request with a server error. */
const MAX_PAGE = 100;

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return name === '__tests__' || name === 'node_modules' ? [] : sources(path);
    return /\.(ts|tsx)$/.test(name) && !/\.test\./.test(name) ? [path] : [];
  });
}

describe('requested page sizes', () => {
  it(`never exceed the service maximum of ${MAX_PAGE}`, () => {
    const offenders: string[] = [];
    for (const file of sources(join(__dirname, '..', '..'))) {
      for (const match of readFileSync(file, 'utf8').matchAll(/\b(?:size|pageSize|limit)\s*[:=]\s*(\d{3,})/g)) {
        if (Number(match[1]) > MAX_PAGE) offenders.push(`${file}: ${match[0]}`);
      }
    }
    expect(offenders).toEqual([]);
  });
});
