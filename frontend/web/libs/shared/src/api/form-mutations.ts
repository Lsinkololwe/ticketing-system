'use client';

import type { DocumentNode, OperationVariables, TypedDocumentNode } from '@apollo/client';
import { useMutation } from '@apollo/client/react';
import { useMutation as useRestMutation, useQueryClient, type QueryKey } from '@tanstack/react-query';
import { useCallback } from 'react';

/** What `<Form onSubmit>` expects. Throwing is the signal: `<Form>` maps the error onto fields/banner. */
export type FormSubmit<TValues, TResult> = (values: TValues) => Promise<TResult>;

export interface UseGraphQLMutationFormOptions<TData, TVars extends OperationVariables, TValues> {
  mutation: DocumentNode | TypedDocumentNode<TData, TVars>;
  /** Parsed form values (zod output) -> operation variables. */
  toVariables: (values: TValues) => TVars;
  onSuccess?: (data: TData, values: TValues) => void | Promise<void>;
  /** Operation names or documents to refetch after success (Apollo `refetchQueries`). */
  refetchQueries?: Array<string | DocumentNode>;
  /** Wait for the refetch before resolving (keeps the submit button busy until lists are fresh). */
  awaitRefetchQueries?: boolean;
  /** Optimistic result built from the values (Apollo `optimisticResponse`). */
  optimisticResponse?: (values: TValues) => TData;
  /** Cache update after success, e.g. evict or modify a list. */
  update?: (cache: any, result: { data?: TData | null }, values: TValues) => void;
}

/**
 * Apollo `useMutation` shaped for `<Form>`: `onSubmit={m.submit}`. Rejects on GraphQL/network errors so the
 * Form maps `extensions.errorCode` / `fields` through `applyServerError`; never swallow errors here.
 */
export function useGraphQLMutationForm<TData, TVars extends OperationVariables, TValues>(
  options: UseGraphQLMutationFormOptions<TData, TVars, TValues>
) {
  const { mutation, toVariables, onSuccess, refetchQueries, awaitRefetchQueries, optimisticResponse, update } = options;
  const [mutate, state] = useMutation<TData, TVars>(mutation as TypedDocumentNode<TData, TVars>);

  const submit = useCallback<FormSubmit<TValues, TData>>(
    async (values) => {
      const result = (await mutate({
        variables: toVariables(values) as never,
        refetchQueries,
        awaitRefetchQueries,
        optimisticResponse: optimisticResponse ? (optimisticResponse(values) as never) : undefined,
        update: update ? (cache: any, r: any) => update(cache, r, values) : undefined,
        errorPolicy: 'all',
      } as never)) as { data?: TData | null; error?: unknown };
      if (result.error) throw result.error;
      const data = result.data as TData;
      await onSuccess?.(data, values);
      return data;
    },
    [mutate, toVariables, refetchQueries, awaitRefetchQueries, optimisticResponse, update, onSuccess]
  );

  return { submit, loading: state.loading, data: state.data, error: state.error, reset: state.reset };
}

export interface UseRestMutationFormOptions<TData, TValues> {
  /** Performs the call, typically `(v) => restRequest<Out>('/api/x', { body: toBody(v) })`. */
  mutationFn: (values: TValues) => Promise<TData>;
  onSuccess?: (data: TData, values: TValues) => void | Promise<void>;
  /** TanStack query keys to invalidate after success (see `defineQueryKeys`). */
  invalidate?: QueryKey[];
}

/** TanStack Query `useMutation` shaped for `<Form>`; same contract as the GraphQL variant. */
export function useRestMutationForm<TData, TValues>(options: UseRestMutationFormOptions<TData, TValues>) {
  const { mutationFn, onSuccess, invalidate } = options;
  const qc = useQueryClient();
  const mutation = useRestMutation<TData, Error, TValues>({
    mutationFn,
    retry: false, // a retried submit risks a double write; the user can press submit again
  });
  const submit = useCallback<FormSubmit<TValues, TData>>(
    async (values) => {
      const data = await mutation.mutateAsync(values);
      if (invalidate?.length) await Promise.all(invalidate.map((queryKey) => qc.invalidateQueries({ queryKey })));
      await onSuccess?.(data, values);
      return data;
    },
    [mutation, invalidate, qc, onSuccess]
  );
  return { submit, loading: mutation.isPending, data: mutation.data, error: mutation.error, reset: mutation.reset };
}
