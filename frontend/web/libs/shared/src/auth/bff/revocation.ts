import { IdentityRevocationClient } from '../revocation/IdentityRevocationClient';
import { createServiceAccountTokenProvider } from '../revocation/serviceAccountToken';
import type { RevocationReason } from '../revocation/types';
import type { ResolvedConfig } from './config';

export interface RevokeInput {
  jti?: string;
  sid?: string;
  sub?: string;
  reason: RevocationReason;
}

export interface RevocationAdapter {
  /** Idempotent identity-service write. Throws RevocationWriteError when it did not persist. */
  revoke(input: RevokeInput): Promise<void>;
}

/**
 * Thin adapter over the existing identity-service client. The BFF never writes `pml:*` keys; the
 * authoritative writer stays identity-service (Mongo + Redis + events).
 */
export function createRevocationAdapter(cfg: Pick<ResolvedConfig, 'identity' | 'app' | 'fetchImpl'>): RevocationAdapter | null {
  const id = cfg.identity;
  if (!id) return null;
  const client = new IdentityRevocationClient({
    baseUrl: id.baseUrl,
    timeoutMs: id.timeoutMs ?? 3000,
    fetchImpl: cfg.fetchImpl,
    getAccessToken: createServiceAccountTokenProvider({
      tokenUrl: id.tokenUrl,
      clientId: id.clientId,
      clientSecret: id.clientSecret,
      scope: 'openid internal-write internal-read',
      fetchImpl: cfg.fetchImpl,
    }),
  });
  return {
    async revoke(input) {
      await client.revokeSignOut({ ...input, revokedBy: `${cfg.app}-web` });
    },
  };
}
