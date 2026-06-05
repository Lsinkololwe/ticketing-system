import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { StatsReport } from 'iconoir-react';

export default function AnalyticsPage() {
  return (
    <PagePlaceholder
      title="Platform Overview"
      description="View comprehensive platform analytics and insights"
      icon={<StatsReport style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
