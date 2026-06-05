import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { ClipboardCheck } from 'iconoir-react';

export default function ApprovalsPage() {
  return (
    <PagePlaceholder
      title="Pending Approvals"
      description="Review and manage all pending approvals across the platform"
      icon={<ClipboardCheck style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
