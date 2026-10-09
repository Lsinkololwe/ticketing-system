import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

/**
 * Test setup for the organizer portal.
 *
 * <h2>No request mocking, by policy</h2>
 *
 * There is no MSW server here and no fixture data anywhere in this app. Any
 * test that needs a backend talks to a real one in a container — see
 * `backend/booking-service/.../OrganizerDashboardAnalyticsIntegrationTest.java`
 * for the server-side equivalent.
 *
 * The rationale is not purity. A hand-written fixture encodes what we *believe*
 * the backend returns, so it keeps passing after the backend changes shape —
 * which is precisely when a test should fail. A BigDecimal stored as a String
 * is exactly such a defect: it passes every hand-written fixture and shows
 * only when the pipeline runs against a real MongoDB.
 *
 * What remains below are ENVIRONMENT SHIMS, not mocks: jsdom has no Next.js
 * router, no `matchMedia` and no `IntersectionObserver`, so rendering any
 * component would throw without them. They stand in for browser platform, not
 * for application data.
 */

afterEach(() => {
  if (typeof window !== 'undefined') {
    cleanup();
  }
});

// Next.js router — jsdom provides no routing context.
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
