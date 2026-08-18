/**
 * Application Flow Layout (Server Component)
 *
 * Layout for the KYB (Know Your Business) application process, and the single
 * place that decides which onboarding screen a user is entitled to see.
 *
 * ## Security Model (3-Layer)
 *
 * ```
 * Layer 1: Proxy (proxy.ts)
 * -------------------------
 * - Optimistic routing based on session cookie PRESENCE
 * - NOT SECURE - cookies can be forged
 * - Purpose: fast redirects; forwards the pathname to Layer 2
 *
 * Layer 2: THIS LAYOUT (Server Component)
 * ---------------------------------------
 * - Validates session via auth.api.getSession()
 * - Resolves organization status from GraphQL
 * - Redirects to the one route that matches that status
 * - DEFENSE IN DEPTH
 *
 * Layer 3: Backend API
 * --------------------
 * - @PreAuthorize on GraphQL resolvers, JWT validation, RBAC
 * - SOURCE OF TRUTH (SECURE)
 * ```
 *
 * ## The defect this layout fixes
 *
 * The guard used to redirect only users who were already approved. Every other
 * status — including `PENDING_REVIEW` — fell through and rendered whatever page
 * was requested, which after login was always `/welcome`: the "set up your
 * organization" screen. A client-side `useEffect` in that page then corrected
 * the destination once Apollo resolved, so a user with a fully submitted
 * application saw the setup screen flash on every login, and stayed on it
 * permanently whenever the client query failed.
 *
 * Two things changed. The decision now happens here, before anything renders,
 * from data the server already had. And "we could not determine the status" is
 * its own outcome that routes to a retry screen, rather than collapsing into
 * "no application yet" and offering the form.
 *
 * @see lib/onboarding/state.ts — the status → route map
 * @see https://nextjs.org/docs/app/guides/authentication
 */

import { ReactNode } from 'react';
import { headers } from 'next/headers';
import { redirect } from 'next/navigation';
import { verifySession, getOnboardingState } from '@/lib/auth/dal';
import { redirectFor, ROUTES } from '@/lib/onboarding/state';
import { PATHNAME_HEADER } from '@/proxy';
import { ApplicationLayoutContent } from '@/components/layout/ApplicationLayoutContent';

// =============================================================================
// TYPES
// =============================================================================

interface ApplicationLayoutProps {
  children: ReactNode;
}

// =============================================================================
// SERVER COMPONENT LAYOUT
// =============================================================================

export default async function ApplicationLayout({ children }: ApplicationLayoutProps) {
  // Step 1: Verify session (validates against MongoDB, not just the cookie).
  // Redirects to /login if not authenticated.
  await verifySession();

  // Step 2: Resolve where this user actually belongs.
  //
  // No try/catch: `getOnboardingState()` already converts every failure into an
  // explicit `unknown` state. Swallowing errors here is what allowed a backend
  // outage to render the setup form to someone who had already applied.
  const state = await getOnboardingState();

  // Step 3: Redirect unless the requested path is one this state permits.
  //
  // `redirectFor` returns null when the user is already somewhere legitimate,
  // which is what keeps the multi-step wizard navigable — without it, every
  // request would bounce back to the first step.
  const pathname = (await headers()).get(PATHNAME_HEADER) ?? ROUTES.welcome;
  const target = redirectFor(state, pathname);
  if (target) {
    redirect(target);
  }

  return (
    <ApplicationLayoutContent status={state.kind === 'org' ? state.status : null}>
      {children}
    </ApplicationLayoutContent>
  );
}
