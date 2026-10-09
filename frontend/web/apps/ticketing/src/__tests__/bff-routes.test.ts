import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  absorb,
  APP,
  jar,
  jsonResponse,
  kc,
  mockFetch,
  problemResponse,
  req,
  send,
  setupServerEnv,
  signInAs,
} from "@/test/server";

vi.mock("@/lib/bff", async (orig) =>
  (await import("@/test/server")).bffModule(await orig()),
);

import { bff } from "@/lib/bff";
import {
  GET as authGet,
  POST as authPost,
} from "@/app/api/auth/[action]/route";
import { POST as graphql } from "@/app/api/graphql/route";
import { POST as intent } from "@/app/api/checkout/intent/route";
import { config as proxyConfig } from "@/proxy";

const SESSION = "pml_buyer";
const FLOW = "pml_buyer_f";
const status = (s: string) => ({
  "/accounts/acc-1/status": () =>
    jsonResponse({ accountId: "acc-1", status: s }),
});
const loc = (r: Response) => new URL(r.headers.get("location") as string, APP);
/** The `?error=` of a refusal, whether the redirect goes straight to /auth or first through Keycloak's end-session hop. */
function errorOf(r: Response) {
  const u = loc(r);
  const back = u.searchParams.get("post_logout_redirect_uri");
  return (back ? new URL(back) : u).searchParams.get("error");
}

async function withHandle(handle = "H-1", accountId = "acc-1") {
  const { setCookie } = await bff.flow.attachHandle(req("/x"), {
    value: handle,
    accountId,
    isNew: true,
    ttlSec: 60,
  });
  absorb(
    new Response(null, {
      headers: setCookie.map((c) => ["set-cookie", c]) as [string, string][],
    }),
  );
}
const start = (qs = "") =>
  sendA(
    authGet,
    req(`/api/auth/start${qs}`, undefined, {}, "GET").clone() as Request,
  ).catch(() => null);

/** Next passes `{ params: { action } }` to the `[action]` dispatcher. */
const actions = new WeakMap<Request, string>();
const withAction = (r: Request, action: string) => (actions.set(r, action), r);
const sendA = async (fn: typeof authGet, r: Request) =>
  absorb(
    await fn(r, {
      params: Promise.resolve({ action: actions.get(r) as string }),
    }),
  );

beforeEach(() => {
  setupServerEnv();
  vi.unstubAllGlobals();
});

describe("buyer BFF config", () => {
  it("is a buyer handoff BFF: account id claim, /auth login page, guarded buyer prefixes", () => {
    expect(bff.config).toMatchObject({
      app: "buyer",
      mode: "handoff",
      loginPath: "/auth",
      access: { accountClaim: "accountId" },
      guarded: [{ prefix: "/my-tickets" }, { prefix: "/profile" }],
    });
  });

  it("the proxy skips api, static assets and prefetches", () => {
    const [m] = proxyConfig.matcher;
    expect(m.source).toContain("(?!api|_next/static|_next/image|favicon.ico)");
    expect(m.missing.map((x) => x.key)).toEqual([
      "next-router-prefetch",
      "purpose",
    ]);
  });
});

describe("GET /api/auth/start (handoff)", () => {
  it("redirects to Keycloak with PKCE, state, login_hint and prompt=login; consumes the handle", async () => {
    await withHandle("H-1");
    const res = await sendA(
      authGet,
      withAction(
        req("/api/auth/start?next=/events/e1/book", undefined, {}, "GET"),
        "start",
      ),
    );
    expect(res.status).toBe(303);
    const u = loc(res);
    expect(u.origin + u.pathname).toBe(
      "http://kc.test/realms/myticketzm/protocol/openid-connect/auth",
    );
    expect(u.searchParams.get("client_id")).toBe("myticketzm-web");
    expect(u.searchParams.get("code_challenge_method")).toBe("S256");
    expect(u.searchParams.get("login_hint")).toBe("H-1");
    expect(u.searchParams.get("prompt")).toBe("login");
    expect(u.searchParams.get("redirect_uri")).toBe(`${APP}/api/auth/callback`);
    const again = await sendA(
      authGet,
      withAction(req("/api/auth/start", undefined, {}, "GET"), "start"),
    );
    expect(loc(again).searchParams.get("error")).toBe("LOGIN_HANDLE_INVALID");
  });

  it("does not accept a handle from the query string and refuses cross-site navigations", async () => {
    const res = await sendA(
      authGet,
      withAction(
        req(
          "/api/auth/start?handle=EVIL&login_hint=EVIL",
          undefined,
          {},
          "GET",
        ),
        "start",
      ),
    );
    expect(loc(res).searchParams.get("error")).toBe("LOGIN_HANDLE_INVALID");
    await withHandle();
    const cross = await sendA(
      authGet,
      withAction(
        req(
          "/api/auth/start",
          undefined,
          { "sec-fetch-site": "cross-site" },
          "GET",
        ),
        "start",
      ),
    );
    expect(loc(cross).pathname).toBe("/auth");
    expect(loc(cross).searchParams.get("error")).toBe("LOGIN_FAILED");
  });
});

describe("GET /api/auth/callback", () => {
  it("creates the session only when the account is ACTIVE and restores the parked cart", async () => {
    mockFetch(status("ACTIVE"));
    await send(
      intent,
      req("/api/checkout/intent", { eventId: "e1", quantities: { t1: 2 } }),
    );
    const res = await signInAs(bff, "acc-1", { next: "/events/e1/book" });
    expect(res.status).toBe(303);
    expect(loc(res).pathname).toBe("/events/e1/book");
    expect(jar.has(SESSION)).toBe(true);
    const s = await bff.getSession();
    expect(s).toMatchObject({ accountId: "acc-1", displayName: "Jane Doe" });
    const { getCartFor } = await import("@/lib/server/cart");
    expect(await getCartFor("e1")).toEqual({ t1: 2 });
  });

  it.each([
    ["PROVISIONING", "SETUP"],
    ["MERGED", "SETUP"],
    ["SUSPENDED", "ACCOUNT_SUSPENDED"],
  ])("refuses %s with ?error=%s and no session", async (st, error) => {
    mockFetch(status(st));
    const res = await signInAs(bff, "acc-1");
    expect(errorOf(res)).toBe(error);
    expect(jar.has(SESSION)).toBe(false);
  });

  it("does not create a session when the status check fails", async () => {
    mockFetch({
      "/status": () =>
        problemResponse(503, { errorCode: "SERVICE_UNAVAILABLE" }),
    });
    const res = await signInAs(bff, "acc-1");
    expect(errorOf(res)).toBe("SERVICE_UNAVAILABLE");
    expect(jar.has(SESSION)).toBe(false);
  });

  it("rejects a token for another account than the one the handle was issued for", async () => {
    mockFetch(status("ACTIVE"));
    await withHandle("H-9", "acc-1");
    const s = await sendA(
      authGet,
      withAction(req("/api/auth/start", undefined, {}, "GET"), "start"),
    );
    const { code, state } = kc.issueCode(s.headers.get("location") as string, {
      sub: "kc-other",
      accountId: "acc-OTHER",
    });
    const res = await sendA(
      authGet,
      withAction(
        req(
          `/api/auth/callback?code=${code}&state=${state}`,
          undefined,
          {},
          "GET",
        ),
        "callback",
      ),
    );
    expect(errorOf(res)).toBe("SIGN_IN_FAILED");
    expect(jar.has(SESSION)).toBe(false);
  });

  it("rejects a state mismatch (login CSRF)", async () => {
    mockFetch(status("ACTIVE"));
    await withHandle();
    const s = await sendA(
      authGet,
      withAction(req("/api/auth/start", undefined, {}, "GET"), "start"),
    );
    const { code } = kc.issueCode(s.headers.get("location") as string, {
      sub: "kc-1",
      accountId: "acc-1",
    });
    const res = await sendA(
      authGet,
      withAction(
        req(
          `/api/auth/callback?code=${code}&state=WRONG`,
          undefined,
          {},
          "GET",
        ),
        "callback",
      ),
    );
    expect(errorOf(res)).toBe("SIGN_IN_FAILED");
    expect(jar.has(SESSION)).toBe(false);
  });
});

describe("POST /api/graphql (same-origin BFF proxy)", () => {
  it("attaches the server-held token and never exposes it", async () => {
    const m = mockFetch({
      ...status("ACTIVE"),
      "gw.test/graphql": () => jsonResponse({ data: { ok: true } }),
    });
    await signInAs(bff, "acc-1");
    const res = await graphql(req("/api/graphql", { query: "{ me { id } }" }));
    expect(res.status).toBe(200);
    const sent = m.calls.find((c) => c.url === "http://gw.test/graphql");
    const auth = new Headers(sent?.init?.headers).get(
      "authorization",
    ) as string;
    expect(auth).toMatch(/^Bearer ey/);
    expect(JSON.stringify(await res.json())).not.toContain(auth.slice(7));
  });

  it("forwards anonymous public queries without a token and rejects foreign origins", async () => {
    const m = mockFetch({
      "gw.test/graphql": () => jsonResponse({ data: {} }),
    });
    await graphql(
      req(
        "/api/graphql",
        { query: "{ events { id } }" },
        { "x-forwarded-for": "203.0.113.50" },
      ),
    );
    expect(
      new Headers(m.calls[0].init?.headers).get("authorization"),
    ).toBeNull();
    const evil = await graphql(
      req("/api/graphql", { query: "{a}" }, { origin: "https://evil.example" }),
    );
    expect(evil.status).toBe(403);
  });
});

describe("checkout intent", () => {
  it("parks the cart in the flow before sign-in and validates its shape", async () => {
    const ok = await send(
      intent,
      req("/api/checkout/intent", {
        eventId: "e1",
        quantities: { t1: 2, t2: 0 },
      }),
    );
    expect(ok.status).toBe(200);
    expect(jar.has(FLOW)).toBe(true);
    const { getCartFor } = await import("@/lib/server/cart");
    expect(await getCartFor("e1")).toEqual({ t1: 2 });
    expect(
      (
        await intent(
          req("/api/checkout/intent", {
            eventId: "e1",
            quantities: { t1: 9999 },
          }),
        )
      ).status,
    ).toBe(400);
    expect(
      (
        await intent(
          req(
            "/api/checkout/intent",
            { eventId: "e1", quantities: {} },
            { origin: "https://evil.example" },
          ),
        )
      ).status,
    ).toBe(403);
    expect(
      (
        await intent(
          req(
            "/api/checkout/intent",
            { eventId: "e1", quantities: {} },
            { "x-pml-csrf": "" },
          ),
        )
      ).status,
    ).toBe(403);
  });

  it("keeps the cart in the session once signed in", async () => {
    mockFetch(status("ACTIVE"));
    await signInAs(bff, "acc-1");
    const ok = await send(
      intent,
      req("/api/checkout/intent", { eventId: "e2", quantities: { t9: 1 } }),
    );
    expect(ok.status).toBe(200);
    const { getCartFor } = await import("@/lib/server/cart");
    expect(await getCartFor("e2")).toEqual({ t9: 1 });
  });
});

describe("logout and back-channel logout", () => {
  it("POST /api/auth/logout destroys the session and ends the Keycloak session", async () => {
    mockFetch({
      ...status("ACTIVE"),
      "/revocations/logout": () => jsonResponse({}),
    });
    await signInAs(bff, "acc-1");
    expect(await bff.getSession()).not.toBeNull();
    const res = await sendA(
      authPost,
      withAction(
        req("/api/auth/logout", undefined, { "x-pml-csrf": "" }),
        "logout",
      ),
    );
    expect(res.status).toBe(303);
    expect(loc(res).pathname).toBe(
      "/realms/myticketzm/protocol/openid-connect/logout",
    );
    expect(jar.has(SESSION)).toBe(false);
    expect(await bff.getSession()).toBeNull();
  });

  it("POST /api/auth/backchannel-logout deletes the sessions of the sid", async () => {
    mockFetch({
      ...status("ACTIVE"),
      "/revocations/logout": () => jsonResponse({}),
    });
    await signInAs(bff, "acc-1");
    const d = await bff.internals();
    const live = await d.sessions.load(jar.get(SESSION));
    const logout_token = await kc.logoutToken({ sid: live?.record.kcSid });
    const r = new Request(`${APP}/api/auth/backchannel-logout`, {
      method: "POST",
      headers: {
        "content-type": "application/x-www-form-urlencoded",
        "x-forwarded-for": "198.51.100.7",
      },
      body: new URLSearchParams({ logout_token }),
    });
    expect(
      (await sendA(authPost, withAction(r, "backchannel-logout"))).status,
    ).toBe(200);
    expect(await bff.getSession()).toBeNull();
    const bad = new Request(`${APP}/api/auth/backchannel-logout`, {
      method: "POST",
      headers: {
        "content-type": "application/x-www-form-urlencoded",
        "x-forwarded-for": "198.51.100.7",
      },
      body: new URLSearchParams({ logout_token: "not.a.jwt" }),
    });
    expect(
      (await sendA(authPost, withAction(bad, "backchannel-logout"))).status,
    ).toBe(400);
  });
});
