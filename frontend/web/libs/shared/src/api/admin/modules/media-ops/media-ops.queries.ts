/**
 * Media moderation, stock images and banner override documents (catalog-service).
 */
import { gql } from '@apollo/client';

export const OPS_MEDIA_ASSET_FIELDS = gql`
  fragment OpsMediaAssetFields on MediaAsset {
    id
    eventId
    organizationId
    fileName
    title
    altText
    contentType
    sizeBytes
    url
    status
    flaggedAt
    flaggedReason
    removedAt
    removedReason
    uploadedBy
    createdAt
    updatedAt
    moderationLog {
      action
      reason
      actorId
      at
    }
  }
`;

export const OPS_MEDIA_ASSETS = gql`
  ${OPS_MEDIA_ASSET_FIELDS}
  query OpsMediaAssets($filter: MediaModerationFilterInput, $pagination: OffsetPaginationInput) {
    mediaAssets(filter: $filter, pagination: $pagination) {
      content {
        ...OpsMediaAssetFields
      }
      totalElements
      totalPages
      pageNumber
      pageSize
      hasNext
      hasPrevious
    }
  }
`;

export const OPS_FLAG_MEDIA = gql`
  ${OPS_MEDIA_ASSET_FIELDS}
  mutation OpsFlagMedia($id: ID!, $reason: String!) {
    flagMedia(id: $id, reason: $reason) {
      ...OpsMediaAssetFields
    }
  }
`;

export const OPS_REMOVE_MEDIA = gql`
  ${OPS_MEDIA_ASSET_FIELDS}
  mutation OpsRemoveMedia($id: ID!, $reason: String!) {
    removeMedia(id: $id, reason: $reason) {
      ...OpsMediaAssetFields
    }
  }
`;

export const OPS_RESTORE_MEDIA = gql`
  ${OPS_MEDIA_ASSET_FIELDS}
  mutation OpsRestoreMedia($id: ID!) {
    restoreMedia(id: $id) {
      ...OpsMediaAssetFields
    }
  }
`;

export const OPS_STOCK_IMAGE_FIELDS = gql`
  fragment OpsStockImageFields on StockImage {
    id
    title
    altText
    url
    purpose
    categoryCode
    active
    createdAt
    updatedAt
  }
`;

export const OPS_STOCK_IMAGES = gql`
  ${OPS_STOCK_IMAGE_FIELDS}
  query OpsStockImages($filter: StockImageFilterInput, $pagination: CursorPaginationInput) {
    stockImages(filter: $filter, pagination: $pagination) {
      edges {
        node {
          ...OpsStockImageFields
        }
      }
    }
  }
`;

export const OPS_UPLOAD_STOCK_IMAGE = gql`
  ${OPS_STOCK_IMAGE_FIELDS}
  mutation OpsUploadStockImage($input: UploadStockImageInput!) {
    uploadStockImage(input: $input) {
      ...OpsStockImageFields
    }
  }
`;

export const OPS_UPDATE_STOCK_IMAGE = gql`
  ${OPS_STOCK_IMAGE_FIELDS}
  mutation OpsUpdateStockImage($id: ID!, $input: UpdateStockImageInput!) {
    updateStockImage(id: $id, input: $input) {
      ...OpsStockImageFields
    }
  }
`;

export const OPS_DELETE_STOCK_IMAGE = gql`
  mutation OpsDeleteStockImage($id: ID!) {
    deleteStockImage(id: $id)
  }
`;

export const OPS_OVERRIDE_EVENT_BANNER = gql`
  mutation OpsOverrideEventBanner($eventId: ID!, $mediaId: ID, $reason: String!) {
    overrideEventBanner(eventId: $eventId, mediaId: $mediaId, reason: $reason) {
      id
      bannerImageUrl
    }
  }
`;
