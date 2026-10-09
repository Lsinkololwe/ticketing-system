import type { EditorTier } from '@/lib/api/event-editor';
import { tierInput, tierUpdateInput, type TierDraft } from './model';

export interface TierOps {
  createTier: (eventId: string, input: unknown) => Promise<{ data?: unknown } | null | undefined>;
  updateTier: (tierId: string, input: unknown) => Promise<unknown>;
  deleteTier: (tierId: string) => Promise<unknown>;
  reorderTiers: (eventId: string, tierIds: string[]) => Promise<unknown>;
}

/**
 * Brings the server's tiers in line with the drafts: delete removed ones,
 * update existing, create new, then persist the order. Returns the final ids.
 */
export async function syncTiers(eventId: string, server: EditorTier[], drafts: TierDraft[], ops: TierOps): Promise<string[]> {
  const keep = new Set(drafts.map((d) => d.id).filter(Boolean));
  for (const s of server) if (!keep.has(s.id) && s.soldQuantity === 0) await ops.deleteTier(s.id);

  const ids: string[] = [];
  for (let i = 0; i < drafts.length; i += 1) {
    const d = drafts[i] as TierDraft;
    if (d.id) {
      await ops.updateTier(d.id, tierUpdateInput(d, i));
      ids.push(d.id);
    } else {
      const res = await ops.createTier(eventId, tierInput(d, i));
      const created = (res?.data as { createTicketTier?: { id?: string } } | undefined)?.createTicketTier?.id;
      if (created) ids.push(created);
    }
  }
  if (ids.length > 1) await ops.reorderTiers(eventId, ids);
  return ids;
}
