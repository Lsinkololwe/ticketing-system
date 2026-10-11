import type { BffConfig } from '@pml.tickets/shared/auth/bff';

type Env = Record<string, string | undefined>;

/** Seconds since last interactive login within which money movement and bank changes are allowed. */
export const FRESH_AUTH_SEC = 300;

/** CSP additions for the console's webfonts. */
export const CSP_OPTIONS = {
  styleSrc: ['https://fonts.googleapis.com', 'https://fonts.cdnfonts.com'],
  fontSrc: ['data:', 'https://fonts.gstatic.com', 'https://fonts.googleapis.com', 'https://fonts.cdnfonts.com'],
};

/** Pure, env-driven organizer BFF config (no Next/server-only imports, so integration tests can build it). */
export function organizerBffConfig(env: Env): BffConfig {
  const issuer = env.KEYCLOAK_ISSUER ?? '';
  // The platform account id travels in the `accountId` claim (the organizer client maps it, exactly like the buyer
  // client). ACCOUNT_CLAIM='' selects the legacy behaviour where the Keycloak subject stands in for it.
  const accountClaim = env.ACCOUNT_CLAIM === undefined ? 'accountId' : env.ACCOUNT_CLAIM;
  return {
    app: 'organizer',
    appUrl: env.APP_URL ?? 'http://localhost:3003',
    oidc: {
      issuer,
      internalIssuer: env.KEYCLOAK_INTERNAL_ISSUER,
      clientId: env.KEYCLOAK_CLIENT_ID ?? '',
      clientSecret: env.KEYCLOAK_CLIENT_SECRET ?? '',
    },
    // Any signed-in account may enter: applicants have no organizer role until their application is approved.
    access: { audience: env.API_AUDIENCE || undefined, ...(accountClaim ? { accountClaim } : {}) },
    guarded: [
      // The console is for the members of an organization, not for holders of a realm role: the platform
      // grants ORGANIZER to the owner when the application is approved, while an invited administrator,
      // manager, marketer or contributor is an organization member with no realm role at all. Who may
      // enter is decided by membership and the organization's status (the layouts, from the backend's own
      // answer), and what they may do is decided by the backend's permissions on every operation.
      ...['/dashboard', '/events', '/bookings', '/finance', '/team', '/media', '/notifications', '/settings'].map(
        (prefix) => ({ prefix })
      ),
      // The application flow needs a session only.
      { prefix: '/welcome' },
      { prefix: '/apply' },
    ],
    // A signed-in account without an organizer role lands on the application flow, not an error page.
    login: { unauthorizedPath: '/welcome' },
    publicPaths: ['/login', '/unauthorized', '/logout', '/features'],
    redis: { url: env.REDIS_URL },
    encKeys: env.BFF_ENC_KEYS ?? '',
    trustProxyHops: Number(env.TRUST_PROXY_HOPS ?? 0),
    upstream: { graphql: env.GRAPHQL_URL, rest: env.API_BASE_URL },
    // Without an account claim the Keycloak subject is the organizer id. With one (the default) the claim wins:
    // the subject is NOT the platform account id, and finance, team and step-up all key on the account id.
    ...(accountClaim ? {} : { postLogin: async (ctx: { sub: string }) => ({ ok: true as const, accountId: ctx.sub }) }),
    identity: env.IDENTITY_BASE_URL && issuer
      ? {
          baseUrl: env.IDENTITY_BASE_URL,
          tokenUrl: `${env.KEYCLOAK_INTERNAL_ISSUER ?? issuer}/protocol/openid-connect/token`,
          clientId: env.IDENTITY_CLIENT_ID ?? '',
          clientSecret: env.IDENTITY_CLIENT_SECRET ?? '',
        }
      : undefined,
  };
}
