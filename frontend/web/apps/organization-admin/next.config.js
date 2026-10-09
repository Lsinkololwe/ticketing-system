//@ts-check

const { withNx } = require('@nx/next');

/**
 * Security headers and the CSP nonce are set per request by `src/proxy.ts` (shared BFF helper);
 * nothing is hand-written here.
 *
 * @type {import('next').NextConfig}
 **/
const nextConfig = {
  compress: true,
  experimental: {
    serverActions: {
      allowedOrigins: [process.env.APP_URL ? new URL(process.env.APP_URL).host : 'localhost:3003'],
    },
  },
};

module.exports = withNx(nextConfig);
