/**
 * Console layout (server component). Defence in depth:
 *  1. Keycloak admin realm: non-staff cannot obtain a token at all.
 *  2. proxy.ts: coarse gate (session exists, staff role) for every page.
 *  3. THIS LAYOUT: requireSession() reloads the session from Redis and re-checks the staff roles.
 *     Pages that read data and every Server Action repeat it (layouts are not re-run per navigation).
 *  4. Backend resolvers: role checks (source of truth).
 */
import type { ReactNode } from 'react';
import { bff, STAFF_ACCESS_ROLES } from '@/lib/bff';
import { ConsoleShell } from '@/components/console/ConsoleShell';
import { StaffProvider } from '@/components/console/StaffContext';
import { staffRolesOf } from '@/config/navigation';

export default async function ConsoleLayout({ children }: { children: ReactNode }) {
  const session = await bff.requireSession({ roles: STAFF_ACCESS_ROLES });
  const roles = staffRolesOf(session.roles);
  return (
    <StaffProvider staff={{ id: session.accountId ?? session.sub, name: session.displayName || 'Staff', email: '', roles }}>
      <ConsoleShell>{children}</ConsoleShell>
    </StaffProvider>
  );
}
