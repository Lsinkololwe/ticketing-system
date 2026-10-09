// @vitest-environment jsdom

import { gql } from '@apollo/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * The shared client ends the session on a revoked token — through the real link chain.
 *
 * <p>`sessionAction` is unit-tested on its own; what this adds is that the client every app
 * builds actually acts on it: the stored credential is dropped, the app's sign-out runs once,
 * and the operation is not retried with the token the server has just refused. The server is a
 * stubbed `fetch` returning the platform's error shape, so everything between the query and the
 * wire is the production code.</p>
 */

const PING = gql`
  query SessionProbe {
    me {
      id
    }
  }
`;

function respond(body: unknown, status = 200) {
  // A fresh Response per call: a body can be read once.
  return vi.fn().mockImplementation(async () =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
  );
}

function refusal(errorCode: string, classification: string, retryable = false) {
  return {
    data: null,
    errors: [{ message: 'refused', extensions: { errorCode, classification, retryable } }],
  };
}

async function freshClient(onAuthError: () => void) {
  // The client guards its sign-out with module state so a burst of failures signs out once;
  // a fresh module per test keeps one test's sign-out from swallowing the next one's.
  vi.resetModules();
  const { createBffGraphQLClient } = await import('./client');
  return createBffGraphQLClient({
    uri: 'http://graph.test/graphql',
    onAuthError,
  });
}

describe('shared GraphQL client · a revoked token ends the session', () => {
  beforeEach(() => {
    window.localStorage.setItem('access_token', 'a-token-the-server-revoked');
    window.localStorage.setItem('refresh_token', 'its-refresh-token');
    window.sessionStorage.setItem('draft', 'kept only for this session');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('TOKEN_REVOKED drops the stored credential and signs out once, without retrying', async () => {
    const server = respond(refusal('TOKEN_REVOKED', 'UNAUTHENTICATED'));
    vi.stubGlobal('fetch', server);
    const signOut = vi.fn();

    const client = await freshClient(signOut);
    await client.query({ query: PING, fetchPolicy: 'network-only' });
    await client.query({ query: PING, fetchPolicy: 'network-only' });

    expect(signOut).toHaveBeenCalledTimes(1);
    expect(window.localStorage.getItem('access_token')).toBeNull();
    expect(window.localStorage.getItem('refresh_token')).toBeNull();
    expect(window.sessionStorage.length).toBe(0);
    expect(server, 'one request per query: a revoked token is never re-presented by a retry').toHaveBeenCalledTimes(2);
  });

  it('an unauthenticated caller is sent to sign in, and nothing stored is thrown away', async () => {
    vi.stubGlobal('fetch', respond(refusal('ACTOR_NOT_AUTHENTICATED', 'UNAUTHENTICATED')));
    const signOut = vi.fn();

    const client = await freshClient(signOut);
    await client.query({ query: PING, fetchPolicy: 'network-only' });

    expect(signOut).toHaveBeenCalledTimes(1);
    expect(window.sessionStorage.getItem('draft')).toBe('kept only for this session');
  });

  it('an ordinary refusal leaves the session alone', async () => {
    vi.stubGlobal('fetch', respond(refusal('ACTOR_NOT_PERMITTED', 'PERMISSION_DENIED')));
    const signOut = vi.fn();

    const client = await freshClient(signOut);
    const result = await client.query({ query: PING, fetchPolicy: 'network-only' });

    expect(signOut).not.toHaveBeenCalled();
    expect(window.localStorage.getItem('access_token')).toBe('a-token-the-server-revoked');
    expect(String(result.error?.message)).toContain('refused');
  });

  it('a 401 from the gateway signs out', async () => {
    vi.stubGlobal('fetch', respond({ message: 'Unauthorized' }, 401));
    const signOut = vi.fn();

    const client = await freshClient(signOut);
    await client.query({ query: PING, fetchPolicy: 'network-only' }).catch(() => undefined);

    expect(signOut).toHaveBeenCalledTimes(1);
  });
});
