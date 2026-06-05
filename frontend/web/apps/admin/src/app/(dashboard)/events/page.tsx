import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { Calendar } from 'iconoir-react';

export default function EventsPage() {
  return (
    <PagePlaceholder
      title="All Events"
      description="Browse and manage all events on the platform"
      icon={<Calendar style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
