import { NextResponse } from "next/server";
import { callIdentity } from "@/lib/server/identity";
import { bff } from "@/lib/bff";
import { guardMutation, problem } from "@/lib/server/http";
import { getServerEnv } from "@/lib/server/env";

export const dynamic = "force-dynamic";

/**
 * POST /api/identity/ensure — create or find the account for the verified contact.
 *
 * Handle transport decision: identity-service's single-use `loginHandle` is stored in the
 * server-side flow record (keyed by the httpOnly flow cookie) with a 60 s expiry, and the
 * browser is only told to navigate to GET /api/auth/start, which consumes it as
 * `login_hint`. The handle is never in a JSON body, URL, or script-readable cookie.
 *
 * Idempotent: a 202 PROVISIONING leaves the proof in place so the client may call again.
 */
export async function POST(req: Request) {
  const ctx = await guardMutation(req, "ensure", async () => ({
    flow: (await bff.flow.read(req)).id,
  }));
  if (ctx instanceof NextResponse) return ctx;

  let body: { consent?: unknown; displayName?: unknown };
  try {
    body = await req.json();
  } catch {
    body = {};
  }
  // Terms + consent line must be shown and accepted (implicit by continuing is not enough).
  if (body.consent !== true) return problem(400, "CONSENT_REQUIRED");
  const displayName =
    typeof body.displayName === "string" &&
    body.displayName.trim() &&
    body.displayName.length <= 80
      ? body.displayName.trim()
      : undefined;

  const flow = await bff.flow.read(req);
  if (!flow.id || !flow.rec.proof) return problem(400, "PROOF_INVALID");

  const env = getServerEnv();
  const res = await callIdentity<{
    accountId: string | null;
    status: string;
    isNew?: boolean;
    loginHandle?: string;
    retryAfterSeconds?: number;
  }>("POST", "/api/internal/auth/accounts/ensure", {
    correlationId: ctx.correlationId,
    clientIp: ctx.clientIp,
    body: {
      proof: flow.rec.proof.value,
      clientId: env.clientId,
      issueHandle: true,
      ...(displayName ? { displayName } : {}),
      consents: [{ purpose: "TERMS", version: env.termsVersion }],
    },
  });

  if (!res.ok) {
    if (["PROOF_INVALID", "ACCOUNT_SUSPENDED"].includes(res.error.errorCode)) {
      // The proof is spent or unusable; do not keep it.
      await bff.flow.update(req, ({ proof: _spent, ...rest }) => {
        void _spent;
        return rest;
      });
    }
    const { status, ...rest } = res.error;
    return problem(status, rest.errorCode, rest);
  }

  if (res.status === 202 || res.data.status === "PROVISIONING") {
    return NextResponse.json(
      {
        status: "PROVISIONING",
        retryAfterSeconds: res.data.retryAfterSeconds ?? 2,
      },
      {
        status: 202,
        headers: {
          "cache-control": "no-store",
          "retry-after": String(res.data.retryAfterSeconds ?? 2),
        },
      },
    );
  }

  if (res.data.status !== "ACTIVE" || !res.data.loginHandle)
    return problem(502, "SERVICE_UNAVAILABLE");

  if (!res.data.accountId) return problem(502, "SERVICE_UNAVAILABLE");
  const handle = {
    value: res.data.loginHandle,
    accountId: res.data.accountId,
    isNew: res.data.isNew === true,
    ttlSec: 55,
  };
  const { setCookie } = await bff.flow.attachHandle(req, handle);
  // The proof is spent: drop it from the same flow record.
  await bff.flow.update(req, ({ proof: _consumed, ...rest }) => {
    void _consumed;
    return rest;
  });

  const response = NextResponse.json(
    {
      status: "ACTIVE",
      isNew: res.data.isNew === true,
      next: "/api/auth/start",
    },
    {
      headers: {
        "cache-control": "no-store",
        "x-correlation-id": ctx.correlationId,
      },
    },
  );
  for (const c of setCookie) response.headers.append("set-cookie", c);
  return response;
}
