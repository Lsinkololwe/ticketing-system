/**
 * Approvals Section Layout (Server Component)
 *
 * Per-section role gate (defense in depth). The parent `(dashboard)/layout.tsx`
 * only requires "any dashboard role"; this layout enforces the section-specific
 * role set so a FINANCE user cannot reach approval routes by direct navigation.
 *
 * Role set mirrors `apps/admin/src/config/navigation.ts` (Action Center).
 */

import { ReactNode } from 'react';
import { requireRoles } from '@/lib/auth/dal';
import type { AdminRole } from '@/lib/auth/interfaces';

const ALLOWED_ROLES = ['ADMIN', 'SUPER_ADMIN'] satisfies readonly AdminRole[];

export default async function ApprovalsLayout({ children }: { children: ReactNode }) {
  await requireRoles(ALLOWED_ROLES);
  return <>{children}</>;
}
