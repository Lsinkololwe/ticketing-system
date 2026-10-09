/** Designed placeholder for a region whose backend operation does not exist yet. Shows no invented values. */
export function NotAvailable({ what }: { what?: string }) {
  return (
    <p className="buyer-na" role="note">
      <span className="m3-pill" data-tone="neutral">
        Not available yet
      </span>
      {what ? <span className="m3-muted"> {what}</span> : null}
    </p>
  );
}
