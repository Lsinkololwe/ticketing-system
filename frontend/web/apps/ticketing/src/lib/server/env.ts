import "server-only";

/**
 * Server-only configuration for the buyer app. Nothing here is prefixed NEXT_PUBLIC_, so none of
 * it can be inlined into a browser bundle. Read lazily so `next build` needs no secrets.
 * Dev fallbacks apply outside production; `next build` itself gets inert placeholders.
 */
const building = process.env.NEXT_PHASE === "phase-production-build";

function required(name: string, fallbackInDev?: string): string {
  const value = process.env[name]?.trim();
  if (value) return value;
  if (
    (building || process.env.NODE_ENV !== "production") &&
    fallbackInDev !== undefined
  )
    return fallbackInDev;
  throw new Error(`Missing required server environment variable ${name}`);
}

export function getServerEnv() {
  const keycloakIssuer = required(
    "KEYCLOAK_ISSUER",
    "http://localhost:8084/realms/myticketzm",
  );
  return {
    appUrl: required("APP_URL", "http://localhost:3001").replace(/\/$/, ""),
    keycloakIssuer,
    keycloakInternalIssuer:
      process.env.KEYCLOAK_INTERNAL_ISSUER?.trim() || undefined,
    clientId: process.env.KEYCLOAK_CLIENT_ID?.trim() || "myticketzm-web",
    clientSecret: required("KEYCLOAK_CLIENT_SECRET", "dev-secret"),
    audience: process.env.API_AUDIENCE?.trim() || undefined,
    redisUrl: process.env.REDIS_URL?.trim() || undefined,
    encKeys: required(
      "BFF_ENC_KEYS",
      "dev:" + Buffer.alloc(32, 7).toString("base64"),
    ),
    trustProxyHops: Number(process.env.TRUST_PROXY_HOPS ?? 0) || 0,
    graphqlUrl: required("GRAPHQL_URL", "http://localhost:8080/graphql"),
    apiBaseUrl: process.env.API_BASE_URL?.trim() || undefined,
    identityBaseUrl: required(
      "IDENTITY_BASE_URL",
      "http://localhost:8081",
    ).replace(/\/$/, ""),
    identityTokenUrl:
      process.env.IDENTITY_TOKEN_URL?.trim() ||
      `${keycloakIssuer}/protocol/openid-connect/token`,
    identityClientId: required("IDENTITY_CLIENT_ID", "buyer-web-service"),
    identityClientSecret: required("IDENTITY_CLIENT_SECRET", "dev-secret"),
    termsVersion: process.env.TERMS_VERSION?.trim() || "2026-10",
  };
}
