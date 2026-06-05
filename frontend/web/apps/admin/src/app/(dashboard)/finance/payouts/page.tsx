import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { SendDiagonal } from 'iconoir-react';

export default function PayoutRequestsPage() {
  return (
    <PagePlaceholder
      title="Payout Requests"
      description="Review and process organizer payout requests"
      icon={<SendDiagonal style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
