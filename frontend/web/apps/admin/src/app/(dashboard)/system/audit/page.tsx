import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { HistoricShield } from 'iconoir-react';

export default function AuditLogsPage() {
  return (
    <PagePlaceholder
      title="Audit logs"
      description="Review system activity and security audit trails"
      icon={<HistoricShield style={{ width: 48, height: 48, color: 'var(--accent-11)' }} />}
    />
  );
}
