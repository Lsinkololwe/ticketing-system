import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { Safe } from 'iconoir-react';

export default function EscrowAccountsPage() {
  return (
    <PagePlaceholder
      title="Escrow accounts"
      description="Monitor and manage escrow accounts and funds"
      icon={<Safe style={{ width: 48, height: 48, color: 'var(--accent-11)' }} />}
    />
  );
}
