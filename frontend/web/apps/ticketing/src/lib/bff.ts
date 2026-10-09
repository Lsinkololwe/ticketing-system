import "server-only";
import { createBff, type BffConfig } from "@pml.tickets/shared/auth/bff";
import { callIdentity } from "@/lib/server/identity";
import { getServerEnv } from "@/lib/server/env";

/** Exported so tests can build the same config over a fake Keycloak and an in-memory store. */
export const buyerBffConfig = (over: Partial<BffConfig> = {}): BffConfig => {
  const e = getServerEnv();
  return {
    app: "buyer",
    appUrl: e.appUrl,
    oidc: {
      issuer: e.keycloakIssuer,
      internalIssuer: e.keycloakInternalIssuer,
      clientId: e.clientId,
      clientSecret: e.clientSecret,
    },
    login: { mode: "handoff", loginPath: "/auth" },
    access: { accountClaim: "accountId", audience: e.audience },
    guarded: [{ prefix: "/my-tickets" }, { prefix: "/profile" }],
    redis: { url: e.redisUrl },
    encKeys: e.encKeys,
    trustProxyHops: e.trustProxyHops,
    upstream: { graphql: e.graphqlUrl, rest: e.apiBaseUrl },
    identity: {
      baseUrl: e.identityBaseUrl,
      tokenUrl: e.identityTokenUrl,
      clientId: e.identityClientId,
      clientSecret: e.identityClientSecret,
    },
    // A session exists only for an ACTIVE account; anything else ends the Keycloak session too.
    postLogin: async ({ accountId, correlationId }) => {
      if (!accountId)
        return { ok: false, error: "SIGN_IN_FAILED", endSso: true };
      const r = await callIdentity<{ accountId?: string; status: string }>(
        "GET",
        `/api/internal/auth/accounts/${encodeURIComponent(accountId)}/status`,
        { correlationId },
      );
      if (!r.ok)
        return { ok: false, error: "SERVICE_UNAVAILABLE", endSso: true };
      if (r.data.status === "ACTIVE")
        return { ok: true, accountId: r.data.accountId ?? accountId };
      return {
        ok: false,
        error: r.data.status === "SUSPENDED" ? "ACCOUNT_SUSPENDED" : "SETUP",
        endSso: true,
      };
    },
    // `next build` evaluates routes with placeholder env: the production checks apply at runtime only.
    production:
      process.env.NEXT_PHASE === "phase-production-build" ? false : undefined,
    ...over,
  };
};

export const bff = createBff(buyerBffConfig());
