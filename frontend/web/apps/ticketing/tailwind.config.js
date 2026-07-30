/** @type {import('tailwindcss').Config} */

/**
 * Radix Themes + Tailwind — theme configuration.
 *
 * Best practice: Radix Themes is the single source of truth for the palette. It
 * publishes the live 12-step accent/gray/semantic scales as CSS custom properties
 * on `.radix-themes` (respecting `accentColor="iris"` / `grayColor="slate"` and
 * the light/dark appearance). Tailwind therefore CONSUMES those CSS vars instead
 * of hardcoding hex — `bg-accent-9` (Tailwind) and `color="iris"` (Radix) resolve
 * to the same token and both react to theme/appearance changes.
 *
 * Preflight is intentionally LEFT ENABLED: Tailwind injects it into the `base`
 * cascade layer, while `@radix-ui/themes/styles.css` is unlayered and so always
 * wins the cascade for elements Radix styles — there is no reset war. Preflight
 * still normalises the raw `html/body/a/img` that Radix does not scope.
 *
 * `darkMode: 'class'` matches next-themes (`attribute="class"`) and the DS `.dark`
 * token overrides, keeping Tailwind `dark:` variants in lockstep with Radix.
 */

/** Build a 1..12 (+ a1..a12 alpha) scale that points at Radix CSS vars. */
const radixScale = (name) => {
  const scale = {};
  for (let i = 1; i <= 12; i += 1) {
    scale[i] = `var(--${name}-${i})`;
    scale[`a${i}`] = `var(--${name}-a${i})`;
  }
  return scale;
};

const config = {
  content: [
    './{src,pages,components,app}/**/*.{ts,tsx,js,jsx,html}',
    '!./{src,pages,components,app}/**/*.{stories,spec}.{ts,tsx,js,jsx,html}',
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Full Radix runtime scales (accent = iris, gray = slate) + the semantic
        // scales the DS uses. All resolve to the live `.radix-themes` CSS vars.
        accent: radixScale('accent'),
        gray: radixScale('gray'),
        iris: radixScale('iris'),
        jade: radixScale('jade'),
        copper: radixScale('copper'),
        green: radixScale('green'),
        amber: radixScale('amber'),
        red: radixScale('red'),
        blue: radixScale('blue'),

        // MyTicketZM Design System role tokens (style THROUGH these).
        // brand = iris (identity), money = jade (commerce), highlight = copper.
        // In this app iris IS the Radix accent, so brand routes through
        // --accent-* / --color-secondary-* — both declared by the shared token
        // layer. There is no app-local --color-brand-* scale to drift from.
        brand: {
          DEFAULT: 'var(--color-secondary)',
          hover: 'var(--color-secondary-hover)',
          text: 'var(--color-secondary-text)',
          surface: 'var(--color-secondary-surface)',
        },
        money: {
          DEFAULT: 'var(--color-money)',
          hover: 'var(--color-money-hover)',
          text: 'var(--color-money-text)',
          surface: 'var(--color-money-surface)',
        },
        highlight: {
          DEFAULT: 'var(--color-highlight)',
          text: 'var(--color-highlight-text)',
          surface: 'var(--color-highlight-surface)',
        },

        // Surface aliases → Radix panel/background vars (theme-aware).
        surface: 'var(--color-surface)',
        panel: 'var(--color-panel-solid)',

        // Back-compat aliases (primary = brand identity, secondary = money).
        primary: {
          DEFAULT: 'var(--accent-9)',
          dark: 'var(--accent-11)',
          light: 'var(--accent-7)',
        },
        secondary: {
          DEFAULT: 'var(--jade-9)',
          dark: 'var(--jade-11)',
          light: 'var(--jade-7)',
        },
      },
      // Body is Inter; Space Grotesk is the ticketing DISPLAY face (headings
      // only) per the design system. Both resolve through the shared token
      // layer so Tailwind and Radix Themes never disagree.
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif'],
        display: ['Space Grotesk', 'Inter', 'system-ui', 'sans-serif'],
        mono: ['Fira Code', 'ui-monospace', 'monospace'],
      },
      borderRadius: {
        bento: 'var(--card-radius-bento)',
        card: 'var(--card-radius)',
      },
    },
  },
  plugins: [],
};

export default config;
