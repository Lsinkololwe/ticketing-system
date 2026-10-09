import { useState } from 'react';

/** Client-side paging for small lists. Page is 1-based. */
export function usePaged<T>(list: T[], initialSize = 5) {
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(initialSize);
  const pages = Math.max(1, Math.ceil(list.length / size));
  const current = Math.min(page, pages);
  return {
    rows: list.slice((current - 1) * size, current * size),
    pagination: {
      page: current,
      pageSize: size,
      total: list.length,
      onPageChange: setPage,
      onPageSizeChange: (n: number) => {
        setSize(n);
        setPage(1);
      },
    },
    reset: () => setPage(1),
  };
}
