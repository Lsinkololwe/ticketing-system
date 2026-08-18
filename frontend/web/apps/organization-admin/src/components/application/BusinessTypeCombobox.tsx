'use client';

/**
 * Business Type Combobox
 *
 * The first decision in the wizard, and the one that shapes everything after it:
 * the selected business type determines which verification documents step 2 will
 * ask for (spec ET-ORG-001 §4).
 *
 * ## Why a combobox and not a select
 *
 * The design authority (`Org Admin - Onboarding Wizard.dc.html`) specifies a
 * searchable combobox whose options each carry a "N documents required" hint.
 * That hint is the point: it tells the applicant the cost of each choice before
 * they commit, so choosing "Limited company" when they are a sole trader — and
 * then hitting a certificate-of-incorporation slot they can never fill — stops
 * being a trap.
 *
 * ## Accessibility
 *
 * Implements the WAI-ARIA combobox pattern: `role="combobox"` with
 * `aria-expanded`, `aria-controls` and `aria-activedescendant` on the input, a
 * `role="listbox"` popup, and `role="option"` items. Arrow keys move the active
 * option, Enter selects, Escape closes. The active option is tracked in state
 * rather than by DOM focus, so focus never leaves the input — which is what
 * keeps typing and navigating usable in the same gesture.
 *
 * @see Org Admin - Onboarding Wizard.dc.html
 */

import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { Box, Flex, Text } from '@radix-ui/themes';
import {
  Bank,
  Building,
  Check,
  Group,
  Heart,
  NavArrowDown,
  Search,
  User,
} from 'iconoir-react';
import {
  BUSINESS_TYPES,
  businessTypeSpec,
  type BusinessType,
} from '@/lib/onboarding/documents';

// =============================================================================
// TYPES
// =============================================================================

interface BusinessTypeComboboxProps {
  value: BusinessType | null;
  onChange: (value: BusinessType) => void;
  /** Rendered and announced when validation fails. */
  error?: string;
  disabled?: boolean;
  id?: string;
}

// =============================================================================
// COMPONENT
// =============================================================================

export function BusinessTypeCombobox({
  value,
  onChange,
  error,
  disabled = false,
  id,
}: BusinessTypeComboboxProps) {
  const generatedId = useId();
  const inputId = id ?? `business-type-${generatedId}`;
  const listboxId = `${inputId}-listbox`;
  const errorId = `${inputId}-error`;

  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);

  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const selected = businessTypeSpec(value);

  const matches = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return BUSINESS_TYPES;
    return BUSINESS_TYPES.filter((b) => b.label.toLowerCase().includes(q));
  }, [query]);

  // Keep the active index inside the (possibly shrunken) match list, otherwise
  // Enter selects nothing after the user narrows the query.
  const active = Math.min(activeIndex, Math.max(0, matches.length - 1));
  const activeOptionId = matches[active] ? `${inputId}-opt-${matches[active].type}` : undefined;

  const close = useCallback(() => {
    setOpen(false);
    setQuery('');
  }, []);

  const select = useCallback(
    (type: BusinessType) => {
      onChange(type);
      close();
      inputRef.current?.focus();
    },
    [onChange, close]
  );

  // Close on outside click. Without this the listbox stays open behind the rest
  // of the form and swallows the next click.
  useEffect(() => {
    if (!open) return undefined;
    const onPointerDown = (event: PointerEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) {
        close();
      }
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [open, close]);

  const onKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLInputElement>) => {
      switch (event.key) {
        case 'ArrowDown':
          event.preventDefault();
          if (!open) {
            setOpen(true);
            setActiveIndex(0);
          } else {
            setActiveIndex((i) => Math.min(i + 1, matches.length - 1));
          }
          break;
        case 'ArrowUp':
          event.preventDefault();
          setActiveIndex((i) => Math.max(i - 1, 0));
          break;
        case 'Enter': {
          if (!open) break;
          // Only swallow Enter when the popup is open, so Enter still submits
          // the form when it is closed.
          event.preventDefault();
          const pick = matches[active];
          if (pick) select(pick.type);
          break;
        }
        case 'Escape':
          event.preventDefault();
          close();
          break;
        case 'Home':
          if (open) {
            event.preventDefault();
            setActiveIndex(0);
          }
          break;
        case 'End':
          if (open) {
            event.preventDefault();
            setActiveIndex(matches.length - 1);
          }
          break;
        default:
          break;
      }
    },
    [open, matches, active, select, close]
  );

  return (
    <Box ref={containerRef} position="relative" data-testid="business-type-combobox">
      <Box position="relative">
        <Box
          position="absolute"
          left="3"
          top="50%"
          aria-hidden="true"
          style={{ transform: 'translateY(-50%)', pointerEvents: 'none', color: 'var(--gray-9)' }}
        >
          <Search width={14} height={14} />
        </Box>

        <input
          ref={inputRef}
          id={inputId}
          type="text"
          role="combobox"
          aria-expanded={open}
          aria-controls={listboxId}
          aria-autocomplete="list"
          aria-activedescendant={open ? activeOptionId : undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          aria-required="true"
          placeholder="Search business type…"
          disabled={disabled}
          // Show the query while typing, the selected label otherwise. This is
          // what makes the field read as "Limited company" at rest instead of
          // as an empty search box that has forgotten the choice.
          value={open ? query : (selected?.label ?? '')}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
            setActiveIndex(0);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          className="business-type-input"
          data-testid="business-type-input"
        />

        <Box
          position="absolute"
          right="3"
          top="50%"
          aria-hidden="true"
          style={{ transform: 'translateY(-50%)', pointerEvents: 'none', color: 'var(--gray-9)' }}
        >
          <NavArrowDown width={15} height={15} />
        </Box>
      </Box>

      {open && (
        <Box
          id={listboxId}
          role="listbox"
          aria-label="Business type"
          className="business-type-listbox"
          data-testid="business-type-listbox"
        >
          {matches.map((option, index) => {
            const isSelected = option.type === value;
            return (
              <Flex
                key={option.type}
                id={`${inputId}-opt-${option.type}`}
                role="option"
                aria-selected={isSelected}
                align="center"
                gap="3"
                px="3"
                py="2"
                className="business-type-option"
                data-active={index === active ? 'true' : undefined}
                data-selected={isSelected ? 'true' : undefined}
                data-testid={`business-type-option-${option.type}`}
                // pointerdown, not click: the outside-click handler also listens
                // on pointerdown, and click would fire after the popup closed.
                onPointerDown={(e) => {
                  e.preventDefault();
                  select(option.type);
                }}
                onMouseEnter={() => setActiveIndex(index)}
              >
                <Box flexShrink="0">
                  <Text size="2" color="gray">
                    <Flex align="center" justify="center" width="20px" height="20px" aria-hidden="true">
                      <BusinessTypeIcon name={option.icon} />
                    </Flex>
                  </Text>
                </Box>

                <Box flexGrow="1" minWidth="0">
                  <Text as="p" size="2" weight="medium" highContrast>
                    {option.label}
                  </Text>
                  <Text as="p" size="1" color="gray">
                    {option.required.length} document
                    {option.required.length === 1 ? '' : 's'} required
                  </Text>
                </Box>

                {isSelected && (
                  <Box flexShrink="0" style={{ color: 'var(--accent-11)' }} aria-hidden="true">
                    <Check width={15} height={15} />
                  </Box>
                )}
              </Flex>
            );
          })}

          {matches.length === 0 && (
            <Box px="3" py="3">
              <Text size="2" color="gray">
                No business type matches “{query}”
              </Text>
            </Box>
          )}
        </Box>
      )}

      {error && (
        <Text as="p" id={errorId} role="alert" size="1" mt="1" style={{ color: 'var(--red-11)' }}>
          {error}
        </Text>
      )}

      <style jsx global>{`
        .business-type-input {
          width: 100%;
          height: 36px;
          padding: 0 var(--space-6) 0 var(--space-6);
          font-family: var(--default-font-family);
          font-size: var(--font-size-2);
          color: var(--gray-12);
          background: var(--color-surface);
          border: 1px solid var(--gray-7);
          border-radius: var(--radius-2);
          outline: none;
          transition: border-color 150ms ease, box-shadow 150ms ease;
        }

        .business-type-input::placeholder {
          color: var(--gray-9);
        }

        .business-type-input:focus-visible {
          border-color: var(--accent-8);
          box-shadow: 0 0 0 2px var(--accent-a5);
        }

        .business-type-input[aria-invalid='true'] {
          border-color: var(--red-8);
        }

        .business-type-input:disabled {
          opacity: 0.6;
          cursor: not-allowed;
        }

        .business-type-listbox {
          position: absolute;
          top: calc(100% + var(--space-1));
          left: 0;
          right: 0;
          z-index: 30;
          background: var(--color-panel-solid);
          border: 1px solid var(--gray-a5);
          border-radius: var(--radius-3);
          box-shadow: var(--shadow-4);
          overflow-y: auto;
          max-height: 240px;
        }

        .business-type-option {
          cursor: pointer;
          border-bottom: 1px solid var(--gray-a3);
        }

        .business-type-option:last-child {
          border-bottom: none;
        }

        /* Active is keyboard/pointer position; selected is the committed value.
           They are shown differently so arrowing past the current choice does
           not look like it changed it. */
        .business-type-option[data-selected='true'] {
          background: var(--gray-3);
        }

        .business-type-option[data-active='true'] {
          background: var(--accent-3);
        }

        @media (prefers-reduced-motion: reduce) {
          .business-type-input {
            transition: none;
          }
        }
      `}</style>
    </Box>
  );
}

// =============================================================================
// ICON
// =============================================================================

/** Maps the design's iconoir names onto components. */
const ICONS = { user: User, group: Group, building: Building, heart: Heart, bank: Bank } as const;

function BusinessTypeIcon({ name }: { name: string }) {
  const Icon = ICONS[name as keyof typeof ICONS] ?? Building;
  return <Icon width={16} height={16} strokeWidth={1.5} />;
}
