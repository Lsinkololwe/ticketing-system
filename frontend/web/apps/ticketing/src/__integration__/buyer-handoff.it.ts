import { createServer, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import path from "node:path";
import { existsSync } from "node:fs";
import { randomUUID } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { jar } from "@/test/nextHeadersStub";

/**
 * Buyer handoff against REAL Keycloak 26.5.2 (testcontainers) with the built plugin jar
 * (backend/keycloak-extensions) and the docker-resources/keycloak buyer realm, real Redis, and a stub
 * identity-service (handle redemption, account status, revocations) and gateway on the host.
 *
 * Opt in: BUYER_IT=1. Needs Docker, quay.io/keycloak/keycloak:26.5.2 and the packaged jar.
 */
const enabled = process.env.BUYER_IT === "1";
const REPO = path.resolve(import.meta.dirname, "../../../../../..");
const JAR = path.join(
  REPO,
  "backend/keycloak-extensions/target/keycloak-extensions-1.0.0.jar",
);
const REALM = path.join(REPO, "../docker-resources/keycloak/myticketzm-realm.json");
const APP = "http://localhost:3000";
const WEB_SECRET = "web-secret-for-it";
const PLUGIN_SECRET = "plugin-secret-for-it";
const GATEWAY = "api-gateway-it";

class Browser {
  cookies = new Map<string, string>();
  private keep(res: Response) {
    for (const c of res.headers.getSetCookie()) {
      const [pair] = c.split(";");
      const i = pair.indexOf("=");
      const v = pair.slice(i + 1);
      if (v === "" || /Max-Age=0|expires=Thu, 01 Jan 1970/i.test(c))
        this.cookies.delete(pair.slice(0, i));
      else this.cookies.set(pair.slice(0, i), v);
    }
  }
  /** Follows redirects by hand; stops (returning the Location) at the first one that starts with `stopAt`. */
  async go(
    url: string,
    stopAt: string,
  ): Promise<{ location?: string; status: number; html: string }> {
    let current = url;
    for (let hop = 0; hop < 15; hop++) {
      const cookie = [...this.cookies].map(([k, v]) => `${k}=${v}`).join("; ");
      const res = await fetch(current, {
        redirect: "manual",
        headers: cookie ? { cookie } : {},
      });
      this.keep(res);
      const loc = res.headers.get("location");
      if (res.status >= 300 && res.status < 400 && loc) {
        const abs = new URL(loc, current).toString();
        if (abs.startsWith(stopAt))
          return { location: abs, status: res.status, html: "" };
        current = abs;
        continue;
      }
      return { status: res.status, html: await res.text() };
    }
    throw new Error("too many redirects");
  }
}

const claims = (jwt: string) =>
  JSON.parse(Buffer.from(jwt.split(".")[1], "base64url").toString());

describe.skipIf(!enabled)(
  "buyer handoff: real Keycloak 26.5.2 + Redis + plugin jar",
  () => {
    let kc: import("testcontainers").StartedTestContainer;
    let redisC: import("testcontainers").StartedTestContainer;
    let base = "";
    let idp: Server;
    let gw: Server;
    let bcl: Server;
    const handles = new Map<string, string>();
    const revocations: Array<Record<string, unknown>> = [];
    const gatewayAuth: string[] = [];
    let bff: import("@pml.tickets/shared/auth/bff").Bff;
    const browser = new Browser();

    const listen = (s: Server) =>
      new Promise<number>((r) =>
        s.listen(0, "0.0.0.0", () => r((s.address() as AddressInfo).port)),
      );
    const readBody = (req: import("node:http").IncomingMessage) =>
      new Promise<string>((r) => {
        let b = "";
        req.on("data", (c) => (b += c));
        req.on("end", () => r(b));
      });

    async function adminToken() {
      const r = await fetch(
        `${base}/realms/master/protocol/openid-connect/token`,
        {
          method: "POST",
          body: new URLSearchParams({
            grant_type: "password",
            client_id: "admin-cli",
            username: "admin",
            password: "admin",
          }),
        },
      );
      return ((await r.json()) as { access_token: string }).access_token;
    }
    async function admin(method: string, p: string, body?: unknown) {
      return fetch(`${base}/admin/realms${p}`, {
        method,
        headers: {
          authorization: `Bearer ${await adminToken()}`,
          "content-type": "application/json",
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    }
    async function createBuyer(): Promise<{ accountId: string; kcId: string }> {
      const accountId = randomUUID();
      const r = await admin("POST", "/myticketzm/users", {
        username: accountId,
        enabled: true,
        attributes: { accountId: [accountId] },
      });
      expect(r.status).toBe(201);
      return {
        accountId,
        kcId: (r.headers.get("location") as string).split("/").pop() as string,
      };
    }

    // ---- app side: the same calls the identify routes and Next make ---------------------------
    const appReq = (p: string, init: RequestInit = {}) => {
      const cookie = [...jar].map(([k, v]) => `${k}=${v}`).join("; ");
      return new Request(APP + p, {
        ...init,
        headers: {
          ...(cookie ? { cookie } : {}),
          ...((init.headers as Record<string, string>) ?? {}),
        },
      });
    };
    const absorb = (res: Response) => {
      for (const c of res.headers.getSetCookie()) {
        const [pair, ...attrs] = c.split(";");
        const i = pair.indexOf("=");
        if (pair.slice(i + 1) === "" || attrs.some((a) => /max-age=0/i.test(a)))
          jar.delete(pair.slice(0, i));
        else jar.set(pair.slice(0, i), pair.slice(i + 1));
      }
      return res;
    };
    /** What /api/identity/ensure does once identity-service issued a handle. */
    async function ensureHandle(accountId: string, handle: string) {
      handles.set(handle, accountId);
      const { setCookie } = await bff.flow.attachHandle(
        appReq("/api/identity/ensure", { method: "POST" }),
        { value: handle, accountId, isNew: false, ttlSec: 60 },
      );
      absorb(
        new Response(null, {
          headers: setCookie.map((c) => ["set-cookie", c]) as [
            string,
            string,
          ][],
        }),
      );
    }
    /** start -> Keycloak (plugin redeems the handle, no page) -> callback. Returns the callback response. */
    async function signIn(next = "/my-tickets") {
      const start = absorb(
        await bff.handlers.start(appReq(`/api/auth/start?next=${next}`)),
      );
      expect(start.status).toBe(303);
      let target = start.headers.get("location") as string;
      // A previous Keycloak session is ended through the RP-logout hop first; follow it back to resume.
      let hopped = false;
      for (let i = 0; i < 3; i++) {
        const step = await browser.go(target, APP);
        expect(
          step.location,
          `Keycloak answered ${step.status}: ${step.html.slice(0, 300)}`,
        ).toBeTruthy();
        const u = new URL(step.location as string);
        if (u.pathname === "/api/auth/start") {
          hopped = true;
          const resume = absorb(
            await bff.handlers.start(appReq(`${u.pathname}${u.search}`)),
          );
          target = resume.headers.get("location") as string;
          continue;
        }
        const cb = absorb(
          await bff.handlers.callback(appReq(`${u.pathname}${u.search}`)),
        );
        return { cb, hopped };
      }
      throw new Error("did not reach the callback");
    }
    const currentSession = async () => {
      const d = await bff.internals();
      return d.sessions.load(jar.get(d.names.session), { touch: false });
    };

    beforeAll(async () => {
      if (!enabled) return;
      expect(
        existsSync(JAR),
        "package backend/keycloak-extensions first (mvn -o package)",
      ).toBe(true);
      const { GenericContainer, Wait } = await import("testcontainers");
      const fs = await import("node:fs/promises");
      const os = await import("node:os");

      idp = createServer(async (req, res) => {
        const url = new URL(req.url as string, "http://x");
        const body = await readBody(req);
        const json = (status: number, o: unknown) => {
          res.writeHead(status, { "content-type": "application/json" });
          res.end(JSON.stringify(o));
        };
        if (url.pathname === "/token")
          return json(200, { access_token: "svc", expires_in: 300 });
        if (url.pathname === "/api/internal/auth/handles/redeem") {
          const h =
            (JSON.parse(body || "{}") as { handle?: string }).handle ?? "";
          const account = handles.get(h);
          handles.delete(h);
          return account
            ? json(200, { accountId: account })
            : json(400, { errorCode: "LOGIN_HANDLE_INVALID" });
        }
        const st = /^\/api\/internal\/auth\/accounts\/(.+)\/status$/.exec(
          url.pathname,
        );
        if (st)
          return json(200, {
            accountId: decodeURIComponent(st[1]),
            status: "ACTIVE",
          });
        if (url.pathname === "/api/internal/revocations/logout") {
          revocations.push(JSON.parse(body || "{}"));
          return json(200, { revoked: true });
        }
        return json(200, {});
      });
      gw = createServer((req, res) => {
        gatewayAuth.push(String(req.headers.authorization ?? ""));
        res.writeHead(200, { "content-type": "application/json" });
        res.end('{"data":{"ok":true}}');
      });
      bcl = createServer(async (req, res) => {
        const body = await readBody(req);
        const r = await bff.handlers["backchannel-logout"](
          new Request(`${APP}/api/auth/backchannel-logout`, {
            method: "POST",
            headers: { "content-type": "application/x-www-form-urlencoded" },
            body,
          }),
        );
        res.writeHead(r.status);
        res.end();
      });
      const [idpPort, gwPort, bclPort] = [
        await listen(idp),
        await listen(gw),
        await listen(bcl),
      ];

      redisC = await new GenericContainer("redis:7-alpine")
        .withExposedPorts(6379)
        .withWaitStrategy(Wait.forLogMessage(/Ready to accept connections/))
        .start();

      const tmp = await fs.mkdtemp(path.join(os.tmpdir(), "buyer-it-"));
      await fs.copyFile(REALM, path.join(tmp, "myticketzm-realm.json"));
      kc = await new GenericContainer("quay.io/keycloak/keycloak:26.5.2")
        .withExposedPorts(8080)
        .withExtraHosts([
          { host: "host.docker.internal", ipAddress: "host-gateway" },
        ])
        .withEnvironment({
          KC_BOOTSTRAP_ADMIN_USERNAME: "admin",
          KC_BOOTSTRAP_ADMIN_PASSWORD: "admin",
          KC_HTTP_ENABLED: "true",
          KC_HOSTNAME_STRICT: "false",
          IDENTITY_BASE_URL: `http://host.docker.internal:${idpPort}`,
          IDENTITY_CLIENT_ID: "keycloak-otp-authenticator",
          IDENTITY_CLIENT_SECRET: PLUGIN_SECRET,
          KEYCLOAK_TOKEN_URL:
            "http://localhost:8080/realms/myticketzm/protocol/openid-connect/token",
          TICKETING_REALM_NAME: "myticketzm",
          TICKETING_REALM_DISPLAY_NAME: "MyTicket Zambia",
          TICKETING_REALM_DISPLAY_HTML: "MyTicket Zambia",
          TICKETING_LOGIN_THEME: "keycloak",
          TICKETING_DEFAULT_ROLE: "default-roles-myticketzm",
          TICKETING_BUYER_APP_URL: APP,
          TICKETING_ORGANIZER_APP_URL: "http://localhost:3010",
          TICKETING_ADMIN_APP_URL: "http://localhost:3030",
          ADMIN_WEB_CLIENT_SECRET: "a",
          TICKETING_MOBILE_CLIENT_ID: "myticketzm-mobile",
          TICKETING_CATALOG_SERVICE_CLIENT_ID: "catalog-service",
          TICKETING_BOOKING_SERVICE_CLIENT_ID: "booking-service",
          TICKETING_API_GATEWAY_CLIENT_ID: GATEWAY,
          TICKETING_IDENTITY_SERVICE_CLIENT_ID: "identity-service",
          TICKETING_OTP_AUTHENTICATOR_CLIENT_ID: "keycloak-otp-authenticator",
          TICKETING_ORGANIZER_CLIENT_ID: "myticketzm-organizer",
          WEB_CLIENT_SECRET: WEB_SECRET,
          CATALOG_SERVICE_SECRET: "s1",
          BOOKING_SERVICE_SECRET: "s2",
          API_GATEWAY_SECRET: "s3",
          IDENTITY_SERVICE_SECRET: "s4",
          KEYCLOAK_OTP_AUTHENTICATOR_SECRET: PLUGIN_SECRET,
          ORGANIZER_PORTAL_CLIENT_SECRET: "s6",
        })
        .withCopyFilesToContainer([
          {
            source: JAR,
            target: "/opt/keycloak/providers/keycloak-extensions.jar",
          },
          {
            source: path.join(tmp, "myticketzm-realm.json"),
            target: "/opt/keycloak/data/import/myticketzm-realm.json",
          },
        ])
        .withCommand(["start-dev", "--import-realm"])
        .withWaitStrategy(
          Wait.forHttp("/realms/myticketzm", 8080)
            .forStatusCode(200)
            .withStartupTimeout(240_000),
        )
        .start();
      base = `http://${kc.getHost()}:${kc.getMappedPort(8080)}`;

      // Point the client's back-channel logout at this process (the realm points at the real app URL).
      const clients = (await (
        await admin("GET", "/myticketzm/clients?clientId=myticketzm-web")
      ).json()) as Array<Record<string, any>>;
      const web = clients[0];
      web.attributes["backchannel.logout.url"] =
        `http://host.docker.internal:${bclPort}/api/auth/backchannel-logout`;
      expect(
        (await admin("PUT", `/myticketzm/clients/${web.id}`, web)).status,
      ).toBe(204);

      Object.assign(process.env, {
        APP_URL: APP,
        KEYCLOAK_ISSUER: `${base}/realms/myticketzm`,
        KEYCLOAK_CLIENT_ID: "myticketzm-web",
        KEYCLOAK_CLIENT_SECRET: WEB_SECRET,
        API_AUDIENCE: GATEWAY,
        IDENTITY_BASE_URL: `http://127.0.0.1:${idpPort}`,
        IDENTITY_TOKEN_URL: `http://127.0.0.1:${idpPort}/token`,
        IDENTITY_CLIENT_ID: "svc",
        IDENTITY_CLIENT_SECRET: "x",
        GRAPHQL_URL: `http://127.0.0.1:${gwPort}/graphql`,
        BFF_ENC_KEYS: `k1:${Buffer.alloc(32, 9).toString("base64")}`,
        REDIS_URL: `redis://${redisC.getHost()}:${redisC.getMappedPort(6379)}`,
      });
      const { createBff } = await import("@pml.tickets/shared/auth/bff");
      const { buyerBffConfig } = await import("@/lib/bff");
      bff = createBff(buyerBffConfig({ production: false, trustProxyHops: 1 }));
    });

    afterAll(async () => {
      await Promise.allSettled([kc?.stop(), redisC?.stop()]);
      for (const s of [idp, gw, bcl]) s?.close();
    });

    it("handoff: the handle becomes a session with no Keycloak page; accountId claim equals the issued account; GraphQL carries the bearer", async () => {
      jar.clear();
      const { accountId } = await createBuyer();
      await ensureHandle(accountId, "handle-1");
      const { cb } = await signIn("/my-tickets");
      expect(cb.status).toBe(303);
      expect(new URL(cb.headers.get("location") as string).pathname).toBe(
        "/my-tickets",
      );
      const s = await currentSession();
      expect(s?.record.accountId).toBe(accountId);
      const at = claims(s?.record.accessToken as string);
      expect(at.aud).toContain(GATEWAY);
      expect(at.accountId).toBe(accountId);
      expect(handles.has("handle-1")).toBe(false); // redeemed once by the plugin

      const g = await bff.upstream
        .graphql({ allowAnonymous: true })
        .POST(
          appReq("/api/graphql", {
            method: "POST",
            headers: {
              origin: APP,
              "x-pml-csrf": "1",
              "content-type": "application/json",
              "x-forwarded-for": "198.51.100.1",
            },
            body: '{"query":"{me{id}}"}',
          }),
        );
      expect(g.status).toBe(200);
      expect(gatewayAuth.at(-1)).toBe(`Bearer ${s?.record.accessToken}`);
    });

    it("a different buyer on the same browser: the RP-logout hop ends the old SSO session and the new account gets its own session", async () => {
      const a = (await currentSession())?.record.accountId;
      const b = await createBuyer();
      expect(b.accountId).not.toBe(a);
      await ensureHandle(b.accountId, "handle-2");
      const { cb, hopped } = await signIn("/profile");
      expect(
        hopped,
        "start must hop through Keycloak logout while an SSO session may exist",
      ).toBe(true);
      expect(new URL(cb.headers.get("location") as string).pathname).toBe(
        "/profile",
      );
      expect((await currentSession())?.record.accountId).toBe(b.accountId);
    });

    it("a replayed or forged handle never yields a session (Keycloak falls back to its own page, no code)", async () => {
      jar.clear();
      browser.cookies.clear();
      const { accountId } = await createBuyer();
      await ensureHandle(accountId, "handle-3");
      handles.delete("handle-3"); // spent elsewhere
      const start = absorb(await bff.handlers.start(appReq("/api/auth/start")));
      const step = await browser.go(
        start.headers.get("location") as string,
        APP,
      );
      expect(step.location).toBeUndefined();
      expect(step.status).toBe(200);
      expect(jar.has("pml_buyer")).toBe(false);
    });

    it("logout: POST /api/auth/logout destroys the session, revokes via identity-service and ends the Keycloak session", async () => {
      jar.clear();
      browser.cookies.clear();
      const { accountId } = await createBuyer();
      await ensureHandle(accountId, "handle-4");
      await signIn();
      const before = revocations.length;
      const out = absorb(
        await bff.handlers.logout(
          appReq("/api/auth/logout", {
            method: "POST",
            headers: { origin: APP, "x-forwarded-for": "198.51.100.2" },
          }),
        ),
      );
      expect(out.status).toBe(303);
      expect(await currentSession()).toBeNull();
      expect(revocations.length).toBeGreaterThan(before);
      const end = await browser.go(out.headers.get("location") as string, APP);
      expect(end.location).toMatch(/^http:\/\/localhost:3000\/?/);
      // the Keycloak SSO cookie is gone: authorize without a handle shows a page (the contact screen)
      const probe = await browser.go(
        `${base}/realms/myticketzm/protocol/openid-connect/auth?client_id=myticketzm-web&response_type=code&scope=openid&redirect_uri=${encodeURIComponent(APP + "/api/auth/callback")}&state=x&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256`,
        APP,
      );
      expect(probe.location).toBeUndefined();
    });

    it("back-channel logout from Keycloak deletes the BFF session", async () => {
      jar.clear();
      browser.cookies.clear();
      const { accountId, kcId } = await createBuyer();
      await ensureHandle(accountId, "handle-5");
      await signIn();
      expect(await currentSession()).not.toBeNull();
      expect(
        (await admin("POST", `/myticketzm/users/${kcId}/logout`)).status,
      ).toBe(204);
      const until = Date.now() + 20_000;
      while ((await currentSession()) && Date.now() < until)
        await new Promise((r) => setTimeout(r, 300));
      expect(await currentSession()).toBeNull();
    });
  },
);
