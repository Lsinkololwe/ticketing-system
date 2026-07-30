/**
 * MyTicketZM ticketing — design-system primitives.
 *
 * Core primitives (Button / Badge / Input / Textarea / Checkbox / Radio /
 * StyledCard / EmptyState / Toast) implement the component contracts in §7 of
 * docs/MYTICKETZM_DESIGN_SYSTEM.md exactly — no extra props.
 *
 * App primitives (Money / MobileMoneyStrip / StatTile / BrandMark) encode the
 * customer-app rules the contracts do not cover: Kwacha formatting,
 * always-visible mobile money, bento stat tiles, and the plain wordmark.
 */

/* ---- Core (spec §7 contracts) ---- */
export { Button, type ButtonProps } from './Button';
export { Badge, type BadgeProps } from './Badge';
export { Input, type InputProps } from './Input';
export { Textarea, type TextareaProps } from './Textarea';
export { Checkbox, type CheckboxProps } from './Checkbox';
export { Radio, type RadioProps } from './Radio';
export { StyledCard, type StyledCardProps } from './StyledCard';
export { EmptyState, type EmptyStateProps } from './EmptyState';
export { Toast, type ToastProps } from './Toast';

/* ---- Customer-app primitives ---- */
export { Money, type MoneyProps } from './Money';
export { MobileMoneyStrip, type MobileMoneyStripProps } from './MobileMoneyStrip';
export { StatTile, type StatTileProps, type StatAccent } from './StatTile';
export { BrandMark, type BrandMarkProps } from './BrandMark';
