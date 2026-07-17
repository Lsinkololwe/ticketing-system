/**
 * Dashboard Layout (Server Component)
 *
 * Protected layout for the platform admin dashboard.
 *
 * ## Security Model (defense in depth)
 *
 * 1. Keycloak admin realm (myticketzm-admin) — non-staff cannot obtain a token at all.
 * 2. THIS LAYOUT — server-side session validation (against the DB) + role gate via
 *    {@link requireRoles}. Redirects to /login (no session) or /unauthorized (wrong role)
 *    BEFORE any admin UI is sent to the client.
 * 3. Backend resolvers — @PreAuthorize role checks (source of truth).
 *
 * The interactive chrome lives in the client {@link DashboardShell}; it only renders
 * once this guard has passed.
 */

import { ReactNode } from 'react';
import { requireRoles, ADMIN_DASHBOARD_ROLES } from '@/lib/auth/dal';
import { DashboardShell } from '@/components/layout/DashboardShell';

interface DashboardLayoutProps {
  children: ReactNode;
}

export default async function DashboardLayout({ children }: DashboardLayoutProps) {
  // Server-side: verify session against the DB and require an admin role.
  // Redirects to /login or /unauthorized as appropriate.
  await requireRoles(ADMIN_DASHBOARD_ROLES);

  return <DashboardShell>{children}</DashboardShell>;
}
