import { defineConfig } from 'vitest/config';
import path from 'path';

/**
 * Unit tests for the shared library.
 *
 * `node`, not `jsdom`: nothing here touches the DOM, and jsdom costs about a
 * second of startup per run to provide an environment these functions never
 * look at. The app projects keep jsdom because they render components.
 */
export default defineConfig({
  // Set here rather than passed as --root, so the config is self-contained and
  // the same invocation works from the workspace root or from this directory.
  root: __dirname,
  test: {
    globals: true,
    environment: 'node',
    setupFiles: ['./src/test-setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
  resolve: {
    alias: {
      // `server-only` throws outside a React Server build; tests run in plain Node.
      'server-only': path.resolve(__dirname, './src/auth/bff/__tests__/server-only-stub.ts'),
      '@pml.tickets/shared': path.resolve(__dirname, './src'),
    },
  },
});
