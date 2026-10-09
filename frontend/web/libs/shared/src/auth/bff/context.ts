import type { ResolvedConfig } from './config';
import type { CookieNames } from './cookies';
import type { FlowService } from './flow';
import type { OidcClient } from './oidc';
import type { RateLimiter } from './ratelimit';
import type { TokenManager } from './refresh';
import type { RevocationAdapter } from './revocation';
import type { SessionService } from './session';

/** Everything handlers need, built once per process by createBff. */
export interface BffDeps {
  cfg: ResolvedConfig;
  names: CookieNames;
  sessions: SessionService;
  flows: FlowService;
  tokens: TokenManager;
  oidc: OidcClient;
  limiter: RateLimiter;
  revocation: RevocationAdapter | null;
}
