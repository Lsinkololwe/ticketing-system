import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { Percentage } from 'iconoir-react';

export default function CommissionsPage() {
  return (
    <PagePlaceholder
      title="Commissions"
      description="View platform commission earnings and distributions"
      icon={<Percentage style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
