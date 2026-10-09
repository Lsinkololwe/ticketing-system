import { InMemoryCache, gql } from '@apollo/client';
import { describe, expect, it } from 'vitest';
import { CACHE_TYPE_POLICIES } from './client';

describe('cache type policies', () => {
  it('stores a Location selected without an id inside its parent instead of throwing', () => {
    const cache = new InMemoryCache({ typePolicies: CACHE_TYPE_POLICIES as never });
    const query = gql`
      query Q { event { __typename id location { __typename name city } } }
    `;
    const data = { event: { __typename: 'Event', id: 'e1', location: { __typename: 'Location', name: 'Hall', city: 'Lusaka' } } };
    expect(() => cache.writeQuery({ query, data })).not.toThrow();
    expect(cache.readQuery({ query })).toEqual(data);
  });

  it('still normalises by id when the selection has one', () => {
    const cache = new InMemoryCache({ typePolicies: CACHE_TYPE_POLICIES as never });
    const query = gql`
      query Q { event { __typename id location { __typename id name } } }
    `;
    cache.writeQuery({ query, data: { event: { __typename: 'Event', id: 'e1', location: { __typename: 'Location', id: 'l1', name: 'Hall' } } } });
    expect(Object.keys(cache.extract())).toContain('Location:l1');
  });
});
