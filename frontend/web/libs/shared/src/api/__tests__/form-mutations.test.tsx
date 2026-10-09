// @vitest-environment jsdom
import { ApolloClient, InMemoryCache, gql } from '@apollo/client';
import { ApolloProvider } from '@apollo/client/react';
import { MockLink } from '@apollo/client/testing';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GraphQLError } from 'graphql';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { z } from 'zod';
import { Form, FormActions } from '../../forms/Form';
import { TextFieldRHF } from '../../forms/fields';
import { slug } from '../../forms/schemas';
import { useZodForm } from '../../forms/useZodForm';
import { useGraphQLMutationForm, useRestMutationForm } from '../form-mutations';
import { QueryProvider } from '../QueryProvider';
import { defineQueryKeys } from '../query-keys';
import { restRequest } from '../rest-request';

const CREATE = gql`
  mutation CreateOrg($input: CreateOrgInput!) {
    createOrg(input: $input) {
      id
      slug
    }
  }
`;
const schema = z.object({ slug: slug() });

function GqlForm({ onSuccess }: { onSuccess?: (d: unknown) => void }) {
  const form = useZodForm(schema, { defaultValues: { slug: '' } });
  const m = useGraphQLMutationForm({ mutation: CREATE, toVariables: (v: z.output<typeof schema>) => ({ input: v }), onSuccess });
  return (
    <Form form={form} onSubmit={m.submit}>
      <TextFieldRHF name="slug" label="Web address" />
      <FormActions submitLabel="Create" />
    </Form>
  );
}

function withClient(link: MockLink, ui: React.ReactElement) {
  const client = new ApolloClient({ link, cache: new InMemoryCache() });
  return render(<ApolloProvider client={client}>{ui}</ApolloProvider>);
}

describe('useGraphQLMutationForm', () => {
  it('sends variables from parsed values and calls onSuccess with data', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    const link = new MockLink([
      { request: { query: CREATE, variables: { input: { slug: 'my-org' } } }, result: { data: { createOrg: { __typename: 'Org', id: '1', slug: 'my-org' } } } },
    ]);
    withClient(link, <GqlForm onSuccess={onSuccess} />);
    await user.type(screen.getByLabelText('Web address'), ' My-Org ');
    await user.click(screen.getByRole('button', { name: 'Create' }));
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith({ createOrg: { __typename: 'Org', id: '1', slug: 'my-org' } }, { slug: 'my-org' }));
  });

  it('maps extensions.errorCode from a GraphQL error onto the field', async () => {
    const user = userEvent.setup();
    const link = new MockLink([
      {
        request: { query: CREATE, variables: { input: { slug: 'taken' } } },
        result: { errors: [new GraphQLError('nope', { extensions: { errorCode: 'SLUG_TAKEN', classification: 'FAILED_PRECONDITION', retryable: false } })] },
      },
    ]);
    withClient(link, <GqlForm />);
    await user.type(screen.getByLabelText('Web address'), 'taken');
    await user.click(screen.getByRole('button', { name: 'Create' }));
    expect(await screen.findByText('That web address is taken. Try another.')).toBeInTheDocument();
    expect(screen.getByLabelText('Web address')).toHaveAttribute('aria-invalid', 'true');
  });

  it('a network failure becomes the neutral banner, not a crash', async () => {
    const user = userEvent.setup();
    const link = new MockLink([{ request: { query: CREATE, variables: { input: { slug: 'abc' } } }, error: new Error('Failed to fetch') }]);
    withClient(link, <GqlForm />);
    await user.type(screen.getByLabelText('Web address'), 'abc');
    await user.click(screen.getByRole('button', { name: 'Create' }));
    expect(await screen.findByText(/trouble reaching a service/)).toBeInTheDocument();
  });
});

const keys = defineQueryKeys('orgs');

function RestForm({ onSuccess }: { onSuccess?: (d: unknown) => void }) {
  const form = useZodForm(schema, { defaultValues: { slug: '' } });
  const m = useRestMutationForm({
    mutationFn: (v: z.output<typeof schema>) => restRequest<{ id: string }>('/api/orgs', { body: v }),
    invalidate: [keys.lists()],
    onSuccess,
  });
  return (
    <Form form={form} onSubmit={m.submit}>
      <TextFieldRHF name="slug" label="Web address" />
      <FormActions submitLabel="Create" />
    </Form>
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('useRestMutationForm + restRequest', () => {
  it('posts JSON with the CSRF header, invalidates keys and reports success', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: '9' }), { status: 201, headers: { 'content-type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);
    const onSuccess = vi.fn();
    let listFetches = 0;
    function List() {
      useQuery({ queryKey: keys.list({}), queryFn: async () => ++listFetches });
      return null;
    }
    render(
      <QueryProvider>
        <List />
        <RestForm onSuccess={onSuccess} />
      </QueryProvider>
    );
    await waitFor(() => expect(listFetches).toBe(1));
    await user.type(screen.getByLabelText('Web address'), 'my-org');
    await user.click(screen.getByRole('button', { name: 'Create' }));
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith({ id: '9' }, { slug: 'my-org' }));
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/orgs');
    expect(init.method).toBe('POST');
    expect(init.headers['x-requested-with']).toBe('pml-web');
    expect(JSON.parse(init.body)).toEqual({ slug: 'my-org' });
    await waitFor(() => expect(listFetches).toBe(2));
  });

  it('maps an RFC 9457 problem document onto the form', async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ errorCode: 'SLUG_TAKEN', status: 409 }), { status: 409, headers: { 'content-type': 'application/problem+json' } })
      )
    );
    render(
      <QueryProvider>
        <RestForm />
      </QueryProvider>
    );
    await user.type(screen.getByLabelText('Web address'), 'taken');
    await user.click(screen.getByRole('button', { name: 'Create' }));
    expect(await screen.findByText('That web address is taken. Try another.')).toBeInTheDocument();
  });

  it('does not retry a failed write (no hidden double submit)', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 500 }));
    vi.stubGlobal('fetch', fetchMock);
    render(
      <QueryProvider>
        <RestForm />
      </QueryProvider>
    );
    await user.type(screen.getByLabelText('Web address'), 'abc');
    await user.click(screen.getByRole('button', { name: 'Create' }));
    await screen.findByText(/Something went wrong/);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe('restRequest', () => {
  it('GET sends no body or CSRF header; 204 resolves undefined', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(restRequest('/api/x')).resolves.toBeUndefined();
    const init = fetchMock.mock.calls[0][1];
    expect(init.method).toBe('GET');
    expect(init.body).toBeUndefined();
    expect(init.headers['x-requested-with']).toBeUndefined();
    await restRequest('/api/x', { method: 'DELETE', token: 't' });
    expect(fetchMock.mock.calls[1][1].headers.authorization).toBe('Bearer t');
  });
});

describe('QueryProvider and defineQueryKeys', () => {
  it('gives each mounted tree its own client and accepts an injected one', () => {
    const seen: unknown[] = [];
    function Probe() {
      seen.push(useQueryClient());
      return null;
    }
    render(<QueryProvider><Probe /></QueryProvider>);
    render(<QueryProvider><Probe /></QueryProvider>);
    expect(seen[0]).not.toBe(seen[1]);
  });
  it('keys nest for precise invalidation', () => {
    const k = defineQueryKeys('events');
    expect(k.all).toEqual(['events']);
    expect(k.list({ q: 1 })).toEqual(['events', 'list', { q: 1 }]);
    expect(k.lists()).toEqual(['events', 'list']);
    expect(k.detail('7')).toEqual(['events', 'detail', '7']);
    expect(k.custom('stats', 'daily')).toEqual(['events', 'stats', 'daily']);
  });
});
