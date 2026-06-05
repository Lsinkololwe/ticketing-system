import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { Undo } from 'iconoir-react';

export default function RefundRequestsPage() {
  return (
    <PagePlaceholder
      title="Refund Requests"
      description="Process and manage customer refund requests"
      icon={<Undo style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
