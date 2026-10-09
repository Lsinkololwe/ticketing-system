// @vitest-environment jsdom
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { z } from 'zod';
import { Form, FormActions } from '../Form';
import { TextFieldRHF, SelectRHF } from '../fields';
import { useFieldRows } from '../useFieldRows';
import { useZodForm } from '../useZodForm';
import { nonEmptyTrimmed, email } from '../schemas';
import { RestProblemError } from '../server-errors';

const schema = z.object({ name: nonEmptyTrimmed(), email: email(), kind: z.enum(['a', 'b'], { error: 'Choose a kind' }) });

function Harness({
  onSubmit = vi.fn(),
  extra,
  ...props
}: { onSubmit?: (v: z.output<typeof schema>) => unknown; extra?: React.ReactNode } & Partial<React.ComponentProps<typeof Form>>) {
  const form = useZodForm(schema, { defaultValues: { name: '', email: '', kind: '' as never } });
  return (
    <Form form={form as never} onSubmit={onSubmit as never} aria-label="Profile" {...(props as object)}>
      <TextFieldRHF name="name" label="Full name" helperText="As on your ID" />
      <TextFieldRHF name="email" label="Email" type="email" />
      <SelectRHF name="kind" label="Kind" placeholder="Choose" options={[{ value: 'a', label: 'A' }, { value: 'b', label: 'B' }]} />
      {extra}
      <FormActions submitLabel="Save" onCancel={() => undefined} />
    </Form>
  );
}

describe('useZodForm + Form validation', () => {
  it('validates on blur (onTouched) then on change, with aria wiring', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const name = screen.getByLabelText('Full name');
    expect(name).toHaveAttribute('aria-describedby');
    expect(name).not.toHaveAttribute('aria-invalid');
    await user.click(name);
    await user.tab();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Required');
    expect(name).toHaveAttribute('aria-invalid', 'true');
    expect(name.getAttribute('aria-describedby')).toBe(alert.id);
    await user.type(name, 'Ada');
    await waitFor(() => expect(name).not.toHaveAttribute('aria-invalid'));
    expect(screen.getByLabelText('Full name').getAttribute('aria-describedby')).toMatch(/-help$/);
  });

  it('on invalid submit focuses the first invalid field and announces a summary', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<Harness onSubmit={onSubmit} />);
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.getByLabelText('Full name')).toHaveFocus());
    expect(onSubmit).not.toHaveBeenCalled();
    const summary = document.querySelector('[data-form-summary]') as HTMLElement;
    expect(summary).toHaveTextContent('Fix 3 fields to continue');
    expect(summary.querySelector('[role="alert"]')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Email: Enter an email address' })).toBeInTheDocument();
    // clicking a summary item moves focus to that field
    await user.click(screen.getByRole('button', { name: 'Email: Enter an email address' }));
    expect(screen.getByLabelText('Email')).toHaveFocus();
  });

  it('focuses the first invalid field in DOM order when a later field is the only one wrong', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.type(screen.getByLabelText('Full name'), 'Ada');
    await user.type(screen.getByLabelText('Email'), 'ada@x.zm');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.getByLabelText('Kind')).toHaveFocus());
    expect(screen.getByLabelText('Kind')).toHaveAttribute('aria-invalid', 'true');
  });

  it('submits parsed output (trimmed, lowercased)', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<Harness onSubmit={onSubmit} />);
    await user.type(screen.getByLabelText('Full name'), '  Ada ');
    await user.type(screen.getByLabelText('Email'), 'ADA@X.ZM');
    await user.selectOptions(screen.getByLabelText('Kind'), 'b');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0]).toEqual({ name: 'Ada', email: 'ada@x.zm', kind: 'b' });
  });
});

async function fillValid(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Full name'), 'Ada');
  await user.type(screen.getByLabelText('Email'), 'ada@x.zm');
  await user.selectOptions(screen.getByLabelText('Kind'), 'a');
}

describe('double submit guard', () => {
  it('ignores a second submit while the first is in flight and shows loading', async () => {
    const user = userEvent.setup();
    let release!: () => void;
    const onSubmit = vi.fn(() => new Promise<void>((r) => (release = r)));
    render(<Harness onSubmit={onSubmit} />);
    await fillValid(user);
    const save = screen.getByRole('button', { name: 'Save' });
    await user.click(save);
    await user.click(save);
    await user.keyboard('{Enter}');
    expect(onSubmit).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save' })).toHaveAttribute('aria-busy', 'true'));
    expect(screen.getByRole('form', { name: 'Profile' })).toHaveAttribute('aria-busy', 'true');
    await act(async () => release());
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save' })).not.toHaveAttribute('aria-busy'));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(2));
  });
});

describe('server errors through Form', () => {
  it('puts field-owned errors on the field and focuses it', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockRejectedValue({ errors: [{ message: 'x', extensions: { errorCode: 'COMMAND_NOT_WELL_FORMED', classification: 'BAD_REQUEST', fields: [{ path: 'input.email', constraint: 'Email' }] } }] });
    render(<Harness onSubmit={onSubmit} />);
    await fillValid(user);
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true'));
    expect(screen.getByText('Enter a valid email address')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Email')).toHaveFocus());
  });

  it('shows form-level refusals as an urgent banner and clears them on resubmit', async () => {
    const user = userEvent.setup();
    const onSubmit = vi
      .fn()
      .mockRejectedValueOnce(new RestProblemError(409, { status: 409, errorCode: 'TIER_SOLD_OUT', classification: 'FAILED_PRECONDITION' }))
      .mockResolvedValueOnce(undefined);
    render(<Harness onSubmit={onSubmit} />);
    await fillValid(user);
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Those tickets have sold out.')).toBeInTheDocument();
    expect(screen.getByText('Those tickets have sold out.').closest('[role="alert"]')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.queryByText('Those tickets have sold out.')).not.toBeInTheDocument());
  });

  it('sends transient errors to notify and fires onServerError', async () => {
    const user = userEvent.setup();
    const notify = vi.fn();
    const onServerError = vi.fn();
    render(<Harness onSubmit={vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))} notify={notify} onServerError={onServerError} />);
    await fillValid(user);
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(notify).toHaveBeenCalledWith(expect.objectContaining({ tone: 'error' })));
    expect(onServerError).toHaveBeenCalledWith(expect.objectContaining({ retryable: true }));
  });

  it('reports a revoked session', async () => {
    const user = userEvent.setup();
    const onSessionEnded = vi.fn();
    render(<Harness onSubmit={vi.fn().mockRejectedValue({ errors: [{ extensions: { errorCode: 'TOKEN_REVOKED', classification: 'UNAUTHENTICATED' } }] })} onSessionEnded={onSessionEnded} />);
    await fillValid(user);
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(onSessionEnded).toHaveBeenCalled());
  });
});

describe('dirty-leave guard', () => {
  it('blocks beforeunload and same-origin link clicks only when dirty', async () => {
    const user = userEvent.setup();
    const confirmLeave = vi.fn(() => false);
    render(
      <>
        <a href="/elsewhere" onClick={(e) => e.preventDefault()}>
          Elsewhere
        </a>
        <Harness confirmLeave={confirmLeave} />
      </>
    );
    const clean = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(clean);
    expect(clean.defaultPrevented).toBe(false);
    await user.click(screen.getByText('Elsewhere'));
    expect(confirmLeave).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText('Full name'), 'A');
    const dirty = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(dirty);
    expect(dirty.defaultPrevented).toBe(true);
    const clickEvt = new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 });
    screen.getByText('Elsewhere').dispatchEvent(clickEvt);
    expect(confirmLeave).toHaveBeenCalledTimes(1);
    expect(clickEvt.defaultPrevented).toBe(true);
  });

  it('can be switched off', async () => {
    const user = userEvent.setup();
    render(<Harness guardLeave={false} />);
    await user.type(screen.getByLabelText('Full name'), 'A');
    const evt = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(evt);
    expect(evt.defaultPrevented).toBe(false);
  });
});

describe('FormActions', () => {
  it('renders cancel and a single submit button; requireDirty disables until edited', async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    function F() {
      const form = useZodForm(z.object({ n: z.string() }), { defaultValues: { n: '' } });
      return (
        <Form form={form} onSubmit={() => undefined}>
          <TextFieldRHF name="n" label="N" />
          <FormActions submitLabel="Go" onCancel={onCancel} requireDirty />
        </Form>
      );
    }
    render(<F />);
    expect(screen.getByRole('button', { name: 'Go' })).toBeDisabled();
    expect(document.querySelectorAll('button[type="submit"]')).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onCancel).toHaveBeenCalled();
    await user.type(screen.getByLabelText('N'), 'x');
    expect(screen.getByRole('button', { name: 'Go' })).toBeEnabled();
  });

  it('a disabled Form disables fields and submit', () => {
    render(<Harness disabled />);
    expect(screen.getByLabelText('Full name')).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });
});

describe('useFieldRows', () => {
  const rowSchema = z.object({ tiers: z.array(z.object({ name: nonEmptyTrimmed() })).min(1) });
  function Rows() {
    const form = useZodForm(rowSchema, { defaultValues: { tiers: [{ name: '' }] } });
    return (
      <Form form={form} onSubmit={() => undefined}>
        <RowsInner />
        <FormActions />
      </Form>
    );
  }
  function RowsInner() {
    const rows = useFieldRows('tiers', () => ({ name: '' }), { min: 1, max: 2 });
    return (
      <>
        {rows.fields.map((r, i) => (
          <div key={r.key}>
            <TextFieldRHF name={rows.path(i, 'name')} label={`Tier ${i + 1}`} />
            <button type="button" onClick={() => rows.remove(i)} disabled={!rows.canRemove}>
              Remove {i + 1}
            </button>
          </div>
        ))}
        <button type="button" onClick={rows.append} disabled={!rows.canAdd}>
          Add tier
        </button>
      </>
    );
  }
  it('adds, removes within limits and validates each row', async () => {
    const user = userEvent.setup();
    render(<Rows />);
    expect(screen.getByRole('button', { name: 'Remove 1' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Add tier' }));
    expect(screen.getByLabelText('Tier 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add tier' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.getByLabelText('Tier 1')).toHaveFocus());
    expect(screen.getByLabelText('Tier 2')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('button', { name: 'Tier 2: Required' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Remove 2' }));
    expect(screen.queryByLabelText('Tier 2')).not.toBeInTheDocument();
  });
});
