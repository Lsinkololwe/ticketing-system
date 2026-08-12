/**
 * The fail-closed guard for sensitive frontend operations.
 *
 * ## Per-operation policy
 *
 * The strict policy is opted into per operation rather than applied globally, so an unreachable
 * revocation store degrades read paths but blocks operations that grant or transfer
 * money-adjacent capability. Mirrors `@FailClosedOnRevocation` on the backend.
 *
 * ## Relationship to the backend guard
 *
 * The backend re-checks independently, since a Next.js route handler is not a trust boundary.
 * Checking here as well gives the user a coherent message and avoids sending a mutation that
 * would be refused.
 *
 * @module libs/shared/src/auth/revocation/failClosed
 */

import { RevocationUnavailableError, TokenRevokedError } from './errors';
import type { IRevocationService } from './IRevocationService';
import type { RevocationDecision, TokenClaims } from './types';

/**
 * Throws unless the token is definitively active.
 *
 * @param service   the app's revocation service
 * @param claims    the caller's token claims
 * @param operation label used in the thrown error and in logs, e.g. `organizer.submitForReview`
 *
 * @throws {TokenRevokedError} 401 — the token was revoked
 * @throws {RevocationUnavailableError} 503 — no store could answer
 */
export async function assertNotRevoked(
  service: IRevocationService,
  claims: TokenClaims,
  operation: string
): Promise<void> {
  const decision = await service.check(claims);
  applyDecision(decision, operation);
}

/** The policy itself, split out so it can be unit-tested without a service. */
export function applyDecision(decision: RevocationDecision, operation: string): void {
  switch (decision) {
    case 'ACTIVE':
      return;
    case 'REVOKED':
      throw new TokenRevokedError(operation);
    case 'UNKNOWN':
      throw new RevocationUnavailableError(operation);
  }
}

/**
 * The operations on the organizer path that must not run on an unverified token.
 *
 * Held as data rather than scattered literals so the frontend and backend tiers can be diffed
 * against each other. Mirrors the `@FailClosedOnRevocation` annotations on
 * `OrganizationMutationResolver`.
 */
export const FAIL_CLOSED_ORGANIZER_OPERATIONS = [
  'organizer.apply',
  'organizer.updateApplication',
  'organizer.submitForReview',
  'organizer.getOrCreateOrganization',
  'organizer.upgradeToBusiness',
  'organizer.updateOrganization',
  'organizer.updateOrganizationSettings',
  'admin.approveOrganization',
  'admin.requestOrganizationChanges',
  'admin.rejectOrganization',
  'admin.suspendOrganization',
  'admin.unsuspendOrganization',
  'admin.updateOrganizationStatus',
] as const;

export type FailClosedOrganizerOperation =
  (typeof FAIL_CLOSED_ORGANIZER_OPERATIONS)[number];
