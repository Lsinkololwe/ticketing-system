// Mobile-money payment types for the ticketing app.
//
// The customer checkout is mobile-money-only (MTN / Airtel / Zamtel), matching
// the MyTicketZM Design System. Card/bank capture is intentionally NOT handled
// in the browser — raw PAN/CVV must never touch client JS (PCI-DSS). The backend
// `PaymentMethod` enum (MOBILE_MONEY) comes from the generated GraphQL types.

export enum ZambianMobileProvider {
  MTN = 'MTN',
  AIRTEL = 'AIRTEL',
  ZAMTEL = 'ZAMTEL',
}

export interface MobileProviderInfo {
  /** Full display name, e.g. "MTN MoMo". */
  name: string;
  /** Short chip label, e.g. "MTN". */
  shortName: string;
  /** Provider brand color — the `--momo-*` CSS token. */
  colorVar: string;
  /** Valid local number prefixes (10-digit form, e.g. 096…). */
  prefix: string[];
}
