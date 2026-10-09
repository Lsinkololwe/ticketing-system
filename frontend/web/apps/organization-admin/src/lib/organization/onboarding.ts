import {
  NO_ORGANIZATION,
  parseStatus,
  unknownState,
  type OnboardingState,
} from '../onboarding/state';

interface OrgPayload {
  data?: {
    myOwnedOrganization?: { id: string; name: string; status: string } | null;
    /** The organization the caller belongs to by active membership (every role, not only the owner). */
    myOrganization?: { id: string; name: string; status: string } | null;
  };
  errors?: Array<{ message: string }>;
}

export interface OnboardingDeps {
  /** Server-side bearer for this request (BFF); null when the session has no usable token. */
  getAccessToken: () => Promise<string | null>;
  graphqlUrl: string;
  fetchImpl?: typeof fetch;
}

const describe = (e: unknown) => (e instanceof Error ? e.message : String(e));

/**
 * Resolve the caller's onboarding state. Every failure returns `unknown`, never `none`: "backend
 * unreachable" and "has not applied yet" are different facts and must route differently.
 */
export async function resolveOnboardingState(deps: OnboardingDeps): Promise<OnboardingState> {
  let token: string | null;
  try {
    token = await deps.getAccessToken();
  } catch (e) {
    return unknownState(`access token unavailable: ${describe(e)}`);
  }
  if (!token) return unknownState('access token unavailable');

  let payload: OrgPayload;
  try {
    const res = await (deps.fetchImpl ?? fetch)(deps.graphqlUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ query: 'query MyOrganizationStatus { myOwnedOrganization { id name status } myOrganization { id name status } }' }),
      cache: 'no-store',
    });
    if (!res.ok) return unknownState(`graphql http ${res.status}`);
    payload = (await res.json()) as OrgPayload;
  } catch (e) {
    return unknownState(`graphql request failed: ${describe(e)}`);
  }
  if (payload.errors?.length) return unknownState(`graphql errors: ${payload.errors.map((e) => e.message).join('; ')}`);
  if (!('data' in payload) || payload.data === undefined) return unknownState('graphql response had no data key');
  // Owners (including applicants who are still in onboarding) own their organization; every other
  // staff role only has a membership, so falling back to it keeps them out of the application flow.
  const org = payload.data?.myOwnedOrganization ?? payload.data?.myOrganization;
  if (!org) return NO_ORGANIZATION;
  const status = parseStatus(org.status);
  if (!status) return unknownState(`unrecognised status: ${org.status}`);
  return { kind: 'org', status, id: org.id, name: org.name ?? null };
}

export interface OrganizationAuthStatus {
  readonly hasOrganization: boolean;
  readonly id: string | null;
  readonly name: string | null;
  readonly status: string | null;
  readonly isApproved: boolean;
  readonly isPendingReview: boolean;
  readonly needsChanges: boolean;
  readonly isRejected: boolean;
  readonly isDraft: boolean;
}

export const NO_ORGANIZATION_STATUS: OrganizationAuthStatus = {
  hasOrganization: false,
  id: null,
  name: null,
  status: null,
  isApproved: false,
  isPendingReview: false,
  needsChanges: false,
  isRejected: false,
  isDraft: false,
};

/** Lifecycle booleans for an onboarding state. `unknown` reports no organization; route on the state, not on this. */
export function toAuthStatus(state: OnboardingState): OrganizationAuthStatus {
  if (state.kind !== 'org') return NO_ORGANIZATION_STATUS;
  const { status } = state;
  return {
    hasOrganization: true,
    id: state.id,
    name: state.name,
    status,
    isApproved: status === 'APPROVED' || status === 'ACTIVE',
    isPendingReview: status === 'PENDING_REVIEW',
    needsChanges: status === 'CHANGES_REQUESTED',
    isRejected: status === 'REJECTED',
    isDraft: status === 'DRAFT',
  };
}
