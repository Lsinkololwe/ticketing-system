import type { ReactNode } from 'react';

/** Public marketing pages: no auth guard; the frame is rendered by each view. */
export default function PublicRouteLayout({ children }: { children: ReactNode }) {
  return <>{children}</>;
}
