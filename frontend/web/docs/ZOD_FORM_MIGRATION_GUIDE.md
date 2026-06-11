# Zod Form Migration Guide

**Complete guide for migrating forms from manual validation to Zod-based type-safe validation.**

## Overview

This project uses **Zod** for runtime validation with full TypeScript type inference. Forms should use the `useZodForm` hook from `@pml.tickets/shared` for type-safe, validated form handling.

### Key Principles

1. **Type Inference**: All form types are inferred from Zod schemas using `z.infer<typeof schema>`
2. **No Manual Types**: Never create custom TypeScript interfaces for form data
3. **Schema First**: Define validation rules in Zod schemas, then use throughout the app
4. **Single Source of Truth**: Validation logic lives in `libs/shared/src/validation/schemas.ts`

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     ZOD VALIDATION ARCHITECTURE                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. SCHEMA DEFINITION (libs/shared/src/validation/schemas.ts)   │
│     ↓                                                           │
│     export const businessInfoFormSchema = z.object({ ... })     │
│     export type BusinessInfoFormSchema = z.infer<typeof ...>    │
│                                                                 │
│  2. FORM UTILITIES (libs/shared/src/utils/form-utils.ts)        │
│     ↓                                                           │
│     export function useZodForm<T>(schema, initialData, onSubmit)│
│     export function formatZodErrors(error: ZodError)            │
│                                                                 │
│  3. FORM COMPONENT (apps/*/src/app/**/page.tsx)                 │
│     ↓                                                           │
│     const form = useZodForm(                                    │
│       businessInfoFormSchema,                                   │
│       initialData,                                              │
│       async (validatedData) => { /* submit */ }                 │
│     );                                                          │
│                                                                 │
│     <TextField                                                  │
│       value={form.formData.name}                                │
│       onChange={(e) => form.handleChange('name', e.target.value)}│
│       error={form.errors.name}                                  │
│     />                                                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Migration Steps

### Step 1: Import Zod Schema and Hook

```typescript
// BEFORE: Manual types and validation
import { useState } from 'react';

interface FormData {
  name: string;
  email: string;
  // ... manual type definitions
}

// AFTER: Zod-based type inference
import {
  businessInfoFormSchema,
  type BusinessInfoFormSchema,
  useZodForm
} from '@pml.tickets/shared';

// Type is automatically inferred from schema
type FormData = BusinessInfoFormSchema;
```

### Step 2: Replace State Management

```typescript
// BEFORE: Manual state and error handling
const [formData, setFormData] = useState<FormData>({
  name: '',
  email: '',
});
const [errors, setErrors] = useState<Record<string, string>>({});

const handleChange = (field: keyof FormData, value: string) => {
  setFormData((prev) => ({ ...prev, [field]: value }));
  // Clear error...
};

const validateForm = (): boolean => {
  const newErrors: Record<string, string> = {};

  if (!formData.name.trim()) {
    newErrors.name = 'Name is required';
  }
  if (!formData.email.match(/^[^\s@]+@[^\s@]+\.[^\s@]+$/)) {
    newErrors.email = 'Invalid email';
  }

  setErrors(newErrors);
  return Object.keys(newErrors).length === 0;
};

// AFTER: Zod hook with automatic validation
const form = useZodForm(
  businessInfoFormSchema,
  { name: '', email: '' },
  async (validatedData) => {
    // validatedData is fully validated and typed
    await submitToBackend(validatedData);
  }
);

// form.handleChange automatically validates and updates state
// form.errors contains Zod-generated error messages
```

### Step 3: Update Form Field Bindings

```typescript
// BEFORE: Manual bindings
<TextField
  value={formData.name}
  onChange={(e) => handleChange('name', e.target.value)}
  error={errors.name}
/>

// AFTER: Zod form bindings
<TextField
  value={form.formData.name}
  onChange={(e) => form.handleChange('name', e.target.value)}
  error={form.errors.name}
/>
```

### Step 4: Update Submit Handler

```typescript
// BEFORE: Manual validation in submit
const handleSubmit = async () => {
  if (!validateForm()) return;

  try {
    await submitToBackend(formData);
  } catch (error) {
    // ...
  }
};

<Button onClick={handleSubmit} disabled={submitting}>
  Submit
</Button>

// AFTER: Zod form handles validation automatically
<Button
  onClick={() => form.handleSubmit()}
  disabled={form.isSubmitting}
>
  {form.isSubmitting ? 'Saving...' : 'Submit'}
</Button>
```

---

## Complete Example: Business Info Form

### Schema Definition

```typescript
// libs/shared/src/validation/schemas.ts

import { z } from 'zod';

export const businessInfoFormSchema = z.object({
  name: z.string().min(1, 'Organization name is required').max(100),
  type: z.enum(['INDIVIDUAL', 'BUSINESS'], {
    required_error: 'Organization type is required',
  }),
  businessEmail: z.string().email('Please enter a valid email'),
  businessPhone: z
    .string()
    .regex(/^(\+?260|0)?[79]\d{8}$/, 'Invalid Zambian phone number'),
  city: z.string().min(1, 'City is required'),
  province: z.enum(['LUSAKA', 'COPPERBELT', /* ... */]),
  website: z.string().url().optional().or(z.literal('')),
  facebook: z.string().optional(),
  instagram: z.string().optional(),
  twitter: z.string().optional(),
});

export type BusinessInfoFormSchema = z.infer<typeof businessInfoFormSchema>;
```

### Form Component

```typescript
// apps/organization-admin/src/app/(application)/apply/business-info/page.tsx

'use client';

import { useCallback } from 'react';
import { useRouter } from 'next/navigation';
import {
  businessInfoFormSchema,
  type BusinessInfoFormSchema,
  useZodForm
} from '@pml.tickets/shared';

type FormData = BusinessInfoFormSchema;

export default function BusinessInfoPage() {
  const router = useRouter();

  // Build GraphQL input from validated data
  const buildInput = useCallback(
    (validatedData: FormData): OrganizationApplicationInput => {
      return {
        name: validatedData.name,
        type: validatedData.type,
        businessEmail: validatedData.businessEmail,
        businessPhone: validatedData.businessPhone,
        city: validatedData.city,
        province: validatedData.province,
        website: validatedData.website || undefined,
        socialLinks: {
          facebook: validatedData.facebook || undefined,
          instagram: validatedData.instagram || undefined,
          twitter: validatedData.twitter || undefined,
        },
      };
    },
    []
  );

  // Zod form with type inference
  const form = useZodForm(
    businessInfoFormSchema,
    {
      name: '',
      type: 'INDIVIDUAL',
      businessEmail: '',
      businessPhone: '',
      city: '',
      province: 'LUSAKA',
      website: '',
      facebook: '',
      instagram: '',
      twitter: '',
    },
    async (validatedData) => {
      // This receives fully validated, typed data
      const input = buildInput(validatedData);
      await saveApplication(input);
      router.push('/apply/review');
    }
  );

  return (
    <form onSubmit={form.handleSubmit}>
      {/* Organization Name */}
      <TextField
        label="Organization Name"
        value={form.formData.name}
        onChange={(e) => form.handleChange('name', e.target.value)}
        error={form.errors.name}
        required
      />

      {/* Email */}
      <TextField
        label="Email"
        type="email"
        value={form.formData.businessEmail}
        onChange={(e) => form.handleChange('businessEmail', e.target.value)}
        error={form.errors.businessEmail}
        required
      />

      {/* Phone */}
      <TextField
        label="Phone"
        type="tel"
        value={form.formData.businessPhone}
        onChange={(e) => form.handleChange('businessPhone', e.target.value)}
        error={form.errors.businessPhone}
        required
      />

      {/* Submit */}
      <Button
        type="submit"
        disabled={form.isSubmitting}
      >
        {form.isSubmitting ? 'Saving...' : 'Continue'}
      </Button>
    </form>
  );
}
```

---

## useZodForm API Reference

### Parameters

```typescript
useZodForm<T extends z.ZodTypeAny>(
  schema: T,                          // Zod validation schema
  initialData: z.infer<T>,            // Initial form data
  onSubmit: (data: z.infer<T>) => void | Promise<void>,  // Submit handler
  options?: {
    validateOnChange?: boolean;       // Validate as user types (default: false)
    validateOnBlur?: boolean;         // Validate on blur (default: false)
  }
)
```

### Return Value

```typescript
{
  // State
  formData: T;                        // Current form data (typed from schema)
  errors: Record<string, string>;     // Field-level errors
  isSubmitting: boolean;              // True during form submission
  isDirty: boolean;                   // True if form has been modified

  // Methods
  handleChange: <K extends keyof T>(field: K, value: T[K]) => void;
  setFormData: (data: T | ((prev: T) => T)) => void;
  handleSubmit: (e?: React.FormEvent) => Promise<void>;
  validateField: <K extends keyof T>(field: K) => boolean;
  validateAll: () => boolean;
  clearError: (field: keyof T) => void;
  clearErrors: () => void;
  reset: () => void;
  setErrors: (errors: Record<string, string>) => void;
}
```

---

## Advanced Patterns

### Conditional Validation (superRefine)

```typescript
// Schema with conditional validation
export const payoutConfigFormSchema = z
  .object({
    method: z.enum(['BANK_TRANSFER', 'MOBILE_MONEY']),
    bankName: z.string().optional(),
    accountNumber: z.string().optional(),
    mobileMoneyProvider: z.enum(['MTN', 'AIRTEL', 'ZAMTEL']).optional(),
    mobileMoneyNumber: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    // Require bank fields if method is BANK_TRANSFER
    if (data.method === 'BANK_TRANSFER') {
      if (!data.bankName) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'Bank name is required for bank transfer',
          path: ['bankName'],
        });
      }
      if (!data.accountNumber) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'Account number is required',
          path: ['accountNumber'],
        });
      }
    }

    // Require mobile money fields if method is MOBILE_MONEY
    if (data.method === 'MOBILE_MONEY') {
      if (!data.mobileMoneyProvider) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'Provider is required',
          path: ['mobileMoneyProvider'],
        });
      }
    }
  });
```

### Field-Level Validation

```typescript
// Validate a specific field on blur
<TextField
  value={form.formData.email}
  onChange={(e) => form.handleChange('email', e.target.value)}
  onBlur={() => form.validateField('email')}
  error={form.errors.email}
/>
```

### Manual Error Handling

```typescript
const form = useZodForm(schema, initialData, async (validatedData) => {
  try {
    await saveData(validatedData);
  } catch (error) {
    if (error.code === 'DUPLICATE_EMAIL') {
      form.setErrors({
        email: 'This email is already registered'
      });
    }
  }
});
```

### Transform and Normalize Data

```typescript
// Phone number schema with normalization
export const zambianPhoneSchema = z
  .string()
  .regex(/^(\+?260|0)?[79]\d{8}$/, 'Invalid phone number')
  .transform((val) => {
    // Normalize to E.164 format (+260...)
    if (val.startsWith('+260')) return val;
    if (val.startsWith('260')) return `+${val}`;
    if (val.startsWith('0')) return `+260${val.slice(1)}`;
    return `+260${val}`;
  });
```

---

## Error Message Customization

### Field-Level Messages

```typescript
z.string()
  .min(1, 'Name is required')
  .max(100, 'Name must be less than 100 characters');

z.string().email('Please enter a valid email address');

z.enum(['INDIVIDUAL', 'BUSINESS'], {
  required_error: 'Organization type is required',
  invalid_type_error: 'Invalid organization type',
});
```

### Custom Error Messages

```typescript
z.string().refine(
  (val) => val.toLowerCase() !== 'admin',
  {
    message: 'The name "admin" is reserved',
  }
);
```

---

## Testing Forms with Zod

### Unit Test Example

```typescript
import { businessInfoFormSchema } from '@pml.tickets/shared';

describe('businessInfoFormSchema', () => {
  it('should validate correct data', () => {
    const validData = {
      name: 'My Organization',
      type: 'INDIVIDUAL',
      businessEmail: 'contact@example.com',
      businessPhone: '+260971234567',
      city: 'Lusaka',
      province: 'LUSAKA',
      website: 'https://example.com',
    };

    const result = businessInfoFormSchema.safeParse(validData);
    expect(result.success).toBe(true);
  });

  it('should reject invalid email', () => {
    const invalidData = {
      // ... valid fields
      businessEmail: 'not-an-email',
    };

    const result = businessInfoFormSchema.safeParse(invalidData);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.errors[0].path).toEqual(['businessEmail']);
      expect(result.error.errors[0].message).toContain('valid email');
    }
  });
});
```

### Component Test Example

```typescript
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import BusinessInfoPage from './page';

describe('BusinessInfoPage', () => {
  it('should show validation errors', async () => {
    render(<BusinessInfoPage />);

    const submitButton = screen.getByText('Continue');
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('Organization name is required')).toBeInTheDocument();
      expect(screen.getByText('Email is required')).toBeInTheDocument();
    });
  });

  it('should submit valid form', async () => {
    const mockSubmit = jest.fn();
    render(<BusinessInfoPage onSubmit={mockSubmit} />);

    fireEvent.change(screen.getByLabelText('Organization Name'), {
      target: { value: 'Test Org' },
    });
    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'test@example.com' },
    });

    fireEvent.click(screen.getByText('Continue'));

    await waitFor(() => {
      expect(mockSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Test Org',
          businessEmail: 'test@example.com',
        })
      );
    });
  });
});
```

---

## Common Pitfalls

### ❌ Don't: Create Custom Types for Forms

```typescript
// WRONG - Manual type definition
interface BusinessInfoFormData {
  name: string;
  email: string;
}

const form = useZodForm(businessInfoFormSchema, initialData, onSubmit);
```

### ✅ Do: Infer Types from Zod Schemas

```typescript
// CORRECT - Type inference
type BusinessInfoFormData = z.infer<typeof businessInfoFormSchema>;

const form = useZodForm(businessInfoFormSchema, initialData, onSubmit);
```

### ❌ Don't: Use Manual Validation Logic

```typescript
// WRONG - Manual validation
const validateEmail = (email: string) => {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
};

if (!validateEmail(formData.email)) {
  setErrors({ email: 'Invalid email' });
}
```

### ✅ Do: Use Zod Schema Validation

```typescript
// CORRECT - Zod handles validation
const form = useZodForm(
  businessInfoFormSchema,  // Contains email validation
  initialData,
  onSubmit
);

// Validation happens automatically on submit
```

### ❌ Don't: Forget to Handle Optional Fields

```typescript
// WRONG - May cause runtime errors
<TextField
  value={form.formData.website}  // Could be undefined
  onChange={(e) => form.handleChange('website', e.target.value)}
/>
```

### ✅ Do: Provide Default for Optional Fields

```typescript
// CORRECT - Safe handling of optional fields
<TextField
  value={form.formData.website || ''}
  onChange={(e) => form.handleChange('website', e.target.value)}
/>
```

---

## Migration Checklist

Use this checklist when migrating a form:

- [ ] Import Zod schema and `useZodForm` hook
- [ ] Replace manual `FormData` interface with `z.infer<typeof schema>`
- [ ] Remove manual `useState` for form data and errors
- [ ] Replace with `useZodForm` hook
- [ ] Update all form field bindings to use `form.formData` and `form.handleChange`
- [ ] Update error displays to use `form.errors`
- [ ] Replace submit handler with `form.handleSubmit`
- [ ] Update loading/disabled states to use `form.isSubmitting`
- [ ] Remove manual validation functions
- [ ] Test form with valid and invalid data
- [ ] Verify error messages display correctly
- [ ] Check TypeScript compilation (no type errors)

---

## Additional Resources

- **Zod Documentation**: https://zod.dev
- **Schema Definitions**: `/libs/shared/src/validation/schemas.ts`
- **Form Utilities**: `/libs/shared/src/utils/form-utils.ts`
- **Example Form**: `/apps/organization-admin/src/app/(application)/apply/business-info/page.tsx`

---

## Support

For questions or issues with Zod form migration, please:

1. Review this guide and the example forms
2. Check the Zod schema definitions in `libs/shared/src/validation/schemas.ts`
3. Consult the `useZodForm` implementation in `libs/shared/src/utils/form-utils.ts`
4. Refer to the Zod documentation for advanced validation patterns
