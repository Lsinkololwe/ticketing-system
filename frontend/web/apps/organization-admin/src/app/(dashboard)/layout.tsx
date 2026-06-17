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

  // Step 2: Check organization status (with graceful fallback)
  try {
    const organization = await getOrganizationStatus();

    // Step 3: Enforce authorization rules
    if (!organization.hasOrganization) {
      redirect('/welcome');
    }

    if (!organization.isApproved) {
      const route = getRouteForStatus(organization.status);
      redirect(route);
    }
  } catch (error) {
    // GraphQL unavailable - redirect to welcome, client will handle
    console.warn('[DashboardLayout] Organization status check failed:', error);
    redirect('/welcome');
  }

  // ============================================================================
  // User is authenticated with an approved organization - render dashboard
  // ============================================================================

  return <DashboardLayoutContent>{children}</DashboardLayoutContent>;
}
