import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { CreditCard } from 'iconoir-react';

export default function PaymentHistoryPage() {
  return (
    <PagePlaceholder
      title="Payment History"
      description="View and manage all payment transactions"
      icon={<CreditCard style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
