/**
 * Centralized Theme Configuration for Radix Themes
 *
 * This module provides a unified theme configuration that can be used
 * across all applications in the monorepo.
 *
 * @see https://www.radix-ui.com/themes/docs/theme/color
 * @see https://www.radix-ui.com/colors
 */

// =============================================================================
// RADIX ACCENT COLORS - All available accent colors
// =============================================================================

/**
 * All available accent colors in Radix Themes
 * @see https://www.radix-ui.com/themes/docs/theme/color#accents
 */
export const accentColors = [
  'gray',
  'gold',
  'bronze',
  'brown',
  'yellow',
  'amber',
  'orange',
  'tomato',
  'red',
  'ruby',
  'crimson',
  'pink',
  'plum',
  'purple',
  'violet',
  'iris',
  'indigo',
  'blue',
  'cyan',
  'teal',
  'jade',
  'green',
  'grass',
  'lime',
  'mint',
  'sky',
] as const;

export type AccentColor = (typeof accentColors)[number];

// =============================================================================
// RADIX GRAY COLORS - All available gray colors
// =============================================================================

/**
 * All available gray colors in Radix Themes
 * @see https://www.radix-ui.com/themes/docs/theme/color#grays
 */
export const grayColors = [
  'gray',
  'mauve',
  'slate',
  'sage',
  'olive',
  'sand',
] as const;

export type GrayColor = (typeof grayColors)[number];

// =============================================================================
// COLOR SCALES - Radix uses 12-step color scales
// =============================================================================

/**
 * Color scale steps in Radix
 * Steps 1-12 serve different purposes:
 * 1-2: Backgrounds
 * 3-5: Interactive components
 * 6-8: Borders and separators
 * 9-10: Solid colors
 * 11-12: Text
 */
export const colorScaleSteps = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] as const;
export type ColorScaleStep = (typeof colorScaleSteps)[number];

// =============================================================================
// SEMANTIC COLORS - Mapping for status and intent
// =============================================================================

/**
 * Semantic color mappings using Radix color names
 * @see https://www.radix-ui.com/colors/docs/overview/aliasing
 */
export const semanticColors = {
  // Status colors
  success: 'green' as AccentColor,
  warning: 'orange' as AccentColor,
  error: 'red' as AccentColor,
  danger: 'red' as AccentColor,
  info: 'blue' as AccentColor,

  // Neutral
  neutral: 'gray' as AccentColor,

  // Brand - defaults
  primary: 'blue' as AccentColor,
  secondary: 'gray' as AccentColor,
  accent: 'iris' as AccentColor,
} as const;

export type SemanticColor = keyof typeof semanticColors;

// =============================================================================
// BRAND COLORS - Custom brand color values
// =============================================================================

/**
 * Brand colors for the application
 * These can be used for custom styling beyond Radix components
 */
export const brandColors = {
  primary: {
    default: '#3B82F6', // blue-500
    dark: '#2563EB',    // blue-600
    light: '#60A5FA',   // blue-400
    foreground: '#FFFFFF',
  },
  secondary: {
    default: '#6B7280', // gray-500
    dark: '#4B5563',    // gray-600
    light: '#9CA3AF',   // gray-400
    foreground: '#FFFFFF',
  },
  success: {
    default: '#22C55E', // green-500
    dark: '#16A34A',    // green-600
    light: '#4ADE80',   // green-400
    foreground: '#FFFFFF',
  },
  warning: {
    default: '#F59E0B', // amber-500
    dark: '#D97706',    // amber-600
    light: '#FBBF24',   // amber-400
    foreground: '#FFFFFF',
  },
  error: {
    default: '#EF4444', // red-500
    dark: '#DC2626',    // red-600
    light: '#F87171',   // red-400
    foreground: '#FFFFFF',
  },
} as const;

// =============================================================================
// RADIX THEME CONFIGURATION
// =============================================================================

/**
 * Radix Theme component configuration
 * @see https://www.radix-ui.com/themes/docs/theme/overview
 */
export const radixThemeConfig = {
  // Accent color - main interactive color
  accentColor: 'blue' as AccentColor,
  // Gray color scale for neutral elements
  grayColor: 'slate' as GrayColor,
  // Panel background style ('solid' | 'translucent')
  panelBackground: 'solid' as const,
  // Global scaling factor
  scaling: '100%' as const,
  // Border radius ('none' | 'small' | 'medium' | 'large' | 'full')
  radius: 'medium' as const,
  // Default appearance
  appearance: 'light' as const,
} as const;

// =============================================================================
// TYPOGRAPHY
// =============================================================================

/**
 * Typography configuration
 * Inter font for the GMAX bento design system
 */
export const typography = {
  fonts: {
    sans: '"Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    mono: '"Fira Code", "JetBrains Mono", Menlo, Monaco, monospace',
  },
  weights: {
    normal: '400',
    medium: '500',
    semibold: '600',
    bold: '700',
  },
  // Visual hierarchy presets (GMAX)
  presets: {
    // Navigation
    navSection: { fontSize: '0.625rem', fontWeight: '600', textTransform: 'uppercase' as const },
    navItem: { fontSize: '0.875rem', fontWeight: '500' },
    // Cards
    cardTitle: { fontSize: '0.875rem', fontWeight: '600' },
    cardSubtitle: { fontSize: '0.75rem', fontWeight: '400' },
    // Stats
    statValue: { fontSize: '1.5rem', fontWeight: '600' },
    statLabel: { fontSize: '0.75rem', fontWeight: '500' },
    // Forms
    label: { fontSize: '0.75rem', fontWeight: '500', textTransform: 'uppercase' as const },
    input: { fontSize: '0.875rem', fontWeight: '400' },
    // Content
    body: { fontSize: '0.875rem', fontWeight: '400' },
    meta: { fontSize: '0.6875rem', fontWeight: '400' },
  },
} as const;

// =============================================================================
// SPACING
// =============================================================================

/**
 * Spacing scale (matches Radix Themes)
 */
export const spacing = {
  0: '0',
  1: '4px',
  2: '8px',
  3: '12px',
  4: '16px',
  5: '24px',
  6: '32px',
  7: '40px',
  8: '48px',
  9: '64px',
} as const;

// =============================================================================
// BREAKPOINTS
// =============================================================================

/**
 * Responsive breakpoints
 */
export const breakpoints = {
  initial: '0px',
  xs: '520px',
  sm: '768px',
  md: '1024px',
  lg: '1280px',
  xl: '1640px',
} as const;

// =============================================================================
// COMPONENT SIZES
// =============================================================================

/**
 * Component size presets following Radix conventions
 * Radix uses 1-4 or 1-5 scale for most components
 */
export const componentSizes = {
  button: {
    sm: '1' as const,
    md: '2' as const,
    lg: '3' as const,
  },
  input: {
    sm: '1' as const,
    md: '2' as const,
    lg: '3' as const,
  },
  avatar: {
    xs: '1' as const,
    sm: '2' as const,
    md: '3' as const,
    lg: '4' as const,
    xl: '5' as const,
  },
  badge: {
    sm: '1' as const,
    md: '2' as const,
  },
  icon: {
    sm: 16,
    md: 20,
    lg: 24,
  },
} as const;

// =============================================================================
// COMPONENT VARIANTS
// =============================================================================

/**
 * Component variant mappings for Radix components
 */
export const variants = {
  button: {
    primary: { variant: 'solid' as const, color: 'blue' as AccentColor },
    secondary: { variant: 'outline' as const, color: 'gray' as AccentColor },
    ghost: { variant: 'ghost' as const, color: 'gray' as AccentColor },
    danger: { variant: 'solid' as const, color: 'red' as AccentColor },
    success: { variant: 'solid' as const, color: 'green' as AccentColor },
    soft: { variant: 'soft' as const, color: 'blue' as AccentColor },
  },
  badge: {
    success: { variant: 'soft' as const, color: 'green' as AccentColor },
    warning: { variant: 'soft' as const, color: 'orange' as AccentColor },
    danger: { variant: 'soft' as const, color: 'red' as AccentColor },
    info: { variant: 'soft' as const, color: 'blue' as AccentColor },
    neutral: { variant: 'soft' as const, color: 'gray' as AccentColor },
  },
  card: {
    default: 'surface' as const,
    elevated: 'classic' as const,
    ghost: 'ghost' as const,
  },
} as const;

// =============================================================================
// CSS VARIABLES
// =============================================================================

/**
 * CSS custom properties for extending Radix styling
 * These are injected into global.css
 */
export const cssVariables = `
  :root {
    /* Surface colors */
    --surface-primary: #FFFFFF;
    --surface-secondary: #F8FAFC;
    --surface-tertiary: #F1F5F9;
    --surface-hover: #E2E8F0;
    --surface-border: #E5E7EB;

    /* Content colors */
    --content-primary: #1F2937;
    --content-secondary: #4B5563;
    --content-tertiary: #6B7280;
    --content-muted: #9CA3AF;
    --content-inverse: #FFFFFF;

    /* Brand colors */
    --brand-primary: ${brandColors.primary.default};
    --brand-primary-dark: ${brandColors.primary.dark};
    --brand-primary-light: ${brandColors.primary.light};
    --brand-secondary: ${brandColors.secondary.default};
    --brand-success: ${brandColors.success.default};
    --brand-warning: ${brandColors.warning.default};
    --brand-error: ${brandColors.error.default};

    /* Typography */
    --font-sans: ${typography.fonts.sans};
    --font-mono: ${typography.fonts.mono};

    /* Shadows - GMAX subtle shadows */
    --shadow-card: 0 1px 3px 0 rgb(0 0 0 / 0.04), 0 1px 2px -1px rgb(0 0 0 / 0.02);
    --shadow-card-hover: 0 4px 12px 0 rgb(0 0 0 / 0.06), 0 2px 4px -1px rgb(0 0 0 / 0.03);
    --shadow-dropdown: 0 4px 16px 0 rgb(0 0 0 / 0.08), 0 2px 4px 0 rgb(0 0 0 / 0.04);
    --shadow-modal: 0 25px 50px -12px rgb(0 0 0 / 0.15);
  }

  /* Dark mode overrides */
  .dark {
    --surface-primary: #1F2937;
    --surface-secondary: #111827;
    --surface-tertiary: #374151;
    --surface-hover: #4B5563;
    --surface-border: #374151;

    --content-primary: #F9FAFB;
    --content-secondary: #E5E7EB;
    --content-tertiary: #9CA3AF;
    --content-muted: #6B7280;
    --content-inverse: #111827;
  }
`;

// =============================================================================
// EXPORTS
// =============================================================================

/**
 * Radix Theme props interface
 */
export interface RadixThemeProps {
  accentColor: AccentColor;
  grayColor: GrayColor;
  panelBackground: 'solid' | 'translucent';
  scaling: '90%' | '95%' | '100%' | '105%' | '110%';
  radius: 'none' | 'small' | 'medium' | 'large' | 'full';
}

/**
 * Get Radix Theme component props
 */
export const getRadixThemeProps = (): RadixThemeProps => ({
  accentColor: radixThemeConfig.accentColor,
  grayColor: radixThemeConfig.grayColor,
  panelBackground: radixThemeConfig.panelBackground,
  scaling: radixThemeConfig.scaling,
  radius: radixThemeConfig.radius,
});

/**
 * Get variant config for a component
 */
export function getButtonVariant(variant: keyof typeof variants.button) {
  return variants.button[variant];
}

export function getBadgeVariant(variant: keyof typeof variants.badge) {
  return variants.badge[variant];
}

/**
 * Theme context type
 */
export interface ThemeContextType {
  theme: 'light' | 'dark' | 'system';
  setTheme: (theme: 'light' | 'dark' | 'system') => void;
  resolvedTheme: 'light' | 'dark';
}

// Default export
export default {
  accentColors,
  grayColors,
  colorScaleSteps,
  semanticColors,
  brandColors,
  radixThemeConfig,
  typography,
  spacing,
  breakpoints,
  componentSizes,
  variants,
  cssVariables,
  getRadixThemeProps,
  getButtonVariant,
  getBadgeVariant,
};
