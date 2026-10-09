import { vi } from "vitest";
import { createBff, type Bff } from "@pml.tickets/shared/auth/bff";
import {
  composeFetch,
  createFakeKeycloak,
  FakeClock,
  TEST_ENC_KEYS,
} from "@pml.tickets/shared/auth/bff/testing";
import { MemoryStore } from "@pml.tickets/shared/auth/bff/store/memory";
import { resetIdentityTokenForTests } from "@/lib/server/identity";

export { jar } from "./nextHeadersStub";
import { jar } from "./nextHeadersStub";

export const ISSUER = "http://kc.test/realms/myticketzm";
export const APP = "http://localhost:3001";

const clock = new FakeClock(Date.now());
export const kc = await createFakeKeycloak({
  issuer: ISSUER,
  clientId: "myticketzm-web",
  clientSecret: "secret",
  clock,
});
export const store = new MemoryStore(clock.now);

export function setupServerEnv() {
  Object.assign(process.env, {
    APP_URL: APP,
    KEYCLOAK_ISSUER: ISSUER,
    KEYCLOAK_CLIENT_ID: "myticketzm-web",
    KEYCLOAK_CLIENT_SECRET: "secret",
    IDENTITY_BASE_URL: "http://identity.test",
    IDENTITY_TOKEN_URL: "http://idp.test/token",
    IDENTITY_CLIENT_ID: "svc",
    IDENTITY_CLIENT_SECRET: "svc-secret",
    GRAPHQL_URL: "http://gw.test/graphql",
    BFF_ENC_KEYS: TEST_ENC_KEYS,
    TRUST_PROXY_HOPS: "1",
  });
  // Optional settings a developer's .env.local commonly sets: leaving them in changes which code path
  // the routes take, so the suite would pass or fail depending on the machine.
  for (const optional of ["REDIS_URL", "API_AUDIENCE", "API_BASE_URL"]) {
    delete process.env[optional];
  }
  jar.clear();
  resetIdentityTokenForTests();
}

/**
 * Builds the app's real BFF config over the fake Keycloak and an in-memory store. Use as
 * `vi.mock('@/lib/bff', async (orig) => (await import('@/test/server')).bffModule(await orig()))`.
 */
export function bffModule(actual: typeof import("@/lib/bff")) {
  setupServerEnv();
  const bff: Bff = createBff(
    actual.buyerBffConfig({
      redis: { store },
      fetch: composeFetch(kc.fetch, (input, init) => fetch(input, init)),
      clock: clock.now,
      production: false,
    }),
  );
  return { ...actual, bff };
}

export function jsonResponse(
  body: unknown,
  status = 200,
  contentType = "application/json",
) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": contentType },
  });
}
export const problemResponse = (
  status: number,
  body: Record<string, unknown>,
) => jsonResponse(body, status, "application/problem+json");

let ipCounter = 10;
let ipBlock = 1;
/** A fresh client address per call so rate-limit counters from other tests do not interfere. */
export const freshIp = () =>
  `10.${ipBlock}.${Math.floor(ipCounter / 250)}.${ipCounter++ % 250}`;

/** Absorbs Set-Cookie into the shared jar (which `next/headers` reads too). */
export function absorb(res: Response) {
  for (const c of res.headers.getSetCookie()) {
    const [pair, ...attrs] = c.split(";");
    const i = pair.indexOf("=");
    const name = pair.slice(0, i).trim();
    const value = pair.slice(i + 1).trim();
    if (!value || attrs.some((a) => /^\s*max-age=0\s*$/i.test(a)))
      jar.delete(name);
    else jar.set(name, value);
  }
  return res;
}

export function req(
  path: string,
  body?: unknown,
  headers: Record<string, string> = {},
  method = "POST",
) {
  const cookie = [...jar].map(([k, v]) => `${k}=${v}`).join("; ");
  return new Request(`${APP}${path}`, {
    method,
    headers: {
      ...(method === "GET" ? {} : { origin: APP, "x-pml-csrf": "1" }),
      "content-type": "application/json",
      "x-forwarded-for": freshIp(),
      ...(cookie ? { cookie } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

/** Runs a handler and keeps the cookie jar in step with its Set-Cookie headers. */
export async function send(
  handler: (r: Request) => Promise<Response>,
  request: Request,
) {
  return absorb(await handler(request));
}

/** fetch mock routing by URL substring; the service-token call is answered automatically. */
export function mockFetch(
  routes: Record<
    string,
    (init: RequestInit | undefined, url: string) => Response
  >,
) {
  const calls: { url: string; init?: RequestInit }[] = [];
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    calls.push({ url, init });
    if (url === "http://idp.test/token")
      return jsonResponse({ access_token: "svc-token", expires_in: 300 });
    for (const [needle, handler] of Object.entries(routes)) {
      if (url.includes(needle)) return handler(init, url);
    }
    return new Response("not mocked: " + url, { status: 599 });
  });
  vi.stubGlobal("fetch", fn);
  return { fn, calls };
}

/** Signs in through the real start/callback handlers with a fake Keycloak; returns the session cookie value. */
export async function signInAs(
  bff: Bff,
  accountId: string,
  opts: { handle?: string; next?: string } = {},
) {
  const r = req("/api/identity/ensure", {}, {});
  const { setCookie } = await bff.flow.attachHandle(r, {
    value: opts.handle ?? `H-${accountId}`,
    accountId,
    isNew: false,
    ttlSec: 60,
  });
  absorb(
    new Response(null, {
      headers: setCookie.map((c) => ["set-cookie", c]) as [string, string][],
    }),
  );
  const start = await send(
    bff.handlers.start,
    req(`/api/auth/start?next=${opts.next ?? "/"}`, undefined, {}, "GET"),
  );
  const { code, state } = kc.issueCode(
    start.headers.get("location") as string,
    { sub: `kc-${accountId}`, accountId, name: "Jane Doe" },
  );
  return send(
    bff.handlers.callback,
    req(`/api/auth/callback?code=${code}&state=${state}`, undefined, {}, "GET"),
  );
}
