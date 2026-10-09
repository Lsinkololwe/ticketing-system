import type { ReactNode } from 'react';
import { Brand } from './Brand';

export interface AuthLayoutProps {
  /** Product name, e.g. "MyTicketZM". */
  product: string;
  /** Console label shown beside the name, e.g. "Organizer". */
  console?: string;
  title: string;
  description?: ReactNode;
  children: ReactNode;
  /** Small print under the card content (terms, switch app). */
  footer?: ReactNode;
}

/**
 * Centered 400px card for sign-in, OTP and application-start pages. Renders the
 * page's single h1 (the title).
 */
export function AuthLayout({ product, console: consoleLabel, title, description, children, footer }: AuthLayoutProps) {
  return (
    <main className="m3-auth">
      <div className="m3-auth__card">
        <div className="m3-auth__brand">
          <Brand />
          <span>
            {product} {consoleLabel ? <small>{consoleLabel}</small> : null}
          </span>
        </div>
        <h1 className="m3-auth__title">{title}</h1>
        {description ? <p className="m3-page-sub">{description}</p> : null}
        {children}
        {footer ? <div className="m3-page-sub">{footer}</div> : null}
      </div>
    </main>
  );
}
