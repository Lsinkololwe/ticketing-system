import path from 'node:path';

import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { startSubgraph, type StartedSubgraph } from './subgraph-container';

const SDL = path.join(
  __dirname,
  '..',
  'apps',
  'organization-admin',
  'e2e',
  'microcks',
  'onboarding-identity.graphql'
);

/**
 * Track F0-4 — the fixture speaks the real schema, in a container.
 *
 * <h2>The two properties worth a container</h2>
 * <ol>
 *   <li><b>Fragments work.</b> This is the whole reason the fixture exists.
 *       Microcks answers 500 to any query containing one — measured in
 *       `microcks-limits.spec.ts` — and Apollo Client composes fragments as a
 *       matter of course.</li>
 *   <li><b>The schema is enforced.</b> A query naming a field the SDL does not
 *       have is rejected here, exactly as the real subgraph would reject it.
 *       That is what separates this from the hand-rolled stub the frontend
 *       quality skill forbids: a stub answers whatever the test expects, so a
 *       test written against a field that does not exist passes and the same
 *       code fails in production.</li>
 * </ol>
 */
describe('subgraph fixture', () => {
  let subgraph: StartedSubgraph;

  beforeAll(async () => {
    subgraph = await startSubgraph(SDL);
  }, 180_000);

  afterAll(async () => {
    await subgraph?.stop();
  }, 60_000);

  async function ask(query: string) {
    const response = await fetch(subgraph.url, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ query }),
    });
    return { status: response.status, body: await response.json() };
  }

  it('answers a plain query', async () => {
    const { status, body } = await ask('{ myOwnedOrganization { id name status } }');

    expect(status).toBe(200);
    expect(body.errors, JSON.stringify(body.errors)).toBeUndefined();
    expect(body.data.myOwnedOrganization).toMatchObject({ id: 'mock-id' });
  });

  it('answers a query containing a fragment — the case Microcks cannot serve', async () => {
    const { status, body } = await ask(
      'fragment OrgFields on Organization { id name status } ' +
        '{ myOwnedOrganization { ...OrgFields } }'
    );

    expect(status).toBe(200);
    expect(
      body.errors,
      'a fragment must resolve here, or this fixture buys nothing over Microcks'
    ).toBeUndefined();
    expect(body.data.myOwnedOrganization.id).toBe('mock-id');
  });

  it('rejects a field the schema does not declare', async () => {
    const { body } = await ask('{ myOwnedOrganization { fieldThatDoesNotExist } }');

    expect(
      body.errors?.[0]?.message,
      'a stub would happily answer this; a schema will not, and that difference ' +
        'is the entire argument for running a container'
    ).toContain('fieldThatDoesNotExist');
  });

  it('rejects a malformed query rather than inventing a response', async () => {
    const { body } = await ask('{ myOwnedOrganization { id ');

    expect(body.errors?.length ?? 0).toBeGreaterThan(0);
  });
});
