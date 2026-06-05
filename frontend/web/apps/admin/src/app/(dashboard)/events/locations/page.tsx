import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { MapPin } from 'iconoir-react';

export default function EventLocationsPage() {
  return (
    <PagePlaceholder
      title="Event Locations"
      description="Manage venues and event locations"
      icon={<MapPin style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
