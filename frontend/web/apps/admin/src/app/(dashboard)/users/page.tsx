import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { Group } from 'iconoir-react';

export default function UsersPage() {
  return (
    <PagePlaceholder
      title="All Users"
      description="Manage platform users and their accounts"
      icon={<Group style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
