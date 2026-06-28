'use client';

import * as React from 'react';
import { Box, Flex, Popover, TextField } from '@radix-ui/themes';

/**
 * SearchableSelect
 *
 * A themeable, searchable single-select. Implemented as a Radix Themes
 * {@code Popover} + filtered list (a combobox), NOT a Radix {@code Select}:
 * Radix's {@code Select} doesn't support an interactive input inside its content
 * (the search box's focus management breaks item selection), so the dropdown
 * would filter but fail to select. The Popover approach keeps the native Radix
 * look/theme while actually working, and adds keyboard navigation.
 *
 * <p>The panel shows at most {@code maxVisibleItems} (default 5) before it
 * scrolls. By default the trigger shows the selected option (icon + label); pass
 * {@code renderTrigger} for custom trigger content (e.g. a flag-only phone
 * country picker). Requires a Radix Themes {@code <Theme>} context.</p>
 */

export interface SearchableSelectOption {
  value: string;
  label: string;
  /** Optional leading icon (e.g. a flag) shown in the list and the trigger. */
  icon?: React.ReactNode;
  /** Extra text matched during search, in addition to the label. */
  keywords?: string;
}

type SelectSize = '1' | '2' | '3';

export interface SearchableSelectProps {
  value?: string;
  onValueChange: (value: string) => void;
  options: SearchableSelectOption[];
  placeholder?: string;
  searchPlaceholder?: string;
  /** Max options visible before the list scrolls. Default 5. */
  maxVisibleItems?: number;
  size?: SelectSize;
  disabled?: boolean;
  id?: string;
  name?: string;
  'aria-label'?: string;
  'aria-invalid'?: boolean;
  triggerClassName?: string;
  triggerStyle?: React.CSSProperties;
  /** Custom trigger content; receives the currently selected option (or undefined). */
  renderTrigger?: (selected: SearchableSelectOption | undefined) => React.ReactNode;
  emptyMessage?: string;
}

const ITEM_HEIGHT: Record<SelectSize, number> = { '1': 28, '2': 34, '3': 40 };
const TRIGGER_HEIGHT: Record<SelectSize, string> = {
  '1': 'var(--space-5)',
  '2': 'var(--space-6)',
  '3': 'var(--space-7)',
};
const TRIGGER_FONT: Record<SelectSize, string> = {
  '1': 'var(--font-size-1)',
  '2': 'var(--font-size-2)',
  '3': 'var(--font-size-3)',
};

function ChevronDownIcon() {
  return (
    <svg width="9" height="9" viewBox="0 0 9 9" fill="none" aria-hidden="true" style={{ flexShrink: 0, opacity: 0.7 }}>
      <path d="M1 2.5L4.5 6L8 2.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function CheckIcon({ visible }: { visible: boolean }) {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 14 14"
      fill="none"
      aria-hidden="true"
      style={{ flexShrink: 0, visibility: visible ? 'visible' : 'hidden' }}
    >
      <path d="M2.5 7.5L5.5 10.5L11.5 3.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function SearchableSelect({
  value,
  onValueChange,
  options,
  placeholder = 'Select…',
  searchPlaceholder = 'Search…',
  maxVisibleItems = 5,
  size = '2',
  disabled,
  id,
  name,
  'aria-label': ariaLabel,
  'aria-invalid': ariaInvalid,
  triggerClassName,
  triggerStyle,
  renderTrigger,
  emptyMessage = 'No results found',
}: SearchableSelectProps) {
  const [open, setOpen] = React.useState(false);
  const [query, setQuery] = React.useState('');
  const [highlighted, setHighlighted] = React.useState(0);
  const searchRef = React.useRef<HTMLInputElement>(null);
  const itemRefs = React.useRef<Array<HTMLButtonElement | null>>([]);

  const filtered = React.useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return options;
    return options.filter(
      (o) =>
        o.label.toLowerCase().includes(q) ||
        (o.keywords ? o.keywords.toLowerCase().includes(q) : false)
    );
  }, [options, query]);

  const selected = React.useMemo(
    () => options.find((o) => o.value === value),
    [options, value]
  );

  // Reset the search query whenever the panel closes (focus is handled by
  // Popover.Content's onOpenAutoFocus).
  React.useEffect(() => {
    if (!open) setQuery('');
  }, [open]);

  // Keep the highlighted row in range and scrolled into view.
  React.useEffect(() => {
    setHighlighted((h) => Math.min(h, Math.max(0, filtered.length - 1)));
  }, [filtered.length]);

  React.useEffect(() => {
    if (open) itemRefs.current[highlighted]?.scrollIntoView({ block: 'nearest' });
  }, [highlighted, open]);

  const select = (next: string) => {
    onValueChange(next);
    setOpen(false);
  };

  const onSearchKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlighted((h) => Math.min(h + 1, filtered.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlighted((h) => Math.max(h - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const option = filtered[highlighted];
      if (option) select(option.value);
    }
  };

  const triggerContent = renderTrigger
    ? renderTrigger(selected)
    : selected
      ? (
        <Flex align="center" gap="2" style={{ minWidth: 0 }}>
          {selected.icon}
          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {selected.label}
          </span>
        </Flex>
      )
      : <span style={{ color: 'var(--gray-a9)' }}>{placeholder}</span>;

  return (
    <Popover.Root open={open} onOpenChange={disabled ? undefined : setOpen}>
      <Popover.Trigger>
        <button
          type="button"
          id={id}
          name={name}
          role="combobox"
          aria-expanded={open}
          aria-label={ariaLabel}
          aria-invalid={ariaInvalid}
          disabled={disabled}
          className={triggerClassName}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 'var(--space-2)',
            height: TRIGGER_HEIGHT[size],
            padding: '0 var(--space-2)',
            fontSize: TRIGGER_FONT[size],
            color: 'var(--gray-12)',
            background: 'var(--color-surface)',
            border: `1px solid ${ariaInvalid ? 'var(--red-8)' : 'var(--gray-a7)'}`,
            borderRadius: 'var(--radius-2)',
            cursor: disabled ? 'default' : 'pointer',
            opacity: disabled ? 0.6 : 1,
            ...triggerStyle,
          }}
        >
          {triggerContent}
          <ChevronDownIcon />
        </button>
      </Popover.Trigger>

      <Popover.Content
        size="1"
        // Match the trigger width for full-width selects, but stay wide enough
        // for the compact (flag-only) phone trigger.
        style={{ padding: 0, minWidth: 'max(220px, var(--radix-popover-trigger-width))' }}
        onOpenAutoFocus={(e) => {
          // Focus the search field instead of the first item.
          e.preventDefault();
          searchRef.current?.focus();
        }}
      >
        <Box p="2" style={{ borderBottom: '1px solid var(--gray-a5)' }}>
          <TextField.Root
            ref={searchRef}
            size={size === '3' ? '2' : size}
            placeholder={searchPlaceholder}
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setHighlighted(0);
            }}
            onKeyDown={onSearchKeyDown}
          />
        </Box>

        <Box
          role="listbox"
          p="1"
          style={{ maxHeight: maxVisibleItems * ITEM_HEIGHT[size], overflowY: 'auto' }}
        >
          {filtered.map((option, index) => {
            const isSelected = option.value === value;
            const isHighlighted = index === highlighted;
            return (
              <button
                key={option.value}
                ref={(el) => {
                  itemRefs.current[index] = el;
                }}
                type="button"
                role="option"
                aria-selected={isSelected}
                onClick={() => select(option.value)}
                onMouseEnter={() => setHighlighted(index)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 'var(--space-2)',
                  width: '100%',
                  textAlign: 'left',
                  padding: '0 var(--space-2)',
                  height: ITEM_HEIGHT[size] - 4,
                  fontSize: TRIGGER_FONT[size],
                  color: 'var(--gray-12)',
                  background: isHighlighted ? 'var(--accent-a3)' : 'transparent',
                  border: 'none',
                  borderRadius: 'var(--radius-2)',
                  cursor: 'pointer',
                }}
              >
                <CheckIcon visible={isSelected} />
                {option.icon}
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {option.label}
                </span>
              </button>
            );
          })}

          {filtered.length === 0 && (
            <Box px="2" py="3">
              <span style={{ color: 'var(--gray-9)', fontSize: 'var(--font-size-2)' }}>
                {emptyMessage}
              </span>
            </Box>
          )}
        </Box>
      </Popover.Content>
    </Popover.Root>
  );
}
