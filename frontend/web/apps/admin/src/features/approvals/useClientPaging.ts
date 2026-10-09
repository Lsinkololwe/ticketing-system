import { useEffect, useState } from 'react';

/** Client paging over an already-loaded list; Pagination is 1-based. */
export function useClientPaging<T>(rows: T[], initialSize = 12) {
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(initialSize);
  const pages = Math.max(1, Math.ceil(rows.length / size));
  useEffect(() => {
    if (page > pages) setPage(pages);
  }, [page, pages]);
  const slice = rows.slice((page - 1) * size, page * size);
  return {
    slice,
    pagination: { page, pageSize: size, total: rows.length, onPageChange: setPage, onPageSizeChange: (n: number) => { setSize(n); setPage(1); } },
    reset: () => setPage(1),
  };
}
