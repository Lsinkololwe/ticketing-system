/**
 * Organizations Section Layout (Server Component)
 *
 * Per-section role gate (defense in depth). Enforces the section-specific role
 * set on top of the "any dashboard role" gate in `(dashboard)/layout.tsx`.
 *
 * Role set mirrors `apps/admin/src/config/navigation.ts` (Users -> Organizations).
 */

import { ReactNode } from 'react';
import { requireRoles } from '@/lib/auth/dal';
import type { AdminRole } from '@/lib/auth/interfaces';

const ALLOWED_ROLES = ['ADMIN', 'SUPER_ADMIN'] satisfies readonly AdminRole[];

export default async function OrganizationsLayout({ children }: { children: ReactNode }) {
  await requireRoles(ALLOWED_ROLES);
  return <>{children}</>;
}
