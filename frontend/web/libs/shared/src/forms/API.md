# Forms kit cheat-sheet

Import everything from `@pml.tickets/shared` (client components only: files are `'use client'`).
Rule: forms are ONLY react-hook-form + zod. No `useState` forms, no manual validation.

## Minimal form
```tsx
'use client';
import { z } from 'zod';
import { useZodForm, Form, FormActions, TextFieldRHF, PhoneRHF, MoneyRHF, phoneE164, moneyMinor, nonEmptyTrimmed, useGraphQLMutationForm } from '@pml.tickets/shared';

const schema = z.object({            // schema lives next to the feature
  name: nonEmptyTrimmed(),
  phone: phoneE164(),                // output "+260971234567"
  fee: moneyMinor({ minMinor: 5000 }), // MoneyRHF stores ngwee
});

export function PayoutForm() {
  const form = useZodForm(schema, { defaultValues: { name: '', phone: '', fee: undefined } });
  const m = useGraphQLMutationForm({ mutation: REQUEST_PAYOUT, toVariables: (v) => ({ input: v }), refetchQueries: ['MyPayouts'] });
  return (
    <Form form={form} onSubmit={m.submit} notify={(t) => snackbar.show(t)}>
      <TextFieldRHF name="name" label="Account name" />
      <PhoneRHF name="phone" label="Mobile money number" />
      <MoneyRHF name="fee" label="Amount" />
      <FormActions submitLabel="Request payout" onCancel={close} />
    </Form>
  );
}
```
`Form onSubmit(values)` receives the zod OUTPUT (transforms applied). Throw/reject to surface a server error.

## Pieces
| Export | Notes |
|---|---|
| `useZodForm(schema, { defaultValues, ...rhfOpts })` | zodResolver, `onTouched`, revalidate `onChange`. Provide every default (`''`, `false`, `[]`, `undefined` for money). |
| `<Form form onSubmit onInvalid? notify? onSessionEnded? fieldMap? guardLeave? disabled?>` | noValidate submit, double-submit guard, focus first invalid, aria-live error summary, server-error banner (`errors.root.server`), unsaved-changes guard. |
| `<FormActions submitLabel cancelLabel onCancel requireDirty align danger leading>` | Submit shows loading from Form. |
| `useFormUi()` | `{ submitting, disabled, dirty }` for custom buttons. |
| `useFieldRows(name, makeRow, {min,max})` | tiers/rows: `fields`, `path(i,'x')`, `append`, `remove`, `canAdd`, `canRemove`. Use `row.key` as React key. |

## Fields (all take `name`; label/helperText/etc as the m3 field)
| Component | Stored value | Schema |
|---|---|---|
| `TextFieldRHF` (`type="number"` -> number) | string / number | `nonEmptyTrimmed()`, `email()`, `url()`, `slug()`, `percent()` |
| `TextAreaRHF` | string | `nonEmptyTrimmed()` |
| `SelectRHF options placeholder?` | string | `z.enum([...])` |
| `ComboboxRHF options` | string \| null | `z.string().min(1,'Choose one')` |
| `CheckboxRHF`, `SwitchRHF` | boolean | `z.boolean()` / `.refine(v=>v,'Required')` |
| `RadioGroupRHF legend options` | string | `z.enum` |
| `SegmentedRHF label options` | string | `z.enum` |
| `DateRHF` / `TimeRHF` | `YYYY-MM-DD` / `HH:mm` | `isoDate()` / `isoTime()` |
| `MoneyRHF` | integer ngwee or undefined | `moneyMinor({minMinor, maxMinor, allowZero})` |
| `PhoneRHF defaultCountry?` | E.164 or '' | `phoneE164({region})`, `phoneE164Optional()` |
| `OtpRHF length? onComplete?` | digit string | `otp6()` |
| `FileRHF accept multiple` | `File[]` | `files({max,maxBytes,types})` |
| `ChipsRHF` | string[] | `z.array(...)` |

## Schemas (`forms/schemas`)
`phoneE164`, `phoneE164Optional`, `normalisePhone`, `email`, `otp6`, `money` (major units text -> ngwee), `moneyMinor`, `parseKwachaToMinor`, `minorToKwachaString`, `slug`, `isoDate`, `isoTime`, `dateRange`, `endAfterStart('startsAt','endsAt')` (use in `.superRefine`), `url`, `nonEmptyTrimmed`, `percent`, `files`, `zodIssuesToFieldErrors`.

## Server errors
`applyServerError(form, err, { notify, fieldMap, onSessionEnded })` is called by `<Form>` for you. Field violations (`extensions.fields`, `input.` prefix stripped) land on inputs; codes owned by a field (`SLUG_TAKEN` -> `slug`, `OTP_INVALID` -> `code`, ...) are set on that field (rename with `fieldMap`); everything else is the banner, or `notify` for UNAVAILABLE/INTERNAL. `retryAfterSeconds` is appended to the message. Need to branch on a code? `onServerError={(e) => e.code === 'X' && ...}`. REST: use `restRequest()` (throws `RestProblemError`).

## Data
- GraphQL: `useGraphQLMutationForm({ mutation, toVariables, onSuccess, refetchQueries, optimisticResponse, update })` -> `{ submit, loading }`. Apollo client comes from the app's `ApolloProvider` (`createGraphQLClient`).
- REST/BFF: `useRestMutationForm({ mutationFn: (v) => restRequest('/api/x', { body: v }), invalidate: [keys.lists()] })`; wrap the app in `QueryProvider`; keys via `defineQueryKeys('scope')`.
