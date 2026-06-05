import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { Community } from 'iconoir-react';

export default function OrganizationsPage() {
  return (
    <PagePlaceholder
      title="Organizations"
      description="Manage registered organizations and their profiles"
      icon={<Community style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
