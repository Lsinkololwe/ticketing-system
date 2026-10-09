import { gql } from '@apollo/client';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createBffGraphQLClient } from '../../../api/graphql/client';
import { createBffApiClient } from '../../../api/rest/http-client';

afterEach(() => vi.unstubAllGlobals());

describe('same-origin BFF clients', () => {
  it('GraphQL: posts to /api/graphql with same-origin credentials and the CSRF header, never an Authorization header', async () => {
    const calls: Array<{ url: string; init: RequestInit }> = [];
    vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
      calls.push({ url: String(url), init });
      return new Response(JSON.stringify({ data: { ping: 'pong' } }), { status: 200, headers: { 'content-type': 'application/json' } });
    });
    const client = createBffGraphQLClient();
    const res = await client.query({ query: gql`query Ping { ping }` });
    expect(res.data).toEqual({ ping: 'pong' });
    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe('/api/graphql');
    expect(calls[0].init.credentials).toBe('same-origin');
    const headers = new Headers(calls[0].init.headers);
    expect(headers.get('x-pml-csrf')).toBe('1');
    expect(headers.get('authorization')).toBeNull();
  });

  it('REST: base /api/rest, CSRF header, no Authorization interceptor', async () => {
    const client = createBffApiClient();
    expect(client.defaults.baseURL).toBe('/api/rest');
    expect(client.defaults.headers['x-pml-csrf']).toBe('1');
    let seen: Record<string, unknown> = {};
    client.defaults.adapter = async (config) => {
      seen = { ...config.headers.toJSON() };
      return { data: { ok: true }, status: 200, statusText: 'OK', headers: {}, config };
    };
    const r = await client.get('/events');
    expect(r.data).toEqual({ ok: true });
    expect(seen['x-pml-csrf']).toBe('1');
    expect(seen['Authorization']).toBeUndefined();
  });
});
