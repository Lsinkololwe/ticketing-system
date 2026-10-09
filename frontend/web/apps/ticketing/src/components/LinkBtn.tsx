import Link from 'next/link';
import type { ComponentProps, ReactNode } from 'react';
import { Icon, type ButtonVariant, type IconName } from '@pml.tickets/shared/components/m3';

/** next/link styled as an M3 button (client-side navigation, keyboard and focus like any link). */
export function LinkBtn({
  variant = 'outlined',
  size,
  icon,
  className,
  children,
  ...rest
}: Omit<ComponentProps<typeof Link>, 'children'> & {
  variant?: ButtonVariant;
  size?: 'md' | 'sm';
  icon?: IconName;
  children?: ReactNode;
}) {
  return (
    <Link className={`m3-btn m3-state${className ? ` ${className}` : ''}`} data-variant={variant} data-size={size === 'sm' ? 'sm' : undefined} {...rest}>
      {icon ? <Icon name={icon} /> : null}
      {children}
    </Link>
  );
}
