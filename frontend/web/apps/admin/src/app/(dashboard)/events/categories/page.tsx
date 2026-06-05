import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { Folder } from 'iconoir-react';

export default function EventCategoriesPage() {
  return (
    <PagePlaceholder
      title="Event Categories"
      description="Manage event categories and classifications"
      icon={<Folder style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
