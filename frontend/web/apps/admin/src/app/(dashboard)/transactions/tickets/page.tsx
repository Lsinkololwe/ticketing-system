import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { Label } from 'iconoir-react';

export default function TicketSalesPage() {
  return (
    <PagePlaceholder
      title="Ticket Sales"
      description="Track and analyze ticket sales across all events"
      icon={<Label style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
