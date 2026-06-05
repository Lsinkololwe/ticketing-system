import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { PageSearch } from 'iconoir-react';

export default function DocumentVerificationPage() {
  return (
    <PagePlaceholder
      title="Document Verification"
      description="Verify organizer documents and compliance materials"
      icon={<PageSearch style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
