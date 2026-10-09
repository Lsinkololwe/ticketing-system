import { NextResponse } from "next/server";
import { newCorrelationId } from "@/lib/server/identity";
import { bff } from "@/lib/bff";
import { problem } from "@/lib/server/http";
import { gql, OPERATIONS, toMyContacts } from "@/lib/server/contacts";

export const dynamic = "force-dynamic";

/** GET /api/profile/contacts — the signed-in buyer's contacts (masked values only). */
export async function GET() {
  const session = await bff.getSession();
  if (!session) return problem(401, "UNAUTHENTICATED");
  const limit = await bff.rateLimit("api", { session: session.sessionId });
  if (!limit.allowed)
    return problem(
      limit.unavailable ? 503 : 429,
      limit.unavailable ? "SERVICE_UNAVAILABLE" : "OTP_RATE_LIMITED",
      { retryAfterSeconds: limit.retryAfterSec },
      { "retry-after": String(limit.retryAfterSec) },
    );
  const correlationId = newCorrelationId();
  const res = await gql<unknown>(
    OPERATIONS.list,
    {},
    correlationId,
    "myContacts",
  );
  if (!res.ok) {
    const { status, ...rest } = res.error;
    return problem(status, rest.errorCode, rest);
  }
  return NextResponse.json(toMyContacts(res.data), {
    headers: { "cache-control": "no-store", "x-correlation-id": correlationId },
  });
}
