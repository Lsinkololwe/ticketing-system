import { SessionService, type SessionRecord } from '../../libs/shared/src/auth/bff/session';
import { createRedisStore, type RedisStore } from '../../libs/shared/src/auth/bff/store';
import type { BffApp } from '../../libs/shared/src/auth/bff/config';

/**
 * Seeds BFF sessions straight into Redis with the shared module's own SessionService (the same
 * sealing, keys and TTLs the app uses), so protected pages render with chosen roles and claims.
 *
 * THIS BYPASSES KEYCLOAK. No login, no token exchange, no id_token, no back-channel logout is
 * exercised here. The real OIDC flow is covered by the BFF integration tests
 * (libs/shared/src/auth/bff/__integration__, real Keycloak + Redis). A seeded access token is an
 * opaque string the fake upstream accepts; it is never verified.
 */
export interface SeedOptions {
  accountId?: string | null;
  sub?: string;
  roles?: string[];
  displayName?: string | null;
  /** Epoch seconds of the last interactive login; default now (fresh, so step-up is not demanded). */
  authTime?: number;
  acr?: string | null;
  ext?: Record<string, unknown>;
}

/** Must equal BFF_ENC_KEYS handed to the app. */
export const HARNESS_ENC_KEYS = `harness:${Buffer.alloc(32, 9).toString('base64')}`;
export const COOKIE_BASE: Record<BffApp, string> = { buyer: 'pml_buyer', organizer: 'pml_org', admin: 'pml_admin' };

export interface Seeder {
  /** Creates a session and returns the cookie to add to the browser context (http origin, so no __Host- prefix). */
  seed(opts?: SeedOptions): Promise<{ name: string; value: string; url: string }>;
  close(): Promise<void>;
}

export async function createSeeder(app: BffApp, redisUrl: string, appUrl: string): Promise<Seeder> {
  const store: RedisStore = await createRedisStore(redisUrl);
  const noop = () => undefined;
  const sessions = new SessionService(
    { app, idleSec: 1800, absoluteSec: 28800, clock: Date.now, logger: { debug: noop, info: noop, warn: noop, error: noop } },
    store,
    HARNESS_ENC_KEYS,
  );
  return {
    async seed(o = {}) {
      const now = Date.now();
      const record: SessionRecord = {
        v: 1,
        accountId: o.accountId === undefined ? 'acct-harness' : o.accountId,
        kcSid: `sid-${now}-${Math.random().toString(36).slice(2)}`,
        sub: o.sub ?? 'sub-harness',
        roles: o.roles ?? [],
        aud: ['myticketzm-api'],
        displayName: o.displayName === undefined ? 'Harness User' : o.displayName,
        accessToken: 'harness-access-token',
        refreshToken: null,
        idToken: null,
        atExp: Math.floor(now / 1000) + 3600,
        rtVer: 0,
        authTime: o.authTime ?? Math.floor(now / 1000),
        acr: o.acr ?? null,
        createdAt: now,
        ext: o.ext ?? {},
      };
      const { cookieValue } = await sessions.create(record);
      // https (HARNESS_MODE=start behind the TLS front) means the BFF uses the __Host- cookie name.
      return { name: `${appUrl.startsWith('https:') ? '__Host-' : ''}${COOKIE_BASE[app]}`, value: cookieValue, url: appUrl };
    },
    close: () => store.quit(),
  };
}
