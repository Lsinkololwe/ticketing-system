import type { PageInfo } from './graphql/index';

/**
 * The page metadata a UI can rely on.
 *
 * <h2>Derived from the generated type, never re-declared</h2>
 * The schema's `PageInfo` makes every field nullable, because a subgraph may
 * omit any of them. A component cannot render `totalPages: number | null`
 * without a default on every use, so each hook normalises once and hands on a
 * shape with no nulls in it.
 *
 * <p>The field *names* still come from codegen via `Pick`, and that is the whole
 * point. Re-declaring them by hand produces a type that keeps compiling after
 * the schema renames or drops a field — the query starts returning `undefined`,
 * the UI renders `NaN` or a blank cell, and nothing fails until someone looks at
 * a screen. Picking from the generated type turns that into a compile error at
 * the moment codegen runs.</p>
 */
type NonNullableFields<T> = { [K in keyof T]-?: NonNullable<T[K]> };

/**
 * Offset pagination as the admin surfaces present it.
 *
 * <p>`totalCount` rather than `totalElements`, and `currentPage` rather than
 * `pageNumber`: both spellings exist in the schema because the subgraphs
 * disagree, and picking one here is what stops that disagreement reaching every
 * table component.</p>
 */
export type OffsetPageInfo = NonNullableFields<
  Pick<
    PageInfo,
    | 'totalCount'
    | 'pageSize'
    | 'currentPage'
    | 'totalPages'
    | 'hasNextPage'
    | 'hasPreviousPage'
  >
>;

/**
 * The flat page shape catalog returns, with a cursor for the feeds that use one.
 *
 * <p>`endCursor` stays nullable: the last page genuinely has no next cursor, and
 * flattening that to an empty string would make "no more pages" and "a page
 * whose cursor is empty" the same value.</p>
 */
export type FlatPageInfo = NonNullableFields<
  Pick<PageInfo, 'totalElements' | 'totalPages' | 'pageSize' | 'hasNext' | 'hasPrevious'>
> & {
  pageNumber: number;
  endCursor: string | null;
};
