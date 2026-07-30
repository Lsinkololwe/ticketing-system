/**
 * Zambian Kwacha formatting — MyTicketZM Design System.
 *
 * DS rule (Content Fundamentals): local currency is ALWAYS prefixed with the
 * Kwacha symbol and a space — `K 125,430` — never `ZMW` or `$`. Amounts use
 * grouped thousands and tabular figures so stat cards / tables don't jitter.
 *
 * Pair these strings with the `.amount` class (Fira Code, tabular-nums) defined
 * in global.css whenever they appear in a table, stat tile, or order summary.
 */

const KWACHA = 'K';

/**
 * Format a Kwacha amount as `K 1,500` (no decimals by default — ticket prices
 * and totals in this market are whole Kwacha).
 */
export function formatKwacha(amount: number, options?: { decimals?: boolean }): string {
  const decimals = options?.decimals ?? false;
  const value = new Intl.NumberFormat('en-ZM', {
    minimumFractionDigits: decimals ? 2 : 0,
    maximumFractionDigits: decimals ? 2 : 0,
  }).format(Number.isFinite(amount) ? amount : 0);
  return `${KWACHA} ${value}`;
}

/** Format with two decimal places — for fees and per-unit amounts. */
export function formatKwachaPrecise(amount: number): string {
  return formatKwacha(amount, { decimals: true });
}

export { KWACHA };
