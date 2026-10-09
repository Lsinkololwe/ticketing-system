/**
 * Query key convention for TanStack Query (REST/BFF calls).
 *
 *   [scope, 'list', filters] / [scope, 'detail', id] / [scope, ...custom]
 *
 * One factory per scope keeps invalidation precise:
 *   `queryClient.invalidateQueries({ queryKey: keys.lists() })` after a create,
 *   `keys.detail(id)` after an edit. Apollo manages its own cache by `__typename:id`,
 *   so GraphQL operations never use these keys.
 */
export function defineQueryKeys<Scope extends string>(scope: Scope) {
  const all = [scope] as const;
  return {
    all,
    lists: () => [scope, 'list'] as const,
    list: (filters: Record<string, unknown> = {}) => [scope, 'list', filters] as const,
    details: () => [scope, 'detail'] as const,
    detail: (id: string) => [scope, 'detail', id] as const,
    custom: (...parts: Array<string | number | Record<string, unknown>>) => [scope, ...parts] as const,
  };
}
