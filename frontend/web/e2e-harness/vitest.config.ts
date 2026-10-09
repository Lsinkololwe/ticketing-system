import { defineConfig } from 'vitest/config';

/**
 * Integration tests for the e2e harness itself.
 *
 * <p>Separate from the app and library suites because these start containers:
 * the timeouts are measured in minutes rather than milliseconds, and a first run
 * has to pull an image. Mixing them into a unit-test project would make that
 * project's runtime unpredictable and its failures ambiguous.</p>
 */
export default defineConfig({
  root: __dirname,
  test: {
    globals: true,
    environment: 'node',
    include: ['**/*.test.ts'],
    // A cold run pulls node:20-alpine before anything can start.
    testTimeout: 180_000,
    hookTimeout: 300_000,
    // Containers bind ports; two files racing for the same image and network at
    // once turns a slow run into a flaky one.
    fileParallelism: false,
  },
});
