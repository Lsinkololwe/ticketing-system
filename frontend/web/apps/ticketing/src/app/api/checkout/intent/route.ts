import { NextResponse } from "next/server";
import { guardMutation, problem } from "@/lib/server/http";
import { parseCart, saveCart } from "@/lib/server/cart";

export const dynamic = "force-dynamic";

/**
 * POST /api/checkout/intent — park the selected tickets server-side so they survive the
 * sign-in redirect. Before sign-in they live in the flow record, afterwards in the session.
 *
 * Same guard as every browser mutation: Origin equals the app origin, Sec-Fetch-Site is same-origin,
 * X-Requested-With is present, and the caller's IP (resolved through the trusted proxy chain only) is
 * counted against the `checkout_intent` limiter.
 */
export async function POST(req: Request) {
  const ctx = await guardMutation(req, "checkout_intent");
  if (ctx instanceof NextResponse) return ctx;
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
