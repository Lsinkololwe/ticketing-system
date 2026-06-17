/**
 * Form Components Index
 *
 * Reusable form components using React Hook Form + Zod.
 * Use react-hook-form directly with Radix UI primitives for form fields.
 *
 * @example
 * ```tsx
 * import { useForm, Controller } from 'react-hook-form';
 * import { zodResolver } from '@hookform/resolvers/zod';
 * import { Box, Text, TextField } from '@radix-ui/themes';
 *
 * const { control, handleSubmit, formState: { errors } } = useForm({
 *   resolver: zodResolver(schema),
 *   defaultValues: {...}
 * });
 *
 * <form onSubmit={handleSubmit(onSubmit)}>
 *   <Box>
 *     <Text as="label" size="2" weight="medium" mb="1" style={{ display: 'block' }}>
 *       Name <Text as="span" color="red">*</Text>
 *     </Text>
 *     <Controller
 *       name="name"
 *       control={control}
 *       render={({ field }) => <TextField.Root {...field} />}
 *     />
 *     {errors.name && (
 *       <Text size="1" color="red" mt="1">{errors.name.message}</Text>
 *     )}
 *   </Box>
 * </form>
 * ```
 */

// Validation display components
export {
  ValidationSummary,
  ValidationChecklist,
  ValidationItem,
} from './ValidationSummary';
export type {
  ValidationSummaryProps,
  ValidationChecklistProps,
  ValidationItemProps,
} from './ValidationSummary';
