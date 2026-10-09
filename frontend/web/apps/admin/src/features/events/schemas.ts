import { z } from 'zod';

const lc = (s: string) => s.trim().toLowerCase();

interface Named {
  id: string;
  name: string;
  code?: string | null;
}

function unique<T extends Named>(rows: T[], selfId: string | undefined, keys: Array<'name' | 'code'>) {
  return (v: { name: string; code?: string }, ctx: z.RefinementCtx) => {
    for (const k of keys) {
      const val = k === 'name' ? v.name : v.code;
      if (val && rows.some((r) => r.id !== selfId && lc(String(r[k] ?? '')) === lc(val))) {
        ctx.addIssue({ code: 'custom', path: [k], message: 'Already exists' });
      }
    }
  };
}

const name = (label: string) => z.string().trim().min(1, `${label} is required`);

export const categorySchema = (rows: Named[], selfId?: string) =>
  z
    .object({
      name: name('Name'),
      code: z.string().trim().regex(/^[A-Z][A-Z_]{1,19}$/, 'Use capital letters and underscores, 2 to 20 characters'),
      description: z.string().trim().default(''),
      active: z.boolean().default(true),
    })
    .superRefine(unique(rows, selfId, ['name', 'code']));

export const provinceSchema = (rows: Named[], selfId?: string) =>
  z
    .object({
      name: name('Name'),
      code: z.string().trim().regex(/^[A-Z]{2,6}$/, '2 to 6 capital letters'),
      active: z.boolean().default(true),
    })
    .superRefine(unique(rows, selfId, ['name', 'code']));

export const citySchema = (rows: Named[], selfId?: string) =>
  z
    .object({
      name: name('City'),
      code: z.string().trim().regex(/^[A-Z]{2,6}$/, '2 to 6 capital letters'),
      provinceId: z.string().min(1, 'Choose a province'),
      active: z.boolean().default(true),
    })
    .superRefine(unique(rows, selfId, ['name']));

export const cancelSchema = z.object({
  why: z.string().default(''),
  details: z.string().trim().min(5, 'Give a short reason (at least 5 characters)'),
});
