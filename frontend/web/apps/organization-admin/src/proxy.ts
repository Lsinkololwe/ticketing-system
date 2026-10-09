import { bff } from '@/lib/bff';

/** Coarse gate (session exists + role) plus CSP nonce and security headers. Pages and actions re-check via requireSession. */
export const proxy = bff.proxy;

export const config = {
  matcher: [
    {
      source: '/((?!api|_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)',
      missing: [
        { type: 'header', key: 'next-router-prefetch' },
        { type: 'header', key: 'purpose', value: 'prefetch' },
      ],
    },
  ],
};
