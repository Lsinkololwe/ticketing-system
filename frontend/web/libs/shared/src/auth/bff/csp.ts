/**
 * Content-Security-Policy and the other security headers. Pure and dependency-free so it runs
 * in the proxy and is unit-tested.
 */
export interface CspOptions {
  isDev?: boolean;
  /** Radix/MUI inline styles. Drop once the design system uses no inline style attributes. */
  styleUnsafeInline?: boolean;
  styleSrc?: string[];
  fontSrc?: string[];
  imgSrc?: string[];
  connectSrc?: string[];
  formAction?: string[];
}

export function buildCsp(nonce: string, opts: CspOptions = {}): string {
  const dev = opts.isDev ?? false;
  const list = (base: string[], extra?: string[]) => [...base, ...(extra ?? [])].join(' ');
  const directives = [
    `default-src 'self'`,
    `base-uri 'self'`,
    `object-src 'none'`,
    `frame-ancestors 'none'`,
    `frame-src 'none'`,
    `form-action ${list([`'self'`], opts.formAction)}`,
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${dev ? " 'unsafe-eval'" : ''}`,
    `style-src ${list([`'self'`, ...(opts.styleUnsafeInline === false ? [`'nonce-${nonce}'`] : [`'unsafe-inline'`])], opts.styleSrc)}`,
    `font-src ${list([`'self'`], opts.fontSrc)}`,
    // In development the media service serves pictures over plain http on another localhost port.
    `img-src ${list([`'self'`, 'data:', 'blob:', ...(dev ? ['http://localhost:*'] : [])], opts.imgSrc ?? ['https:'])}`,
    `connect-src ${list([`'self'`, ...(dev ? ['ws://localhost:*', 'http://localhost:*'] : [])], opts.connectSrc)}`,
    `manifest-src 'self'`,
  ];
  if (!dev) directives.push('upgrade-insecure-requests');
  return directives.join('; ');
}

export function newNonce(): string {
  return btoa(crypto.randomUUID());
}

export function securityHeaders(opts: { isDev?: boolean; noStore?: boolean } = {}): Record<string, string> {
  const h: Record<string, string> = {
    'x-content-type-options': 'nosniff',
    'referrer-policy': 'strict-origin-when-cross-origin',
    'permissions-policy': 'camera=(), microphone=(), geolocation=(), payment=(self)',
    'cross-origin-opener-policy': 'same-origin',
  };
  if (!opts.isDev) h['strict-transport-security'] = 'max-age=63072000; includeSubDomains';
  if (opts.noStore) h['cache-control'] = 'no-store';
  return h;
}
