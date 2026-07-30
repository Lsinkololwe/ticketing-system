/**
 * Reference Data GraphQL Queries
 *
 * @see backend/catalog-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

// ==========================================
// Fragments
// ==========================================

export const REFERENCE_DATA_FIELDS = gql`
  fragment ReferenceDataFields on ReferenceData {
    id
    type
    code
    name
    description
    parentType
    parentCode
    displayOrder
    isActive
    isSystem
    effectiveFrom
    effectiveTo
    metadata
    createdAt
    updatedAt
    createdBy
    updatedBy
  }
`;

// ==========================================
// Queries
// ==========================================

/** All rows of a type. activeOnly=true (default) is the storefront dropdown query. */
export const REFERENCE_DATA = gql`
  ${REFERENCE_DATA_FIELDS}
  query ReferenceData($type: ReferenceType!, $activeOnly: Boolean) {
    referenceData(type: $type, activeOnly: $activeOnly) {
      ...ReferenceDataFields
    }
  }
`;

/** Child rows within a hierarchy (active only), e.g. genres of a category. */
export const REFERENCE_DATA_BY_PARENT = gql`
  ${REFERENCE_DATA_FIELDS}
  query ReferenceDataByParent($type: ReferenceType!, $parentCode: String!) {
    referenceDataByParent(type: $type, parentCode: $parentCode) {
      ...ReferenceDataFields
    }
  }
`;

/** The type registry that powers the generic admin management screen. */
export const REFERENCE_TYPES = gql`
  query ReferenceTypes {
    referenceTypes {
      type
      label
      group
      groupLabel
      requiredMetadataKeys
    }
  }
`;

/** Admin offset table for one reference type. */
export const REFERENCE_DATA_OFFSET = gql`
  ${REFERENCE_DATA_FIELDS}
  query ReferenceDataOffsetPagination(
    $type: ReferenceType!
    $pagination: OffsetPaginationInput
  ) {
    referenceDataOffsetPagination(type: $type, pagination: $pagination) {
      content {
        ...ReferenceDataFields
      }
      pageNumber
      pageSize
      totalElements
      totalPages
      hasNext
      hasPrevious
    }
  }
`;
