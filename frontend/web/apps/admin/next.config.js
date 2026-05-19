//@ts-check

const { withNx } = require('@nx/next');

const isProduction = process.env.NODE_ENV === 'production';

const strictTransportSecurity = isProduction
  ? 'max-age=63072000; includeSubDomains; preload'
  : 'max-age=0';

const scriptSrc = isProduction
  ? "script-src 'self'"
  : "script-src 'self' 'unsafe-eval' 'unsafe-inline'";

const styleSrc =
  "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://fonts.cdnfonts.com";

const fontSrc =
  "font-src 'self' data: https://fonts.gstatic.com https://fonts.googleapis.com https://fonts.cdnfonts.com";

const devConnectSrc =
  "connect-src 'self' http://localhost:* ws://localhost:* http://127.0.0.1:* ws://127.0.0.1:* https:";

let prodConnectSrcOrigin = 'https:';
if (process.env.NEXT_PUBLIC_GRAPHQL_ENDPOINT) {
  try {
    const endpointUrl = new URL(process.env.NEXT_PUBLIC_GRAPHQL_ENDPOINT);
    prodConnectSrcOrigin = `${endpointUrl.protocol}//${endpointUrl.host}`;
  } catch {
    prodConnectSrcOrigin = 'https:';
  }
}

const connectSrc = isProduction
  ? `connect-src 'self' ${prodConnectSrcOrigin} https:`
  : devConnectSrc;

// Keycloak URL for frame-src (silent SSO check uses iframe)
const keycloakUrl = process.env.NEXT_PUBLIC_KEYCLOAK_URL || 'http://localhost:8084';
const frameSrc = `frame-src 'self' ${keycloakUrl}`;

/**
 * @type {import('next').NextConfig}
 **/
const nextConfig = {
  compress: true,
  async headers() {
    const cspHeader = [
      "default-src 'self'",
      scriptSrc,
      styleSrc,
      "img-src 'self' data: https:",
      fontSrc,
      connectSrc,
      frameSrc, // Allow Keycloak iframe for silent SSO check
      "frame-ancestors 'none'",
      "base-uri 'self'",
      "form-action 'self' " + keycloakUrl, // Allow form submission to Keycloak
    ].join('; ');

    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-DNS-Prefetch-Control', value: 'on' },
          { key: 'Strict-Transport-Security', value: strictTransportSecurity },
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'X-XSS-Protection', value: '1; mode=block' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'Permissions-Policy', value: 'geolocation=(), microphone=(), camera=()' },
          { key: 'X-Permitted-Cross-Domain-Policies', value: 'none' },
          { key: 'Content-Security-Policy', value: cspHeader },
        ],
      },
    ];
  },
};

module.exports = withNx(nextConfig);
