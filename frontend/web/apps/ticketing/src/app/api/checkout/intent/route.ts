import { NextResponse } from "next/server";
import { assertSameOrigin } from "@pml.tickets/shared/auth/bff";
import { bff } from "@/lib/bff";
import { problem } from "@/lib/server/http";
import { parseCart, saveCart } from "@/lib/server/cart";

export const dynamic = "force-dynamic";

/**
 * POST /api/checkout/intent — park the selected tickets server-side so they survive the
 * sign-in redirect. Before sign-in they live in the flow record, afterwards in the session.
 */
export async function POST(req: Request) {
  if (assertSameOrigin(req, bff.config.appOrigin))
    return problem(403, "ORIGIN_REJECTED");
  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return problem(400, "REQUEST_FAILED");
  }
  const cart = parseCart(body);
  if (!cart) return problem(400, "REQUEST_FAILED");

  const setCookie = await saveCart(req, cart);
  const res = NextResponse.json(
    { saved: true },
    { headers: { "cache-control": "no-store" } },
  );
  for (const c of setCookie) res.headers.append("set-cookie", c);
  return res;
}
