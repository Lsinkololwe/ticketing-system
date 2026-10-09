import { defineConfig } from 'vitest/config';
import path from 'path';

/**
 * Container-backed integration tests for the admin BFF wiring (real Keycloak 26.5.2 + real Redis).
 *
 *   BFF_IT_KEYCLOAK=1 npx vitest run -c apps/admin/vitest.it.config.ts
 *
 * Needs Docker. Set BFF_TEST_REDIS_URL to reuse a Redis instead of starting redis:7-alpine.
 * Files are `*.it.ts`, so the unit run (vitest.config.ts) never picks them up.
 */
export default defineConfig({
  root: __dirname,
  test: {
    globals: true,
    environment: 'node',
    include: ['src/**/*.it.ts'],
    globalSetup: ['../../libs/shared/src/auth/bff/__integration__/global-setup.ts'],
    testTimeout: 60_000,
    hookTimeout: 240_000,
    fileParallelism: false,
  },
  resolve: {
    alias: {
      'server-only': path.resolve(__dirname, '../../libs/shared/src/auth/bff/__tests__/server-only-stub.ts'),
      '@': path.resolve(__dirname, './src'),
      '@pml.tickets/shared': path.resolve(__dirname, '../../libs/shared/src'),
    },
  },
});
