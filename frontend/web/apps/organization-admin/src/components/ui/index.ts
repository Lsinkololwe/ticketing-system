/**
 * MyTicketZM design system — Organization Admin component barrel.
 *
 * Every component here matches its declared contract in
 * `docs/MYTICKETZM_DESIGN_SYSTEM.md` §7 exactly — no extra props.
 *
 *   import { Button, Badge, StatCard, PageHeader, useToast } from '@/components/ui';
 */

// --- Core -------------------------------------------------------------------
export { Button } from './Button';
export type { ButtonProps, ButtonVariant, ButtonColor, ButtonSize } from './Button';

export { Badge } from './Badge';
export type { BadgeProps, BadgeColor, BadgeVariant, BadgeSize } from './Badge';

export { Input } from './Input';
export type { InputProps, InputVariant, InputSize } from './Input';

export { Textarea } from './Textarea';
export type { TextareaProps, TextareaVariant } from './Textarea';

export { Checkbox } from './Checkbox';
export type { CheckboxProps, CheckboxSize } from './Checkbox';

export { Radio } from './Radio';
export type { RadioProps, RadioSize } from './Radio';

// --- Data display -----------------------------------------------------------
export { StyledCard } from './StyledCard';
export type { StyledCardProps, CardPadding, CardHover } from './StyledCard';

export { StatCard } from './StatCard';
export type { StatCardProps, StatTrend } from './StatCard';

export { QuickActionCard } from './QuickActionCard';
export type { QuickActionCardProps } from './QuickActionCard';

export {
  EmptyState,
  NoEventsEmptyState,
  NoTeamMembersEmptyState,
  NoTransactionsEmptyState,
  NoSearchResultsEmptyState,
  NoPayoutsEmptyState,
  NoBankAccountsEmptyState,
  NoAttendeesEmptyState,
  NoAnalyticsEmptyState,
} from './EmptyState';
export type { EmptyStateProps, EmptyStateAction, EmptyStateSize } from './EmptyState';

// --- Navigation -------------------------------------------------------------
export { PageHeader } from './PageHeader';
export type { PageHeaderProps, Breadcrumb, PageAction } from './PageHeader';

export { SidebarNavItem } from './SidebarNavItem';
export type { SidebarNavItemProps } from './SidebarNavItem';

// --- Feedback ---------------------------------------------------------------
export { Toast, ToastProvider, ToastViewport, ToastRoot, ToastAction } from './Toast';
export type { ToastProps, ToastData, ToastVariant } from './Toast';

export { ToastContextProvider, useToast } from './useToast';
