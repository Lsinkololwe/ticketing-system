import { NextResponse } from "next/server";
import { randomBytes } from "node:crypto";
import { bff } from "@/lib/bff";
import { callIdentity } from "@/lib/server/identity";
import { guardMutation, problem } from "@/lib/server/http";
import { cookieValue } from "@/lib/server/cookies";

export const dynamic = "force-dynamic";

interface ChallengeBody {
  contact?: { value?: unknown; type?: unknown };
  regionHint?: unknown;
  preferredChannel?: unknown;
}

/** POST /api/identity/challenge — request a one-time code for a WhatsApp number or email. */
export async function POST(req: Request) {
  // Long-lived opaque device id (httpOnly, shared with the module's per-device hint) feeds
  // identity-service's distinct-contacts-per-device limit and our own limiter.
  const deviceName = (await bff.internals()).names.device;
  const existing = cookieValue(req, deviceName);
  const deviceId =
    existing && existing.length >= 20
      ? existing
      : randomBytes(24).toString("base64url");

  const parsed: { body: ChallengeBody | null } = { body: null };
  const ctx = await guardMutation(req, "challenge", async () => {
    parsed.body = (await req
      .clone()
      .json()
      .catch(() => null)) as ChallengeBody | null;
    const v =
      typeof parsed.body?.contact?.value === "string"
        ? parsed.body.contact.value.trim()
        : "";
    return { contact: v || undefined, device: deviceId };
  });
  if (ctx instanceof NextResponse) return ctx;
  const body = parsed.body;
  if (!body || typeof body !== "object") return problem(400, "CONTACT_INVALID");
  const value =
    typeof body.contact?.value === "string" ? body.contact.value.trim() : "";
  if (!value || value.length > 254) return problem(400, "CONTACT_INVALID");
  const type = body.contact?.type;
  const regionHint =
    typeof body.regionHint === "string" && /^[A-Z]{2}$/.test(body.regionHint)
      ? body.regionHint
      : undefined;
  const channel = body.preferredChannel;

  const res = await callIdentity<{
    challengeId: string;
    contactType: string;
    maskedContact: string;
    channel: string;
    expiresInSeconds: number;
    resendAfterSeconds: number;
  }>("POST", "/api/internal/auth/challenges", {
    correlationId: ctx.correlationId,
    clientIp: ctx.clientIp,
    body: {
      contact: {
        value,
        ...(type === "WHATSAPP" || type === "EMAIL" ? { type } : {}),
      },
      ...(regionHint ? { regionHint } : {}),
      clientIp: ctx.clientIp,
      deviceId,
      ...(channel === "WHATSAPP" || channel === "EMAIL"
        ? { preferredChannel: channel }
        : {}),
    },
  });

  if (!res.ok) {
    const { status, ...rest } = res.error;
    return problem(status, rest.errorCode, rest);
  }
  const d = res.data;
  const response = NextResponse.json(
    {
      challengeId: d.challengeId,
      contactType: d.contactType,
      maskedContact: d.maskedContact,
      channel: d.channel,
      expiresInSeconds: d.expiresInSeconds,
      resendAfterSeconds: d.resendAfterSeconds,
    },
    {
      status: 202,
      headers: {
        "cache-control": "no-store",
        "x-correlation-id": ctx.correlationId,
      },
    },
  );
  if (!existing || existing !== deviceId) {
    response.cookies.set(deviceName, deviceId, {
      httpOnly: true,
      secure: bff.config.secure,
      sameSite: "lax",
      path: "/",
      maxAge: 60 * 60 * 24 * 365,
    });
  }
  return response;
}
