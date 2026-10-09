import { useEffect } from 'react';
import { useFormContext } from 'react-hook-form';

/** Fills a field with the platform's first listed value once its list has loaded, if the field is still empty. */
export function useDefaultChoice(name: string, first: string | undefined, enabled = true) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const { getValues, setValue } = useFormContext<any>();
  useEffect(() => {
    if (enabled && first && !getValues(name)) setValue(name, first, { shouldDirty: false });
  }, [enabled, first, name, getValues, setValue]);
}
