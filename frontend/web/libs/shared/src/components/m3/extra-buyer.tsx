'use client';

import { useMemo, type ReactNode } from 'react';
import { qrMatrix } from './qr';
import { cx } from './utils';

export interface QrCodeProps {
  /** The text a gate scanner reads (ticket code or validation URL). */
  value: string;
  label?: string;
  className?: string;
}

/**
 * A real, scannable QR code (not a placeholder pattern). Dark modules use currentColor on a white
 * quiet zone so it scans in dark mode too. Size comes from `.m3-qr` (`--m3-site-qr`).
 */
export function QrCode({ value, label = 'Ticket QR code', className }: QrCodeProps) {
  const { d, size } = useMemo(() => {
    const m = qrMatrix(value);
    const quiet = 2;
    let path = '';
    m.forEach((row, y) =>
      row.forEach((dark, x) => {
        if (dark) path += `M${x + quiet} ${y + quiet}h1v1h-1z`;
      })
    );
    return { d: path, size: m.length + quiet * 2 };
  }, [value]);
  return (
    <svg className={cx('m3-qr', className)} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={label} shapeRendering="crispEdges">
      <rect width={size} height={size} fill="var(--m3-white)" />
      <path d={d} fill="currentColor" />
    </svg>
  );
}

export interface GateFallbackProps {
  code: string;
  /** Short line telling the holder what the gate will ask for when the QR does not scan. */
  children?: ReactNode;
}

/** Ticket code and ID-check line shown under the QR for gates where scanning fails. */
export function GateFallback({ code, children }: GateFallbackProps) {
  return (
    <div className="m3-stack" style={{ alignItems: 'center' }}>
      <span className="m3-mono" aria-label="Ticket code">
        {code}
      </span>
      <small className="m3-muted">{children ?? 'If the QR will not scan, give staff this ticket code and show a photo ID.'}</small>
    </div>
  );
}
