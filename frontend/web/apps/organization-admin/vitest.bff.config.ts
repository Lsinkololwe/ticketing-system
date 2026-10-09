import { defineConfig } from 'vitest/config';
import path from 'path';

/**
 * Organizer BFF integration (real Redis + real Keycloak 26.5.2, testcontainers):
 *   BFF_IT_KEYCLOAK=1 npx vitest run -c apps/organization-admin/vitest.bff.config.ts
 * Set BFF_TEST_REDIS_URL to reuse a running Redis. Files are `*.it.ts` so the default run never picks them up.
 */
const shared = path.resolve(__dirname, '../../libs/shared');
export default defineConfig({
  root: __dirname,
  test: {
    globals: true,
    environment: 'node',
    include: ['src/**/*.it.ts'],
    globalSetup: [path.join(shared, 'src/auth/bff/__integration__/global-setup.ts')],
    testTimeout: 60_000,
    hookTimeout: 240_000,
    fileParallelism: false,
  },
  resolve: {
    alias: [
      { find: 'server-only', replacement: path.join(shared, 'src/auth/bff/__tests__/server-only-stub.ts') },
      { find: '@pml.tickets/shared', replacement: path.join(shared, 'src') },
      { find: '@', replacement: path.resolve(__dirname, 'src') },
    ],
  },
});
