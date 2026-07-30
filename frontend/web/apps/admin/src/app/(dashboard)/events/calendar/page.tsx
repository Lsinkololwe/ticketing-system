import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { CalendarPlus } from 'iconoir-react';

export default function EventCalendarPage() {
  return (
    <PagePlaceholder
      title="Event calendar"
      description="View events in calendar format with scheduling insights"
      icon={<CalendarPlus style={{ width: 48, height: 48, color: 'var(--accent-11)' }} />}
    />
  );
}
