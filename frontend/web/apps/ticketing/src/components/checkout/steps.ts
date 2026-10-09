export const CHECKOUT_STEPS = [
  { id: 'reserve', label: 'Reserve' },
  { id: 'contact', label: 'Contact' },
  { id: 'pay', label: 'Pay' },
  { id: 'approval', label: 'Approval' },
  { id: 'confirmation', label: 'Confirmation' },
];

/** A mobile-money provider code, as the platform's `MOBILE_MONEY_OPERATOR` list names it. */
export type ProviderCode = string;
