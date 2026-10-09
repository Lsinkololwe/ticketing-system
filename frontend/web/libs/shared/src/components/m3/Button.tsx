'use client';

import { forwardRef, type AnchorHTMLAttributes, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { Icon, type IconName } from './icons';
import { cx } from './utils';

export type ButtonVariant = 'filled' | 'tonal' | 'outlined' | 'text' | 'link' | 'accent' | 'ghost-inverse';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** filled = primary action; tonal = secondary; outlined = default; text = tertiary; link = inline underlined link-style action; accent = high-emphasis call to action (the accent colour). */
  variant?: ButtonVariant;
  /** 'md' (34px, buyer 48px) or 'sm' (28px, buyer 36px). */
  size?: 'md' | 'sm';
  /** Leading icon. */
  icon?: IconName;
  /** Destructive action colouring. */
  danger?: boolean;
  fullWidth?: boolean;
  /** Shows busy state and blocks activation; keeps width. */
  loading?: boolean;
}

/**
 * M3 button. Heights, paddings and radii come from `--m3-btn-*` tokens.
 * Never put an `onClick` on a table row: give the row a Button in its action
 * column instead.
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'outlined',
    size = 'md',
    icon,
    danger,
    fullWidth,
    loading,
    className,
    children,
    disabled,
    type = 'button',
    onClick,
    ...rest
  },
  ref
) {
  return (
    <button
      ref={ref}
      type={type}
      className={cx('m3-btn m3-state', className)}
      data-variant={variant}
      data-size={size === 'sm' ? 'sm' : undefined}
      data-danger={danger ? 'true' : undefined}
      data-full={fullWidth ? 'true' : undefined}
      aria-busy={loading || undefined}
      aria-disabled={loading || undefined}
      disabled={disabled}
      onClick={loading ? (e) => e.preventDefault() : onClick}
      {...rest}
    >
      {icon ? <Icon name={icon} /> : null}
      {children}
    </button>
  );
});

export interface LinkButtonProps extends AnchorHTMLAttributes<HTMLAnchorElement> {
  variant?: ButtonVariant;
  size?: 'md' | 'sm';
  icon?: IconName;
  children?: ReactNode;
}

/** An anchor styled as a Button (navigation, not an action). */
export const LinkButton = forwardRef<HTMLAnchorElement, LinkButtonProps>(function LinkButton(
  { variant = 'outlined', size = 'md', icon, className, children, ...rest },
  ref
) {
  return (
    <a
      ref={ref}
      className={cx('m3-btn m3-state', className)}
      data-variant={variant}
      data-size={size === 'sm' ? 'sm' : undefined}
      {...rest}
    >
      {icon ? <Icon name={icon} /> : null}
      {children}
    </a>
  );
});

export interface IconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'aria-label'> {
  icon: IconName;
  /** Required: icon-only controls must be named. */
  label: string;
  variant?: 'standard' | 'filled' | 'tonal' | 'outlined';
  danger?: boolean;
}

/** Circular icon-only button (34px at medium density, 40px on the storefront). */
export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
  { icon, label, variant = 'standard', danger, className, type = 'button', ...rest },
  ref
) {
  return (
    <button
      ref={ref}
      type={type}
      aria-label={label}
      title={label}
      className={cx('m3-iconbtn m3-state', className)}
      data-variant={variant === 'standard' ? undefined : variant}
      data-danger={danger ? 'true' : undefined}
      {...rest}
    >
      <Icon name={icon} />
    </button>
  );
});

export interface FabProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'aria-label'> {
  icon: IconName;
  label: string;
  /** Show the label next to the icon. */
  extended?: boolean;
}

/** Floating action button (accent colour). One per screen. */
export const Fab = forwardRef<HTMLButtonElement, FabProps>(function Fab(
  { icon, label, extended, className, type = 'button', ...rest },
  ref
) {
  return (
    <button
      ref={ref}
      type={type}
      aria-label={extended ? undefined : label}
      className={cx('m3-fab m3-state', className)}
      data-extended={extended ? 'true' : undefined}
      {...rest}
    >
      <Icon name={icon} />
      {extended ? <span>{label}</span> : null}
    </button>
  );
});
