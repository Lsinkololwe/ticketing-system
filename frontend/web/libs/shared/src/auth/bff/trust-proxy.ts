// Kept free of server-only imports so app configuration modules and their unit tests can use it.

/**
 * Reads TRUST_PROXY_HOPS. Unset or blank gives `undefined` (the BFF then defaults to 0 outside production and
 * refuses to start in production); anything that is not a small non-negative whole number is an error.
 */
export function parseTrustProxyHops(raw: string | undefined): number | undefined {
  const text = raw?.trim();
  if (!text) return undefined;
  const hops = Number(text);
  if (!Number.isInteger(hops) || hops < 0 || hops > 10) {
    throw new Error('BFF: TRUST_PROXY_HOPS must be a whole number from 0 to 10');
  }
  return hops;
}
