import "server-only";

import { cookies } from "next/headers";
import { bff } from "@/lib/bff";

export interface CartIntent {
  eventId: string;
  quantities: Record<string, number>;
}

const ID = /^[A-Za-z0-9_-]{1,64}$/;

/** Validates a cart sent by the browser: bounded size, ids and integer quantities only. */
export function parseCart(body: unknown): CartIntent | null {
  if (!body || typeof body !== "object") return null;
  const b = body as { eventId?: unknown; quantities?: unknown };
  if (typeof b.eventId !== "string" || !ID.test(b.eventId)) return null;
  if (!b.quantities || typeof b.quantities !== "object") return null;
  const entries = Object.entries(b.quantities as Record<string, unknown>);
  if (entries.length > 20) return null;
  const quantities: Record<string, number> = {};
  for (const [k, v] of entries) {
    if (
      !ID.test(k) ||
      typeof v !== "number" ||
      !Number.isInteger(v) ||
      v < 0 ||
      v > 20
    )
      return null;
    if (v > 0) quantities[k] = v;
  }
  return { eventId: b.eventId, quantities };
}

const isCart = (v: unknown): v is CartIntent =>
  !!v &&
  typeof v === "object" &&
  typeof (v as CartIntent).eventId === "string" &&
  typeof (v as CartIntent).quantities === "object";

/**
 * The cart is the buyer app's one piece of state in the shared module's opaque `ext` bag:
 * `flow.ext.cart` before sign-in, `session.record.ext.cart` after (the module merges it at callback).
 */
export async function getCartFor(
  eventId: string,
): Promise<Record<string, number>> {
  const d = await bff.internals();
  const jar = await cookies();
  const session = await d.sessions.load(jar.get(d.names.session)?.value, {
    touch: false,
  });
  const cart = session
    ? session.record.ext.cart
    : (await d.flows.load(jar.get(d.names.flow)?.value))?.ext?.cart;
  return isCart(cart) && cart.eventId === eventId ? cart.quantities : {};
}

/** Parks the cart. Returns Set-Cookie strings to attach (a new flow cookie before sign-in). */
export async function saveCart(
  req: Request,
  cart: CartIntent,
): Promise<string[]> {
  const d = await bff.internals();
  const jar = await cookies();
  for (let i = 0; i < 3; i++) {
    const s = await d.sessions.load(jar.get(d.names.session)?.value, {
      touch: false,
    });
    if (!s) break;
    if (
      await d.sessions.cas(s, { ...s.record, ext: { ...s.record.ext, cart } })
    )
      return [];
  }
  const { setCookie } = await bff.flow.update(req, (rec) => ({
    ...rec,
    ext: { ...rec.ext, cart },
  }));
  return setCookie;
}
