// const { createGlobPatternsForDependencies } = require('@nx/next/tailwind');

// The above utility import will not work if you are using Next.js' --turbo.
// Instead you will have to manually add the dependent paths to be included.
// For example
// ../libs/buttons/**/*.{ts,tsx,js,jsx,html}',                 <--- Adding a shared lib
// !../libs/buttons/**/*.{stories,spec}.{ts,tsx,js,jsx,html}', <--- Skip adding spec/stories files from shared lib

// If you are **not** using `--turbo` you can uncomment both lines 1 & 19.
// A discussion of the issue can be found: https://github.com/nrwl/nx/issues/26510

/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './{src,pages,components,app}/**/*.{ts,tsx,js,jsx,html}',
    '!./{src,pages,components,app}/**/*.{stories,spec}.{ts,tsx,js,jsx,html}',
//     ...createGlobPatternsForDependencies(__dirname)
  ],
  darkMode: 'class',
  theme: {
    extend: {
      // Token-bridged colors ONLY. The Tailwind default palette
      // (bg-emerald-500, text-gray-700, …) is banned by the MyTicketZM design
      // system — every color must resolve through a design token so brand
      // context and appearance switching work.
      colors: {
        // Radix accent scale (teal for org-admin) — use `bg-accent-9`,
        // `text-accent-11`, `bg-accent-a3`, …
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
          a2: 'var(--accent-a2)',
          a3: 'var(--accent-a3)',
          a5: 'var(--accent-a5)',
          a6: 'var(--accent-a6)',
          contrast: 'var(--accent-contrast)',
        },

        // Radix gray scale (slate). NOTE: this deliberately shadows Tailwind's
        // default `gray`, so `text-gray-11` resolves to the token and legacy
        // `text-gray-700` no longer compiles to a hardcoded hex.
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
          a3: 'var(--gray-a3)',
          a5: 'var(--gray-a5)',
          a6: 'var(--gray-a6)',
        },

        // Semantic role tokens
        primary: 'var(--color-primary)',
        secondary: 'var(--color-secondary)',
        highlight: 'var(--color-highlight)',
        // Jade — MONEY ONLY (prices, payouts, revenue, "paid").
        money: {
          DEFAULT: 'var(--color-money)',
          text: 'var(--color-money-text)',
          surface: 'var(--color-money-surface)',
        },

        // Surfaces
        'surface-primary': 'var(--surface-primary)',
        'surface-secondary': 'var(--surface-secondary)',
        'surface-tertiary': 'var(--surface-tertiary)',
        'surface-hover': 'var(--surface-hover)',
        'surface-border': 'var(--surface-border)',
        'surface-elevated': 'var(--surface-elevated)',
        'surface-subtle': 'var(--surface-subtle)',
        panel: 'var(--color-panel-solid)',
        canvas: 'var(--color-background)',

        // Content
        'content-primary': 'var(--content-primary)',
        'content-secondary': 'var(--content-secondary)',
        'content-tertiary': 'var(--content-tertiary)',
        'content-muted': 'var(--content-muted)',

        // Brand ramp → accent (legacy alias, kept resolving)
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

        // Status — generic semantics. `success` is GREEN; money is jade above.
        success: {
          50: 'var(--success-50)',
          100: 'var(--success-100)',
          500: 'var(--success-500)',
          600: 'var(--success-600)',
          text: 'var(--success-text)',
        },
        warning: {
          50: 'var(--warning-50)',
          100: 'var(--warning-100)',
          500: 'var(--warning-500)',
          600: 'var(--warning-600)',
          text: 'var(--warning-text)',
        },
        danger: {
          50: 'var(--danger-50)',
          100: 'var(--danger-100)',
          500: 'var(--danger-500)',
          600: 'var(--danger-600)',
          text: 'var(--danger-text)',
        },
        info: {
          50: 'var(--info-50)',
          100: 'var(--info-100)',
          500: 'var(--info-500)',
          600: 'var(--info-600)',
          text: 'var(--info-text)',
        },
      },
      fontFamily: {
        sans: ['var(--font-sans)', 'system-ui', 'sans-serif'],
        mono: ['var(--font-mono)', 'monospace'],
      },
      boxShadow: {
        'xs': 'var(--shadow-xs)',
        'sm': 'var(--shadow-sm)',
        'md': 'var(--shadow-md)',
        'lg': 'var(--shadow-lg)',
        'xl': 'var(--shadow-xl)',
        'card': 'var(--card-shadow)',
        'card-hover': 'var(--card-shadow-hover)',
        'elevated': 'var(--shadow-elevated)',
        'dropdown': 'var(--shadow-dropdown)',
        'modal': 'var(--shadow-modal)',
      },
      borderRadius: {
        // Radix radius scale. Buttons/inputs 6-8px (radius-3/4), standard cards
        // 8px, bento tiles 14px — so tiles read as a distinct language from
        // form controls.
        'sm': 'var(--radius-2)',
        'md': 'var(--radius-3)',
        'lg': 'var(--radius-4)',
        'xl': 'var(--radius-5)',
        '2xl': 'var(--radius-6)',
        'card': 'var(--card-radius)',
        'bento': 'var(--card-radius-bento)',
      },
      transitionDuration: {
        'fast': 'var(--transition-fast)',
        'default': 'var(--transition-default)',
        'slow': 'var(--transition-slow)',
      },
      // Toast animation keyframes
      keyframes: {
        'slide-in-from-top-full': {
          '0%': { transform: 'translateY(-100%)' },
          '100%': { transform: 'translateY(0)' },
        },
        'slide-in-from-bottom-full': {
          '0%': { transform: 'translateY(100%)' },
          '100%': { transform: 'translateY(0)' },
        },
        'slide-out-to-right-full': {
          '0%': { transform: 'translateX(0)' },
          '100%': { transform: 'translateX(100%)' },
        },
        'fade-out-80': {
          '0%': { opacity: '1' },
          '100%': { opacity: '0.8' },
        },
      },
      animation: {
        'in': 'slide-in-from-bottom-full 0.3s ease-out',
        'out': 'slide-out-to-right-full 0.3s ease-in',
      },
    },
  },
  plugins: [
    require('tailwindcss-animate'),
  ],
};
