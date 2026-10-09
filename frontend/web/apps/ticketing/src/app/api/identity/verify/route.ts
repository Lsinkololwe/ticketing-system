import { NextResponse } from "next/server";
import { callIdentity } from "@/lib/server/identity";
import { bff } from "@/lib/bff";
import { guardMutation, problem } from "@/lib/server/http";

export const dynamic = "force-dynamic";

/**
 * POST /api/identity/verify — check the 6 digit code.
 *
 * The proof token is a bearer credential for the next step, so it is NOT sent to
 * the browser: it is parked in the server-side flow record (httpOnly flow cookie)
 * and consumed by /api/identity/ensure.
 */
export async function POST(req: Request) {
  const ctx = await guardMutation(req, "verify", async () => ({
    flow: (await bff.flow.read(req)).id,
  }));
  if (ctx instanceof NextResponse) return ctx;

  let body: { challengeId?: unknown; code?: unknown };
  try {
    body = await req.json();
  } catch {
    return problem(400, "OTP_INVALID");
  }
  const challengeId =
    typeof body.challengeId === "string" ? body.challengeId : "";
  const code = typeof body.code === "string" ? body.code : "";
  if (!/^[0-9a-fA-F-]{36}$/.test(challengeId) || !/^\d{6}$/.test(code))
    return problem(400, "OTP_INVALID");

  const res = await callIdentity<{
    proof: string;
    contactType: string;
    maskedContact: string;
    expiresInSeconds: number;
  }>("POST", "/api/internal/auth/challenges/verify", {
    correlationId: ctx.correlationId,
    clientIp: ctx.clientIp,
    body: { challengeId, code },
  });
  if (!res.ok) {
    const { status, ...rest } = res.error;
    return problem(status, rest.errorCode, rest);
  }

  const { setCookie } = await bff.flow.update(req, (rec) => ({
    ...rec,
    proof: { value: res.data.proof, maskedContact: res.data.maskedContact },
  }));
  const response = NextResponse.json(
    {
      verified: true,
      contactType: res.data.contactType,
      maskedContact: res.data.maskedContact,
      expiresInSeconds: res.data.expiresInSeconds,
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
