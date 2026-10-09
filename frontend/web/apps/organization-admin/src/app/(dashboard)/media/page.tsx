'use client';

import { useSnackbar } from '@pml.tickets/shared/components/m3';
import { MediaView } from '@/components/media/MediaView';
import { useOrgContext } from '@/lib/api/org-context';
import { fileToBase64, MEDIA_MAX_BYTES, MEDIA_TYPES, useMediaActions, useMyMedia } from '@/lib/api/media';

export default function MediaPage() {
  const { capabilities } = useOrgContext();
  const snack = useSnackbar();
  const media = useMyMedia();
  const actions = useMediaActions();

  return (
    <MediaView
      items={media.items}
      loading={media.loading}
      error={media.error}
      onRetry={() => void media.refetch()}
      hasMore={media.hasMore}
      onLoadMore={() => void media.loadMore()}
      canManage={capabilities.canManageMedia}
      onUpload={async (files) => {
        const problems: string[] = [];
        let done = 0;
        for (const f of files) {
          if (!MEDIA_TYPES.includes(f.type)) problems.push(`${f.name}: only JPG, PNG or WEBP images can be uploaded.`);
          else if (f.size > MEDIA_MAX_BYTES) problems.push(`${f.name} is larger than 5 MB.`);
          else {
            try {
              await actions.upload({ fileName: f.name, contentType: f.type, contentBase64: await fileToBase64(f), title: null, altText: null, eventId: null });
              done += 1;
            } catch (e) {
              problems.push(`${f.name}: ${(e as Error).message}`);
            }
          }
        }
        if (done) snack.show(`${done} ${done === 1 ? 'image' : 'images'} added to your library`);
        return problems.length ? problems.join(' ') : null;
      }}
      onSave={async (id, v) => {
        await actions.update(id, { title: v.title || null, altText: v.altText || null, eventId: null });
        snack.show('Image saved');
      }}
      onDelete={async (id) => {
        await actions.remove(id);
        snack.show('Image deleted');
      }}
    />
  );
}
