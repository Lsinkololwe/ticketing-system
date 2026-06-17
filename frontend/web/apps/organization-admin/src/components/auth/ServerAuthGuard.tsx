/**
 * Server-Side Authentication Guards
 *
 * SERVER COMPONENTS: These components handle authentication validation
 * at the server level before rendering any content.
 *
 * ## Security Model
 *
 * ```
 * Request → Proxy (UX) → ServerAuthGuard (Validation) → Backend (@PreAuthorize)
 *           ↓               ↓                              ↓
 *        Optimistic      Session Check               Actual Auth
 *        Redirect        via MongoDB                 JWT + RBAC
 * ```
 *
 * ## Usage
 *
 * ```tsx
 * // In (dashboard)/layout.tsx
 * import { DashboardAuthGuard } from '@/components/auth/ServerAuthGuard';
 *
 * export default function DashboardLayout({ children }) {
 *   return (
 *     <DashboardAuthGuard>
 *       <DashboardLayoutContent>{children}</DashboardLayoutContent>
 *     </DashboardAuthGuard>
 *   );
 * }
 * ```
 *
 * @module apps/organization-admin/src/components/auth/ServerAuthGuard
 */

import { ReactNode } from 'react';
import { redirect } from 'next/navigation';
import {
  verifySession,
  getOrganizationStatus,
  getRouteForStatus,
  type Session,
  type OrganizationStatus,
} from '@/lib/auth/dal';

// =============================================================================
// TYPES
// =============================================================================

interface AuthGuardProps {
  children: ReactNode;
}

interface AuthContextValue {
  session: Session;
  organization: OrganizationStatus;
}

// =============================================================================
// DASHBOARD AUTH GUARD (Requires Approved Organization)
// =============================================================================

/**
 * Server-side auth guard for dashboard routes
 *
 * Validates:
 * 1. User has a valid session (verified against MongoDB)
 * 2. User has an approved organization
 *
 * Redirects to:
 * - /login if not authenticated
 * - /welcome if no organization
 * - /apply/business-info if organization is draft/needs changes
 * - /apply/status if pending review/rejected
 *
 * @example
 * ```tsx
 * // In (dashboard)/layout.tsx
 * export default function DashboardLayout({ children }) {
 *   return (
 *     <DashboardAuthGuard>
 *       {children}
 *     </DashboardAuthGuard>
 *   );
 * }
 * ```
 */
export async function DashboardAuthGuard({ children }: AuthGuardProps) {
  // Verify session - redirects to /login if not authenticated
  await verifySession();

  // Check organization status
  const organization = await getOrganizationStatus();

  // Redirect based on organization status
  if (!organization.hasOrganization) {
    redirect('/welcome');
  }

  if (!organization.isApproved) {
    const route = getRouteForStatus(organization.status);
    redirect(route);
  }

  // User is authenticated and has an approved organization
  return <>{children}</>;
}

/**
 * Get auth context for dashboard (use in Server Components that need the data)
 *
 * @returns Session and organization data
 * @throws Redirects if not authorized for dashboard
 */
export async function getDashboardAuthContext(): Promise<AuthContextValue> {
  const session = await verifySession();
  const organization = await getOrganizationStatus();

  if (!organization.hasOrganization) {
    redirect('/welcome');
  }

  if (!organization.isApproved) {
    const route = getRouteForStatus(organization.status);
    redirect(route);
  }

  return { session, organization };
}

// =============================================================================
// APPLICATION FLOW AUTH GUARD (For Welcome/Apply Pages)
// =============================================================================

/**
 * Server-side auth guard for application flow routes
 *
 * Validates:
 * 1. User has a valid session
 *
 * Redirects to:
 * - /login if not authenticated
 * - /dashboard if organization is already approved
 *
 * @example
 * ```tsx
 * // In (application)/layout.tsx
 * export default function ApplicationLayout({ children }) {
 *   return (
 *     <ApplicationAuthGuard>
 *       {children}
 *     </ApplicationAuthGuard>
 *   );
 * }
 * ```
 */
export async function ApplicationAuthGuard({ children }: AuthGuardProps) {
  // Verify session - redirects to /login if not authenticated
  await verifySession();

  // Check organization status
  const organization = await getOrganizationStatus();

  // If already approved, send to dashboard
  if (organization.isApproved) {
    redirect('/dashboard');
  }

  return <>{children}</>;
}

/**
 * Get auth context for application flow
 *
 * @returns Session and organization data
 * @throws Redirects if not authenticated or already approved
 */
export async function getApplicationAuthContext(): Promise<AuthContextValue> {
  const session = await verifySession();
  const organization = await getOrganizationStatus();

  if (organization.isApproved) {
    redirect('/dashboard');
  }

  return { session, organization };
}

// =============================================================================
// WELCOME PAGE AUTH GUARD (For Users Without Organization)
// =============================================================================

/**
 * Server-side auth guard for welcome page
 *
 * Validates:
 * 1. User has a valid session
 *
 * Redirects to:
 * - /login if not authenticated
 * - /dashboard if organization is approved
 * - appropriate apply page if organization exists but not approved
 *
 * @example
 * ```tsx
 * // In welcome/page.tsx (as Server Component wrapper)
 * export default async function WelcomePage() {
 *   await WelcomeAuthGuard();
 *   return <WelcomePageContent />;
 * }
 * ```
 */
export async function WelcomeAuthGuard({ children }: AuthGuardProps) {
  // Verify session - redirects to /login if not authenticated
  await verifySession();

  // Check organization status
  const organization = await getOrganizationStatus();

  // Redirect based on organization status
  if (organization.hasOrganization) {
    const route = getRouteForStatus(organization.status);
    redirect(route);
  }

  // User is authenticated but has no organization - show welcome page
  return <>{children}</>;
}

/**
 * Get auth context for welcome page
 *
 * @returns Session and organization data
 * @throws Redirects if has organization or not authenticated
 */
export async function getWelcomeAuthContext(): Promise<AuthContextValue> {
  const session = await verifySession();
  const organization = await getOrganizationStatus();

  if (organization.hasOrganization) {
    const route = getRouteForStatus(organization.status);
    redirect(route);
  }

  return { session, organization };
}

// =============================================================================
// GENERIC AUTH GUARD (Session Only)
// =============================================================================

/**
 * Generic server-side auth guard
 *
 * Only validates that the user has a valid session.
 * Use when you need authentication but no specific organization requirements.
 *
 * @example
 * ```tsx
 * export default function SettingsLayout({ children }) {
 *   return (
 *     <AuthGuard>
 *       {children}
 *     </AuthGuard>
 *   );
 * }
 * ```
 */
export async function AuthGuard({ children }: AuthGuardProps) {
  // Verify session - redirects to /login if not authenticated
  await verifySession();

  return <>{children}</>;
}
