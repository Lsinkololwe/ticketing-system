//@ts-check

const { withNx } = require('@nx/next');

/**
 * Security headers (OWASP hardening).
 *
 * The customer app is a client-side OIDC SPA: the Keycloak access/refresh tokens
 * live in browser JS, so a strict Content-Security-Policy is the main lever that
 * shrinks the XSS -> token-theft blast radius. `connect-src` must allow the API
 * Gateway and Keycloak; `img-src` allows arbitrary https for organizer-uploaded
 * event artwork; Google Fonts (Space Grotesk / Fira Code) need font/style hosts.
 *
 * Radix Themes injects inline styles, so `style-src` needs 'unsafe-inline'.
 * Next.js needs 'unsafe-eval' for scripts in dev (and the webpack runtime).
 */
const GRAPHQL_ORIGIN = new URL(
  process.env.NEXT_PUBLIC_GRAPHQL_ENDPOINT || 'http://localhost:8080/graphql'
).origin;
const KEYCLOAK_ORIGIN = new URL(
  process.env.NEXT_PUBLIC_KEYCLOAK_URL || 'http://localhost:8084'
).origin;

const isDev = process.env.NODE_ENV !== 'production';

const contentSecurityPolicy = [
  `default-src 'self'`,
  `base-uri 'self'`,
  `object-src 'none'`,
  `frame-ancestors 'none'`,
  `form-action 'self' ${KEYCLOAK_ORIGIN}`,
  // Next.js runtime + (dev) eval. Inline needed for the Next bootstrap script.
  `script-src 'self' 'unsafe-inline'${isDev ? " 'unsafe-eval'" : ''}`,
  // Radix Themes injects inline styles; Google Fonts stylesheet.
  `style-src 'self' 'unsafe-inline' https://fonts.googleapis.com`,
  `font-src 'self' https://fonts.gstatic.com`,
  // Event artwork is organizer-uploaded from arbitrary https origins / CDNs.
  `img-src 'self' data: blob: https:`,
  // GraphQL gateway + Keycloak (token, JWKS, userinfo); ws for dev HMR.
  `connect-src 'self' ${GRAPHQL_ORIGIN} ${KEYCLOAK_ORIGIN}${isDev ? ' ws: http://localhost:*' : ''}`,
  `manifest-src 'self'`,
  `upgrade-insecure-requests`,
].join('; ');

const securityHeaders = [
  { key: 'Content-Security-Policy', value: contentSecurityPolicy },
  { key: 'X-Frame-Options', value: 'DENY' },
  { key: 'X-Content-Type-Options', value: 'nosniff' },
  { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
  { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=()' },
  {
    key: 'Strict-Transport-Security',
    value: 'max-age=63072000; includeSubDomains; preload',
  },
];

/**
 * @type {import('next').NextConfig}
 **/
const nextConfig = {
  async headers() {
    return [
      {
        source: '/:path*',
        headers: securityHeaders,
      },
    ];
  },
};

module.exports = withNx(nextConfig);
