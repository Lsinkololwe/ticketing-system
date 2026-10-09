import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  jar,
  jsonResponse,
  mockFetch,
  req,
  setupServerEnv,
  signInAs,
} from "@/test/server";

vi.mock("@/lib/bff", async (orig) =>
  (await import("@/test/server")).bffModule(await orig()),
);

import { bff } from "@/lib/bff";
import { POST as action } from "@/app/api/profile/contacts/[action]/route";
import { GET as list } from "@/app/api/profile/contacts/route";

const CID = "123e4567-e89b-12d3-a456-426614174000";
const call = (
  name: string,
  body: unknown,
  headers: Record<string, string> = {},
) =>
  action(req(`/api/profile/contacts/${name}`, body, headers), {
    params: Promise.resolve({ action: name }),
  });
const gqlError = (errorCode: string, extra: Record<string, unknown> = {}) =>
  jsonResponse({
    data: null,
    errors: [
      { message: "internal detail", extensions: { errorCode, ...extra } },
    ],
  });

let n = 0;
async function signIn() {
  // Real handoff sign-in over the fake Keycloak; a fresh ACTIVE account per test keeps limiter counters apart.
  mockFetch({ "/status": () => jsonResponse({ status: "ACTIVE" }) });
  await signInAs(bff, `acc-${++n}`);
}

beforeEach(async () => {
  setupServerEnv();
  vi.unstubAllGlobals();
  await signIn();
});

describe("GET /api/profile/contacts", () => {
  it("lists masked contacts with the user token and drops unknown fields", async () => {
    const m = mockFetch({
      "/graphql": () =>
        jsonResponse({
          data: {
            myContacts: {
              contacts: [
                {
                  id: "c1",
                  type: "EMAIL",
                  valueMasked: "j***@gmail.com",
                  verifiedAt: "2026-10-01T00:00:00Z",
                  primary: true,
                  valueEncrypted: "LEAK",
                },
              ],
              pendingChange: {
                changeId: "chg-1",
                kind: "CHANGE",
                newContactMasked: "k***@x.com",
                expiresAt: "2026-10-06T00:00:00Z",
                currentContactVerified: true,
                attemptsRemaining: 4,
                raw: "LEAK",
              },
            },
          },
        }),
    });
    const res = await list();
    const body = await res.json();
    const text = JSON.stringify(body);
    expect(body.pendingChange).toMatchObject({
      changeId: "chg-1",
      currentContactVerified: true,
      attemptsRemaining: 4,
    });
    expect(text).toContain("j***@gmail.com");
    expect(text).toContain("k***@x.com");
    expect(text).not.toContain("LEAK");
    const headers = m.calls.find((c) => c.url.includes("/graphql"))?.init
      ?.headers as Record<string, string>;
    expect(headers.authorization).toMatch(/^Bearer ey/);
  });

  it("is 401 without a session", async () => {
    jar.clear();
    mockFetch({});
    expect((await list()).status).toBe(401);
  });
});

describe("POST /api/profile/contacts/* limiter", () => {
  it("applies the shared per-account policy and answers 429 with retry-after", async () => {
    mockFetch({ "/graphql": () => gqlError("CONTACT_UNKNOWN") });
    let last = 0;
    for (let i = 0; i < 12; i++)
      last = (await call("change-cancel", { changeId: "change-12345" })).status;
    expect(last).toBe(429);
  });
});

describe("POST /api/profile/contacts/*", () => {
  it("rejects wrong origin / missing CSRF header without calling upstream", async () => {
    const m = mockFetch({});
    expect(
      (
        await call(
          "primary-request",
          { contactId: "contact-1234" },
          { origin: "https://evil.example" },
        )
      ).status,
    ).toBe(403);
    expect(
      (
        await call(
          "primary-request",
          { contactId: "contact-1234" },
          { "x-pml-csrf": "" },
        )
      ).status,
    ).toBe(403);
    expect(m.fn).not.toHaveBeenCalled();
  });

  it("requires a session and 404s unknown actions", async () => {
    mockFetch({});
    jar.clear();
    expect(
      (await call("primary-request", { contactId: "contact-1234" })).status,
    ).toBe(401);
    expect((await call("nope", {})).status).toBe(404);
  });

  it("add-request returns only the challenge fields; the raw value goes upstream only", async () => {
    const m = mockFetch({
      "/graphql": () =>
        jsonResponse({
          data: {
            requestContactAdd: {
              challengeId: CID,
              maskedContact: "j***@gmail.com",
              contactType: "EMAIL",
              expiresInSeconds: 300,
              resendAfterSeconds: 60,
              code: "123456",
            },
          },
        }),
    });
    const res = await call("add-request", {
      value: "j@gmail.com",
      type: "EMAIL",
    });
    expect(res.status).toBe(200);
    const text = JSON.stringify(await res.json());
    expect(text).not.toContain("j@gmail.com");
    expect(text).not.toContain("123456");
    expect(text).toContain(CID);
    expect(String(m.calls.at(-1)?.init?.body)).toContain("j@gmail.com");
  });

  it("validates input before calling upstream", async () => {
    const m = mockFetch({});
    expect(
      (await call("add-request", { value: "x", type: "FAX" })).status,
    ).toBe(400);
    expect(
      (await call("add-confirm", { challengeId: CID, code: "12" })).status,
    ).toBe(400);
    expect(m.fn).not.toHaveBeenCalled();
  });

  it("change-request returns both challenges", async () => {
    mockFetch({
      "/graphql": () =>
        jsonResponse({
          data: {
            requestContactChange: {
              changeId: "change-12345",
              expiresAt: "2026-10-06T00:00:00Z",
              newContact: {
                challengeId: CID,
                maskedContact: "n***@x.com",
                contactType: "EMAIL",
                expiresInSeconds: 300,
                resendAfterSeconds: 60,
              },
              currentContact: {
                challengeId: "prim-1234",
                maskedContact: "+260 97* ***123",
                contactType: "WHATSAPP",
                expiresInSeconds: 300,
                resendAfterSeconds: 60,
              },
            },
          },
        }),
    });
    const body = await (
      await call("change-request", {
        contactId: "contact-1234",
        value: "n@x.com",
        type: "EMAIL",
      })
    ).json();
    expect(body.changeId).toBe("change-12345");
    expect(body.currentContact.maskedContact).toBe("+260 97* ***123");
  });

  it.each([
    ["OTP_INVALID", 400, { attemptsRemaining: 3 }],
    ["OTP_LOCKED", 423, { lockedUntil: "2026-10-04T10:00:00Z" }],
    ["OTP_EXPIRED", 410, {}],
    ["CONTACT_ALREADY_CLAIMED", 409, {}],
    ["LAST_VERIFIED_CONTACT", 409, {}],
  ])(
    "maps %s to %i without leaking upstream detail",
    async (code, status, extra) => {
      mockFetch({ "/graphql": () => gqlError(code, extra) });
      const res = await call("add-confirm", {
        challengeId: CID,
        code: "000000",
      });
      expect(res.status).toBe(status);
      const body = await res.json();
      expect(body.errorCode).toBe(code);
      for (const [k, v] of Object.entries(extra)) expect(body[k]).toEqual(v);
      expect(JSON.stringify(body)).not.toContain("internal detail");
    },
  );

  it("claimed-by-other and a lost race are indistinguishable to the browser", async () => {
    mockFetch({ "/graphql": () => gqlError("CONTACT_ALREADY_CLAIMED") });
    const a = await call("add-confirm", { challengeId: CID, code: "123456" });
    const b = await call("change-confirm", {
      changeId: "change-12345",
      newCode: "123456",
    });
    expect(await a.json()).toEqual(await b.json());
  });

  it("removal of the last verified contact is refused with its code", async () => {
    mockFetch({ "/graphql": () => gqlError("LAST_VERIFIED_CONTACT") });
    const res = await call("remove-request", { contactId: "contact-1234" });
    expect(res.status).toBe(409);
    expect((await res.json()).errorCode).toBe("LAST_VERIFIED_CONTACT");
  });

  it("answers 202 with retry-after for APPLYING and sends the real input names", async () => {
    const m = mockFetch({
      "/graphql": () =>
        jsonResponse({
          data: {
            confirmContactChange: {
              changeId: "change-12345",
              kind: "CHANGE",
              status: "APPLYING",
              contacts: [{ id: "x", valueMasked: "LEAK" }],
            },
          },
        }),
    });
    const res = await call("change-confirm", {
      changeId: "change-12345",
      newCode: "123456",
      currentCode: "654321",
    });
    expect(res.status).toBe(202);
    expect(res.headers.get("retry-after")).toBe("2");
    const text = JSON.stringify(await res.json());
    expect(text).toContain("APPLYING");
    expect(text).not.toContain("LEAK");
    const sent = JSON.parse(String(m.calls.at(-1)?.init?.body));
    expect(sent.variables.input).toEqual({
      changeId: "change-12345",
      newContactCode: "123456",
      currentContactCode: "654321",
    });
  });

  it("cancels a pending change and confirms a primary switch", async () => {
    mockFetch({
      "/graphql": (init) =>
        String(init?.body).includes("cancelContactChange")
          ? jsonResponse({ data: { cancelContactChange: true } })
          : jsonResponse({
              data: {
                setPrimaryContact: {
                  changeId: "c",
                  kind: "PRIMARY",
                  status: "COMPLETED",
                },
              },
            }),
    });
    expect(
      await (await call("change-cancel", { changeId: "change-12345" })).json(),
    ).toEqual({ cancelled: true });
    const res = await call("primary-confirm", {
      contactId: "contact-1234",
      challengeId: CID,
      code: "123456",
    });
    expect(res.status).toBe(200);
    expect(await res.json()).toMatchObject({
      kind: "PRIMARY",
      status: "COMPLETED",
    });
  });

  it("resend passes challengeId or changeId+target, returns masked fields, and surfaces OTP_RATE_LIMITED", async () => {
    const m = mockFetch({
      "/graphql": () =>
        jsonResponse({
          data: {
            resendContactCode: {
              challengeId: CID,
              contactType: "EMAIL",
              maskedContact: "j***@gmail.com",
              expiresInSeconds: 300,
              resendAfterSeconds: 300,
              code: "123456",
            },
          },
        }),
    });
    const a = await call("resend", { challengeId: CID });
    expect(a.status).toBe(200);
    expect(JSON.stringify(await a.json())).not.toContain("123456");
    expect(
      JSON.parse(String(m.calls.at(-1)?.init?.body)).variables.input,
    ).toEqual({ challengeId: CID });
    await call("resend", { changeId: "change-12345", target: "CURRENT" });
    expect(
      JSON.parse(String(m.calls.at(-1)?.init?.body)).variables.input,
    ).toEqual({ changeId: "change-12345", target: "CURRENT" });
    expect((await call("resend", { target: "NEW" })).status).toBe(400);
    mockFetch({
      "/graphql": () =>
        gqlError("OTP_RATE_LIMITED", { retryAfterSeconds: 120 }),
    });
    const r = await call("resend", { challengeId: CID });
    expect(r.status).toBe(429);
    expect(await r.json()).toMatchObject({
      errorCode: "OTP_RATE_LIMITED",
      retryAfterSeconds: 120,
    });
  });

  it.each([
    ["CONTACT_UNKNOWN", 404],
    ["CONTACT_CHANGE_IN_PROGRESS", 409],
    ["NO_VERIFIED_CONTACT", 409],
  ])("maps %s", async (code, status) => {
    mockFetch({ "/graphql": () => gqlError(code) });
    expect(
      (await call("remove-request", { contactId: "contact-1234" })).status,
    ).toBe(status);
  });

  it("answers a generic 503 when the gateway is down and 401 when the token is rejected", async () => {
    mockFetch({
      "/graphql": () => {
        throw new Error("boom");
      },
    });
    expect(
      (await call("primary-request", { contactId: "contact-1234" })).status,
    ).toBe(503);
    mockFetch({ "/graphql": () => new Response("", { status: 401 }) });
    expect(
      (await call("primary-request", { contactId: "contact-1234" })).status,
    ).toBe(401);
  });
});
