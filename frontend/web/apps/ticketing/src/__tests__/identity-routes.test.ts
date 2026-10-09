import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  absorb,
  jar,
  jsonResponse,
  mockFetch,
  problemResponse,
  req,
  send,
  setupServerEnv,
} from "@/test/server";

vi.mock("@/lib/bff", async (orig) =>
  (await import("@/test/server")).bffModule(await orig()),
);

import { bff } from "@/lib/bff";
import { POST as challenge } from "@/app/api/identity/challenge/route";
import { POST as verify } from "@/app/api/identity/verify/route";
import { POST as ensure } from "@/app/api/identity/ensure/route";

let n = 0;
const uniq = () => `u${++n}@gmail.com`;
const CHALLENGE_ID = "123e4567-e89b-12d3-a456-426614174000";

beforeEach(() => {
  setupServerEnv();
  vi.unstubAllGlobals();
});

describe("POST /api/identity/challenge", () => {
  it("forwards to identity-service with client ip, correlation id and service token", async () => {
    const m = mockFetch({
      "/api/internal/auth/challenges": () =>
        jsonResponse(
          {
            challengeId: CHALLENGE_ID,
            contactType: "EMAIL",
            maskedContact: "j***@gmail.com",
            channel: "EMAIL",
            expiresInSeconds: 300,
            resendAfterSeconds: 60,
            secret: "leak",
          },
          202,
        ),
    });
    const res = await challenge(
      req(
        "/api/identity/challenge",
        { contact: { value: uniq(), type: "EMAIL" } },
        { "x-forwarded-for": "203.0.113.9" },
      ),
    );
    expect(res.status).toBe(202);
    const body = await res.json();
    expect(body).toEqual({
      challengeId: CHALLENGE_ID,
      contactType: "EMAIL",
      maskedContact: "j***@gmail.com",
      channel: "EMAIL",
      expiresInSeconds: 300,
      resendAfterSeconds: 60,
    });
    const call = m.calls.find((c) =>
      c.url.endsWith("/api/internal/auth/challenges"),
    );
    const headers = call?.init?.headers as Record<string, string>;
    expect(headers.authorization).toBe("Bearer svc-token");
    expect(headers["x-correlation-id"]).toMatch(/^[0-9a-f-]{36}$/);
    const sent = JSON.parse(call?.init?.body as string);
    expect(sent.clientIp).toBe("203.0.113.9");
    expect(sent.deviceId).toBeTruthy();
  });

  it.each([
    [400, "CONTACT_INVALID"],
    [429, "OTP_RATE_LIMITED"],
    [429, "OTP_COOLDOWN_ACTIVE"],
    [423, "OTP_LOCKED"],
    [503, "OTP_DELIVERY_FAILED"],
  ])("maps problem+json %i %s", async (status, errorCode) => {
    mockFetch({
      "/api/internal/auth/challenges": () =>
        problemResponse(status, {
          type: "about:blank",
          title: "x",
          status,
          errorCode,
          retryAfterSeconds: 30,
          detail: "internal detail",
        }),
    });
    const res = await challenge(
      req("/api/identity/challenge", { contact: { value: uniq() } }),
    );
    expect(res.status).toBe(status);
    const body = await res.json();
    expect(body.errorCode).toBe(errorCode);
    expect(body.retryAfterSeconds).toBe(30);
    expect(JSON.stringify(body)).not.toContain("internal detail");
  });

  it("rejects a wrong origin (CSRF) without calling identity-service", async () => {
    const m = mockFetch({});
    const res = await challenge(
      req(
        "/api/identity/challenge",
        { contact: { value: "a@b.co" } },
        { origin: "https://evil.example" },
      ),
    );
    expect(res.status).toBe(403);
    expect((await res.json()).errorCode).toBe("ORIGIN_REJECTED");
    expect(m.fn).not.toHaveBeenCalled();
  });

  it("rejects a missing x-pml-csrf header and cross-site fetch metadata", async () => {
    const m = mockFetch({});
    const a = await challenge(
      req(
        "/api/identity/challenge",
        { contact: { value: "a@b.co" } },
        { "x-pml-csrf": "" },
      ),
    );
    const b = await challenge(
      req(
        "/api/identity/challenge",
        { contact: { value: "a@b.co" } },
        { "sec-fetch-site": "cross-site" },
      ),
    );
    expect(a.status).toBe(403);
    expect(b.status).toBe(403);
    expect(m.fn).not.toHaveBeenCalled();
  });

  it("rate limits per client ip (shared limiter policy challenge)", async () => {
    mockFetch({
      "/api/internal/auth/challenges": () =>
        jsonResponse(
          {
            challengeId: CHALLENGE_ID,
            contactType: "EMAIL",
            maskedContact: "x",
            channel: "EMAIL",
            expiresInSeconds: 300,
            resendAfterSeconds: 60,
          },
          202,
        ),
    });
    let last = 202;
    for (let i = 0; i < 12; i++) {
      last = (
        await challenge(
          req(
            "/api/identity/challenge",
            { contact: { value: uniq() } },
            { "x-forwarded-for": "203.0.113.77" },
          ),
        )
      ).status;
    }
    expect(last).toBe(429);
  });

  it("answers a generic 503 when identity-service is down", async () => {
    mockFetch({
      "/api/internal/auth/challenges": () => {
        throw new Error("boom");
      },
    });
    const res = await challenge(
      req("/api/identity/challenge", { contact: { value: uniq() } }),
    );
    expect(res.status).toBe(503);
    expect((await res.json()).errorCode).toBe("SERVICE_UNAVAILABLE");
  });
});

describe("POST /api/identity/verify", () => {
  it("keeps the proof server-side and never returns it", async () => {
    mockFetch({
      "/challenges/verify": () =>
        jsonResponse({
          proof: "PROOF-SECRET",
          contactType: "EMAIL",
          maskedContact: "j***@gmail.com",
          expiresInSeconds: 120,
        }),
    });
    const res = await send(
      verify,
      req("/api/identity/verify", {
        challengeId: CHALLENGE_ID,
        code: "123456",
      }),
    );
    expect(res.status).toBe(200);
    const text = JSON.stringify(await res.json());
    expect(text).not.toContain("PROOF-SECRET");
    expect(text).toContain('"verified":true');
    absorb(res);
    expect((await bff.flow.read(req("/x"))).rec.proof?.value).toBe(
      "PROOF-SECRET",
    );
  });

  it("returns OTP_INVALID with attemptsRemaining", async () => {
    mockFetch({
      "/challenges/verify": () =>
        problemResponse(400, {
          errorCode: "OTP_INVALID",
          attemptsRemaining: 3,
        }),
    });
    const res = await verify(
      req("/api/identity/verify", {
        challengeId: CHALLENGE_ID,
        code: "000000",
      }),
    );
    expect(res.status).toBe(400);
    expect(await res.json()).toMatchObject({
      errorCode: "OTP_INVALID",
      attemptsRemaining: 3,
    });
  });

  it.each([
    [410, "OTP_EXPIRED"],
    [423, "OTP_LOCKED"],
  ])("maps %i %s", async (status, errorCode) => {
    mockFetch({
      "/challenges/verify": () =>
        problemResponse(status, {
          errorCode,
          lockedUntil: "2026-10-04T10:00:00Z",
        }),
    });
    const res = await verify(
      req("/api/identity/verify", {
        challengeId: CHALLENGE_ID,
        code: "123456",
      }),
    );
    expect(res.status).toBe(status);
    expect((await res.json()).errorCode).toBe(errorCode);
  });

  it("rejects malformed codes locally", async () => {
    const m = mockFetch({});
    const res = await verify(
      req("/api/identity/verify", {
        challengeId: CHALLENGE_ID,
        code: "12ab56",
      }),
    );
    expect(res.status).toBe(400);
    expect(m.fn).not.toHaveBeenCalled();
  });

  it("rejects a foreign origin", async () => {
    mockFetch({});
    const res = await verify(
      req(
        "/api/identity/verify",
        { challengeId: CHALLENGE_ID, code: "123456" },
        { origin: "https://evil.example" },
      ),
    );
    expect(res.status).toBe(403);
  });
});

async function verifiedFlow() {
  mockFetch({
    "/challenges/verify": () =>
      jsonResponse({
        proof: "P1",
        contactType: "EMAIL",
        maskedContact: "j***",
        expiresInSeconds: 120,
      }),
  });
  await send(
    verify,
    req("/api/identity/verify", { challengeId: CHALLENGE_ID, code: "123456" }),
  );
}

describe("POST /api/identity/ensure", () => {
  it("requires a verified proof (PROOF_INVALID otherwise)", async () => {
    mockFetch({});
    const res = await ensure(req("/api/identity/ensure", { consent: true }));
    expect(res.status).toBe(400);
    expect((await res.json()).errorCode).toBe("PROOF_INVALID");
  });

  it("requires consent", async () => {
    await verifiedFlow();
    const res = await ensure(req("/api/identity/ensure", { consent: false }));
    expect((await res.json()).errorCode).toBe("CONSENT_REQUIRED");
  });

  it("never returns the login handle; stores it server-side and points at /api/auth/start", async () => {
    await verifiedFlow();
    const m = mockFetch({
      "/accounts/ensure": () =>
        jsonResponse({
          accountId: "acc-1",
          status: "ACTIVE",
          isNew: true,
          loginHandle: "HANDLE-SECRET",
        }),
    });
    const res = await send(
      ensure,
      req("/api/identity/ensure", { consent: true }),
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(JSON.stringify(body)).not.toContain("HANDLE-SECRET");
    absorb(res);
    expect(JSON.stringify([...jar.values()])).not.toContain("HANDLE-SECRET");
    expect(body).toEqual({
      status: "ACTIVE",
      isNew: true,
      next: "/api/auth/start",
    });
    const sent = JSON.parse(
      m.calls.find((c) => c.url.includes("/accounts/ensure"))?.init
        ?.body as string,
    );
    expect(sent).toMatchObject({
      proof: "P1",
      clientId: "myticketzm-web",
      issueHandle: true,
      consents: [{ purpose: "TERMS" }],
    });
    const flow = (await bff.flow.read(req("/x"))).rec;
    expect(flow.handle).toMatchObject({
      value: "HANDLE-SECRET",
      accountId: "acc-1",
      isNew: true,
    });
    expect(flow.proof).toBeUndefined();
  });

  it("passes isNew=false through for returning buyers", async () => {
    await verifiedFlow();
    mockFetch({
      "/accounts/ensure": () =>
        jsonResponse({
          accountId: "a",
          status: "ACTIVE",
          isNew: false,
          loginHandle: "H",
        }),
    });
    const res = await ensure(req("/api/identity/ensure", { consent: true }));
    expect((await res.json()).isNew).toBe(false);
  });

  it("202 PROVISIONING keeps the proof so the retry is idempotent", async () => {
    await verifiedFlow();
    mockFetch({
      "/accounts/ensure": () =>
        jsonResponse(
          { accountId: null, status: "PROVISIONING", retryAfterSeconds: 2 },
          202,
        ),
    });
    const res = await ensure(req("/api/identity/ensure", { consent: true }));
    expect(res.status).toBe(202);
    expect(await res.json()).toEqual({
      status: "PROVISIONING",
      retryAfterSeconds: 2,
    });
    expect((await bff.flow.read(req("/x"))).rec.proof?.value).toBe("P1");
  });

  it.each([
    [403, "ACCOUNT_SUSPENDED"],
    [409, "ACCOUNT_MERGING"],
    [400, "PROOF_INVALID"],
    [409, "CONTACT_ALREADY_CLAIMED"],
  ])("maps %i %s", async (status, errorCode) => {
    await verifiedFlow();
    mockFetch({
      "/accounts/ensure": () => problemResponse(status, { errorCode }),
    });
    const res = await ensure(req("/api/identity/ensure", { consent: true }));
    expect(res.status).toBe(status);
    expect((await res.json()).errorCode).toBe(errorCode);
  });

  it("rejects a foreign origin", async () => {
    await verifiedFlow();
    const res = await ensure(
      req(
        "/api/identity/ensure",
        { consent: true },
        { origin: "https://evil.example" },
      ),
    );
    expect(res.status).toBe(403);
  });
});
