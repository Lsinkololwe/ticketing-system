import { describe, expect, it, vi } from 'vitest';
import { newTier } from './model';
import { syncTiers } from './saveTiers';
import type { EditorTier } from '@/lib/api/event-editor';

const server = (id: string, sold = 0): EditorTier => ({ id, code: id, name: id, description: null, price: '1', currency: 'ZMW', quantity: 10, soldQuantity: sold, minPerOrder: 1, maxPerOrder: 8, benefits: [], salesStartAt: null, salesEndAt: null, earlyBirdPrice: null, earlyBirdEndsAt: null, sortOrder: 0, isActive: true, isHidden: false, accessCode: null });

describe('syncTiers', () => {
  it('deletes removed unsold tiers, updates kept, creates new and reorders', async () => {
    const ops = {
      createTier: vi.fn().mockResolvedValue({ data: { createTicketTier: { id: 'n1' } } }),
      updateTier: vi.fn().mockResolvedValue({}),
      deleteTier: vi.fn().mockResolvedValue({}),
      reorderTiers: vi.fn().mockResolvedValue({}),
    };
    const drafts = [newTier({ name: 'New', id: null }), newTier({ name: 'Keep', id: 'a', key: 'a' })];
    const ids = await syncTiers('e1', [server('a'), server('b'), server('c', 3)], drafts, ops);
    expect(ops.deleteTier).toHaveBeenCalledTimes(1);
    expect(ops.deleteTier).toHaveBeenCalledWith('b');
    expect(ops.updateTier).toHaveBeenCalledWith('a', expect.objectContaining({ name: 'Keep', sortOrder: 1 }));
    expect(ids).toEqual(['n1', 'a']);
    expect(ops.reorderTiers).toHaveBeenCalledWith('e1', ['n1', 'a']);
  });
});
