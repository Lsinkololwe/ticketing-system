import type { BffConfig, createBff } from '@pml.tickets/shared/auth/bff';
import { STAFF_ROLES } from '@/config/navigation';

/** Roles that may enter the console at all; per-module gating lives in config/navigation. */
export const STAFF_ACCESS_ROLES: string[] = [...STAFF_ROLES];

/** Seconds since the last interactive sign-in that sensitive finance/config actions tolerate. */
export const STEP_UP_SEC = 300;

type CspOptions = NonNullable<NonNullable<Parameters<typeof createBff>[1]>['csp']>;
type Env = Record<string, string | undefined>;

/**
 * Platform admin: realm myticketzm-admin, cookie __Host-pml_admin, SameSite=Strict, idle 15 min, absolute 8 h.
 * Pure so the integration test builds the same configuration the app runs with (overrides inject a clock,
 * store or fetch). `required` decides what a missing variable means (the app throws; `next build` substitutes).
 */
export function adminBffConfig(env: Env, required: (name: string) => string, overrides: Partial<BffConfig> = {}): BffConfig {
  return {
    app: 'admin',
    appUrl: required('APP_URL'),
    oidc: {
      issuer: required('KEYCLOAK_ISSUER'),
      internalIssuer: env.KEYCLOAK_INTERNAL_ISSUER,
      clientId: required('KEYCLOAK_CLIENT_ID'),
      clientSecret: required('KEYCLOAK_CLIENT_SECRET'),
    },
    login: { loginPath: '/login' },
    access: { roles: STAFF_ACCESS_ROLES, audience: env.API_AUDIENCE },
    guarded: [{ prefix: '/', roles: STAFF_ACCESS_ROLES }],
    publicPaths: ['/login', '/unauthorized'],
    redis: { url: env.REDIS_URL },
    encKeys: required('BFF_ENC_KEYS'),
    trustProxyHops: Number(env.TRUST_PROXY_HOPS ?? 0),
    upstream: { graphql: env.GRAPHQL_URL, rest: env.API_BASE_URL },
    identity: env.IDENTITY_BASE_URL
      ? {
          baseUrl: env.IDENTITY_BASE_URL,
          tokenUrl: `${env.KEYCLOAK_ISSUER}/protocol/openid-connect/token`,
          clientId: required('IDENTITY_CLIENT_ID'),
          clientSecret: required('IDENTITY_CLIENT_SECRET'),
        }
      : undefined,
    ...overrides,
  };
}

export function adminCsp(issuer: string): CspOptions {
  return {
    styleSrc: ['https://fonts.googleapis.com'],
    fontSrc: ['https://fonts.gstatic.com', 'data:'],
    // The logout form is answered with a redirect to Keycloak's end-session endpoint; browsers apply form-action to it.
    formAction: [new URL(issuer).origin],
  };
}
