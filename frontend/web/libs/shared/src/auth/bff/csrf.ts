/**
 * CSRF for the BFF: SameSite cookie plus, for every unsafe method, `Origin` equal to the app
 * origin, `Sec-Fetch-Site: same-origin` when the browser sends it, and the custom header
 * `x-pml-csrf: 1` (cannot be added cross-origin without a CORS preflight we never grant).
 */
export const CSRF_HEADER = 'x-pml-csrf';
const SAFE = new Set(['GET', 'HEAD', 'OPTIONS']);

export type CsrfFailure = 'METHOD_UNSAFE_ORIGIN_MISSING' | 'ORIGIN_MISMATCH' | 'FETCH_SITE' | 'CSRF_HEADER';

export function isUnsafeMethod(method: string): boolean {
  return !SAFE.has(method.toUpperCase());
}

export interface CsrfOptions {
  /** Server Actions and plain form posts cannot add the custom header. */
  requireHeader?: boolean;
}

/** Returns null when the request is acceptable, otherwise the failure reason. */
export function assertSameOrigin(req: Request, appOrigin: string, opts: CsrfOptions = {}): CsrfFailure | null {
  if (!isUnsafeMethod(req.method)) return null;
  const origin = req.headers.get('origin');
  if (!origin) return 'METHOD_UNSAFE_ORIGIN_MISSING';
  if (origin !== appOrigin) return 'ORIGIN_MISMATCH';
  const site = req.headers.get('sec-fetch-site');
  if (site && site !== 'same-origin') return 'FETCH_SITE';
  if ((opts.requireHeader ?? true) && req.headers.get(CSRF_HEADER) !== '1') return 'CSRF_HEADER';
  return null;
}

/** For GET navigations that start a flow (start, stepup): refuse cross-site initiators. */
export function assertNotCrossSite(req: Request): boolean {
  const site = req.headers.get('sec-fetch-site');
  return site !== 'cross-site';
}
