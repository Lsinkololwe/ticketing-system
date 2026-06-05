import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { StatsUpSquare } from 'iconoir-react';

export default function UserGrowthPage() {
  return (
    <PagePlaceholder
      title="User Growth"
      description="Track user acquisition and growth metrics"
      icon={<StatsUpSquare style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
