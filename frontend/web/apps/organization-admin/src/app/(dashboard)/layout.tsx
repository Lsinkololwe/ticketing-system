/**
 * Dashboard Layout (Server Component)
 *
 * Protected layout for authenticated organizers with approved status.
 *
 * ## Security Model (Industry Standard 3-Layer)
 *
 * ```
 * Layer 1: Proxy (proxy.ts)
 * -------------------------
 * - Optimistic routing based on session cookie PRESENCE
 * - NOT SECURE - cookies can be forged
 * - Purpose: Better UX with fast redirects
 *
 * Layer 2: THIS LAYOUT (Server Component)
 * ---------------------------------------
 * - Validates session via auth.api.getSession()
 * - Checks organization status from GraphQL
 * - Redirects unauthorized users
 * - DEFENSE IN DEPTH
 *
 * Layer 3: Backend API
 * --------------------
 * - @PreAuthorize annotations on GraphQL resolvers
 * - JWT validation + role-based access control
 * - Returns only authorized data
 * - SOURCE OF TRUTH (SECURE)
 * ```
 *
 * @see https://nextjs.org/docs/app/guides/authentication
 * @see https://better-auth.com/docs/integrations/next
 */

import { ReactNode } from 'react';
import { redirect } from 'next/navigation';
import { verifySession, getOrganizationStatus, getRouteForStatus } from '@/lib/auth/dal';
import { DashboardLayoutContent } from '@/components/layout/DashboardLayoutContent';

// =============================================================================
// TYPES
// =============================================================================

interface DashboardLayoutProps {
  children: ReactNode;
}

// =============================================================================
// SERVER COMPONENT LAYOUT
// =============================================================================

export default async function DashboardLayout({ children }: DashboardLayoutProps) {
  // ============================================================================
  // LAYER 2: Server-Side Authentication & Authorization
  // ============================================================================

  // Step 1: Verify session (validates against MongoDB, not just cookie)
  // Redirects to /login if not authenticated
  await verifySession();

  // Step 2: Check organization status (with graceful fallback on transport errors only).
  // Keep the GraphQL call inside try/catch, but NOT the redirects — redirect() throws a
  // NEXT_REDIRECT control-flow error that must not be swallowed by this catch.
  let organization: Awaited<ReturnType<typeof getOrganizationStatus>>;
  try {
    organization = await getOrganizationStatus();
  } catch (error) {
    console.warn('[DashboardLayout] Organization status check failed:', error);
    redirect('/welcome');
  }

  // Step 3: Route by business lifecycle state (derived from backend status).
  // The dashboard is for operational orgs (approved/active) and those under review
  // (read-only preview); everything else routes to the application flow. This is a
  // presentation guard only — every privileged action is independently enforced
  // server-side at the GraphQL resolvers (defense in depth).
  if (!organization.hasOrganization) {
    redirect('/welcome');
  }

  const canUseDashboard = organization.isApproved || organization.isPendingReview;
  if (!canUseDashboard) {
    redirect(getRouteForStatus(organization.status));
  }

  // ============================================================================
  // Render the dashboard. Orgs still under review (not yet approved) see a
  // read-only preview banner.
  // ============================================================================

  const previewMode = organization.isPendingReview;

  return (
    <DashboardLayoutContent previewMode={previewMode}>
      {children}
    </DashboardLayoutContent>
  );
}
