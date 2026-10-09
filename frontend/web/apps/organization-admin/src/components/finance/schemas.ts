import { z } from 'zod';
import { moneyMinor, nonEmptyTrimmed, phoneE164 } from '@pml.tickets/shared';
import { referenceCode } from '@pml.tickets/shared/api/graphql/shared/reference';

/** Bank account add/edit. The account number is fixed after creation, so edits do not validate it. */
/** `currencies` are the CURRENCY codes the platform lists; empty while that list is unavailable (the server then decides). */
export function bankAccountSchema(isEdit: boolean, currencies: readonly string[] = []) {
  return z.object({
    holder: nonEmptyTrimmed().min(2, 'Enter the account holder name'),
    bankName: z.string().min(1, 'Choose a bank'),
    branchCode: z.string().trim(),
    number: z
      .string()
      .transform((v) => v.replace(/\s/g, ''))
      .refine((v) => isEdit || /^\d{10,16}$/.test(v), 'Account numbers have 10 to 16 digits'),
    swift: z
      .string()
      .trim()
      .transform((v) => v.toUpperCase())
      .refine((v) => v === '' || v.length === 8 || v.length === 11, 'SWIFT codes have 8 or 11 characters'),
    currency: referenceCode(currencies, 'Choose one of the listed currencies', 'Choose a currency'),
  });
}
export type BankFormInput = z.input<ReturnType<typeof bankAccountSchema>>;
export type BankFormValues = z.output<ReturnType<typeof bankAccountSchema>>;

/** Mobile wallet: network and a Zambian mobile number (the server normalises it to E.164). */
/** `networks` are the MOBILE_MONEY_OPERATOR codes the platform lists. */
export const walletSchemaFor = (networks: readonly string[] = []) =>
  z.object({
    holder: nonEmptyTrimmed().min(2, 'Enter the wallet holder name'),
    network: referenceCode(networks, 'Choose one of the listed networks', 'Choose a network'),
    phone: phoneE164({ requiredMessage: 'Enter the wallet mobile number', invalidMessage: 'Enter a valid Zambian mobile number' }),
  });
export const walletSchema = walletSchemaFor();
export type WalletFormValues = z.output<typeof walletSchema>;

/** Test-deposit confirmation: the exact amount (ngwee) sent to the account. */
export const bankVerifySchema = z.object({
  amount: moneyMinor(),
});
export type BankVerifyValues = z.output<typeof bankVerifySchema>;

/** Payout request. Limits come from eligibility (platform minimum, available balance), all in ngwee. */
export function payoutRequestSchema(limits: { eligible: boolean; minMinor: number; maxMinor: number }) {
  return z
    .object({
      escrowId: z.string().min(1, 'Choose an event'),
      bankId: z.string().min(1, 'Add and verify a bank account first'),
      amount: limits.eligible
        ? moneyMinor({
            minMinor: limits.minMinor,
            maxMinor: Math.max(limits.maxMinor, limits.minMinor),
            belowMinMessage: (min) => `The minimum payout is ${min}`,
          })
        : moneyMinor(),
    })
    .superRefine((_v, ctx) => {
      if (!limits.eligible) ctx.addIssue({ code: 'custom', path: ['amount'], message: 'This event is not eligible for a payout yet' });
    });
}
export type PayoutRequestValues = z.output<ReturnType<typeof payoutRequestSchema>>;
