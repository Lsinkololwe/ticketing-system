import http from 'node:http';
import type { AddressInfo } from 'node:net';
import { Kind, parse, type FragmentDefinitionNode, type SelectionSetNode } from 'graphql';

/**
 * A fake backend for browser tests: a plain Node http server the app's BFF upstream URLs point at
 * (GRAPHQL_URL, API_BASE_URL, IDENTITY_BASE_URL, IDENTITY_TOKEN_URL).
 *
 * GraphQL requests are answered by OPERATION NAME from the fixtures the test registered; REST
 * requests by "METHOD /path". Fixtures live in test files only. A request nobody registered a
 * fixture for is answered with a GraphQL error `NOT_FIXTURED` and recorded in `unhandled`, so a
 * spec can assert that the page asked for nothing it did not expect.
 */

export type Vars = Record<string, unknown>;
export interface UpstreamRequest {
  operationName: string | null;
  variables: Vars;
  query: string;
  headers: http.IncomingHttpHeaders;
  method: string;
  path: string;
  body: unknown;
}
/** A GraphQL fixture returns the `data` object, or one of the helpers below. */
export type GqlResult = Record<string, unknown> | GqlRaw;
export type GqlHandler = GqlResult | ((vars: Vars, req: UpstreamRequest) => GqlResult | Promise<GqlResult>);

class GqlRaw {
  constructor(readonly status: number, readonly body: unknown, readonly delayMs = 0) {}
}
/** GraphQL `errors` with HTTP 200, as the gateway answers for domain errors. */
export const gqlErrors = (...errors: Array<{ message: string; code?: string; extensions?: Record<string, unknown> }>) =>
  new GqlRaw(200, { errors: errors.map((e) => ({ message: e.message, extensions: { code: e.code ?? 'INTERNAL', ...e.extensions } })) });
/** A transport failure: the gateway or network is down. */
export const httpFailure = (status = 503, body: unknown = { error: 'unavailable' }) => new GqlRaw(status, body);
/** Data after a delay, to hold a loading state open. */
export const delayed = (ms: number, data: Record<string, unknown>) => new GqlRaw(200, { data }, ms);

export interface RestResult {
  status?: number;
  body?: unknown;
  headers?: Record<string, string>;
}
export type RestHandler = RestResult | ((req: UpstreamRequest) => RestResult | Promise<RestResult>);

export interface FakeUpstream {
  url: string;
  port: number;
  /** Register GraphQL fixtures by operation name; replaces earlier ones with the same name. */
  gql(fixtures: Record<string, GqlHandler>): void;
  /** Register a REST fixture: `rest('POST', '/api/internal/x', {...})`. */
  rest(method: string, path: string, handler: RestHandler): void;
  /** Forget every fixture, recorded request and unhandled entry (between tests). */
  reset(): void;
  requests: UpstreamRequest[];
  unhandled: string[];
  /** Fields a query selected that a fixture omitted (nulled), as `$.field.path`. Empty when fixtures are complete. */
  missing: string[];
  /** Calls to one operation, for asserting variables and counts. */
  calls(operationName: string): UpstreamRequest[];
  close(): Promise<void>;
}

/**
 * Shapes fixture data to the query's selection set, as a real GraphQL server would: fields the
 * query did not ask for are dropped, fields it asked for that the fixture omits become null and are
 * reported in `missing` (so a spec can insist on complete fixtures), and `__typename` is added when
 * selected. Without this, Apollo treats a partial result as an error and the page shows the wrong state.
 */
export function conform(query: string, data: Record<string, unknown>, missing: string[] = []): Record<string, unknown> {
  const doc = parse(query);
  const frags = new Map<string, FragmentDefinitionNode>();
  for (const d of doc.definitions) if (d.kind === Kind.FRAGMENT_DEFINITION) frags.set(d.name.value, d);
  const op = doc.definitions.find((d) => d.kind === Kind.OPERATION_DEFINITION);
  if (!op || op.kind !== Kind.OPERATION_DEFINITION) return data;
  const shape = (sel: SelectionSetNode, value: unknown, path: string): unknown => {
    if (value === null || value === undefined) return null;
    if (Array.isArray(value)) return value.map((v, i) => shape(sel, v, `${path}[${i}]`));
    if (typeof value !== 'object') return value;
    const src = value as Record<string, unknown>;
    const out: Record<string, unknown> = {};
    const visit = (s: SelectionSetNode) => {
      for (const n of s.selections) {
        if (n.kind === Kind.FIELD) {
          const key = n.alias?.value ?? n.name.value;
          if (n.name.value === '__typename') {
            out[key] = src.__typename ?? 'Object';
            continue;
          }
          if (!(key in src)) {
            missing.push(`${path}.${key}`);
            out[key] = null;
          } else out[key] = n.selectionSet ? shape(n.selectionSet, src[key], `${path}.${key}`) : src[key];
        } else if (n.kind === Kind.INLINE_FRAGMENT) visit(n.selectionSet);
        else {
          const f = frags.get(n.name.value);
          if (f) visit(f.selectionSet);
        }
      }
    };
    visit(sel);
    return out;
  };
  return shape(op.selectionSet, data, '$') as Record<string, unknown>;
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export async function startFakeUpstream(port = 0): Promise<FakeUpstream> {
  let gqlFx = new Map<string, GqlHandler>();
  let restFx = new Map<string, RestHandler>();
  const requests: UpstreamRequest[] = [];
  const unhandled: string[] = [];
  const missing: string[] = [];

  const server = http.createServer(async (req, res) => {
    const chunks: Buffer[] = [];
    for await (const c of req) chunks.push(c as Buffer);
    const raw = Buffer.concat(chunks).toString('utf8');
    let body: unknown = raw;
    try {
      body = raw ? JSON.parse(raw) : null;
    } catch {
      /* form bodies stay as text */
    }
    const path = (req.url ?? '/').split('?')[0];
    const gqlBody = (body && typeof body === 'object' ? body : {}) as { operationName?: string; variables?: Vars; query?: string };
    // Server-side callers (BFF route handlers) send the document without operationName: read it from the text.
    let opName: string | null = gqlBody.operationName ?? null;
    if (!opName && gqlBody.query) {
      try {
        const def = parse(gqlBody.query).definitions.find((d) => d.kind === Kind.OPERATION_DEFINITION);
        opName = def && def.kind === Kind.OPERATION_DEFINITION ? (def.name?.value ?? null) : null;
      } catch {
        opName = null;
      }
    }
    const entry: UpstreamRequest = {
      operationName: opName,
      variables: gqlBody.variables ?? {},
      query: gqlBody.query ?? '',
      headers: req.headers,
      method: req.method ?? 'GET',
      path,
      body,
    };
    const send = (status: number, payload: unknown, headers: Record<string, string> = {}) => {
      res.writeHead(status, { 'content-type': 'application/json', ...headers });
      res.end(JSON.stringify(payload));
    };
    try {
      if (path.endsWith('/graphql') && req.method === 'POST') {
        requests.push(entry);
        const op = entry.operationName ?? '(anonymous)';
        const fx = gqlFx.get(op);
        if (!fx) {
          unhandled.push(`gql ${op}`);
          return send(200, { errors: [{ message: `No fixture for operation ${op}`, extensions: { code: 'NOT_FIXTURED' } }] });
        }
        const out = typeof fx === 'function' ? await fx(entry.variables, entry) : fx;
        if (out instanceof GqlRaw) {
          if (out.delayMs) await sleep(out.delayMs);
          return send(out.status, out.body);
        }
        if (!entry.query) return send(200, { data: out });
        return send(200, { data: conform(entry.query, out, missing) });
      }
      // Service-account token endpoint the buyer identity client calls: any client credentials work.
      if (path.endsWith('/protocol/openid-connect/token') && !restFx.has(`POST ${path}`)) {
        return send(200, { access_token: 'harness-service-token', expires_in: 300, token_type: 'Bearer' });
      }
      requests.push(entry);
      const key = `${entry.method} ${path}`;
      const fx = restFx.get(key);
      if (!fx) {
        unhandled.push(`rest ${key}`);
        return send(404, { error: 'NOT_FIXTURED', detail: key });
      }
      const out = typeof fx === 'function' ? await fx(entry) : fx;
      return send(out.status ?? 200, out.body ?? {}, out.headers);
    } catch (e) {
      unhandled.push(`fixture threw: ${String(e)}`);
      return send(500, { error: 'FIXTURE_ERROR', detail: String(e) });
    }
  });
  await new Promise<void>((r) => server.listen(port, '127.0.0.1', r));
  const p = (server.address() as AddressInfo).port;

  return {
    url: `http://127.0.0.1:${p}`,
    port: p,
    gql(f) {
      for (const [k, v] of Object.entries(f)) gqlFx.set(k, v);
    },
    rest(method, path, handler) {
      restFx.set(`${method.toUpperCase()} ${path}`, handler);
    },
    reset() {
      gqlFx = new Map();
      restFx = new Map();
      requests.length = 0;
      unhandled.length = 0;
      missing.length = 0;
    },
    requests,
    unhandled,
    missing,
    calls: (op) => requests.filter((r) => r.operationName === op),
    close: () => new Promise<void>((r) => server.close(() => r())),
  };
}
