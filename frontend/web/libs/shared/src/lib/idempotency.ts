'use client';

/**
 * Client-supplied idempotency keys (ET-PLT-007 R6): a key must survive exactly the retries it
 * exists to protect against, and never survive past them.
 *
 * <h2>Why a key is never minted fresh on every call</h2>
 * The backend's {@code IdempotencyGuard} replays a completed result for a repeated key and frees
 * a failed one for a clean retry — so reusing the same key across retries of the *same* intent is
 * always correct, and minting a new key on every call (the bug this module fixes at several call
 * sites) defeats the guard entirely: a dropped response followed by a user tapping again just
 * becomes two different keys, so the backend processes both. A key must change only when the
 * user's intent genuinely changes — a different ticket, a different reservation, an explicit
 * "send this again" action — never on an accidental double call for the same one.
 */

import { useCallback, useEffect, useState } from 'react';

function readStorage(storageKey: string): string | null {
  try {
    return window.sessionStorage.getItem(`idem:${storageKey}`);
  } catch {
    return null;
  }
}

function writeStorage(storageKey: string, value: string): void {
  try {
    window.sessionStorage.setItem(`idem:${storageKey}`, value);
  } catch {
    // Private mode or a blocked storage API: the key still works, it just will not survive a reload.
  }
}

function mintFor(storageKey: string | null): string {
  if (storageKey && typeof window !== 'undefined') {
    const existing = readStorage(storageKey);
    if (existing) return existing;
    const fresh = crypto.randomUUID();
    writeStorage(storageKey, fresh);
    return fresh;
  }
  return crypto.randomUUID();
}

/**
 * A stable idempotency key for one user intent, persisted so a reload mid-submission reuses it
 * instead of minting a new one.
 *
 * @param storageKey Identifies the intent (a reservation id, a ticket id, an escrow account id).
 *   `null`/`undefined` while that identity is not yet known falls back to an in-memory key for
 *   that render only. Changing `storageKey` (a different ticket opened in the same dialog) mints
 *   a new key automatically — callers do not need to call `regenerate()` for that case.
 * @returns `[key, regenerate]` — call `regenerate()` only for a deliberate new attempt at the
 *   *same* identity (resending an OTP prompt, retrying after the user fixed something), not for
 *   an ordinary retry of a request that may have gone through. It returns the fresh key
 *   synchronously (the state update that re-renders `key` is not yet visible to the caller), so a
 *   call that must use the new key immediately — not on the next render — reads the return value.
 */
export function useIdempotencyKey(storageKey: string | null | undefined): [string, () => string] {
  const normalized = storageKey ?? null;
  const [key, setKey] = useState(() => mintFor(normalized));

  useEffect(() => {
    setKey(mintFor(normalized));
    // Re-mint only when the identity itself changes, not on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [normalized]);

  const regenerate = useCallback(() => {
    const fresh = crypto.randomUUID();
    if (normalized) writeStorage(normalized, fresh);
    setKey(fresh);
    return fresh;
  }, [normalized]);

  return [key, regenerate];
}

/**
 * For an imperative action (not a component holding a ref), the same key for repeat calls with
 * the same inputs and a fresh key when any input differs — composing the key from the inputs
 * themselves is what tells "a retry of this" apart from "a genuinely new request that happens to
 * share an id" (e.g. a second refund raised for the same ticket with a different reason, after
 * the first was rejected). `cache` is a `Map` the caller owns (typically a `useRef(new Map())`
 * inside the hook); entries are never evicted — the memory cost is one UUID per distinct input
 * tuple ever acted on in the page's lifetime, which is bounded by what the staff member actually
 * clicked.
 */
export function stableActionKey(cache: Map<string, string>, ...inputs: unknown[]): string {
  const composite = JSON.stringify(inputs);
  const existing = cache.get(composite);
  if (existing) return existing;
  const fresh = crypto.randomUUID();
  cache.set(composite, fresh);
  return fresh;
}
