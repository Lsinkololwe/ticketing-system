/**
 * useZodForm Hook
 *
 * Simple form hook with Zod validation
 * TODO: Implement proper form management (consider react-hook-form integration)
 */

import { useState, useCallback } from 'react';
import type { ZodSchema } from 'zod';

export function useZodForm<T>(
  schema: ZodSchema<T>,
  initialData: T,
  onSubmit: (data: T) => Promise<void>
) {
  const [data, setData] = useState<T>(initialData);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = useCallback(
    async (e?: React.FormEvent) => {
      e?.preventDefault();
      setIsSubmitting(true);
      setErrors({});

      try {
        const validatedData = schema.parse(data);
        await onSubmit(validatedData);
      } catch (error: any) {
        if (error.errors) {
          const fieldErrors: Record<string, string> = {};
          error.errors.forEach((err: any) => {
            const path = err.path.join('.');
            fieldErrors[path] = err.message;
          });
          setErrors(fieldErrors);
        }
      } finally {
        setIsSubmitting(false);
      }
    },
    [data, schema, onSubmit]
  );

  const updateField = useCallback((field: keyof T, value: any) => {
    setData((prev) => ({ ...prev, [field]: value }));
  }, []);

  const reset = useCallback(() => {
    setData(initialData);
    setErrors({});
  }, [initialData]);

  return {
    data,
    errors,
    isSubmitting,
    handleSubmit,
    updateField,
    setData,
    reset,
  };
}
