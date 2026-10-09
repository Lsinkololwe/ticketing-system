// @vitest-environment jsdom

import { gql } from '@apollo/client';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createBffGraphQLClient } from './client';

/**
 * A refused mutation must reject. With errorPolicy 'all' the promise resolved with `error` set, so
 * every caller's try/catch and success toast treated a refusal as success.
 */
const DO = gql`
  mutation Probe {
    probe
  }
`;

afterEach(() => vi.unstubAllGlobals());

describe('shared client mutation defaults', () => {
  it('rejects when the server answers with GraphQL errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async () => new Response(JSON.stringify({ data: null, errors: [{ message: 'Refused', extensions: { code: 'VALIDATION_FAILED' } }] }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    );
    const client = createBffGraphQLClient({ uri: 'http://localhost/graphql' });
    await expect(client.mutate({ mutation: DO })).rejects.toBeTruthy();
  });

  it('resolves with data on success', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response(JSON.stringify({ data: { probe: true } }), { status: 200, headers: { 'Content-Type': 'application/json' } })));
    const client = createBffGraphQLClient({ uri: 'http://localhost/graphql' });
    const r = await client.mutate({ mutation: DO });
    expect(r.data).toEqual({ probe: true });
  });
});
