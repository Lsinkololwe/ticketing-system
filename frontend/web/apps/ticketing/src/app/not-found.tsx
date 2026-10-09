import { EmptyState } from '@pml.tickets/shared/components/m3';
import { LinkBtn } from '@/components/LinkBtn';
import { SiteShell } from '@/components/shell/SiteShell';

export default function NotFound() {
  return (
    <SiteShell>
      <div className="m3-site-wrap buyer-page">
        <EmptyState icon="search" title="We could not find that page" description="The link may be old or mistyped." action={<LinkBtn href="/" variant="filled">Back to events</LinkBtn>} />
      </div>
    </SiteShell>
  );
}
