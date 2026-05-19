/** @type {import('tailwindcss').Config} */
const config = {
  content: [
    './{src,pages,components,app}/**/*.{ts,tsx,js,jsx,html}',
    '!./{src,pages,components,app}/**/*.{stories,spec}.{ts,tsx,js,jsx,html}',
  ],
  theme: {
    extend: {
      colors: {
        // Custom color variables - these reference Radix theme CSS variables
        primary: {
          DEFAULT: 'var(--accent-9)',
          dark: 'var(--accent-11)',
          light: 'var(--accent-7)',
        },
        secondary: {
          DEFAULT: 'var(--green-9)',
          dark: 'var(--green-11)',
          light: 'var(--green-7)',
        },
      },
      fontFamily: {
        sans: ['Space Grotesk', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif'],
        mono: ['Fira Code', 'monospace'],
      },
    },
  },
  plugins: [],
};

export default config;
