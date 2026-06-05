import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { Safe } from 'iconoir-react';

export default function EscrowAccountsPage() {
  return (
    <PagePlaceholder
      title="Escrow Accounts"
      description="Monitor and manage escrow accounts and funds"
      icon={<Safe style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
