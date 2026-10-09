import Link from 'next/link';
import type { ComponentProps } from 'react';
import { Icon, type IconName } from '@pml.tickets/shared/components/m3';

interface LinkBtnProps extends Omit<ComponentProps<typeof Link>, 'href'> {
  href: string;
  variant?: 'filled' | 'tonal' | 'outlined' | 'text' | 'accent';
  size?: 'md' | 'sm';
  icon?: IconName;
}

/** Client-side navigation styled as an M3 button. */
export function LinkBtn({ href, variant = 'outlined', size = 'md', icon, children, className, ...rest }: LinkBtnProps) {
  return (
    <Link
      href={href}
      className={['m3-btn m3-state', className].filter(Boolean).join(' ')}
      data-variant={variant}
      data-size={size === 'sm' ? 'sm' : undefined}
      {...rest}
    >
      {icon ? <Icon name={icon} /> : null}
      {children}
    </Link>
  );
}
