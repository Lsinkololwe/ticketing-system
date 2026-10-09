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
    # The two fields that make a status configurable rather than merely listed.
    # Their absence from this fragment is why the admin screen could show a
    # status list and not the one thing about it that matters.
    semantic
    allowedTransitions
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

/** Admin table for one reference type — every row including inactive ones. */
export const REFERENCE_DATA_ALL = gql`
  ${REFERENCE_DATA_FIELDS}
  query ReferenceDataAll(
    $type: ReferenceType!
    $pagination: OffsetPaginationInput
  ) {
    referenceDataAll(type: $type, pagination: $pagination) {
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
