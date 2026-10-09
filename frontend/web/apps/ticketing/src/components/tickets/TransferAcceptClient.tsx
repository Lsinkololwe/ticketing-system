'use client';

import { EmptyState, ErrorState, Skeleton } from '@pml.tickets/shared/components/m3';
import { useMyTicketTransfers, type GraphQLLikeError } from '@pml.tickets/shared';
import { LinkBtn } from '@/components/LinkBtn';
import { SiteShell } from '@/components/shell/SiteShell';
import { IncomingTransferCard } from './IncomingTransfers';

/** The page a transfer offer links to: the offer, with Accept and Decline. */
export function TransferAcceptClient({ transferId }: { transferId: string }) {
  const { transfers, loading, error, refetch } = useMyTicketTransfers({ direction: 'INCOMING' });
  const t = transfers.find((x) => x.id === transferId) ?? null;
  return (
    <SiteShell>
      <div className="m3-site-wrap buyer-narrow buyer-page m3-stack">
        <h1 className="m3-page-title">Ticket transfer</h1>
        {loading && !transfers.length ? (
          <div aria-busy="true" aria-label="Loading the transfer"><Skeleton shape="block" width="100%" /></div>
        ) : error && !transfers.length ? (
          <ErrorState error={error as unknown as GraphQLLikeError} onRetry={() => void refetch()} />
        ) : !t ? (
          <EmptyState icon="ticket" title="We could not find that transfer" description="It may have been cancelled or already answered." action={<LinkBtn href="/my-tickets" variant="filled">Go to My tickets</LinkBtn>} />
        ) : t.status !== 'PENDING' ? (
          <EmptyState icon="ticket" title={`This transfer was ${t.status.toLowerCase()}`} description="There is nothing more to do." action={<LinkBtn href="/my-tickets" variant="filled">Go to My tickets</LinkBtn>} />
        ) : (
          <IncomingTransferCard transfer={t} />
        )}
      </div>
    </SiteShell>
  );
}
