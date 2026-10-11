//@ts-check

const { withNx } = require("@nx/next");

// Security headers and the nonce CSP are set per request by the shared BFF proxy (src/proxy.ts).
const publicOrigin = process.env.APP_URL
  ? new URL(process.env.APP_URL).host
  : "localhost:3001";

/**
 * @type {import('next').NextConfig}
 **/
const nextConfig = {
  distDir: process.env.NEXT_DIST_DIR || ".next",
  experimental: { serverActions: { allowedOrigins: [publicOrigin] } },
};

module.exports = withNx(nextConfig);
