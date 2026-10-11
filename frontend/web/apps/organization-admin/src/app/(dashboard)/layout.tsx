/**
 * Dashboard Layout (Server Component)
 *
 * Authoritative session and role check (the proxy only gates coarsely), then the
 * organization lifecycle guard. Every privileged action is independently enforced
 * by the backend resolvers.
 */

import { ReactNode } from 'react';
import { redirect } from 'next/navigation';
import { bff } from '@/lib/bff';
import { getOrganizationStatus, getRouteForStatus } from '@/lib/organization/server';
import { ConsoleShell } from '@/components/console/ConsoleShell';
import { AccessSettingUp } from '@/components/onboarding/AccessSettingUp';
import { hasOrganizerAccess } from '@/lib/organizerAccess';

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

  // Step 1: Verify the BFF session (Redis-backed, server only). Redirects to /login if not authenticated.
  // No realm role is asked for: a team member holds a membership, not the ORGANIZER role, and the
  // organization lookup below (owner or active member) is what decides who belongs here.
  const session = await bff.requireSession();

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

  // A member of an operational organization whose platform access has not been granted yet (it follows
  // an accepted invitation by about a minute) is told so; an applicant who is not yet approved belongs
  // on the application's own screens, as before.
  if (!hasOrganizerAccess(session.roles)) {
    if (!organization.isApproved) {
      redirect(getRouteForStatus(organization.status));
    }
    return <AccessSettingUp organizationName={organization.name} />;
  }

  const canUseDashboard = organization.isApproved || organization.isPendingReview;
  if (!canUseDashboard) {
    redirect(getRouteForStatus(organization.status));
  }

  // ============================================================================
  // Render the dashboard. Orgs still under review (not yet approved) see a
  // read-only preview banner.
  // ============================================================================

  return <ConsoleShell>{children}</ConsoleShell>;
}
