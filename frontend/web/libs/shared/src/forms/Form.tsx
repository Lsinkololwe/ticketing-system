'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from 'react';
import { FormProvider, useFormContext, useFormState, type FieldErrors, type FieldValues, type UseFormReturn } from 'react-hook-form';
import { Banner } from '../components/m3/Display';
import { Button } from '../components/m3/Button';
import { applyServerError, FORM_ERROR_KEY, type ApplyServerErrorOptions, type NormalisedError } from './server-errors';
import { focusFirstInvalid, focusWithin } from './focus';

interface FormUi {
  submitting: boolean;
  disabled: boolean;
  dirty: boolean;
}
const FormUiContext = createContext<FormUi>({ submitting: false, disabled: false, dirty: false });

/** Submit/dirty state of the surrounding `<Form>`; usable by custom buttons. */
export function useFormUi(): FormUi {
  return useContext(FormUiContext);
}

interface SummaryItem {
  name: string;
  message: string;
}

function collectErrors(errors: FieldErrors<FieldValues>, prefix = ''): SummaryItem[] {
  const out: SummaryItem[] = [];
  for (const [key, value] of Object.entries(errors)) {
    if (key === 'root' || !value || typeof value !== 'object') continue;
    const name = prefix ? `${prefix}.${key}` : key;
    const v = value as { message?: unknown; type?: unknown; ref?: unknown };
    if (typeof v.message === 'string' && v.message) out.push({ name, message: v.message });
    else if (typeof v.type === 'string') out.push({ name, message: 'Not valid' });
    // Nested objects / arrays of objects
    const nested = Object.fromEntries(Object.entries(value).filter(([k]) => !['message', 'type', 'ref', 'types'].includes(k)));
    if (Object.keys(nested).length) out.push(...collectErrors(nested as FieldErrors<FieldValues>, name));
  }
  return out;
}

function humanise(name: string): string {
  const parts = name.split('.').map((p) => (/^\d+$/.test(p) ? String(Number(p) + 1) : p.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase()));
  const s = parts.join(' ');
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function labelFor(root: HTMLElement | null, name: string): string {
  if (root) {
    const el = root.querySelector<HTMLInputElement>(`[name="${CSS.escape(name)}"]`) ?? root.querySelector<HTMLElement>(`[data-field="${CSS.escape(name)}"]`);
    const fromLabels = (el as HTMLInputElement | null)?.labels?.[0]?.textContent?.trim();
    if (fromLabels) return fromLabels;
    const aria = el?.getAttribute('aria-label') ?? el?.getAttribute('data-label');
    if (aria) return aria;
  }
  return humanise(name);
}

export interface FormProps<TIn extends FieldValues, TOut> extends Omit<ApplyServerErrorOptions, never> {
  form: UseFormReturn<TIn, any, TOut>;
  /** Validated, parsed values (zod output). Throw (or reject) to have the error mapped onto the form. */
  onSubmit: (values: TOut, form: UseFormReturn<TIn, any, TOut>) => void | Promise<void>;
  onInvalid?: () => void;
  /** Called with the mapped error after it was applied to the form. */
  onServerError?: (error: NormalisedError) => void;
  /** Warn before leaving with unsaved edits (tab close and same-origin link clicks). Default true. */
  guardLeave?: boolean;
  /** Return true to leave. Default: window.confirm. */
  confirmLeave?: () => boolean;
  /** Disable every control while true (e.g. read-only permissions). */
  disabled?: boolean;
  /** Accessible name of the form. */
  'aria-label'?: string;
  className?: string;
  children: ReactNode;
}

const DEFAULT_LEAVE_MESSAGE = 'You have unsaved changes. Leave without saving?';

/**
 * Wires a `useZodForm` form to the DOM: provider, `noValidate` submit, double-submit guard, focus on the
 * first invalid field, an assertive error summary, a form-level server-error banner and the unsaved-changes guard.
 */
export function Form<TIn extends FieldValues, TOut>({
  form,
  onSubmit,
  onInvalid,
  onServerError,
  guardLeave = true,
  confirmLeave,
  disabled = false,
  className,
  children,
  notify,
  onSessionEnded,
  fieldMap,
  stripPrefixes,
  ...aria
}: FormProps<TIn, TOut>) {
  const ref = useRef<HTMLFormElement>(null);
  const inFlight = useRef(false);
  const [attempts, setAttempts] = useState(0);
  const [focusTick, setFocusTick] = useState(0);
  const { errors, isSubmitting, isDirty, isSubmitSuccessful } = useFormState({ control: form.control });

  const run = useCallback(
    async (event?: FormEvent<HTMLFormElement>) => {
      event?.preventDefault();
      if (inFlight.current || disabled) return;
      inFlight.current = true;
      form.clearErrors(FORM_ERROR_KEY as never);
      try {
        await form.handleSubmit(
          async (values) => {
            try {
              await onSubmit(values, form);
            } catch (err) {
              const mapped = applyServerError(form as never, err, { notify, onSessionEnded, fieldMap, stripPrefixes });
              setAttempts((n) => n + 1);
              setFocusTick((n) => n + 1);
              onServerError?.(mapped);
            }
          },
          () => {
            setAttempts((n) => n + 1);
            setFocusTick((n) => n + 1);
            onInvalid?.();
          }
        )();
      } finally {
        inFlight.current = false;
      }
    },
    [disabled, form, notify, onInvalid, onServerError, onSessionEnded, onSubmit, fieldMap, stripPrefixes]
  );

  // Move focus after the invalid state has rendered, so aria-invalid is on the DOM.
  useEffect(() => {
    if (focusTick === 0) return;
    if (focusFirstInvalid(ref.current)) return;
    const t = setTimeout(() => {
      if (!focusFirstInvalid(ref.current)) {
        const banner = ref.current?.querySelector('[data-form-banner]');
        if (banner) focusWithin(banner);
      }
    }, 0);
    return () => clearTimeout(t);
  }, [focusTick]);

  // Unsaved-changes guard.
  const dirty = isDirty && !isSubmitSuccessful;
  useEffect(() => {
    if (!guardLeave || !dirty) return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    const onClick = (e: MouseEvent) => {
      if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
      const a = (e.target as Element | null)?.closest?.('a[href]') as HTMLAnchorElement | null;
      if (!a || a.target === '_blank' || a.hasAttribute('download')) return;
      const url = new URL(a.href, window.location.href);
      if (url.origin !== window.location.origin) return;
      if (url.pathname === window.location.pathname && url.search === window.location.search) return;
      const leave = confirmLeave ? confirmLeave() : window.confirm(DEFAULT_LEAVE_MESSAGE);
      if (!leave) {
        e.preventDefault();
        e.stopPropagation();
      }
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    document.addEventListener('click', onClick, true);
    return () => {
      window.removeEventListener('beforeunload', onBeforeUnload);
      document.removeEventListener('click', onClick, true);
    };
  }, [guardLeave, dirty, confirmLeave]);

  const items = attempts > 0 ? collectErrors(errors as FieldErrors<FieldValues>) : [];
  const serverMessage = (errors as { root?: { server?: { message?: string } } }).root?.server?.message;
  const ui = useMemo<FormUi>(() => ({ submitting: isSubmitting, disabled, dirty }), [isSubmitting, disabled, dirty]);

  const focusField = (name: string) => {
    const el = ref.current?.querySelector(`[name="${CSS.escape(name)}"], [data-field="${CSS.escape(name)}"]`);
    if (el) focusWithin(el);
  };

  return (
    <FormProvider {...(form as unknown as UseFormReturn<FieldValues>)}>
      <FormUiContext.Provider value={ui}>
        <form
          ref={ref}
          noValidate
          onSubmit={run}
          className={className}
          aria-busy={isSubmitting || undefined}
          style={{ display: 'flex', flexDirection: 'column', gap: 'var(--m3-sp-20)' }}
          {...aria}
        >
          {serverMessage ? (
            <div data-form-banner tabIndex={-1}>
              <Banner tone="error" urgent>
                {serverMessage}
              </Banner>
            </div>
          ) : null}
          {items.length > 0 ? (
            <div data-form-summary key={attempts}>
              <Banner
                tone="error"
                urgent
                title={items.length === 1 ? 'Fix 1 field to continue' : `Fix ${items.length} fields to continue`}
              >
                <ul style={{ margin: 0, paddingInlineStart: 'var(--m3-sp-20)' }}>
                  {items.map((it) => (
                    <li key={it.name}>
                      <button type="button" className="m3-link" onClick={() => focusField(it.name)}>
                        {labelFor(ref.current, it.name)}: {it.message}
                      </button>
                    </li>
                  ))}
                </ul>
              </Banner>
            </div>
          ) : null}
          {children}
        </form>
      </FormUiContext.Provider>
    </FormProvider>
  );
}

export interface FormActionsProps {
  submitLabel?: string;
  /** Shown on the submit button while submitting. */
  submittingLabel?: string;
  cancelLabel?: string;
  /** Renders a Cancel button when provided. */
  onCancel?: () => void;
  /** Keep submit disabled until something changed. */
  requireDirty?: boolean;
  align?: 'start' | 'end' | 'between';
  danger?: boolean;
  /** Extra controls placed before Cancel (e.g. "Save draft"). */
  leading?: ReactNode;
}

/** Cancel + submit row. Submit shows loading and is the only `type="submit"` button. */
export function FormActions({
  submitLabel = 'Save',
  submittingLabel,
  cancelLabel = 'Cancel',
  onCancel,
  requireDirty,
  align = 'end',
  danger,
  leading,
}: FormActionsProps) {
  const { submitting, disabled, dirty } = useFormUi();
  const { formState } = useFormContext();
  const isDirty = dirty || formState.isDirty;
  return (
    <div
      style={{
        display: 'flex',
        gap: 'var(--m3-sp-12)',
        flexWrap: 'wrap',
        justifyContent: align === 'end' ? 'flex-end' : align === 'between' ? 'space-between' : 'flex-start',
      }}
    >
      {leading}
      {onCancel ? (
        <Button variant="text" onClick={onCancel} disabled={submitting}>
          {cancelLabel}
        </Button>
      ) : null}
      <Button
        type="submit"
        variant="filled"
        danger={danger}
        loading={submitting}
        disabled={disabled || (requireDirty && !isDirty)}
      >
        {submitting && submittingLabel ? submittingLabel : submitLabel}
      </Button>
    </div>
  );
}
