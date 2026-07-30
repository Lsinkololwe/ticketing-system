/**
 * Reference Data GraphQL Mutations (admin-only)
 *
 * @see backend/catalog-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';
import { REFERENCE_DATA_FIELDS } from './reference-data.queries';

export const CREATE_REFERENCE_DATA = gql`
  ${REFERENCE_DATA_FIELDS}
  mutation CreateReferenceData($input: CreateReferenceDataInput!) {
    createReferenceData(input: $input) {
      success
      message
      errors
      data {
        ...ReferenceDataFields
      }
    }
  }
`;

export const UPDATE_REFERENCE_DATA = gql`
  ${REFERENCE_DATA_FIELDS}
  mutation UpdateReferenceData($id: ID!, $input: UpdateReferenceDataInput!) {
    updateReferenceData(id: $id, input: $input) {
      success
      message
      errors
      data {
        ...ReferenceDataFields
      }
    }
  }
`;

export const DELETE_REFERENCE_DATA = gql`
  mutation DeleteReferenceData($id: ID!) {
    deleteReferenceData(id: $id) {
      success
      message
      errors
    }
  }
`;

export const SET_REFERENCE_DATA_ACTIVE = gql`
  ${REFERENCE_DATA_FIELDS}
  mutation SetReferenceDataActive($id: ID!, $active: Boolean!) {
    setReferenceDataActive(id: $id, active: $active) {
      success
      message
      errors
      data {
        ...ReferenceDataFields
      }
    }
  }
`;
