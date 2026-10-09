'use client';

/**
 * Ctrl/Cmd+K command palette. Pages are limited to what the signed-in role may
 * open. Entity search (users, events) only runs where a real search hook exists
 * and only for modules the role may open; there is no search operation for the
 * other prototype groups (payouts, bookings, escrow ...), so they are not listed.
 */
import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { useRouter } from 'next/navigation';
import { Dialog, TextField } from '@pml.tickets/shared/components/m3';
import { useAdminEvents, useAdminUsers } from '@pml.tickets/shared';
import { MODULES, canOpenModule, tabsFor, type StaffRole } from '@/config/navigation';
import { humanize } from '@/lib/format';

export interface PaletteItem {
  id: string;
  group: string;
  title: string;
  subtitle: string;
  href: string;
}

const MIN_QUERY = 2;
const PER_GROUP_QUERY = 6;
const PER_GROUP_EMPTY = 12;

export function pageItems(roles: readonly StaffRole[]): PaletteItem[] {
  const items: PaletteItem[] = MODULES.filter((m) => canOpenModule(roles, m.id)).map((m) => {
    const first = tabsFor(roles, m.id)[0];
    return { id: `page-${m.id}`, group: 'Pages', title: m.label, subtitle: 'Go to page', href: first ? `${m.path}/${first.id}` : m.path };
  });
  if (canOpenModule(roles, 'config')) {
    items.push({ id: 'page-roles', group: 'Pages', title: 'Roles and access', subtitle: 'Platform configuration', href: '/config/roles' });
  }
  return items;
}

export function filterItems(all: PaletteItem[], q: string): PaletteItem[] {
  const toks = q.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const hit = toks.length ? all.filter((i) => toks.every((t) => `${i.title} ${i.subtitle} ${i.group}`.toLowerCase().includes(t))) : all.filter((i) => i.group === 'Pages');
  const seen: Record<string, number> = {};
  return hit.filter((i) => {
    seen[i.group] = (seen[i.group] ?? 0) + 1;
    return seen[i.group] <= (toks.length ? PER_GROUP_QUERY : PER_GROUP_EMPTY);
  });
}

function UserResults({ q, onItems }: { q: string; onItems: (items: PaletteItem[]) => void }) {
  const { users } = useAdminUsers({ search: q, size: PER_GROUP_QUERY });
  useEffect(() => {
    onItems(users.map((u) => ({ id: `user-${u.id}`, group: 'Users', title: u.fullName ?? u.email ?? u.id, subtitle: u.email ?? '', href: `/user/${u.id}` })));
  }, [users, onItems]);
  return null;
}

function EventResults({ q, onItems }: { q: string; onItems: (items: PaletteItem[]) => void }) {
  const { events } = useAdminEvents({ searchQuery: q, size: PER_GROUP_QUERY });
  useEffect(() => {
    onItems(events.map((e) => ({ id: `event-${e.id}`, group: 'Events', title: e.title, subtitle: `${e.cityName ?? ''} · ${humanize(e.status)}`.replace(/^ · /, ''), href: `/event/${e.id}` })));
  }, [events, onItems]);
  return null;
}

export function CommandPalette({ open, onClose, roles }: { open: boolean; onClose: () => void; roles: readonly StaffRole[] }) {
  const router = useRouter();
  const listId = useId();
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  const [sel, setSel] = useState(0);
  const [users, setUsers] = useState<PaletteItem[]>([]);
  const [events, setEvents] = useState<PaletteItem[]>([]);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const t = setTimeout(() => setDebounced(q.trim()), 250);
    return () => clearTimeout(t);
  }, [q]);

  useEffect(() => {
    if (open) {
      setQ('');
      setDebounced('');
      setSel(0);
      setTimeout(() => input.current?.focus(), 0);
    }
  }, [open]);

  const all = useMemo(() => [...pageItems(roles), ...(debounced.length >= MIN_QUERY ? [...users, ...events] : [])], [roles, debounced, users, events]);
  const items = useMemo(() => filterItems(all, q), [all, q]);
  const idx = Math.min(sel, Math.max(0, items.length - 1));

  const go = (i: PaletteItem | undefined) => {
    if (!i) return;
    onClose();
    router.push(i.href);
  };

  const onKey = (e: KeyboardEvent) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      setSel(Math.max(0, Math.min(items.length - 1, idx + (e.key === 'ArrowDown' ? 1 : -1))));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      go(items[idx]);
    }
  };

  const search = debounced.length >= MIN_QUERY;
  let lastGroup = '';
  return (
    <Dialog open={open} onClose={onClose} title="Search everything">
      <div className="adm-cmdk" onKeyDown={onKey}>
        <TextField
          ref={input}
          label="Search everything"
          type="text"
          role="combobox"
          aria-expanded="true"
          aria-controls={listId}
          aria-activedescendant={items[idx] ? `${listId}-${items[idx].id}` : undefined}
          placeholder="Search users, organizations, events, bookings, payouts…"
          autoComplete="off"
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setSel(0);
          }}
        />
        {search && canOpenModule(roles, 'users') ? <UserResults q={debounced} onItems={setUsers} /> : null}
        {search && canOpenModule(roles, 'events') ? <EventResults q={debounced} onItems={setEvents} /> : null}
        <div id={listId} role="listbox" aria-label="Results" className="adm-cmdk__list">
          {items.length === 0 ? (
            <p className="adm-note">No matches. Try a page name, a person or an event.</p>
          ) : (
            items.map((i, ix) => {
              const head = i.group !== lastGroup;
              lastGroup = i.group;
              return (
                <div key={i.id}>
                  {head ? <div className="adm-cmdk__grp" role="presentation">{i.group}</div> : null}
                  <button
                    type="button"
                    id={`${listId}-${i.id}`}
                    role="option"
                    aria-selected={ix === idx}
                    className="adm-cmdk__it"
                    onClick={() => go(i)}
                    onMouseMove={() => setSel(ix)}
                  >
                    <span>
                      <b>{i.title}</b>
                      <small>{i.subtitle}</small>
                    </span>
                    <em>{i.group}</em>
                  </button>
                </div>
              );
            })
          )}
        </div>
        <p className="adm-note">Use the arrow keys to move, Enter to open, Escape to close.</p>
      </div>
    </Dialog>
  );
}
