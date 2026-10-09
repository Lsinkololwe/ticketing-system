/**
 * Onboarding state and routing — the single source of truth.
 *
 * ISOMORPHIC: no `server-only`, no React, no Next imports. Server guards, client
 * components and tests all import from here so there is exactly one answer to
 * "where does this user belong?".
 *
 * ## Why this module exists
 *
 * The routing decision used to live in three places that disagreed:
 *   - `proxy.ts` hardcoded `/welcome` for every authenticated user
 *   - `(application)/layout.tsx` only redirected users who were already approved
 *   - `welcome/page.tsx` corrected the destination in a `useEffect`, after paint
 *
 * The result was that a user whose application was submitted and awaiting review
 * landed on the "set up your organization" screen on every login, and stayed
 * there whenever the client-side status query did not resolve. The fix is to
 * make the decision once, from data the server already has, before anything
 * renders — and to make "we could not determine the status" a state of its own
 * rather than something that collapses into "no application yet".
 *
 * ## The three-value state
 *
 * `unknown` is the important one. A failed status query must never be treated as
 * "this user has no organization", because that answer routes a fully submitted
 * applicant back to an empty form and invites them to apply a second time.
 *
 * @module lib/onboarding/state
 */

// =============================================================================
// STATUS
// =============================================================================

/**
 * Organization statuses this app is prepared to route.
 *
 * The backend enum (`com.pml.shared.constants.OrganizationStatus`) carries both
 * `APPROVED` and `ACTIVE` and has no `PENDING_DOCUMENTS`. The target lifecycle
 * has no `APPROVED` (`ACTIVE` is the operational state) and a
 * `PENDING_DOCUMENTS` state between `DRAFT` and `PENDING_REVIEW`.
 *
 * This union covers both vocabularies, so routing is correct whichever set of
 * statuses the backend sends. Every function below handles all of them
 * exhaustively.
 */
export type OrganizationStatus =
  | 'DRAFT'
  | 'PENDING_DOCUMENTS'
  | 'PENDING_REVIEW'
  | 'CHANGES_REQUESTED'
  | 'REJECTED'
  | 'APPROVED'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'INACTIVE'
  | 'PENDING_DELETION';

const KNOWN_STATUSES: ReadonlySet<string> = new Set<OrganizationStatus>([
  'DRAFT',
  'PENDING_DOCUMENTS',
  'PENDING_REVIEW',
  'CHANGES_REQUESTED',
  'REJECTED',
  'APPROVED',
  'ACTIVE',
  'SUSPENDED',
  'INACTIVE',
  'PENDING_DELETION',
]);

/**
 * Narrow an arbitrary backend string to a status this app can route.
 *
 * A status we do not recognise is deliberately NOT coerced to a default. It
 * becomes `null`, which callers turn into `unknown` — the safe state — rather
 * than into `none`, which would show the applicant an empty form.
 */
export function parseStatus(value: string | null | undefined): OrganizationStatus | null {
  if (!value) return null;
  return KNOWN_STATUSES.has(value) ? (value as OrganizationStatus) : null;
}

// =============================================================================
// STATE
// =============================================================================

/**
 * Where the current user stands in onboarding.
 *
 * - `none`    — authenticated, no organization document exists. The only state
 *               in which the welcome/setup screen is the correct destination.
 * - `org`     — an organization exists; `status` decides everything downstream.
 * - `unknown` — the status could not be established (backend down, token
 *               refresh failed, unrecognised status value). Distinct from
 *               `none` on purpose: see the module docstring.
 */
export type OnboardingState =
  | { readonly kind: 'none' }
  | { readonly kind: 'org'; readonly status: OrganizationStatus; readonly id: string; readonly name: string | null }
  | { readonly kind: 'unknown'; readonly reason: string };

export const NO_ORGANIZATION: OnboardingState = { kind: 'none' };

export function unknownState(reason: string): OnboardingState {
  return { kind: 'unknown', reason };
}

// =============================================================================
// CAPABILITIES (what each onboarding stage may access)
// =============================================================================

/**
 * May the applicant still edit the application?
 *
 * This is the predicate that gates the wizard. `PENDING_REVIEW` is absent
 * deliberately — an application under review is frozen, which is exactly what
 * the backend's `Organization.canBeEdited()` enforces. Letting the wizard open
 * in `PENDING_REVIEW` would produce a form the user could fill but never save.
 */
export function canEditApplication(status: OrganizationStatus): boolean {
  return status === 'DRAFT' || status === 'PENDING_DOCUMENTS' || status === 'CHANGES_REQUESTED';
}

/** Has the application been submitted and not yet decided? */
export function isUnderReview(status: OrganizationStatus): boolean {
  return status === 'PENDING_REVIEW';
}

/** Is the organization operational (may publish, invite, request payouts)? */
export function isOperational(status: OrganizationStatus): boolean {
  return status === 'APPROVED' || status === 'ACTIVE';
}

/**
 * May the applicant re-apply from scratch?
 *
 * `REJECTED → DRAFT` is a permitted transition. A rejection is a door the
 * applicant may walk back through; a suspension is not.
 */
export function canReapply(status: OrganizationStatus): boolean {
  return status === 'REJECTED';
}

/**
 * May a draft event be created?
 *
 * Permitted from `PENDING_REVIEW` onward. This is the decision that
 * makes the review wait tolerable — the applicant builds an event while they
 * wait instead of watching a holding page.
 */
export function canCreateDraftEvent(status: OrganizationStatus): boolean {
  return (
    status === 'PENDING_REVIEW' ||
    status === 'CHANGES_REQUESTED' ||
    isOperational(status)
  );
}

// =============================================================================
// ROUTES
// =============================================================================

export const ROUTES = {
  welcome: '/welcome',
  businessInfo: '/apply/business-info',
  documents: '/apply/documents',
  review: '/apply/review',
  status: '/apply/status',
  unavailable: '/apply/unavailable',
  dashboard: '/dashboard',
} as const;

/** The wizard steps, in order. */
export const WIZARD_STEPS = [
  ROUTES.businessInfo,
  ROUTES.documents,
  ROUTES.review,
] as const;

/**
 * The one canonical destination for a given state.
 *
 * Every guard in the app resolves through this function. No route string for
 * onboarding is written anywhere else.
 */
export function resolveRoute(state: OnboardingState): string {
  switch (state.kind) {
    case 'unknown':
      // Never the form. The applicant is told we could not load their
      // application and given a way to retry.
      return ROUTES.unavailable;

    case 'none':
      return ROUTES.welcome;

    case 'org':
      return routeForStatus(state.status);
  }
}

/** Canonical destination for a known status. Exhaustive over all ten. */
export function routeForStatus(status: OrganizationStatus): string {
  switch (status) {
    // Editable — the wizard opens at its first step.
    case 'DRAFT':
    case 'PENDING_DOCUMENTS':
    case 'CHANGES_REQUESTED':
      return ROUTES.businessInfo;

    // Submitted, decided against, or administratively held — a status screen,
    // never a form. `PENDING_REVIEW` landing here is the bug this module fixes.
    case 'PENDING_REVIEW':
    case 'REJECTED':
    case 'SUSPENDED':
    case 'INACTIVE':
    case 'PENDING_DELETION':
      return ROUTES.status;

    // Operational.
    case 'APPROVED':
    case 'ACTIVE':
      return ROUTES.dashboard;
  }
}

// =============================================================================
// ACCESS
// =============================================================================

/**
 * Is `pathname` a legitimate place for this user to be right now?
 *
 * Guards call this before redirecting so that a user who is already somewhere
 * valid is left alone. Without it, sending every request to `resolveRoute` would
 * bounce a user off `/apply/documents` back to `/apply/business-info` on every
 * navigation, and would make the status page unreachable from the wizard.
 */
export function isRouteAllowed(state: OnboardingState, pathname: string): boolean {
  const path = normalise(pathname);

  switch (state.kind) {
    case 'unknown':
      return path === ROUTES.unavailable;

    case 'none':
      // The welcome screen, and the first wizard step — which is where the
      // organization document actually gets created.
      return path === ROUTES.welcome || path === ROUTES.businessInfo;

    case 'org': {
      const { status } = state;

      if (isOperational(status)) {
        // Operational users belong in the dashboard, but may revisit their
        // application status page.
        return path.startsWith(ROUTES.dashboard) || path === ROUTES.status;
      }

      if (canEditApplication(status)) {
        // Any wizard step, plus the status page as a read-only overview.
        return (WIZARD_STEPS as readonly string[]).includes(path) || path === ROUTES.status;
      }

      // Submitted / terminal / held: status page only. This is the clause that
      // keeps a PENDING_REVIEW applicant out of the form.
      return path === ROUTES.status;
    }
  }
}

/**
 * The redirect a guard should perform, or `null` to render as requested.
 *
 * Returning `null` rather than the current path matters: `redirect()` to the
 * page you are already rendering is an infinite loop in Next.js.
 */
export function redirectFor(state: OnboardingState, pathname: string): string | null {
  if (isRouteAllowed(state, pathname)) return null;
  const target = resolveRoute(state);
  return normalise(pathname) === target ? null : target;
}

/** Strip a trailing slash so `/welcome/` and `/welcome` compare equal. */
function normalise(pathname: string): string {
  if (pathname.length > 1 && pathname.endsWith('/')) {
    return pathname.slice(0, -1);
  }
  return pathname;
}
