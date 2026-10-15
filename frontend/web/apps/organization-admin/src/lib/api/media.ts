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
const REQUEST_MEDIA_UPLOAD = gql`
  mutation OrganizerRequestMediaUpload($input: RequestMediaUploadInput!) {
    requestMediaUpload(input: $input) { uploadToken uploadUrl method headers { name value } expiresAt maxBytes }
  }
`;
const COMPLETE_MEDIA_UPLOAD = gql`
  mutation OrganizerCompleteMediaUpload($input: CompleteMediaUploadInput!) {
    completeMediaUpload(input: $input) { id url fileName contentType sizeBytes title altText eventId status createdAt }
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
  const [requestUpload] = useMutation(REQUEST_MEDIA_UPLOAD);
  const [completeUpload] = useMutation(COMPLETE_MEDIA_UPLOAD, opts);
  const [update] = useMutation(UPDATE_MEDIA);
  const [remove] = useMutation(DELETE_MEDIA, opts);
  return {
    upload: (input: UploadMediaInput) => upload({ variables: { input } }),
    /**
     * A picture over 5 MB: ask for a grant, send the file straight to the address it names, then complete.
     * The picture is not part of the library, and is not served, until the service has read its bytes.
     */
    uploadLarge: async (file: File, meta: { title?: string | null; altText?: string | null; eventId?: string | null } = {}) => {
      const granted = await requestUpload({ variables: { input: { fileName: file.name, contentType: file.type, sizeBytes: file.size } } });
      const ticket = granted.data?.requestMediaUpload;
      if (!ticket) throw new Error('The upload could not be started.');
      await sendToGrant(ticket, file);
      const done = await completeUpload({ variables: { input: { uploadToken: ticket.uploadToken, title: meta.title ?? null, altText: meta.altText ?? null, eventId: meta.eventId ?? null } } });
      const asset = done.data?.completeMediaUpload;
      if (!asset) throw new Error('The image was uploaded but could not be added to your library.');
      return asset;
    },
    update: (id: string, input: UpdateMediaInput) => update({ variables: { id, input } }),
    remove: (id: string) => remove({ variables: { id } }),
  };
}

/** The grant's address, method and headers, as the service issues them. */
export interface UploadGrant {
  uploadUrl: string;
  method: string;
  headers: ReadonlyArray<{ name: string; value: string }>;
}

/** Sends the file to the grant exactly as issued; a refusal by the store is an error with its status. */
export async function sendToGrant(grant: UploadGrant, file: Blob, send: typeof fetch = fetch): Promise<void> {
  const headers = new Headers();
  for (const h of grant.headers) {
    // The browser sets these from the body; the grant lists them because they are signed.
    if (!['content-length', 'host', 'connection'].includes(h.name.toLowerCase())) headers.set(h.name, h.value);
  }
  const response = await send(grant.uploadUrl, { method: grant.method, headers, body: file });
  if (!response.ok) throw new Error(`The image could not be sent (${response.status}).`);
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
/** What the two-step upload accepts; the service enforces it again from the bytes. */
export const MEDIA_LARGE_MAX_BYTES = 20 * 1048576;
