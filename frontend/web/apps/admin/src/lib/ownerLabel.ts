/** Owners who signed up with a contact code have no name: show their email, then the masked primary contact. */
export const ownerLabel = (o: {
  owner?: { fullName?: string | null; email?: string | null; contacts?: { valueMasked: string; primary: boolean }[] | null } | null;
}): string =>
  o.owner?.fullName?.trim() || o.owner?.email || (o.owner?.contacts?.find((c) => c.primary) ?? o.owner?.contacts?.[0])?.valueMasked || '—';
