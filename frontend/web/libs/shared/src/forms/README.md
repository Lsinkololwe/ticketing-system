# Forms and data kit

One way to build a form in all three apps.

## Rules
1. **Forms are only react-hook-form + zod.** Create them with `useZodForm`, render them in `<Form>`.
2. **Schemas live next to the feature**; shared building blocks (`phoneE164`, `money`, `slug`, ...) live in `forms/schemas`. Do not copy a building block into an app.
3. **No `useState`-driven forms.** No `value`/`onChange` pairs per input, no `useState` for errors or "submitting".
4. **No manual validation.** No `if (!x) setError(...)` in handlers, no regexes in components. If a rule is missing, add it to the schema (or to `forms/schemas` if two apps need it).
5. **Server errors go through `Form`.** Throw from `onSubmit`; `applyServerError` maps `extensions.errorCode` / RFC 9457 onto fields, banner or snackbar. Never match on message text.
6. **Data**: GraphQL operations use Apollo (`useGraphQLMutationForm`); REST/BFF calls use TanStack Query (`useRestMutationForm` + `restRequest`). Nothing else (no axios in new code, no raw `fetch` in components).
7. Money is integer ngwee end to end (`MoneyRHF` + `moneyMinor`); phone is E.164 (`PhoneRHF` + `phoneE164`). Format for display with `formatKwacha`.

See `API.md` for the cheat-sheet.

## Layout
```
forms/
  useZodForm.ts   Form.tsx   fields.tsx   useFieldRows.ts   focus.ts
  server-errors.ts            schemas/{phone,money,primitives,dates,files,issues}.ts
api/form-mutations.ts  QueryProvider.tsx  query-keys.ts  rest-request.ts
```
Error classification and the closed ErrorCode registry live in `lib/errors.ts` (checked against specs/_platform/005-error-contract in `errorContract.test.ts`). `server-errors.ts` only adds the form facts: which field owns a code (`FIELD_FOR_CODE`) and copy for form-level codes (`CODE_COPY`). To support a new code beside an input, add one line to `FIELD_FOR_CODE`.

## Migrating an existing form (no codemod)
Before:
```tsx
const [name, setName] = useState('');
const [phone, setPhone] = useState('');
const [errors, setErrors] = useState<Record<string,string>>({});
const [busy, setBusy] = useState(false);

async function submit(e) {
  e.preventDefault();
  const next = {};
  if (!name.trim()) next.name = 'Required';
  if (!/^\+?\d{9,12}$/.test(phone)) next.phone = 'Invalid phone';
  setErrors(next);
  if (Object.keys(next).length) return;
  setBusy(true);
  try { await createOrg({ variables: { input: { name, phone } } }); }
  catch (err) { setErrors({ form: err.message }); }
  finally { setBusy(false); }
}
return (
  <form onSubmit={submit}>
    <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} errorText={errors.name} />
    <PhoneField value={phone} onChange={setPhone} errorText={errors.phone} />
    {errors.form && <p>{errors.form}</p>}
    <Button type="submit" disabled={busy}>Save</Button>
  </form>
);
```
After:
```tsx
const schema = z.object({ name: nonEmptyTrimmed(), phone: phoneE164() });   // next to the feature

const form = useZodForm(schema, { defaultValues: { name: '', phone: '' } });
const m = useGraphQLMutationForm({ mutation: CREATE_ORG, toVariables: (v) => ({ input: v }), refetchQueries: ['MyOrganizations'] });
return (
  <Form form={form} onSubmit={m.submit} notify={snackbar.show}>
    <TextFieldRHF name="name" label="Name" />
    <PhoneRHF name="phone" label="Phone" />
    <FormActions submitLabel="Save" />
  </Form>
);
```
Steps:
1. Write the zod schema from the existing `if` checks and copy the user-facing messages into it. Delete the checks.
2. Replace each `useState` + input with the matching `*RHF` field (same `label`, `helperText`, props). Put the initial values in `defaultValues`.
3. Replace the submit handler with `onSubmit` returning the mutation (`m.submit`). Delete `busy`, `errors` and try/catch: `Form` shows loading, maps errors and guards double submit.
4. Replace manual money/phone parsing (`parseFloat`, regex) with `MoneyRHF`/`PhoneRHF` and the schema building blocks; the submitted value is already ngwee / E.164.
5. Repeating rows: `useFieldRows('tiers', () => ({...}))` instead of array state.
6. Tests: render the form, submit empty (assert messages + focus on the first invalid field), submit valid (assert mutation variables). Mock the transport, never the form.
