import { z } from 'zod';
import type { Logger, LogLevel } from './logger';
import type { KeyValueStore } from './store';
import type { RateRule } from './ratelimit';
import type { JWTPayload } from 'jose';

export type BffApp = 'buyer' | 'organizer' | 'admin';

export interface PostLoginContext {
  accountId: string | null;
  sub: string;
  roles: string[];
  claims: JWTPayload;
  correlationId: string;
}
export type PostLoginResult =
  | { ok: true; displayName?: string | null; accountId?: string; ext?: Record<string, unknown> }
  /** `error` becomes `?error=` on the login page. `endSso` also ends the Keycloak session we just created. */
  | { ok: false; error: string; endSso?: boolean };

export interface GuardedPrefix {
  prefix: string;
  /** Any-of. Empty/omitted: authentication only. */
  roles?: string[];
}

export interface BffConfig {
  app: BffApp;
  /** Public origin, e.g. https://admin.example.com (no trailing slash). */
  appUrl: string;
  cookie?: { name?: string; sameSite?: 'lax' | 'strict' };
  oidc: {
    /** Public issuer (iss claim, browser redirects). */
    issuer: string;
    /** Server-to-server base when it differs from the public one. */
    internalIssuer?: string;
    clientId: string;
    clientSecret: string;
    scopes?: string[];
    /** Extra authorize parameters, e.g. `{ acr_values: 'loa2' }`. */
    authorizeParams?: Record<string, string>;
  };
  login?: {
    mode?: 'redirect' | 'handoff';
    /** Where failures and unauthenticated users go. Default `/login` (buyer: `/auth`). */
    loginPath?: string;
    unauthorizedPath?: string;
    postLogoutPath?: string;
  };
  access?: {
    /** Any-of realm roles required to sign in at all. */
    roles?: string[];
    /** Required `aud` entry of the access token (resource audience). */
    audience?: string;
    /** Token claim holding the account id; when set it is required. */
    accountClaim?: string;
  };
  lifetimes?: { idleSec?: number; absoluteSec?: number; refreshSkewSec?: number };
  guarded?: GuardedPrefix[];
  publicPaths?: string[];
  upstream?: { graphql?: string; rest?: string; maxBodyBytes?: number; timeoutMs?: number };
  redis?: { url?: string; store?: KeyValueStore };
  /** `kid:base64key[,kid2:base64key2]` (32-byte keys). */
  encKeys: string;
  trustProxyHops?: number;
  identity?: { baseUrl: string; tokenUrl: string; clientId: string; clientSecret: string; timeoutMs?: number };
  postLogin?: (ctx: PostLoginContext) => Promise<PostLoginResult>;
  rateLimits?: Record<string, RateRule[]>;
  refresh?: { lockMs?: number; pollMs?: number; waitMs?: number; timeoutMs?: number; casRetries?: number };
  logLevel?: LogLevel;
  logger?: Logger;
  /** Injectables for tests. */
  clock?: () => number;
  fetch?: typeof fetch;
  production?: boolean;
}

const schema = z.object({
  app: z.enum(['buyer', 'organizer', 'admin']),
  appUrl: z.string().url(),
  oidc: z.object({
    issuer: z.string().url(),
    clientId: z.string().min(1),
    clientSecret: z.string().min(1),
  }),
  encKeys: z.string().min(1),
});

export interface ResolvedConfig extends BffConfig {
  appOrigin: string;
  secure: boolean;
  production: boolean;
  cookieBase: string;
  sameSite: 'lax' | 'strict';
  loginPath: string;
  unauthorizedPath: string;
  postLogoutPath: string;
  mode: 'redirect' | 'handoff';
  scopes: string[];
  idleSec: number;
  absoluteSec: number;
  refreshSkewSec: number;
  trustProxyHops: number;
  clock: () => number;
  fetchImpl: typeof fetch;
  logger: Logger;
}

const DEFAULTS: Record<BffApp, { idle: number; abs: number; cookie: string; login: string; sameSite: 'lax' | 'strict' }> = {
  buyer: { idle: 1800, abs: 28800, cookie: 'pml_buyer', login: '/auth', sameSite: 'lax' },
  organizer: { idle: 1800, abs: 28800, cookie: 'pml_org', login: '/login', sameSite: 'lax' },
  admin: { idle: 900, abs: 28800, cookie: 'pml_admin', login: '/login', sameSite: 'strict' },
};

export function resolveConfig(config: BffConfig, createLog: (c: BffConfig) => Logger): ResolvedConfig {
  schema.parse(config);
  const d = DEFAULTS[config.app];
  const appUrl = config.appUrl.replace(/\/+$/, '');
  const u = new URL(appUrl);
  const production = config.production ?? process.env.NODE_ENV === 'production';
  const secure = u.protocol === 'https:';
  if (production && !secure) throw new Error('BFF: APP_URL must be https in production');
  const idleSec = config.lifetimes?.idleSec ?? d.idle;
  const absoluteSec = config.lifetimes?.absoluteSec ?? d.abs;
  if (idleSec <= 0 || absoluteSec < idleSec) throw new Error('BFF: invalid session lifetimes');
  return {
    ...config,
    appUrl,
    appOrigin: u.origin,
    secure,
    production,
    cookieBase: config.cookie?.name ?? d.cookie,
    sameSite: config.cookie?.sameSite ?? d.sameSite,
    loginPath: config.login?.loginPath ?? d.login,
    unauthorizedPath: config.login?.unauthorizedPath ?? '/unauthorized',
    postLogoutPath: config.login?.postLogoutPath ?? '/',
    mode: config.login?.mode ?? 'redirect',
    scopes: config.oidc.scopes ?? ['openid', 'profile'],
    idleSec,
    absoluteSec,
    refreshSkewSec: config.lifetimes?.refreshSkewSec ?? 60,
    trustProxyHops: config.trustProxyHops ?? 0,
    clock: config.clock ?? Date.now,
    fetchImpl: config.fetch ?? fetch,
    logger: config.logger ?? createLog(config),
  };
}
