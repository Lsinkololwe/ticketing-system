import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { HistoricShield } from 'iconoir-react';

export default function AuditLogsPage() {
  return (
    <PagePlaceholder
      title="Audit Logs"
      description="Review system activity and security audit trails"
      icon={<HistoricShield style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
