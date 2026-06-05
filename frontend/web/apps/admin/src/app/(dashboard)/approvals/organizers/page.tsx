import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { Group } from 'iconoir-react';

export default function OrganizerApplicationsPage() {
  return (
    <PagePlaceholder
      title="Organizer Applications"
      description="Review and approve new organizer registration requests"
      icon={<Group style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
