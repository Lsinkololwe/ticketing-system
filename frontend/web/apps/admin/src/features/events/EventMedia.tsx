'use client';

import { useState } from 'react';
import { Banner, Button, Card, CardHeader, ErrorState, Select, Skeleton, StatusPill, useSnackbar } from '@pml.tickets/shared/components/m3';
import type { AdminEventDetail } from '@pml.tickets/shared/api/admin/modules/catalog-admin';
import { useMediaAssets, useOverrideEventBanner } from '@pml.tickets/shared/api/admin/modules/media-ops';
import { ReasonDialog, useStaff } from '@/components/console';
import { needText } from '@/lib/permissions';
import { useStepUp } from '@/lib/useStepUp';

/** Images of one event and the banner override: replace the banner with another upload, or clear it so buyers see a stock image. */
export function EventMedia({ event }: { event: AdminEventDetail }) {
  const { can } = useStaff();
  const { guard } = useStepUp();
  const snackbar = useSnackbar();
  const { assets, loading, error, refetch } = useMediaAssets({ eventId: event.id, size: 50 });
  const { override, loading: saving } = useOverrideEventBanner();
  const [choice, setChoice] = useState('');
  const [confirm, setConfirm] = useState<{ mediaId: string | null } | null>(null);
  const allowed = can('mediaMod');
  const usable = assets.filter((a) => a.status !== 'REMOVED');

  const apply = async (reason: string) => {
    if (!confirm) return;
    try {
      await guard(() => override(event.id, reason, confirm.mediaId));
      snackbar.show(confirm.mediaId ? 'Banner replaced' : 'Banner cleared. Buyers see a stock image.');
      setConfirm(null);
    } catch (e) {
      snackbar.show((e as Error).message || 'Could not change the banner');
    }
  };

  return (
    <Card>
      <CardHeader title="Media for this event" subtitle="Banner and gallery images uploaded by the organizer" />
      {error && assets.length === 0 ? (
        <ErrorState error={error} onRetry={refetch} />
      ) : loading && assets.length === 0 ? (
        <Skeleton />
      ) : usable.length === 0 ? (
        <p className="m3-muted">No images uploaded yet. Buyers see a category stock image.</p>
      ) : (
        <div className="m3-grid">
          {usable.map((a) => (
            <figure key={a.id}>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={a.url} alt={a.altText || `${event.title} image`} className="adm-thumb" loading="lazy" />
              <figcaption>
                {a.title || a.fileName} <StatusPill status={a.status} />
                {a.url === event.bannerImageUrl ? <> <StatusPill tone="success">Banner</StatusPill></> : null}
              </figcaption>
            </figure>
          ))}
        </div>
      )}
      <div className="m3-stack">
        {!allowed ? <Banner tone="info">{needText('mediaMod')}</Banner> : null}
        <Select label="Use as banner" value={choice} onChange={(e) => setChoice(e.target.value)} disabled={!allowed || usable.length === 0}>
          <option value="">Choose an image</option>
          {usable.map((a) => <option key={a.id} value={a.id}>{a.title || a.fileName}</option>)}
        </Select>
        <div className="m3-row">
          <Button variant="tonal" disabled={!allowed || !choice} onClick={() => setConfirm({ mediaId: choice })}>Override banner…</Button>
          <Button variant="text" danger disabled={!allowed || !event.bannerImageUrl} onClick={() => setConfirm({ mediaId: null })}>Clear banner…</Button>
        </div>
      </div>
      <ReasonDialog
        open={confirm !== null}
        onClose={() => setConfirm(null)}
        onConfirm={(r) => void apply(r)}
        title={confirm?.mediaId ? `Replace the banner of ${event.title}?` : `Clear the banner of ${event.title}?`}
        body="The organizer is told why. The reason is kept in the audit log."
        confirmLabel={confirm?.mediaId ? 'Replace banner' : 'Clear banner'}
        danger={!confirm?.mediaId}
        loading={saving}
      />
    </Card>
  );
}
