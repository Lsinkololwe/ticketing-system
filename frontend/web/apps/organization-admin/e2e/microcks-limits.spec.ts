import { expect, test } from '@playwright/test';

import { graphqlMockEndpoint, setOnboardingState } from './microcks/client';

const MICROCKS_URL = `http://localhost:${process.env.MICROCKS_PORT ?? 18080}`;

/**
 * The boundary of what the Microcks harness can mock.
 *
 * <h2>Why a test asserts a limitation</h2>
 * "Microcks cannot serve fragments" is the reason the subgraph fixture exists,
 * and it is the kind of claim that gets repeated until nobody remembers whether
 * it was ever measured. If it were false, the whole subgraph fixture would be unnecessary
 * work; if it silently became false after an upgrade, this test starts failing
 * and someone gets to delete a container.
 *
 * <p>The practical consequence is a routing rule, not a curiosity. Server
 * Components issue plain queries and are fine here. <b>Apollo Client composes
 * fragments as a matter of course</b>, so any Apollo-driven surface mocked this
 * way fails on transport rather than on anything the test meant to check — and
 * it fails identically to a broken backend, which is the expensive part.</p>
 */
test.describe('Microcks harness limits', () => {
  test.beforeEach(async () => {
    await setOnboardingState(MICROCKS_URL, {
      organization: { status: 'ACTIVE', name: 'Lusaka Live Events' },
    });
  });

  test('serves a plain query but not one containing a fragment', async ({ request }) => {
    const endpoint = graphqlMockEndpoint(MICROCKS_URL);

    const plain = await request.post(endpoint, {
      data: { query: '{ myOwnedOrganization { id name status } }' },
    });

    expect(
      plain.status(),
      'the plain query is the case the harness is built for'
    ).toBeLessThan(400);

    const withFragment = await request.post(endpoint, {
      data: {
        query:
          'fragment OrgFields on Organization { id name status } ' +
          '{ myOwnedOrganization { ...OrgFields } }',
      },
    });

    // Recorded as an inequality rather than "expect 500": the exact status is
    // Microcks' business and may change. What matters is that the same data,
    // asked for through a fragment, does not come back — which is precisely what
    // Apollo Client will do.
    expect(
      withFragment.status(),
      'if this now succeeds, Microcks has gained fragment support and the ' +
        'subgraph fixture in F0-4 may no longer be needed'
    ).not.toBe(plain.status());
  });
});
