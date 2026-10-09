/**
 * TypeScript view of the M3 tokens that JavaScript needs.
 *
 * The CSS files are the source of truth for every measurement. Only values CSS
 * cannot share with JS live here: media-query breakpoints (variables are not
 * allowed inside @media), the app/scheme vocabulary, and the chart palette
 * references. `m3.tokens.test.ts` asserts these agree with the stylesheets.
 */

/** px breakpoints. Mirrors the @media rules in m3.components.css. */
export const m3Breakpoints = {
  /** <= this width: phone (bottom navigation, single column). */
  compact: 599,
  /** <= this width: tablet (narrow work area, 6-col form fields). */
  medium: 839,
  /** Storefront two-column to one-column switch. */
  siteStack: 820,
  /** Editor + preview side-by-side to stacked. */
  sheetSplit: 1180,
} as const;

/** Apps the theme layer knows. Set as `data-app` on <html>. */
export const M3_APPS = ['buyer', 'organizer', 'platform'] as const;
export type M3App = (typeof M3_APPS)[number];

/** Colour scheme preference. 'system' follows prefers-color-scheme. */
export type M3Scheme = 'system' | 'light' | 'dark';

/** Series colours for charts, validated against the surface in both schemes. */
export const m3ChartColors = [
  'var(--m3-chart-1)',
  'var(--m3-chart-2)',
  'var(--m3-chart-3)',
  'var(--m3-chart-4)',
  'var(--m3-chart-5)',
] as const;

/** Sequential 5-step ramp for heatmaps (light to strong). */
export const m3HeatColors = [
  'var(--m3-heat-1)',
  'var(--m3-heat-2)',
  'var(--m3-heat-3)',
  'var(--m3-heat-4)',
  'var(--m3-heat-5)',
] as const;

/** Tone vocabulary shared by pills, banners and snackbars. */
export type M3Tone = 'neutral' | 'info' | 'success' | 'warning' | 'error';

/** Attributes to spread on <html> for an app + scheme. */
export function m3HtmlAttributes(app: M3App, scheme: M3Scheme = 'system') {
  return scheme === 'system'
    ? ({ 'data-app': app } as const)
    : ({ 'data-app': app, 'data-theme': scheme } as const);
}

/** Map a backend status string onto a pill tone (platform-wide convention). */
export function m3StatusTone(status: string): M3Tone {
  const s = status.toLowerCase();
  if (/(paid|published|live|approved|active|completed|verified|success|open|resolved)/.test(s))
    return 'success';
  if (/(pending|scheduled|review|partial|processing|awaiting|changes)/.test(s)) return 'warning';
  if (/(cancel|refund|reject|fail|declin|expired|suspend|block|overdue|error)/.test(s)) return 'error';
  return 'neutral';
}
