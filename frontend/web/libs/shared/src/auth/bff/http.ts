import { randomUUID } from 'node:crypto';
import type { BffDeps } from './context';
import { parseCookies } from './cookies';
import { resolveClientIp, type RateDecision, type Subjects } from './ratelimit';

export interface ReqCtx {
  correlationId: string;
  ip: string;
  cookies: Map<string, string>;
}

export function reqCtx(req: Request, deps: BffDeps): ReqCtx {
  const h = req.headers.get('x-correlation-id');
  return {
    correlationId: h && /^[A-Za-z0-9-]{8,64}$/.test(h) ? h : randomUUID(),
    ip: resolveClientIp(req.headers, deps.cfg.trustProxyHops),
    cookies: parseCookies(req.headers.get('cookie')),
  };
}

export function problem(status: number, errorCode: string, extra: Record<string, unknown> = {}, headers?: HeadersInit): Response {
  return new Response(JSON.stringify({ errorCode, ...extra }), {
    status,
    headers: { 'content-type': 'application/json', 'cache-control': 'no-store', ...(headers as Record<string, string> | undefined) },
  });
}

export function json(body: unknown, status = 200, headers?: HeadersInit): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', 'cache-control': 'no-store', ...(headers as Record<string, string> | undefined) },
  });
}

/** 303 redirect. `cookies` are Set-Cookie strings. */
export function see(deps: BffDeps, target: string, cookies: string[] = [], extra: Record<string, string> = {}): Response {
  const headers = new Headers({ 'cache-control': 'no-store', ...extra });
  headers.set('location', /^https?:\/\//i.test(target) ? target : new URL(target, deps.cfg.appUrl + '/').toString());
  for (const c of cookies) headers.append('set-cookie', c);
  return new Response(null, { status: 303, headers });
}

/** Maps a limiter decision to a refusal, or null when allowed. */
export function refusal(d: RateDecision): Response | null {
  if (d.allowed) return null;
  if (d.unavailable) return problem(503, 'RATE_LIMITER_UNAVAILABLE', { retryAfterSeconds: d.retryAfterSec }, { 'retry-after': String(d.retryAfterSec) });
  return problem(429, 'RATE_LIMITED', { retryAfterSeconds: d.retryAfterSec }, { 'retry-after': String(d.retryAfterSec) });
}

export async function gate(deps: BffDeps, policy: string, subjects: Subjects): Promise<Response | null> {
  return refusal(await deps.limiter.consume(policy, subjects));
}
