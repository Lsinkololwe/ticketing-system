import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { Calendar } from 'iconoir-react';

export default function EventReviewsPage() {
  return (
    <PagePlaceholder
      title="Event Reviews"
      description="Review and approve submitted events before they go live"
      icon={<Calendar style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
