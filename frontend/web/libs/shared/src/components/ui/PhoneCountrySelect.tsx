'use client';

import * as React from 'react';
import { SearchableSelect, type SearchableSelectOption } from './SearchableSelect';

/**
 * Searchable country selector for {@link PhoneNumberInput}.
 *
 * <p>A thin wrapper over the shared {@link SearchableSelect}: maps
 * react-phone-number-input's {@code countrySelectComponent} contract
 * ({@code value}/{@code onChange}/{@code options}) onto it, renders a compact
 * flag-only trigger, and uses emoji flags for each option. The "International"
 * option (value=undefined) is mapped to a sentinel since Radix Select item
 * values must be non-empty.</p>
 */

const INTERNATIONAL = '__INTL__';

/** ISO-3166 alpha-2 → emoji flag (regional indicator symbols); 🌐 for International. */
function flagEmoji(countryCode?: string): string {
  if (!countryCode) return '🌐';
  return countryCode
    .toUpperCase()
    .replace(/./g, (char) => String.fromCodePoint(127397 + char.charCodeAt(0)));
}

interface CountryOption {
  value?: string;
  label: string;
}

export interface PhoneCountrySelectProps {
  value?: string;
  onChange: (value?: string) => void;
  options: CountryOption[];
  disabled?: boolean;
  readOnly?: boolean;
  name?: string;
  'aria-label'?: string;
  onFocus?: () => void;
  onBlur?: () => void;
}

export function PhoneCountrySelect({
  value,
  onChange,
  options,
  disabled,
  readOnly,
  name,
  'aria-label': ariaLabel,
}: PhoneCountrySelectProps) {
  const selectOptions: SearchableSelectOption[] = React.useMemo(
    () =>
      options.map((option) => ({
        value: option.value ?? INTERNATIONAL,
        label: option.label,
        icon: (
          <span style={{ fontSize: 16, lineHeight: 1 }} aria-hidden="true">
            {flagEmoji(option.value)}
          </span>
        ),
      })),
    [options]
  );

  return (
    <SearchableSelect
      size="2"
      value={value ?? INTERNATIONAL}
      onValueChange={(next) => onChange(next === INTERNATIONAL ? undefined : next)}
      options={selectOptions}
      disabled={disabled || readOnly}
      name={name}
      aria-label={ariaLabel || 'Country'}
      searchPlaceholder="Search country…"
      emptyMessage="No countries found"
      maxVisibleItems={5}
      triggerClassName="pml-phone-country-trigger"
      renderTrigger={() => (
        <span style={{ fontSize: 18, lineHeight: 1 }} aria-hidden="true">
          {flagEmoji(value)}
        </span>
      )}
    />
  );
}
