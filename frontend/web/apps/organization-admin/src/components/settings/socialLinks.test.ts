import { describe, expect, it } from 'vitest';
import { orgProfileSchema } from './schemas';

const base = { name: 'Zambezi Live', slug: 'zambezi-live' } as never;
const parse = (extra: Record<string, string>) => orgProfileSchema.safeParse({ ...(base as object), ...extra });

describe('social links', () => {
  it.each(['@zambezilive', 'facebook.com/zambezilive', 'https://x.com/zambezi', ''])('accepts %j', (v) => {
    const r = parse({ instagram: v });
    expect(r.success || !r.error.issues.some((i) => i.path[0] === 'instagram')).toBe(true);
  });
  it('rejects text that is neither a handle nor an address', () => {
    const r = parse({ instagram: 'not a link' });
    expect(!r.success && r.error.issues.some((i) => i.path[0] === 'instagram')).toBe(true);
  });
});
