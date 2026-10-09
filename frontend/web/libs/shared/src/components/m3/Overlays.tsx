'use client';

import {
  Children,
  cloneElement,
  createContext,
  isValidElement,
  useCallback,
  useContext,
  useEffect,
  useId,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type HTMLAttributes,
  type ReactElement,
  type ReactNode,
  type Ref,
} from 'react';
import { Button, IconButton } from './Button';
import { Icon, type IconName } from './icons';
import { Portal, useBodyScrollLock, useModalBehavior, useOutsideClick } from './utils';

/* -------------------------------------------------------------------- Menu */

export type MenuEntry =
  | {
      type?: 'item';
      id: string;
      label: string;
      icon?: IconName;
      hint?: string;
      danger?: boolean;
      disabled?: boolean;
      onSelect: () => void;
    }
  | { type: 'label'; id: string; label: string }
  | { type: 'divider'; id: string };

export interface MenuTriggerProps {
  ref: Ref<HTMLButtonElement>;
  onClick: () => void;
  'aria-haspopup': 'menu';
  'aria-expanded': boolean;
  'aria-controls': string | undefined;
}

export interface MenuProps {
  /** Render the control that opens the menu; spread the given props onto it. */
  trigger: (props: MenuTriggerProps) => ReactNode;
  items: MenuEntry[];
  align?: 'start' | 'end';
  label?: string;
}

/**
 * Action menu. Rendered in a portal with fixed positioning so it is never
 * clipped by a table's scroll container. Arrow keys / Home / End move, Enter or
 * Space activates, Escape closes and returns focus to the trigger.
 */
export function Menu({ trigger, items, align = 'start', label }: MenuProps) {
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState<CSSProperties>({});
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const id = useId();

  const close = useCallback((restore = true) => {
    setOpen(false);
    if (restore) triggerRef.current?.focus({ preventScroll: true });
  }, []);

  useOutsideClick(menuRef, open, () => close(false));

  useLayoutEffect(() => {
    if (!open || !triggerRef.current) return;
    const r = triggerRef.current.getBoundingClientRect();
    setPos(
      align === 'end'
        ? { top: r.bottom, right: window.innerWidth - r.right }
        : { top: r.bottom, left: r.left }
    );
  }, [open, align]);

  useEffect(() => {
    if (open) menuRef.current?.querySelector<HTMLElement>('[role=menuitem]:not([aria-disabled=true])')?.focus();
  }, [open]);

  const onKeyDown = (e: React.KeyboardEvent) => {
    const nodes = Array.from(
      menuRef.current?.querySelectorAll<HTMLElement>('[role=menuitem]:not([aria-disabled=true])') ?? []
    );
    const i = nodes.indexOf(document.activeElement as HTMLElement);
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      nodes[(i + 1) % nodes.length]?.focus();
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      nodes[(i - 1 + nodes.length) % nodes.length]?.focus();
    } else if (e.key === 'Home') {
      e.preventDefault();
      nodes[0]?.focus();
    } else if (e.key === 'End') {
      e.preventDefault();
      nodes[nodes.length - 1]?.focus();
    } else if (e.key === 'Escape') {
      e.preventDefault();
      close();
    } else if (e.key === 'Tab') {
      close(false);
    }
  };

  return (
    <>
      {trigger({
        ref: triggerRef,
        onClick: () => setOpen((o) => !o),
        'aria-haspopup': 'menu',
        'aria-expanded': open,
        'aria-controls': open ? id : undefined,
      })}
      {open ? (
        <Portal>
          <div
            ref={menuRef}
            id={id}
            role="menu"
            aria-label={label}
            className="m3-menu"
            style={pos}
            onKeyDown={onKeyDown}
          >
            {items.map((entry) => {
              if (entry.type === 'divider') return <hr key={entry.id} className="m3-divider" />;
              if (entry.type === 'label')
                return (
                  <span key={entry.id} className="m3-menu__label" role="presentation">
                    {entry.label}
                  </span>
                );
              return (
                <button
                  key={entry.id}
                  type="button"
                  role="menuitem"
                  className="m3-menu__item m3-state"
                  data-danger={entry.danger ? 'true' : undefined}
                  aria-disabled={entry.disabled || undefined}
                  tabIndex={-1}
                  onClick={() => {
                    if (entry.disabled) return;
                    close();
                    entry.onSelect();
                  }}
                >
                  <span className="m3-row">
                    {entry.icon ? <Icon name={entry.icon} /> : null}
                    {entry.label}
                  </span>
                  {entry.hint ? <span className="m3-menu__hint">{entry.hint}</span> : null}
                </button>
              );
            })}
          </div>
        </Portal>
      ) : null}
    </>
  );
}

export interface RowMenuProps {
  label: string;
  items: MenuEntry[];
}
/** The "..." overflow button used in table action columns. */
export function RowMenu({ label, items }: RowMenuProps) {
  return (
    <Menu
      align="end"
      label={label}
      items={items}
      trigger={({ ref, ...p }) => <IconButton ref={ref} icon="more" label={label} {...p} />}
    />
  );
}

/* ----------------------------------------------------------------- Tooltip */

export interface TooltipProps {
  content: ReactNode;
  children: ReactElement<HTMLAttributes<HTMLElement>>;
}

/** Hover/focus tooltip. Describes the trigger via aria-describedby; Escape dismisses. */
export function Tooltip({ content, children }: TooltipProps) {
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState<CSSProperties>({});
  const id = useId();
  const ref = useRef<HTMLElement | null>(null);
  const child = Children.only(children);

  const show = () => {
    const r = ref.current?.getBoundingClientRect();
    if (r) setPos({ top: r.bottom, left: r.left + r.width / 2, transform: 'translateX(-50%)' });
    setOpen(true);
  };
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open]);

  if (!isValidElement(child)) return <>{children}</>;
  const props = child.props;
  return (
    <>
      {cloneElement(child, {
        ref: (el: HTMLElement | null) => {
          ref.current = el;
        },
        'aria-describedby': open ? id : props['aria-describedby'],
        onMouseEnter: (e: React.MouseEvent<HTMLElement>) => {
          props.onMouseEnter?.(e);
          show();
        },
        onMouseLeave: (e: React.MouseEvent<HTMLElement>) => {
          props.onMouseLeave?.(e);
          setOpen(false);
        },
        onFocus: (e: React.FocusEvent<HTMLElement>) => {
          props.onFocus?.(e);
          show();
        },
        onBlur: (e: React.FocusEvent<HTMLElement>) => {
          props.onBlur?.(e);
          setOpen(false);
        },
      } as HTMLAttributes<HTMLElement> & { ref: Ref<HTMLElement> })}
      {open ? (
        <Portal>
          <div id={id} role="tooltip" className="m3-tooltip" style={pos}>
            {content}
          </div>
        </Portal>
      ) : null}
    </>
  );
}

/* ------------------------------------------------------------------ Dialog */

export interface DialogProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children?: ReactNode;
  /** Footer buttons, right aligned. */
  actions?: ReactNode;
  wide?: boolean;
  /** Set false for forced choices (destructive confirmations may still use Escape). */
  dismissOnScrim?: boolean;
  /** role=alertdialog for confirmations that need an answer. */
  alert?: boolean;
}

/** Modal dialog: focus trap, Escape, scrim click, scroll lock, focus returns to the opener. */
export function Dialog({ open, onClose, title, children, actions, wide, dismissOnScrim = true, alert }: DialogProps) {
  const [node, setNode] = useState<HTMLDivElement | null>(null);
  const titleId = useId();
  useBodyScrollLock(open);
  useModalBehavior(node, open, onClose);
  if (!open) return null;
  return (
    <Portal>
      <div className="m3-overlay">
        <div className="m3-overlay__scrim" onClick={dismissOnScrim ? onClose : undefined} />
        <div
          ref={setNode}
          className="m3-dialog"
          role={alert ? 'alertdialog' : 'dialog'}
          aria-modal="true"
          aria-labelledby={titleId}
          data-wide={wide ? 'true' : undefined}
          tabIndex={-1}
        >
          <h2 className="m3-dialog__title" id={titleId}>
            {title}
          </h2>
          {children}
          {actions ? <div className="m3-dialog__actions">{actions}</div> : null}
        </div>
      </div>
    </Portal>
  );
}

export interface ConfirmDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  description?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
  loading?: boolean;
}
/** Two-button confirmation built on Dialog. */
export function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title,
  description,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  danger,
  loading,
}: ConfirmDialogProps) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={title}
      alert
      actions={
        <>
          <Button variant="text" onClick={onClose}>
            {cancelLabel}
          </Button>
          <Button variant="filled" danger={danger} loading={loading} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      {description ? <div className="m3-muted">{description}</div> : null}
    </Dialog>
  );
}

/* ---------------------------------------------------------------- SideSheet */

export interface SideSheetProps {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: ReactNode;
  children?: ReactNode;
  /** Sticky footer actions. */
  actions?: ReactNode;
}

/**
 * Right-hand detail panel for quick views (the "flying drawer").
 * Modal: scrim, focus trap, Escape, focus returns to the control that opened it.
 */
export function SideSheet({ open, onClose, title, subtitle, children, actions }: SideSheetProps) {
  const [node, setNode] = useState<HTMLElement | null>(null);
  const titleId = useId();
  useBodyScrollLock(open);
  useModalBehavior(node, open, onClose);
  if (!open) return null;
  return (
    <Portal>
      <div className="m3-overlay" data-side="end">
        <div className="m3-overlay__scrim" onClick={onClose} />
        <aside ref={setNode} className="m3-sheet" role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}>
          <div className="m3-sheet__head">
            <div>
              <h2 className="m3-sheet__title" id={titleId}>
                {title}
              </h2>
              {subtitle ? <p className="m3-page-sub">{subtitle}</p> : null}
            </div>
            <IconButton icon="close" label="Close panel" onClick={onClose} />
          </div>
          {children}
          {actions ? <div className="m3-sheet__foot">{actions}</div> : null}
        </aside>
      </div>
    </Portal>
  );
}

/* ---------------------------------------------------------------- Snackbar */

export interface SnackbarOptions {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
  tone?: 'neutral' | 'error';
  /** ms before auto-dismiss. 0 keeps it until dismissed. Default 4000. */
  duration?: number;
}
interface SnackItem extends SnackbarOptions {
  id: number;
}
interface SnackbarApi {
  show: (options: SnackbarOptions | string) => number;
  dismiss: (id: number) => void;
}

const SnackbarContext = createContext<SnackbarApi | null>(null);

/** Mount once near the root. Renders a polite live region. */
export function SnackbarProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<SnackItem[]>([]);
  const counter = useRef(0);
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  const dismiss = useCallback((id: number) => {
    setItems((list) => list.filter((i) => i.id !== id));
    const t = timers.current.get(id);
    if (t) clearTimeout(t);
    timers.current.delete(id);
  }, []);

  const show = useCallback(
    (options: SnackbarOptions | string) => {
      const o = typeof options === 'string' ? { message: options } : options;
      const id = ++counter.current;
      setItems((list) => [...list.slice(-2), { ...o, id }]);
      const duration = o.duration ?? 4000;
      if (duration > 0) timers.current.set(id, setTimeout(() => dismiss(id), duration));
      return id;
    },
    [dismiss]
  );

  useEffect(() => {
    const map = timers.current;
    return () => map.forEach(clearTimeout);
  }, []);

  const api = useMemo(() => ({ show, dismiss }), [show, dismiss]);

  return (
    <SnackbarContext.Provider value={api}>
      {children}
      <Portal>
        <div className="m3-snackbars" role="status" aria-live="polite">
          {items.map((i) => (
            <div key={i.id} className="m3-snackbar" data-tone={i.tone === 'error' ? 'error' : undefined}>
              <span>{i.message}</span>
              {i.actionLabel ? (
                <button
                  type="button"
                  className="m3-snackbar__action m3-state"
                  onClick={() => {
                    i.onAction?.();
                    dismiss(i.id);
                  }}
                >
                  {i.actionLabel}
                </button>
              ) : null}
            </div>
          ))}
        </div>
      </Portal>
    </SnackbarContext.Provider>
  );
}

/** `const snackbar = useSnackbar(); snackbar.show('Saved')`. */
export function useSnackbar(): SnackbarApi {
  const ctx = useContext(SnackbarContext);
  if (!ctx) throw new Error('useSnackbar must be used inside <SnackbarProvider>');
  return ctx;
}
