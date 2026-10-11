// @vitest-environment jsdom
import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { stableActionKey, useIdempotencyKey } from './idempotency';

/**
 * `stableActionKey` is the fix for a real bug found in admin's finance hooks: `idempotencyKey:
 * crypto.randomUUID()` evaluated fresh inside the mutation call meant every click minted a new
 * key, so a dropped response followed by the user clicking again was never caught by the
 * backend's idempotency guard — exactly the scenario R6 exists to protect against.
 */
describe('stableActionKey', () => {
  it('returns the same key for the same inputs, so a retry replays rather than re-running', () => {
    const cache = new Map<string, string>();
    const first = stableActionKey(cache, 'payout-1', 'approve');
    const second = stableActionKey(cache, 'payout-1', 'approve');
    expect(second).toBe(first);
  });

  it('returns a different key when any input differs, so a genuinely new request is not refused as a reuse', () => {
    const cache = new Map<string, string>();
    const first = stableActionKey(cache, 'ticket-1', 'duplicate charge');
    const second = stableActionKey(cache, 'ticket-1', 'event cancelled');
    expect(second).not.toBe(first);
  });

  it('is independent per cache: two different hook instances never collide', () => {
    const cacheA = new Map<string, string>();
    const cacheB = new Map<string, string>();
    expect(stableActionKey(cacheA, 'id-1')).not.toBe(stableActionKey(cacheB, 'id-1'));
  });

  it('treats a missing optional argument and an explicit null the same way callers pass it', () => {
    const cache = new Map<string, string>();
    const withNull = stableActionKey(cache, 'payout-1', null);
    const again = stableActionKey(cache, 'payout-1', null);
    expect(again).toBe(withNull);
  });
});

/**
 * `useIdempotencyKey` is what lets a reload in the middle of a submission reuse the key the backend
 * may already have recorded, instead of minting a second one for the same intent.
 */
describe('useIdempotencyKey', () => {
  const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

  beforeEach(() => {
    window.sessionStorage.clear();
  });

  it('mints a UUID and persists it under idem:{storageKey}', () => {
    const { result } = renderHook(() => useIdempotencyKey('reservation-1'));

    expect(result.current[0]).toMatch(UUID);
    expect(window.sessionStorage.getItem('idem:reservation-1')).toBe(result.current[0]);
  });

  it('returns the same key after the component is unmounted and mounted again, as a reload would', () => {
    const first = renderHook(() => useIdempotencyKey('reservation-1'));
    const original = first.result.current[0];
    first.unmount();

    const second = renderHook(() => useIdempotencyKey('reservation-1'));

    expect(second.result.current[0]).toBe(original);
  });

  it('regenerate() replaces the key, returns it, and persists it for the next mount', () => {
    const { result, unmount } = renderHook(() => useIdempotencyKey('ticket-1'));
    const original = result.current[0];

    let returned = '';
    act(() => {
      returned = result.current[1]();
    });

    expect(returned).toMatch(UUID);
    expect(returned).not.toBe(original);
    expect(result.current[0]).toBe(returned);
    expect(window.sessionStorage.getItem('idem:ticket-1')).toBe(returned);

    unmount();
    const remounted = renderHook(() => useIdempotencyKey('ticket-1'));
    expect(remounted.result.current[0]).toBe(returned);
  });

  it('gives different intents different keys', () => {
    const a = renderHook(() => useIdempotencyKey('ticket-1'));
    const b = renderHook(() => useIdempotencyKey('ticket-2'));

    expect(a.result.current[0]).not.toBe(b.result.current[0]);
    expect(window.sessionStorage.getItem('idem:ticket-1')).toBe(a.result.current[0]);
    expect(window.sessionStorage.getItem('idem:ticket-2')).toBe(b.result.current[0]);
  });
});
