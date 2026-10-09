'use client';

/** Organizer media library (catalog `myMedia`, `uploadMedia`, `updateMedia`, `deleteMedia`). */
import type {
  MediaFilterInput,
  OrganizerMediaQuery,
  OrganizerMediaQueryVariables,
  UpdateMediaInput,
  UploadMediaInput,
} from '@pml.tickets/shared/types/graphql';
import { gql } from '@apollo/client';
import { useMutation, useQuery } from '@apollo/client/react';

export const MY_MEDIA = gql`
  query OrganizerMedia($filter: MediaFilterInput, $pagination: CursorPaginationInput) {
    myMedia(filter: $filter, pagination: $pagination) {
      edges {
        cursor
        node {
          id url fileName contentType sizeBytes title altText eventId status flaggedReason removedReason createdAt
        }
      }
      pageInfo { hasNextPage endCursor }
    }
  }
`;

const UPLOAD_MEDIA = gql`
  mutation OrganizerUploadMedia($input: UploadMediaInput!) {
    uploadMedia(input: $input) { id url fileName contentType sizeBytes title altText eventId status createdAt }
  }
`;
const UPDATE_MEDIA = gql`
  mutation OrganizerUpdateMedia($id: ID!, $input: UpdateMediaInput!) {
    updateMedia(id: $id, input: $input) { id title altText eventId }
  }
`;
const DELETE_MEDIA = gql`
  mutation OrganizerDeleteMedia($id: ID!) { deleteMedia(id: $id) }
`;

export type MediaAssetRow = OrganizerMediaQuery['myMedia']['edges'][number]['node'];

export function useMyMedia(filter?: MediaFilterInput, options?: { skip?: boolean; pageSize?: number }) {
  const { data: raw, dataState, loading, error, refetch, fetchMore } = useQuery<OrganizerMediaQuery, OrganizerMediaQueryVariables>(MY_MEDIA, {
    variables: { filter: filter ?? null, pagination: { first: options?.pageSize ?? 48, after: null, last: null, before: null } },
    skip: options?.skip,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  const conn = data?.myMedia;
  return {
    items: conn?.edges.map((e) => e.node) ?? [],
    hasMore: conn?.pageInfo.hasNextPage ?? false,
    loading,
    error,
    refetch,
    loadMore: async () => {
      if (!conn?.pageInfo.endCursor) return;
      await fetchMore({
        variables: { pagination: { first: options?.pageSize ?? 48, after: conn.pageInfo.endCursor, last: null, before: null } },
        updateQuery: (prev, { fetchMoreResult }) =>
          fetchMoreResult
            ? { ...prev, myMedia: { ...fetchMoreResult.myMedia, edges: [...prev.myMedia.edges, ...fetchMoreResult.myMedia.edges] } }
            : prev,
      });
    },
  };
}

export function useMediaActions() {
  const opts = { refetchQueries: [MY_MEDIA] };
  const [upload] = useMutation(UPLOAD_MEDIA, opts);
  const [update] = useMutation(UPDATE_MEDIA);
  const [remove] = useMutation(DELETE_MEDIA, opts);
  return {
    upload: (input: UploadMediaInput) => upload({ variables: { input } }),
    update: (id: string, input: UpdateMediaInput) => update({ variables: { id, input } }),
    remove: (id: string) => remove({ variables: { id } }),
  };
}

/** Reads a File as base64 (no data-URL prefix) for `uploadMedia`. */
export function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onerror = () => reject(new Error(`Could not read ${file.name}`));
    r.onload = () => resolve(String(r.result).split(',')[1] ?? '');
    r.readAsDataURL(file);
  });
}

export const MEDIA_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
export const MEDIA_MAX_BYTES = 5 * 1048576;
