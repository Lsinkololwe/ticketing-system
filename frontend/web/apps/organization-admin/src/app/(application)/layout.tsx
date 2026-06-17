/**
 * Application Flow Layout (Server Component)
 *
 * Layout for the KYB (Know Your Business) application process.
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
 * - Redirects approved organizers to dashboard
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
import { verifySession, getOrganizationStatus } from '@/lib/auth/dal';
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
  // ============================================================================
  // LAYER 2: Server-Side Authentication & Authorization
  // ============================================================================

  // Step 1: Verify session (validates against MongoDB, not just cookie)
  // Redirects to /login if not authenticated
  await verifySession();

  // Step 2: Check organization status (with graceful fallback)
  // If GraphQL is unavailable, proceed with rendering - client will handle
  try {
    const organization = await getOrganizationStatus();

    // Step 3: If user has an approved organization, redirect to dashboard
    if (organization.isApproved) {
      redirect('/dashboard');
    }
  } catch (error) {
    // GraphQL unavailable - proceed with rendering, client-side will handle
    console.warn('[ApplicationLayout] Organization status check failed, proceeding:', error);
  }

  // ============================================================================
  // User is authenticated but needs to complete/view application
  // ============================================================================

  return <ApplicationLayoutContent>{children}</ApplicationLayoutContent>;
}
