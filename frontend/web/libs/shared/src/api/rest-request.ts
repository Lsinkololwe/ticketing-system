import { problemFromResponse } from '../forms/server-errors';

export interface RestRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  /** Sent as `Authorization: Bearer`. Cookie-session BFF routes omit it. */
  token?: string | null;
}

/**
 * JSON request for same-origin BFF routes. Non-2xx responses throw `RestProblemError` carrying the
 * RFC 9457 body, which `applyServerError` understands; network failures throw the underlying TypeError
 * (mapped to a neutral "try again" message).
 */
export async function restRequest<T>(path: string, options: RestRequestOptions = {}): Promise<T> {
  const { method = options.body === undefined ? 'GET' : 'POST', body, headers, signal, token } = options;
  const res = await fetch(path, {
    method,
    credentials: 'same-origin',
    signal,
    headers: {
      accept: 'application/json, application/problem+json',
      ...(body !== undefined ? { 'content-type': 'application/json' } : {}),
      ...(method !== 'GET' ? { 'x-requested-with': 'pml-web' } : {}),
      ...(token ? { authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) throw await problemFromResponse(res);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}
