import { zodResolver } from '@hookform/resolvers/zod';
import { useForm, type DefaultValues, type UseFormProps, type UseFormReturn } from 'react-hook-form';
import type { z } from 'zod';

export interface UseZodFormOptions<S extends z.ZodType<any, any>> extends Omit<UseFormProps<z.input<S>, unknown, z.output<S>>, 'resolver' | 'defaultValues'> {
  /** Initial values. Always provide them for every field so inputs are controlled from the first render. */
  defaultValues: DefaultValues<z.input<S>>;
}

export type ZodForm<S extends z.ZodType<any, any>> = UseFormReturn<z.input<S>, unknown, z.output<S>>;

/**
 * The only way to create a form. react-hook-form + a zod schema.
 * `mode: 'onTouched'` validates after the first blur, then on every change (`reValidateMode: 'onChange'`).
 * Focus on the first invalid field is handled by `<Form>` (works for custom widgets too), so RHF's own
 * `shouldFocusError` is off.
 */
export function useZodForm<S extends z.ZodType<any, any>>(schema: S, options: UseZodFormOptions<S>): ZodForm<S> {
  return useForm<z.input<S>, unknown, z.output<S>>({
    mode: 'onTouched',
    reValidateMode: 'onChange',
    shouldFocusError: false,
    ...options,
    resolver: zodResolver(schema as never) as never,
  });
}
