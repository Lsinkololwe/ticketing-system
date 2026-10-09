import { gql } from '@apollo/client';

export const FEATURE_EVENT = gql`
  mutation FeatureEvent($eventId: ID!, $featured: Boolean!) {
    featureEvent(eventId: $eventId, featured: $featured) {
      id
      featured
    }
  }
`;

export const CANCEL_EVENT = gql`
  mutation CancelEventAsAdmin($id: ID!, $input: EventCancellationInput!) {
    cancelEvent(id: $id, input: $input) {
      ticketsAffected
      refundSagaInitiated
      event {
        id
        status
      }
    }
  }
`;



// Event categories are `EVENT_CATEGORY` rows of the reference data, the store the public `categories` list reads,
// so these write there. The code is fixed once created: events refer to a category by it.
export const CREATE_EVENT_CATEGORY = gql`
  mutation CreateEventCategory($input: CreateReferenceDataInput!) {
    createReferenceData(input: $input) { id code name description isActive }
  }
`;
export const UPDATE_EVENT_CATEGORY = gql`
  mutation UpdateEventCategory($id: ID!, $input: UpdateReferenceDataInput!) {
    updateReferenceData(id: $id, input: $input) { id code name description isActive }
  }
`;
export const DELETE_EVENT_CATEGORY = gql`
  mutation DeleteEventCategory($id: ID!) {
    deleteReferenceData(id: $id)
  }
`;
export const SET_EVENT_CATEGORY_ACTIVE = gql`
  mutation SetEventCategoryActive($id: ID!, $active: Boolean!) {
    setReferenceDataActive(id: $id, active: $active) { id code name description isActive }
  }
`;


export const CREATE_PROVINCE = gql`
  mutation CreateProvince($input: CreateProvinceInput!) {
    createProvince(input: $input) { id name code country cityCount isActive }
  }
`;
export const UPDATE_PROVINCE = gql`
  mutation UpdateProvince($id: ID!, $input: UpdateProvinceInput!) {
    updateProvince(id: $id, input: $input) { id name code country cityCount isActive }
  }
`;
export const DELETE_PROVINCE = gql`
  mutation DeleteProvince($id: ID!) {
    deleteProvince(id: $id)
  }
`;


export const CREATE_CITY = gql`
  mutation CreateCity($input: CreateCityInput!) {
    createCity(input: $input) { id name code provinceId province country eventCount isActive }
  }
`;
export const UPDATE_CITY = gql`
  mutation UpdateCity($id: ID!, $input: UpdateCityInput!) {
    updateCity(id: $id, input: $input) { id name code provinceId province country eventCount isActive }
  }
`;
export const DELETE_CITY = gql`
  mutation DeleteCity($id: ID!) {
    deleteCity(id: $id)
  }
`;

export const ADD_EVENT_APPROVAL_COMMENT = gql`
  mutation AddEventApprovalComment($eventId: ID!, $comment: String!, $isInternal: Boolean) {
    addApprovalComment(eventId: $eventId, comment: $comment, isInternal: $isInternal) {
      eventId
    }
  }
`;
