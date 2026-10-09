import "server-only";

import { randomUUID } from "node:crypto";
import { getServerEnv } from "./env";

/**
 * identity-service client (service-to-service). The service token is a
 * client-credentials token held only in server memory.
 */

export interface AppError {
  status: number;
  errorCode: string;
  title?: string;
  retryable?: boolean;
  retryAfterSeconds?: number;
  attemptsRemaining?: number;
  lockedUntil?: string;
}

export type IdentityResult<T> =
  { ok: true; status: number; data: T } | { ok: false; error: AppError };

let tokenCache: { token: string; expiresAt: number } | null = null;

export function resetIdentityTokenForTests() {
  tokenCache = null;
}

async function serviceToken(): Promise<string> {
  if (tokenCache && tokenCache.expiresAt > Date.now() + 15_000)
    return tokenCache.token;
  const env = getServerEnv();
  const res = await fetch(env.identityTokenUrl, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "client_credentials",
      client_id: env.identityClientId,
      client_secret: env.identityClientSecret,
      // The realm grants internal-read/internal-write as OPTIONAL scopes, so they must be asked for.
      scope: "internal-read internal-write",
    }),
    cache: "no-store",
    signal: AbortSignal.timeout(5000),
  });
  if (!res.ok) throw new Error(`identity service token ${res.status}`);
  const json = (await res.json()) as {
    access_token: string;
    expires_in: number;
  };
  tokenCache = {
    token: json.access_token,
    expiresAt: Date.now() + json.expires_in * 1000,
  };
  return json.access_token;
}

/** Maps RFC 9457 problem+json onto the app error model. Unknown shapes get a generic code. */
export function mapProblem(status: number, body: unknown): AppError {
  const p = (body && typeof body === "object" ? body : {}) as Record<
    string,
    unknown
  >;
  const errorCode =
    typeof p.errorCode === "string"
      ? p.errorCode
      : status >= 500
        ? "SERVICE_UNAVAILABLE"
        : "REQUEST_FAILED";
  const err: AppError = { status, errorCode };
  if (typeof p.title === "string") err.title = p.title;
  if (typeof p.retryable === "boolean") err.retryable = p.retryable;
  if (typeof p.retryAfterSeconds === "number")
    err.retryAfterSeconds = p.retryAfterSeconds;
  if (typeof p.attemptsRemaining === "number")
    err.attemptsRemaining = p.attemptsRemaining;
  if (typeof p.lockedUntil === "string") err.lockedUntil = p.lockedUntil;
  return err;
}

export async function callIdentity<T>(
  method: "GET" | "POST",
  path: string,
  opts: { body?: unknown; correlationId: string; clientIp?: string },
): Promise<IdentityResult<T>> {
  const env = getServerEnv();
  try {
    const token = await serviceToken();
    const res = await fetch(`${env.identityBaseUrl}${path}`, {
      method,
      headers: {
        authorization: `Bearer ${token}`,
        accept: "application/json, application/problem+json",
        "x-correlation-id": opts.correlationId,
        ...(opts.clientIp ? { "x-forwarded-for": opts.clientIp } : {}),
        ...(opts.body !== undefined
          ? { "content-type": "application/json" }
          : {}),
      },
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
      cache: "no-store",
      signal: AbortSignal.timeout(10_000),
    });
    const text = await res.text();
    let json: unknown = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      json = null;
    }
    if (res.ok) return { ok: true, status: res.status, data: json as T };
    if (res.status === 401) tokenCache = null;
    return { ok: false, error: mapProblem(res.status, json) };
  } catch {
    return {
      ok: false,
      error: { status: 503, errorCode: "SERVICE_UNAVAILABLE", retryable: true },
    };
  }
}

export const newCorrelationId = () => randomUUID();
