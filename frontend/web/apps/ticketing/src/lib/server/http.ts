import "server-only";
import { NextResponse } from "next/server";
import { assertSameOrigin } from "@pml.tickets/shared/auth/bff";
import { bff } from "@/lib/bff";
import { newCorrelationId } from "./identity";

export function problem(
  status: number,
  errorCode: string,
  extra: Record<string, unknown> = {},
  headers?: HeadersInit,
) {
  return NextResponse.json(
    { errorCode, ...extra },
    { status, headers: { "cache-control": "no-store", ...(headers ?? {}) } },
  );
}

export interface RequestContext {
  correlationId: string;
  clientIp: string;
}

type Subjects = Parameters<typeof bff.rateLimit>[1];

/**
 * Browser-facing mutation guard from the shared module: Origin / Sec-Fetch-Site / x-pml-csrf, then
 * the named limiter policy. `subjects` may be a function so a body-derived contact can be keyed.
 * Returns a Response when refused, otherwise the request context.
 */
export async function guardMutation(
  req: Request,
  policy: string,
  subjects: Subjects | (() => Promise<Subjects>) = {},
): Promise<RequestContext | NextResponse> {
  if (assertSameOrigin(req, bff.config.appOrigin))
    return problem(403, "ORIGIN_REJECTED");
  const clientIp = bff.clientIp(req);
  const extra = typeof subjects === "function" ? await subjects() : subjects;
  const d = await bff.rateLimit(policy, { ip: clientIp, ...extra });
  if (!d.allowed) {
    if (d.unavailable)
      return problem(
        503,
        "SERVICE_UNAVAILABLE",
        {},
        { "retry-after": String(d.retryAfterSec) },
      );
    return problem(
      429,
      "OTP_RATE_LIMITED",
      { retryAfterSeconds: d.retryAfterSec },
      { "retry-after": String(d.retryAfterSec) },
    );
  }
  const h = req.headers.get("x-correlation-id");
  return {
    correlationId: h && /^[A-Za-z0-9-]{8,64}$/.test(h) ? h : newCorrelationId(),
    clientIp,
  };
}
