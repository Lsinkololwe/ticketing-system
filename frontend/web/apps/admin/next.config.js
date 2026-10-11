//@ts-check

const { withNx } = require('@nx/next');

// Public origin of this app. Server Actions compare Origin with Host; behind a reverse proxy the
// public host must be listed. proxy.ts (shared BFF) sets the CSP nonce and every security header.
let publicHost;
try {
  publicHost = process.env.APP_URL ? new URL(process.env.APP_URL).host : undefined;
} catch {
  publicHost = undefined;
}

/**
 * @type {import('next').NextConfig}
 **/
const nextConfig = {
  distDir: process.env.NEXT_DIST_DIR || '.next',
  compress: true,
  poweredByHeader: false,
  experimental: {
    serverActions: { allowedOrigins: publicHost ? [publicHost] : [] },
  },
};

module.exports = withNx(nextConfig);
