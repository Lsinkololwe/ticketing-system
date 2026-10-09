/**
 * Open-redirect-safe return path. Only same-origin relative paths survive; everything else
 * becomes `fallback`. Used for `next`, `callbackUrl` and `returnTo` everywhere.
 */
export function safeReturnTo(value: string | null | undefined, fallback = '/'): string {
  if (!value) return fallback;
  let v = value;
  // Reject control chars and backslashes outright, before and after one round of decoding.
  const bad = (s: string) => /[\u0000-\u001f\u007f\\]/.test(s);
  if (bad(v)) return fallback;
  if (!v.startsWith('/') || v.startsWith('//')) return fallback;
  let decoded: string;
  try {
    decoded = decodeURIComponent(v);
  } catch {
    return fallback;
  }
  if (bad(decoded) || decoded.startsWith('//')) return fallback;
  if (/^\/api(\/|$)/i.test(decoded)) return fallback;
  try {
    const u = new URL(v, 'http://safe.invalid');
    if (u.origin !== 'http://safe.invalid') return fallback;
    v = u.pathname + u.search + u.hash;
  } catch {
    return fallback;
  }
  if (v.startsWith('//')) return fallback;
  return v;
}
