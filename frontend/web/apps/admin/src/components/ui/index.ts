/**
 * Admin Portal UI kit — MyTicketZM Design System.
 *
 * The §7 contract components. Their prop signatures are enforced by the design
 * system's adherence lint and must match the spec exactly — no extra props.
 * @see docs/MYTICKETZM_DESIGN_SYSTEM.md
 */

// --- Core --------------------------------------------------------------------
export { Button, type ButtonProps } from './Button';
export { Badge, type BadgeProps } from './Badge';
export { Input, type InputProps } from './Input';
export { Textarea, type TextareaProps } from './Textarea';
export { Checkbox, type CheckboxProps } from './Checkbox';
export { Radio, type RadioProps } from './Radio';

// --- Data display ------------------------------------------------------------
export { StyledCard, type StyledCardProps } from './StyledCard';
export { StatCard, type StatCardProps } from './StatCard';
export { QuickActionCard, type QuickActionCardProps } from './QuickActionCard';
export { EmptyState, type EmptyStateProps } from './EmptyState';

// --- Navigation --------------------------------------------------------------
export { PageHeader, type PageHeaderProps, type Breadcrumb } from './PageHeader';
export { SidebarNavItem, type SidebarNavItemProps } from './SidebarNavItem';

// --- Feedback ----------------------------------------------------------------
export { Toast, type ToastProps, type ToastVariant } from './Toast';

// --- Local helpers (not §7 contract components) ------------------------------
export { Amount, type AmountProps } from './Amount';
export {
  SectionCard,
  type SectionCardProps,
  InfoCard,
  type InfoCardProps,
  MetricRow,
  type MetricRowProps,
} from './Panel';
export { PagePlaceholder } from './PagePlaceholder';
export {
  ThemeToggleButton,
  ThemeToggleDropdown,
  ThemeSegmentedControl,
  ThemeSelector,
} from './ThemeToggle';
