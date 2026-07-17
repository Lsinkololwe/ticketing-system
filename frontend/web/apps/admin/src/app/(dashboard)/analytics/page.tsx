import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { StatsReport } from 'iconoir-react';
import { requireRoles } from '@/lib/auth/dal';
import type { AdminRole } from '@/lib/auth/interfaces';

// Platform Overview is admin-only (tighter than the section layout, which also
// admits FINANCE for Revenue Reports). Mirrors config/navigation.ts.
const ALLOWED_ROLES = ['ADMIN', 'SUPER_ADMIN'] satisfies readonly AdminRole[];

export default async function AnalyticsPage() {
  await requireRoles(ALLOWED_ROLES);

  return (
    <PagePlaceholder
      title="Platform Overview"
      description="View comprehensive platform analytics and insights"
      icon={<StatsReport style={{ width: 48, height: 48, color: 'var(--accent-11)' }} />}
    />
  );
}
