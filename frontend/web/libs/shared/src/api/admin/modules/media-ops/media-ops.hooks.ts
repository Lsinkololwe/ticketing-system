'use client';

/** Media moderation, stock images and banner override hooks (catalog-service). */
import type {
  OpsDeleteStockImageMutation,
  OpsDeleteStockImageMutationVariables,
  OpsFlagMediaMutation,
  OpsFlagMediaMutationVariables,
  OpsOverrideEventBannerMutation,
  OpsOverrideEventBannerMutationVariables,
  OpsRemoveMediaMutation,
  OpsRemoveMediaMutationVariables,
  OpsRestoreMediaMutation,
  OpsRestoreMediaMutationVariables,
  OpsUpdateStockImageMutation,
  OpsUpdateStockImageMutationVariables,
  OpsUploadStockImageMutation,
  OpsUploadStockImageMutationVariables,
  MediaStatus,
  OpsMediaAssetsQuery,
  OpsMediaAssetsQueryVariables,
  OpsStockImagesQuery,
  OpsStockImagesQueryVariables,
  StockImagePurpose,
  UpdateStockImageInput,
  UploadStockImageInput,
} from '../../../../types/graphql';
import { useCallback } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import {
  OPS_DELETE_STOCK_IMAGE,
  OPS_FLAG_MEDIA,
  OPS_MEDIA_ASSETS,
  OPS_OVERRIDE_EVENT_BANNER,
  OPS_REMOVE_MEDIA,
  OPS_RESTORE_MEDIA,
  OPS_STOCK_IMAGES,
  OPS_UPDATE_STOCK_IMAGE,
  OPS_UPLOAD_STOCK_IMAGE,
} from './media-ops.queries';

const asError = (e: unknown): Error | undefined => (e ? (e as Error) : undefined);

export type MediaAssetRow = OpsMediaAssetsQuery['mediaAssets']['content'][number];

export function useMediaAssets(o: { status?: MediaStatus | null; eventId?: string | null; organizationId?: string | null; search?: string; page?: number; size?: number; skip?: boolean } = {}) {
  const size = o.size ?? 24;
  const { data, loading, error, refetch } = useQuery<OpsMediaAssetsQuery, OpsMediaAssetsQueryVariables>(OPS_MEDIA_ASSETS, {
    variables: {
      filter: { status: o.status ?? null, eventId: o.eventId ?? null, organizationId: o.organizationId ?? null, search: o.search || null },
      pagination: { page: o.page ?? 0, size },
    } as OpsMediaAssetsQueryVariables,
    skip: o.skip,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const p = data?.mediaAssets;
  return {
    assets: p?.content ?? [],
    totalElements: p?.totalElements ?? 0,
    totalPages: p?.totalPages ?? 0,
    pageNumber: p?.pageNumber ?? 0,
    hasNext: p?.hasNext ?? false,
    hasPrevious: p?.hasPrevious ?? false,
    loading,
    error: asError(error),
    refetch: () => void refetch(),
  };
}

export function useMediaModeration() {
  const opts = { refetchQueries: ['OpsMediaAssets'], errorPolicy: 'none' as const };
  const [flag, a] = useMutation<OpsFlagMediaMutation, OpsFlagMediaMutationVariables>(OPS_FLAG_MEDIA, opts);
  const [remove, b] = useMutation<OpsRemoveMediaMutation, OpsRemoveMediaMutationVariables>(OPS_REMOVE_MEDIA, opts);
  const [restore, c] = useMutation<OpsRestoreMediaMutation, OpsRestoreMediaMutationVariables>(OPS_RESTORE_MEDIA, opts);
  return {
    flag: useCallback(async (id: string, reason: string) => void (await flag({ variables: { id, reason } })), [flag]),
    remove: useCallback(async (id: string, reason: string) => void (await remove({ variables: { id, reason } })), [remove]),
    restore: useCallback(async (id: string) => void (await restore({ variables: { id } })), [restore]),
    busy: a.loading || b.loading || c.loading,
  };
}

export type StockImageRow = OpsStockImagesQuery['stockImages']['edges'][number]['node'];

export function useStockImages(o: { purpose?: StockImagePurpose | null; categoryCode?: string | null; includeInactive?: boolean } = {}) {
  const { data, loading, error, refetch } = useQuery<OpsStockImagesQuery, OpsStockImagesQueryVariables>(OPS_STOCK_IMAGES, {
    variables: { filter: { purpose: o.purpose ?? null, categoryCode: o.categoryCode ?? null, includeInactive: o.includeInactive ?? true }, pagination: { first: 100 } } as OpsStockImagesQueryVariables,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { images: data?.stockImages.edges.map((e) => e.node) ?? [], loading, error: asError(error), refetch: () => void refetch() };
}

export function useStockImageActions() {
  const opts = { refetchQueries: ['OpsStockImages'], errorPolicy: 'none' as const };
  const [upload, a] = useMutation<OpsUploadStockImageMutation, OpsUploadStockImageMutationVariables>(OPS_UPLOAD_STOCK_IMAGE, opts);
  const [update, b] = useMutation<OpsUpdateStockImageMutation, OpsUpdateStockImageMutationVariables>(OPS_UPDATE_STOCK_IMAGE, opts);
  const [del, c] = useMutation<OpsDeleteStockImageMutation, OpsDeleteStockImageMutationVariables>(OPS_DELETE_STOCK_IMAGE, opts);
  return {
    upload: useCallback(async (input: UploadStockImageInput) => (await upload({ variables: { input } })).data?.uploadStockImage ?? null, [upload]),
    update: useCallback(async (id: string, input: UpdateStockImageInput) => (await update({ variables: { id, input } })).data?.updateStockImage ?? null, [update]),
    remove: useCallback(async (id: string) => void (await del({ variables: { id } })), [del]),
    busy: a.loading || b.loading || c.loading,
  };
}

/** Replace the banner of an event with an existing media asset, or clear it (mediaId omitted). */
export function useOverrideEventBanner() {
  const [mutate, { loading }] = useMutation<OpsOverrideEventBannerMutation, OpsOverrideEventBannerMutationVariables>(OPS_OVERRIDE_EVENT_BANNER, { refetchQueries: ['AdminEventDetail', 'OpsMediaAssets'], errorPolicy: 'none' });
  const override = useCallback(async (eventId: string, reason: string, mediaId?: string | null) => void (await mutate({ variables: { eventId, mediaId: mediaId ?? null, reason } })), [mutate]);
  return { override, loading };
}
