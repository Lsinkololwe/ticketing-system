import { defineConfig } from 'vitest/config';
import path from 'path';

/**
 * BFF unit + integration tests.
 *
 *   npx vitest run -c libs/shared/vitest.bff.config.ts                  # unit + real Redis (testcontainers)
 *   BFF_IT_KEYCLOAK=1 npx vitest run -c libs/shared/vitest.bff.config.ts src/auth/bff/__integration__/keycloak   # + real Keycloak 26.5.2
 *
 * Set BFF_TEST_REDIS_URL to reuse an existing Redis instead of starting redis:7-alpine.
 * Integration files are `*.it.ts` so the default shared run (no Docker) never picks them up.
 */
export default defineConfig({
  root: __dirname,
  test: {
    globals: true,
    environment: 'node',
    setupFiles: ['./src/test-setup.ts'],
    include: ['src/auth/bff/**/*.{test,it}.ts'],
    globalSetup: ['./src/auth/bff/__integration__/global-setup.ts'],
    testTimeout: 30_000,
    hookTimeout: 240_000,
    fileParallelism: false,
  },
  resolve: {
    alias: {
      'server-only': path.resolve(__dirname, './src/auth/bff/__tests__/server-only-stub.ts'),
      '@pml.tickets/shared': path.resolve(__dirname, './src'),
    },
  },
});
