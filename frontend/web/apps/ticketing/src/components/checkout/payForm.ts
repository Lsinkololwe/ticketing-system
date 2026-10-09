import { z } from 'zod';
import { referenceCode } from '@pml.tickets/shared/api/graphql/shared/reference';
import { validateForOperator, type MobileOperator } from '@/hooks/useMobileOperators';

/** Local form of a typed number: digits only, leading 0 added when the buyer typed 96 123 4567. */
export function toLocalNumber(raw: string): string {
  const digits = raw.replace(/\D/g, '').replace(/^260/, '');
  return digits.startsWith('0') ? digits : `0${digits}`;
}

/**
 * The pay form for the operators the platform lists. The provider must be one of them and the number must
 * belong to its range; until the list loads the provider is not judged here (the pay button is disabled).
 */
export function makePayFormSchema(operators: readonly MobileOperator[]) {
  return z
    .object({
      provider: referenceCode(operators.map((o) => o.code), 'Choose a mobile money provider', 'Choose a mobile money provider'),
      number: z.string(),
    })
    .superRefine((v, ctx) => {
      if (operators.length > 0 && !validateForOperator(operators, v.provider, toLocalNumber(v.number))) {
        const label = operators.find((o) => o.code === v.provider)?.label;
        ctx.addIssue({ code: 'custom', path: ['number'], message: `Enter a valid ${label} number, for example 96 123 4567.` });
      }
    });
}
export type PayFormValues = { provider: string; number: string };
