import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { GraphUp } from 'iconoir-react';

export default function RevenueReportsPage() {
  return (
    <PagePlaceholder
      title="Revenue Reports"
      description="Detailed revenue analytics and financial reports"
      icon={<GraphUp style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
