'use client';

import { useMemo } from 'react';
import { useReferenceOptions } from '@pml.tickets/shared/api/graphql/shared/reference';

/** A mobile-money operator as the platform lists it (`MOBILE_MONEY_OPERATOR`), with its number ranges in local form. */
export interface MobileOperator {
  code: string;
  label: string;
  /** Local prefixes of the operator's numbers, e.g. ['096', '076'] (the platform stores them as '26096'). */
  prefixes: string[];
}

/** '26096' (national destination code with the country code) to the local '096'. */
export function toLocalPrefix(msisdnPrefix: string): string {
  return `0${msisdnPrefix.replace(/\D/g, '').replace(/^260/, '')}`;
}

/** "Numbers starting 096 / 076", derived from the operator's prefixes. */
export function prefixHint(operator: Pick<MobileOperator, 'prefixes'>): string {
  return operator.prefixes.length ? `Numbers starting ${operator.prefixes.join(' / ')}` : '';
}

export function normalizeDigits(phone: string): string {
  return phone.replace(/\D/g, '');
}

/** A 10-digit local number that starts with one of the operator's prefixes. */
export function validateForOperator(operators: readonly MobileOperator[], code: string, phone: string): boolean {
  const digits = normalizeDigits(phone);
  if (digits.length !== 10) return false;
  return operators.find((o) => o.code === code)?.prefixes.some((p) => digits.startsWith(p)) ?? false;
}

/** The operator whose prefix a number starts with, if recognisable. */
export function detectOperator(operators: readonly MobileOperator[], phone: string): string | null {
  const digits = normalizeDigits(phone);
  if (digits.length < 3) return null;
  const three = digits.slice(0, 3);
  return operators.find((o) => o.prefixes.includes(three))?.code ?? null;
}

/** The platform's mobile-money operators (public list). */
export function useMobileOperators(): {
  operators: MobileOperator[];
  loading: boolean;
  empty: boolean;
  ready: boolean;
  error: Error | undefined;
  labelOf: (code: string | null | undefined) => string;
} {
  const list = useReferenceOptions<{ msisdnPrefixes?: string[] }>('MOBILE_MONEY_OPERATOR');
  const operators = useMemo(
    () =>
      list.options.map((o) => ({
        code: o.value,
        label: o.label,
        prefixes: (o.metadata.msisdnPrefixes ?? []).map(toLocalPrefix),
      })),
    [list.options],
  );
  return { operators, loading: list.loading, empty: list.empty, ready: list.ready, error: list.error, labelOf: list.labelOf };
}
