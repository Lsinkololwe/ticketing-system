/** @type {import('tailwindcss').Config} */

/**
 * Admin Tailwind Config — Radix UI Themes token bridge.
 *
 * Colors resolve to CSS variables (NOT literal hex) so the whole palette
 * follows the Radix accent set by <Theme accentColor="teal"> in Providers.tsx.
 * The `--brand-*`, `--surface-*`, `--content-*` and status vars are aliased to
 * Radix tokens in `src/app/global.css` (single source of truth), matching the
 * organization-admin app.
 */

const shadows = {
  xs: 'var(--shadow-xs)',
  sm: 'var(--shadow-sm)',
  md: 'var(--shadow-md)',
  lg: 'var(--shadow-lg)',
  xl: 'var(--shadow-xl)',
  card: 'var(--card-shadow)',
  'card-hover': 'var(--card-shadow-hover)',
  elevated: 'var(--shadow-elevated)',
  dropdown: 'var(--shadow-dropdown)',
  modal: 'var(--shadow-modal)',
};

const config = {
  content: [
    './{src,pages,components,app}/**/*.{ts,tsx,js,jsx,html}',
    '!./{src,pages,components,app}/**/*.{stories,spec}.{ts,tsx,js,jsx,html}',
    '../../libs/shared/src/**/*.{ts,tsx,js,jsx,html}',
  ],

  important: false,

  darkMode: 'class',

  theme: {
    extend: {
      colors: {
        // Surface colors → Radix tokens (via global.css aliases)
        'surface-primary': 'var(--surface-primary)',
        'surface-secondary': 'var(--surface-secondary)',
        'surface-tertiary': 'var(--surface-tertiary)',
        'surface-hover': 'var(--surface-hover)',
        'surface-border': 'var(--surface-border)',
        'surface-border-light': 'var(--surface-border-light)',

        // Content colors → Radix gray scale
        'content-primary': 'var(--content-primary)',
        'content-secondary': 'var(--content-secondary)',
        'content-tertiary': 'var(--content-tertiary)',
        'content-muted': 'var(--content-muted)',
        'content-inverse': 'var(--content-inverse)',

        // Brand colors → Radix accent (teal)
        brand: {
          50: 'var(--brand-50)',
          100: 'var(--brand-100)',
          200: 'var(--brand-200)',
          300: 'var(--brand-300)',
          400: 'var(--brand-400)',
          500: 'var(--brand-500)',
          600: 'var(--brand-600)',
          700: 'var(--brand-700)',
        },

        // Status colors → Radix semantic scales
        success: {
          50: 'var(--success-50)',
          100: 'var(--success-100)',
          500: 'var(--success-500)',
          600: 'var(--success-600)',
        },
        warning: {
          50: 'var(--warning-50)',
          100: 'var(--warning-100)',
          500: 'var(--warning-500)',
          600: 'var(--warning-600)',
        },
        danger: {
          50: 'var(--danger-50)',
          100: 'var(--danger-100)',
          500: 'var(--danger-500)',
          600: 'var(--danger-600)',
        },

        // Direct Radix scales (for components that need raw tokens)
        accent: {
          1: 'var(--accent-1)',
          2: 'var(--accent-2)',
          3: 'var(--accent-3)',
          4: 'var(--accent-4)',
          5: 'var(--accent-5)',
          6: 'var(--accent-6)',
          7: 'var(--accent-7)',
          8: 'var(--accent-8)',
          9: 'var(--accent-9)',
          10: 'var(--accent-10)',
          11: 'var(--accent-11)',
          12: 'var(--accent-12)',
        },
        gray: {
          1: 'var(--gray-1)',
          2: 'var(--gray-2)',
          3: 'var(--gray-3)',
          4: 'var(--gray-4)',
          5: 'var(--gray-5)',
          6: 'var(--gray-6)',
          7: 'var(--gray-7)',
          8: 'var(--gray-8)',
          9: 'var(--gray-9)',
          10: 'var(--gray-10)',
          11: 'var(--gray-11)',
          12: 'var(--gray-12)',
        },
      },

      fontFamily: {
        sans: ['var(--font-sans)', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
        mono: ['var(--font-mono)', 'Menlo', 'Monaco', 'monospace'],
      },

      boxShadow: shadows,

      borderRadius: {
        sm: 'var(--radius-sm)',
        md: 'var(--radius-md)',
        lg: 'var(--radius-lg)',
        xl: 'var(--radius-xl)',
        '2xl': 'var(--radius-2xl)',
        '3xl': 'var(--radius-3xl)',
        full: '9999px',
        1: 'var(--radius-1)',
        2: 'var(--radius-2)',
        3: 'var(--radius-3)',
        4: 'var(--radius-4)',
        5: 'var(--radius-5)',
        6: 'var(--radius-6)',
      },

      transitionDuration: {
        fast: 'var(--transition-fast)',
        default: 'var(--transition-default)',
        slow: 'var(--transition-slow)',
      },

      spacing: {
        1: 'var(--space-1)',
        2: 'var(--space-2)',
        3: 'var(--space-3)',
        4: 'var(--space-4)',
        5: 'var(--space-5)',
        6: 'var(--space-6)',
        7: 'var(--space-7)',
        8: 'var(--space-8)',
        9: 'var(--space-9)',
      },

      keyframes: {
        fadeIn: {
          from: { opacity: '0', transform: 'translateY(-4px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        fadeOut: {
          from: { opacity: '1' },
          to: { opacity: '0' },
        },
        slideDown: {
          from: { height: '0', opacity: '0' },
          to: { height: 'var(--radix-accordion-content-height)', opacity: '1' },
        },
        slideUp: {
          from: { height: 'var(--radix-accordion-content-height)', opacity: '1' },
          to: { height: '0', opacity: '0' },
        },
        scaleIn: {
          from: { opacity: '0', transform: 'scale(0.95)' },
          to: { opacity: '1', transform: 'scale(1)' },
        },
        pulse: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.5' },
        },
      },
      animation: {
        fadeIn: 'fadeIn 0.2s ease-out',
        fadeOut: 'fadeOut 0.15s ease-in',
        slideDown: 'slideDown 0.3s ease-out',
        slideUp: 'slideUp 0.3s ease-in',
        scaleIn: 'scaleIn 0.2s ease-out',
        pulse: 'pulse 1.5s ease-in-out infinite',
      },
    },
  },

  plugins: [
    require('tailwindcss-radix')({
      variantPrefix: 'radix',
    }),
  ],
};

export default config;
