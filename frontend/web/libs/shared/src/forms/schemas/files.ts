import { z } from 'zod';

export interface FilesOptions {
  min?: number;
  max?: number;
  maxBytes?: number;
  /** Allowed MIME types, e.g. ['image/png', 'application/pdf']. */
  types?: string[];
}

/** `File[]` (what `FileRHF` stores) with count, size and type limits. */
export function files(opts: FilesOptions = {}) {
  const { min = 1, max, maxBytes, types } = opts;
  return z
    .array(z.custom<File>((v) => typeof File !== 'undefined' && v instanceof File), { error: 'Choose a file' })
    .min(min, min === 1 ? 'Choose a file' : `Choose at least ${min} files`)
    .refine((a) => !max || a.length <= max, { error: `Choose at most ${max} files` })
    .refine((a) => !maxBytes || a.every((f) => f.size <= maxBytes), {
      error: `Each file must be ${Math.round((maxBytes ?? 0) / 1024 / 1024)} MB or smaller`,
    })
    .refine((a) => !types || a.every((f) => types.includes(f.type)), { error: 'That file type is not supported' });
}
