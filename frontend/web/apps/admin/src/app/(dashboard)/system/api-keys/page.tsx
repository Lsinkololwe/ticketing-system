import { PagePlaceholder } from '@/components/ui/PagePlaceholder';
import { Key } from 'iconoir-react';

export default function ApiKeysPage() {
  return (
    <PagePlaceholder
      title="API Keys"
      description="Manage API keys and integration credentials"
      icon={<Key style={{ width: 48, height: 48, color: 'var(--violet-11)' }} />}
    />
  );
}
