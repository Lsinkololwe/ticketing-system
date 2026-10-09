// @vitest-environment jsdom
import { ApolloClient, InMemoryCache } from '@apollo/client';
import { ApolloProvider } from '@apollo/client/react';
import { MockLink } from '@apollo/client/testing';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import { useAdminEventCategories } from '../event.hooks';
import { ADMIN_EVENT_CATEGORIES } from '../event.queries';

const row = (id: string, code: string, name: string, isActive = true) => ({
  __typename: 'ReferenceData', id, code, name, description: null, isActive,
});

function wrap(content: unknown[], counts: unknown[]) {
  const link = new MockLink([
    {
      request: {
        query: ADMIN_EVENT_CATEGORIES,
        variables: { pagination: { page: 0, size: 50, sortBy: 'displayOrder', sortDirection: 'ASC' } },
      },
      result: {
        data: {
          referenceDataAll: {
            __typename: 'ReferenceDataPage', content, pageNumber: 0, pageSize: 50,
            totalElements: content.length, totalPages: 1, hasNext: false, hasPrevious: false,
          },
          categories: counts,
        },
      },
    },
  ]);
  const client = new ApolloClient({ cache: new InMemoryCache(), link });
  return ({ children }: { children: ReactNode }) => <ApolloProvider client={client}>{children}</ApolloProvider>;
}

describe('useAdminEventCategories', () => {
  it('lists reference-data rows in order and joins event counts by code', async () => {
    const { result } = renderHook(() => useAdminEventCategories(), {
      wrapper: wrap(
        [row('1', 'MUSIC', 'Music'), row('2', 'SPORT', 'Sport', false)],
        [{ __typename: 'EventCategory', code: 'MUSIC', eventCount: 4 }]
      ),
    });
    await waitFor(() => expect(result.current.categories).toHaveLength(2));
    expect(result.current.categories.map((c) => c.code)).toEqual(['MUSIC', 'SPORT']);
    expect(result.current.categories[0].eventCount).toBe(4);
    // `categories` serves active rows only, so an inactive category has no count to show.
    expect(result.current.categories[1].eventCount).toBeNull();
    expect(result.current.categories[1].isActive).toBe(false);
  });
});
