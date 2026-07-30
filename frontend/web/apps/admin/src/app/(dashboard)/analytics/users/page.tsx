import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { StatsUpSquare } from 'iconoir-react';
import { requireRoles } from '@/lib/auth/dal';
import type { AdminRole } from '@/lib/auth/interfaces';

// User Growth is admin-only (tighter than the section layout, which also admits
// FINANCE for Revenue Reports). Mirrors config/navigation.ts.
const ALLOWED_ROLES = ['ADMIN', 'SUPER_ADMIN'] satisfies readonly AdminRole[];

export default async function UserGrowthPage() {
  await requireRoles(ALLOWED_ROLES);

  return (
    <PagePlaceholder
      title="User growth"
      description="Track user acquisition and growth metrics"
      icon={<StatsUpSquare style={{ width: 48, height: 48, color: 'var(--accent-11)' }} />}
    />
  );
}
