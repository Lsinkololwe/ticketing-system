import type { Page } from '@playwright/test';

/**
 * The brand contract for one app, read off the running document.
 *
 * <h2>Why resolved values and not the stylesheet</h2>
 * Asserting that `design-tokens.css` contains the right declaration proves the
 * file is correct, which was never in doubt. What breaks is the wiring: a
 * missing `data-brand`, a stylesheet that did not load, a second rule later in
 * the cascade. All of those leave the source file untouched and change what the
 * user sees, so the assertion has to come from `getComputedStyle` on a real
 * page.
 */
export interface BrandContract {
  /** `<html data-brand="…">` — the switch every token below hangs off. */
  brand: string | null;
  /** `--accent-9`, the accent's solid step. */
  accent: string;
  /** The palette step this app's accent is supposed to equal. */
  expectedAccentSource: string;
  /** `--font-display`, the display token. Ticketing sets headings in it directly. */
  displayFont: string;
  /**
   * The family Radix Themes actually sets headings in (`--heading-font-family`, resolved on the
   * Theme container). The consoles point it at `--font-sans`, so this — not `--font-display` —
   * is what a console's headings render in.
   */
  headingFont: string;
  /** `--font-sans`, used for body text. */
  bodyFont: string;
  /** `--font-mono`, used for tabular figures and identifiers. */
  monoFont: string;
}

/**
 * Fails with a diagnosis if the browser is no longer on the app under test.
 *
 * <h2>Why this guard exists</h2>
 * An app whose Keycloak provider redirects to the identity server on
 * load, so a spec that navigates and reads tokens ends up measuring
 * <em>Keycloak's</em> themed login page. That page is a valid HTML document
 * with its own token sheet, so every assertion still runs — and the failure
 * reads as "the accent is wrong" when the truth is "you are on a different
 * site". Naming it here turns a confusing assertion failure into a statement of
 * what happened.
 */
export async function assertStillOnTheApp(page: Page, baseUrl: string): Promise<void> {
  const current = new URL(page.url());
  const expected = new URL(baseUrl);

  if (current.host !== expected.host) {
    const portal = await page.evaluate(() =>
      document.documentElement.getAttribute('data-portal')
    );
    throw new Error(
      `Navigated away from ${expected.host} to ${current.host}` +
        (portal ? ` (a Keycloak theme: data-portal="${portal}")` : '') +
        '. The app redirected to the identity server, so nothing below is ' +
        'measuring the app. Sign in first, or make the route public.'
    );
  }
}

/**
 * Reads the resolved brand contract.
 *
 * <h2>Two elements, because the tokens genuinely live in two places</h2>
 * `data-brand` and the palette (`--iris-9`, `--teal-9`) are declared on
 * `<html>`, where the design system's sheet puts them. The <b>resolved accent</b>
 * (`--accent-9`) is not: Radix Themes emits it on its own `.radix-themes`
 * container, so reading it from `documentElement` returns an empty string on a
 * perfectly correct page — which reads as "the token sheet failed to load" and
 * sends you looking for the wrong bug entirely.
 *
 * <p>Falls back to `<html>` when no Theme container is present, so an app that
 * does not use Radix still gets a meaningful reading rather than a crash.</p>
 */
export async function readBrandContract(
  page: Page,
  expectedAccentSource: string
): Promise<BrandContract> {
  return page.evaluate((accentSource) => {
    const root = document.documentElement;
    const theme = document.querySelector('.radix-themes') ?? root;

    const fromRoot = (token: string) =>
      getComputedStyle(root).getPropertyValue(token).trim();
    const fromTheme = (token: string) =>
      getComputedStyle(theme).getPropertyValue(token).trim();

    return {
      brand: root.getAttribute('data-brand'),
      accent: fromTheme('--accent-9'),
      expectedAccentSource: fromTheme(accentSource),
      displayFont: fromRoot('--font-display'),
      headingFont: fromTheme('--heading-font-family'),
      bodyFont: fromRoot('--font-sans'),
      monoFont: fromRoot('--font-mono'),
    };
  }, expectedAccentSource);
}

/**
 * The three fonts the design system provides. Nothing else may appear in a
 * font stack — see `_adherence.oxlintrc.json`, which enforces the same set.
 */
export const PERMITTED_FONTS = ['Inter', 'Space Grotesk', 'Fira Code'] as const;

/**
 * Whether a font stack <b>begins</b> with one of the three permitted families.
 *
 * <h2>Only the first family, matching the design system's own rule</h2>
 * `_adherence.oxlintrc.json` tests what follows `font-family:` with a negative
 * lookahead for `Inter|Space Grotesk|Fira Code` — so it constrains the family
 * that will actually be used and says nothing about the fallbacks behind it.
 *
 * <p>That is the right shape. A real stack reads
 * `'Space Grotesk', 'Inter', -apple-system, sans-serif`: everything after the
 * first entry is what the browser reaches for when the webfont has not loaded,
 * which is a resilience decision rather than a design one. A checker that
 * required every entry to be a design-system family would reject the platform's
 * own tokens — and being stricter than the authority is its own kind of wrong.</p>
 */
export function firstFamilyIsPermitted(fontStack: string): boolean {
  const first = fontStack
    .split(',')[0]
    ?.trim()
    .replace(/^["']|["']$/g, '');

  return !!first && (PERMITTED_FONTS as readonly string[]).includes(first);
}
