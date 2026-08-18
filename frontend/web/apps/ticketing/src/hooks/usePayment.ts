'use client';

import { ZambianMobileProvider, MobileProviderInfo } from '@/types/payment';

/**
 * Mobile-money helpers (MTN / Airtel / Zamtel).
 *
 * Pure client-side utilities for the checkout: provider metadata, phone-number
 * validation and E.164 formatting, and prefix-based provider detection. The
 * actual payment is driven by the backend reservation pipeline
 * (`useReserveTickets` / `usePayReservation`) — this hook never touches
 * card data or logs payment details.
 */
export const MOBILE_PROVIDERS: Record<ZambianMobileProvider, MobileProviderInfo> = {
  [ZambianMobileProvider.MTN]: {
    name: 'MTN MoMo',
    shortName: 'MTN',
    colorVar: 'var(--momo-mtn)',
    prefix: ['096', '076'],
  },
  [ZambianMobileProvider.AIRTEL]: {
    name: 'Airtel Money',
    shortName: 'Airtel',
    colorVar: 'var(--momo-airtel)',
    prefix: ['097', '077'],
  },
  [ZambianMobileProvider.ZAMTEL]: {
    name: 'Zamtel Kwacha',
    shortName: 'Zamtel',
    colorVar: 'var(--momo-zamtel)',
    prefix: ['095', '075'],
  },
};

export const MOBILE_PROVIDER_LIST = Object.values(ZambianMobileProvider);

/** Strip non-digits from a phone number. */
export function normalizeDigits(phone: string): string {
  return phone.replace(/\D/g, '');
}

/** Validate a 10-digit local number against a provider's prefixes. */
export function validatePhoneNumber(phone: string, provider: ZambianMobileProvider): boolean {
  const digits = normalizeDigits(phone);
  if (digits.length !== 10) return false;
  return MOBILE_PROVIDERS[provider].prefix.some((p) => digits.startsWith(p));
}

/** Detect the provider from a phone number's prefix, if recognisable. */
export function detectProvider(phone: string): ZambianMobileProvider | null {
  const digits = normalizeDigits(phone);
  if (digits.length < 3) return null;
  const three = digits.slice(0, 3);
  for (const provider of MOBILE_PROVIDER_LIST) {
    if (MOBILE_PROVIDERS[provider].prefix.includes(three)) return provider;
  }
  return null;
}

/** Format a local number to display E.164, e.g. "+260 96 123 4567". */
export function formatPhoneDisplay(phone: string): string {
  const digits = normalizeDigits(phone);
  if (digits.length !== 10) return phone;
  const national = digits.slice(1); // drop leading 0
  return `+260 ${national.slice(0, 2)} ${national.slice(2, 5)} ${national.slice(5)}`;
}

/** Convert a local number to E.164 for the backend, e.g. "+260961234567". */
export function toE164(phone: string): string {
  const digits = normalizeDigits(phone);
  if (digits.startsWith('0')) return `+260${digits.slice(1)}`;
  if (digits.startsWith('260')) return `+${digits}`;
  return `+260${digits}`;
}
