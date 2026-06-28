'use client';

/**
 * PhoneNumberInput
 *
 * Shared country-selector phone input (flag + dial code + national number).
 * Built on `react-phone-number-input` (which embeds Google libphonenumber-js),
 * so it formats/validates per the selected country and ALWAYS emits a canonical
 * E.164 string (e.g. "+260971234567") — matching the backend `PhoneNumber`
 * GraphQL scalar and the `users` collection's E.164 validator.
 *
 * Designed to drop into a react-hook-form `Controller`:
 *
 * ```tsx
 * <Controller
 *   name="phoneNumber"
 *   control={control}
 *   render={({ field, fieldState }) => (
 *     <PhoneNumberInput
 *       {...field}
 *       aria-invalid={!!fieldState.error}
 *       onCountryChange={(c) => setValue('phoneCountry', c ?? '')}
 *     />
 *   )}
 * />
 * ```
 */
import * as React from 'react';
import PhoneInput, { type Country } from 'react-phone-number-input';
import { isValidPhoneNumber, parsePhoneNumber } from 'libphonenumber-js';
import 'react-phone-number-input/style.css';
import { PhoneCountrySelect } from './PhoneCountrySelect';

export interface PhoneNumberInputProps {
  /** Current E.164 value (or empty/undefined). */
  value?: string;
  /** Called with the E.164 string, or undefined when cleared. */
  onChange: (value: string | undefined) => void;
  onBlur?: () => void;
  /** Fires when the selected country changes; useful to persist phoneCountry. */
  onCountryChange?: (country: Country | undefined) => void;
  /** Default selected country (ISO-3166 alpha-2). Defaults to Zambia. */
  defaultCountry?: Country;
  id?: string;
  name?: string;
  placeholder?: string;
  disabled?: boolean;
  autoComplete?: string;
  'aria-invalid'?: boolean;
  className?: string;
}

/**
 * Whether a value is a valid phone number (for some country).
 * Use in zod refinements / form validation.
 */
export function isValidPhone(value: string | undefined | null): boolean {
  if (!value) return false;
  try {
    return isValidPhoneNumber(value);
  } catch {
    return false;
  }
}

/**
 * Resolve the ISO-3166 alpha-2 country of an E.164 number (e.g. "ZM"),
 * or undefined if it can't be determined. Handy on submit to persist
 * `phoneCountry` alongside the E.164 value.
 */
export function countryForPhone(
  value: string | undefined | null
): string | undefined {
  if (!value) return undefined;
  try {
    return parsePhoneNumber(value)?.country;
  } catch {
    return undefined;
  }
}

export const PhoneNumberInput = React.forwardRef<
  HTMLInputElement,
  PhoneNumberInputProps
>(function PhoneNumberInput(
  {
    value,
    onChange,
    onBlur,
    onCountryChange,
    defaultCountry = 'ZM',
    id,
    name,
    placeholder = 'Enter phone number',
    disabled,
    autoComplete = 'tel',
    className,
    'aria-invalid': ariaInvalid,
  },
  ref
) {
  return (
    <PhoneInput
      // react-phone-number-input emits E.164 (or undefined) directly.
      //
      // `international` keeps the input in "+<code> <number>" format with an
      // EDITABLE calling code, so the country can be set EITHER way:
      //   1. pick a country  → the "+<code>" prefix is filled in, ready to complete
      //   2. type "+<code>…" → the country (and flag) is auto-detected
      // `addInternationalOption={false}` removes the "International" (🌐) entry so
      // there is always a real country selected — it falls back to `defaultCountry`
      // instead of starting blank, and a preloaded E.164 value resolves its country.
      international
      addInternationalOption={false}
      // Searchable country picker built from native Radix Themes Select.
      countrySelectComponent={PhoneCountrySelect}
      defaultCountry={defaultCountry}
      value={value || undefined}
      onChange={onChange}
      onBlur={onBlur}
      onCountryChange={onCountryChange}
      id={id}
      name={name}
      placeholder={placeholder}
      disabled={disabled}
      autoComplete={autoComplete}
      aria-invalid={ariaInvalid}
      numberInputProps={{ ref, 'aria-invalid': ariaInvalid }}
      className={['pml-phone-input', className].filter(Boolean).join(' ')}
    />
  );
});

export default PhoneNumberInput;
