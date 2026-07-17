/**
 * Analytics Section Layout (Server Component)
 *
 * Per-section role gate (defense in depth). Enforces the LOOSEST role set for
 * this mixed section (Revenue Reports is FINANCE-visible) on top of the "any
 * dashboard role" gate in `(dashboard)/layout.tsx`.
 *
 * The admin-only sub-pages (Platform Overview `/analytics`, User Growth
 * `/analytics/users`) tighten this further with their own `requireRoles` call.
 *
 * Role set mirrors `apps/admin/src/config/navigation.ts` (Analytics section).
 */

import { ReactNode } from 'react';
import { requireRoles } from '@/lib/auth/dal';
import type { AdminRole } from '@/lib/auth/interfaces';

const ALLOWED_ROLES = ['FINANCE', 'ADMIN', 'SUPER_ADMIN'] satisfies readonly AdminRole[];

export default async function AnalyticsLayout({ children }: { children: ReactNode }) {
  await requireRoles(ALLOWED_ROLES);
  return <>{children}</>;
}
