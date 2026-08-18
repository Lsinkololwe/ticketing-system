/**
 * Postman collection generator for the Microcks GraphQL mock.
 *
 * ## Why generated, and why this exact shape
 *
 * A GraphQL IDL carries no examples, so Microcks takes its mock dataset from a
 * companion Postman Collection imported as a *secondary* artifact. The shape
 * below is not a guess — it mirrors Microcks' own `films-postman.json` sample,
 * and three details in it are load-bearing:
 *
 * 1. `request.url.raw` must be `http://<operationName>`. The **host is the
 *    operation name**; that is how Microcks binds a Postman request to a
 *    GraphQL operation. With any other URL the mock imports cleanly, registers
 *    the operations, and then answers every query with
 *    `400 No matching response found`.
 * 2. Requests must sit inside `queries` / `mutations` folders.
 * 3. `info.description` must **start** with `version=<v>` — that is where
 *    Microcks reads the API version from.
 *
 * ## Why a generator rather than ten checked-in files
 *
 * This suite drives ten organization statuses through one no-argument query.
 * Ten near-identical 150-line Postman files would be a copy-paste farm where a
 * single stale field silently changes what a test proves. One fixture function
 * keeps them in lockstep.
 *
 * @see onboarding-identity.graphql — the main artifact
 * @see https://microcks.io/documentation/references/artifacts/graphql-conventions/
 */

export const MICROCKS_API_NAME = 'Onboarding Identity API';
export const MICROCKS_API_VERSION = '1.0';

// =============================================================================
// TYPES
// =============================================================================

export type OrgStatus =
  | 'DRAFT'
  | 'PENDING_DOCUMENTS'
  | 'PENDING_REVIEW'
  | 'CHANGES_REQUESTED'
  | 'REJECTED'
  | 'APPROVED'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'INACTIVE'
  | 'PENDING_DELETION';

export type BusinessType =
  | 'SOLE_PROPRIETORSHIP'
  | 'PARTNERSHIP'
  | 'LIMITED_COMPANY'
  | 'NGO'
  | 'GOVERNMENT'
  | 'INDIVIDUAL';

export interface OrganizationOptions {
  status: OrgStatus;
  businessType?: BusinessType | null;
  rejectionReason?: string | null;
  name?: string;
}

// =============================================================================
// FIXTURE
// =============================================================================

/**
 * A full Organization, carrying every field the `OrganizationFields` fragment
 * selects.
 *
 * Microcks narrows the response to the requested selection set, but it can only
 * return fields the example actually defines — a field omitted here comes back
 * `null` and quietly changes what the screen renders, which reads as a UI bug.
 */
export function organization(options: OrganizationOptions) {
  const {
    status,
    businessType = 'LIMITED_COMPANY',
    rejectionReason = null,
    name = 'Lusaka Live Events',
  } = options;

  const operational = status === 'ACTIVE' || status === 'APPROVED';
  const editable =
    status === 'DRAFT' || status === 'PENDING_DOCUMENTS' || status === 'CHANGES_REQUESTED';

  return {
    // Apollo appends __typename to every selection set and rejects a response
    // that omits it with "Server response was malformed for query ...".
    // Microcks serves the example verbatim, so the example must carry it.
    __typename: 'Organization',
    id: 'org-e2e-1',
    ownerId: 'user-e2e-1',
    name,
    slug: 'lusaka-live-events',
    description: 'We run live music events across Zambia',
    tagline: 'Zambia’s live music home',
    logoUrl: null,
    bannerUrl: null,
    website: 'https://lusakalive.co.zm',
    socialLinks: {
      __typename: 'SocialLinks',
      facebook: null,
      instagram: null,
      twitter: null,
      linkedin: null,
      youtube: null,
      tiktok: null,
    },
    type: 'BUSINESS',
    status,
    kybStatus: 'IN_PROGRESS',
    businessEmail: 'hello@lusakalive.co.zm',
    businessPhone: '+260971111111',
    businessAddress: {
      __typename: 'BusinessAddress',
      addressLine1: 'Plot 12, Great East Road',
      addressLine2: null,
      city: 'Lusaka',
      province: 'LUSAKA',
      country: 'Zambia',
      countryCode: 'ZM',
      postalCode: '10101',
      formattedAddress: 'Plot 12, Great East Road, Lusaka',
    },
    businessType,
    businessRegistrationNumber: 'PACRA-12345',
    taxId: '1002003004',
    verified: operational,
    documentsVerified: operational,
    payoutAccountVerified: false,
    verifiedAt: null,
    submittedAt: status === 'DRAFT' ? null : '2026-08-01T09:00:00Z',
    approvedAt: operational ? '2026-08-05T09:00:00Z' : null,
    rejectionReason,
    reviewedAt: rejectionReason ? '2026-08-04T09:00:00Z' : null,
    canCreateDraftEvents: status !== 'REJECTED' && status !== 'SUSPENDED',
    canPublishEvents: operational,
    canReceivePayouts: operational,
    canBeEdited: editable || operational,
    canSubmitForReview: editable,
    isApproved: operational,
    isInApprovalWorkflow: editable || status === 'PENDING_REVIEW',
    createdAt: '2026-07-28T09:00:00Z',
    updatedAt: '2026-08-01T09:00:00Z',
  };
}

// =============================================================================
// COLLECTION
// =============================================================================

/**
 * The full selection set the app's `OrganizationFields` fragment requests.
 *
 * This must match the fragment, not merely be "enough to identify the record".
 * Microcks shapes its response from the query declared on the Postman request:
 * declare three fields here and a client asking for forty gets a response Apollo
 * rejects outright with `Server response was malformed for query 'MyOrganization'`.
 * The symptom is a page stuck on its error state while the *server-side* guard,
 * which asks for only id/name/status, works perfectly — so the app looks broken
 * in exactly the place the mock is thin.
 */
const ORGANIZATION_SELECTION = `
        __typename
        id
        ownerId
        name
        slug
        description
        tagline
        logoUrl
        bannerUrl
        website
        socialLinks {
            __typename
            facebook
            instagram
            twitter
            linkedin
            youtube
            tiktok
        }
        type
        status
        kybStatus
        businessEmail
        businessPhone
        businessAddress {
            __typename
            addressLine1
            addressLine2
            city
            province
            country
            countryCode
            postalCode
            formattedAddress
        }
        businessType
        businessRegistrationNumber
        taxId
        verified
        documentsVerified
        payoutAccountVerified
        verifiedAt
        submittedAt
        approvedAt
        rejectionReason
        reviewedAt
        canCreateDraftEvents
        canPublishEvents
        canReceivePayouts
        canBeEdited
        canSubmitForReview
        isApproved
        isInApprovalWorkflow
        createdAt
        updatedAt`;

/** One Postman request bound to one GraphQL operation. */
function operationItem(operationName: string, query: string, responseBody: unknown) {
  return {
    name: operationName,
    request: {
      method: 'POST',
      header: [],
      body: { mode: 'graphql', graphql: { query, variables: '{}' } },
      // Load-bearing: the host IS the operation name.
      url: { raw: `http://${operationName}`, protocol: 'http', host: [operationName] },
    },
    response: [
      {
        name: operationName,
        originalRequest: {
          method: 'POST',
          header: [],
          body: { mode: 'graphql', graphql: { query, variables: '{}' } },
          url: { raw: '{{url}}', host: ['{{url}}'] },
        },
        status: 'OK',
        code: 200,
        _postman_previewlanguage: 'json',
        header: null,
        cookie: [],
        body: JSON.stringify(responseBody, null, 2),
      },
    ],
  };
}

/**
 * A dataset whose query answers with a GraphQL authorization error.
 *
 * This is the exact shape a `hasRole('ORGANIZER')` failure returns: the field is
 * `null` **and** `errors` is populated. The old client code read only the null
 * and reported "no organization", which is how a token-refresh blip erased a
 * live application from the UI's point of view. Microcks serves whatever body
 * the example carries, so the real failure shape can be replayed verbatim.
 */
export function buildErrorCollection() {
  return {
    info: {
      _postman_id: 'a1b2c3d4-0000-4000-8000-000000000002',
      name: MICROCKS_API_NAME,
      description: `version=${MICROCKS_API_VERSION} - Authorization-failure dataset`,
      schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    },
    item: [
      {
        name: 'queries',
        item: [
          operationItem(
            'myOwnedOrganization',
            `query MyOrganization {\n    myOwnedOrganization {${ORGANIZATION_SELECTION}\n    }\n}`,
            {
              data: { myOwnedOrganization: null },
              errors: [{ message: 'Access is denied', extensions: { classification: 'FORBIDDEN' } }],
            }
          ),
        ],
      },
    ],
    variable: [],
  };
}

/**
 * Build the mock dataset for a given organization state.
 *
 * @param org the organization to report, or `null` for a caller who has not applied
 */
export function buildCollection(org: OrganizationOptions | null) {
  const record = org ? organization(org) : null;

  // Mutations resolve to plausible successors. The specs that exercise them
  // assert navigation and gating; the real state transitions are covered
  // against a real MongoDB by the Testcontainers suite in identity-service.
  const applied = organization({ ...(org ?? { status: 'DRAFT' }), status: 'DRAFT' });
  const submitted = organization({
    ...(org ?? { status: 'PENDING_REVIEW' }),
    status: 'PENDING_REVIEW',
  });

  return {
    info: {
      _postman_id: 'a1b2c3d4-0000-4000-8000-000000000001',
      name: MICROCKS_API_NAME,
      // `version=` must come first — Microcks parses the version from here.
      description: `version=${MICROCKS_API_VERSION} - Mock dataset for organizer onboarding e2e`,
      schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    },
    item: [
      {
        name: 'queries',
        item: [
          operationItem(
            'myOwnedOrganization',
            `query MyOrganization {\n    myOwnedOrganization {${ORGANIZATION_SELECTION}\n    }\n}`,
            { data: { myOwnedOrganization: record } }
          ),
        ],
      },
      {
        name: 'mutations',
        item: [
          operationItem(
            'applyToBeOrganizer',
            `mutation ApplyToBeOrganizer {\n    applyToBeOrganizer {${ORGANIZATION_SELECTION}\n    }\n}`,
            { data: { applyToBeOrganizer: applied } }
          ),
          operationItem(
            'updateOrganizationApplication',
            `mutation UpdateOrganizationApplication {\n    updateOrganizationApplication {${ORGANIZATION_SELECTION}\n    }\n}`,
            { data: { updateOrganizationApplication: record ?? applied } }
          ),
          operationItem(
            'submitOrganizationForReview',
            `mutation SubmitOrganizationForReview {\n    submitOrganizationForReview {${ORGANIZATION_SELECTION}\n    }\n}`,
            { data: { submitOrganizationForReview: submitted } }
          ),
        ],
      },
    ],
    variable: [],
  };
}
