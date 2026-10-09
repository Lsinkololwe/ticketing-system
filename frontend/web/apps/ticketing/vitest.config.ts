/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  root: __dirname,
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '@components': path.resolve(__dirname, 'src/components'),
      '@pml.tickets/shared': path.resolve(__dirname, '../../libs/shared/src'),
      // `server-only` throws outside a react-server build; tests run in plain node/jsdom.
      'next/headers': path.resolve(__dirname, 'src/test/nextHeadersStub.ts'),
      'server-only': path.resolve(__dirname, 'src/test/empty.ts'),
    },
  },
  test: {
    globals: true,
    environment: 'node',
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    setupFiles: ['./src/test/setup.ts'],
  },
});
