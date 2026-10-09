import type { ReactNode } from 'react';
import { EmptyState, ErrorState, Skeleton, type EmptyStateProps } from '@pml.tickets/shared/components/m3';

interface DataStateProps {
  loading: boolean;
  error?: Parameters<typeof ErrorState>[0]['error'];
  onRetry?: () => void;
  empty?: boolean;
  emptyState?: EmptyStateProps;
  /** Skeleton rows while loading. */
  rows?: number;
  children: ReactNode;
}

/**
 * The four designed states of a data region: loading skeleton, error banner
 * (retry only when the server says it is retryable), empty state, content.
 */
export function DataState({ loading, error, onRetry, empty, emptyState, rows = 4, children }: DataStateProps) {
  if (error) return <ErrorState error={error} onRetry={onRetry} />;
  if (loading) {
    return (
      <div className="m3-stack" role="status" aria-label="Loading" data-testid="loading">
        {Array.from({ length: rows }, (_, i) => (
          <Skeleton key={i} />
        ))}
      </div>
    );
  }
  if (empty && emptyState) return <EmptyState {...emptyState} />;
  return <>{children}</>;
}
