import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { afterEach, beforeAll, afterAll, vi } from 'vitest';
import { server } from './mocks/server';

/**
 * Component suites run in jsdom; container-backed integration suites opt into the node
 * environment. Only the former get the DOM helpers and the MSW interceptor — an integration
 * suite talks to a real container over real HTTP, and intercepting that traffic would break it.
 */
const isBrowserLikeSuite = typeof window !== 'undefined';

// Establish API mocking before all tests
beforeAll(() => {
  if (isBrowserLikeSuite) {
    server.listen({ onUnhandledRequest: 'error' });
  }
});

// Reset any request handlers that we may add during the tests,
// so they don't affect other tests
afterEach(() => {
  if (isBrowserLikeSuite) {
    cleanup();
    server.resetHandlers();
  }
});

// Clean up after the tests are finished
afterAll(() => {
  if (isBrowserLikeSuite) {
    server.close();
  }
});

// Mock Next.js router
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    refresh: vi.fn(),
    prefetch: vi.fn(),
  }),
  usePathname: () => '/',
  useSearchParams: () => new URLSearchParams(),
}));

// Browser globals, skipped for suites that opt into the node environment (container-backed
// integration tests, which exercise server-side modules and have no DOM).
if (typeof window !== 'undefined') {
  // Mock window.matchMedia
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

// Mock IntersectionObserver
global.IntersectionObserver = class IntersectionObserver {
  constructor() {}
  disconnect() {}
  observe() {}
  takeRecords() {
    return [];
  }
  unobserve() {}
} as any;
