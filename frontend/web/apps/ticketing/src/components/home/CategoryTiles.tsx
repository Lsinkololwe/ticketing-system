'use client';

import { SectionHeader } from '@pml.tickets/shared/components/m3';

/** "Browse by category": one tile per active catalog category. */
export function CategoryTiles({ categories, onPick }: { categories: Array<{ id: string; name: string; imageUrl?: string | null }>; onPick: (id: string) => void }) {
  if (!categories.length) return null;
  return (
    <section className="m3-site-section" id="browse" aria-labelledby="browse-title">
      <SectionHeader title="Browse by category" />
      <div className="buyer-tiles">
        {categories.map((c) => (
          <button key={c.id} type="button" className="buyer-tile" data-image={c.imageUrl ? 'true' : undefined} onClick={() => onPick(c.id)}>
            {c.imageUrl ? <img src={c.imageUrl} alt="" loading="lazy" /> : null}
            <b>{c.name}</b>
          </button>
        ))}
      </div>
    </section>
  );
}
