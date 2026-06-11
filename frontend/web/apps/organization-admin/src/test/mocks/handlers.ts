import { graphql, HttpResponse } from 'msw';

// Mock data for organization
const mockOrganization = {
  id: 'org-123',
  name: 'Test Event Company',
  slug: 'test-event-company',
  status: 'DRAFT',
  type: 'BUSINESS',
  tagline: 'Creating amazing events',
  description: 'We organize the best events in Lusaka',
  logoUrl: null,
  bannerUrl: null,
  website: 'https://testevents.com',
  businessEmail: 'contact@testevents.com',
  businessPhone: '+260971234567',
  businessType: null,
  businessRegistrationNumber: null,
  taxId: null,
  city: 'Lusaka',
  province: 'LUSAKA',
  country: 'Zambia',
  businessAddress: {
    street: null,
    city: 'Lusaka',
    province: 'LUSAKA',
    country: 'Zambia',
    postalCode: null,
  },
  socialLinks: {
    facebook: 'https://facebook.com/testevents',
    twitter: null,
    instagram: 'https://instagram.com/testevents',
    linkedin: null,
    youtube: null,
    tiktok: null,
  },
  submittedAt: null,
  approvedAt: null,
  canSubmitForReview: false,
  isApproved: false,
  documentsVerified: false,
  payoutAccountVerified: false,
  rejectionReason: null,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
};

export const handlers = [
  // Query: myOrganization
  graphql.query('MyOrganization', () => {
    return HttpResponse.json({
      data: {
        myOrganization: null, // Default: no organization
      },
    });
  }),

  // Query: myOrganizationStatus
  graphql.query('MyOrganizationStatus', () => {
    return HttpResponse.json({
      data: {
        myOrganizationStatus: null,
      },
    });
  }),

  // Mutation: applyToBeOrganizer
  graphql.mutation('ApplyToBeOrganizer', ({ variables }) => {
    const input = variables.input as any;
    return HttpResponse.json({
      data: {
        applyToBeOrganizer: {
          __typename: 'Organization',
          ...mockOrganization,
          ...input,
          id: 'org-' + Math.random().toString(36).substr(2, 9),
          status: 'DRAFT',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      },
    });
  }),

  // Mutation: updateOrganizationApplication
  graphql.mutation('UpdateOrganizationApplication', ({ variables }) => {
    const { id, input } = variables as any;
    return HttpResponse.json({
      data: {
        updateOrganizationApplication: {
          __typename: 'Organization',
          ...mockOrganization,
          ...input,
          id,
          updatedAt: new Date().toISOString(),
        },
      },
    });
  }),

  // Mutation: submitOrganizationForReview
  graphql.mutation('SubmitOrganizationForReview', ({ variables }) => {
    const { id } = variables as any;
    return HttpResponse.json({
      data: {
        submitOrganizationForReview: {
          __typename: 'Organization',
          ...mockOrganization,
          id,
          status: 'PENDING_REVIEW',
          submittedAt: new Date().toISOString(),
          canSubmitForReview: false,
        },
      },
    });
  }),
];

// Helper to create custom handlers for specific test scenarios
export const createMockHandlers = {
  // Organization with existing data
  withExistingOrganization: (org: Partial<typeof mockOrganization>) =>
    graphql.query('MyOrganization', () => {
      return HttpResponse.json({
        data: {
          myOrganization: {
            __typename: 'Organization',
            ...mockOrganization,
            ...org,
          },
        },
      });
    }),

  // Organization in specific status
  withStatus: (status: string) =>
    graphql.query('MyOrganization', () => {
      return HttpResponse.json({
        data: {
          myOrganization: {
            __typename: 'Organization',
            ...mockOrganization,
            status,
            submittedAt: status !== 'DRAFT' ? new Date().toISOString() : null,
            approvedAt: status === 'APPROVED' || status === 'ACTIVE' ? new Date().toISOString() : null,
            isApproved: status === 'APPROVED' || status === 'ACTIVE',
          },
        },
      });
    }),

  // Network error
  withNetworkError: (operation: 'query' | 'mutation', name: string) => {
    if (operation === 'query') {
      return graphql.query(name, () => {
        return HttpResponse.json(
          {
            errors: [
              {
                message: 'Network error',
                extensions: { code: 'NETWORK_ERROR' },
              },
            ],
          },
          { status: 503 }
        );
      });
    } else {
      return graphql.mutation(name, () => {
        return HttpResponse.json(
          {
            errors: [
              {
                message: 'Network error',
                extensions: { code: 'NETWORK_ERROR' },
              },
            ],
          },
          { status: 503 }
        );
      });
    }
  },

  // Validation error
  withValidationError: (mutationName: string, field: string, message: string) =>
    graphql.mutation(mutationName, () => {
      return HttpResponse.json({
        errors: [
          {
            message,
            extensions: {
              code: 'BAD_USER_INPUT',
              field,
            },
          },
        ],
      });
    }),
};
