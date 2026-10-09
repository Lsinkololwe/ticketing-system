import { EmptyState } from '@pml.tickets/shared/components/m3';

/**
 * Marks a region whose backend operation does not exist yet. No values are
 * invented; the missing operation is named in the report, not on screen.
 */
export function NotAvailable({ what }: { what: string }) {
  return (
    <div data-testid="not-available">
      <EmptyState icon="info" title="Not available yet" description={`${what} will appear here once it is available.`} />
    </div>
  );
}
