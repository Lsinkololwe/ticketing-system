/**
 * The one query every reference dropdown reads (catalog `referenceData`).
 *
 * Lists are owned by the platform and edited by staff, so no client carries one. The
 * operation name is unique across the three apps (codegen refuses duplicates).
 */
import { gql } from '@apollo/client';

export const REFERENCE_OPTIONS = gql`
  query ReferenceOptions($type: ReferenceType!) {
    referenceData(type: $type, activeOnly: true) {
      id
      code
      name
      description
      parentCode
      displayOrder
      metadata
    }
  }
`;
