const INVALID = '[aria-invalid="true"], [data-rhf-invalid="true"]';
const FOCUSABLE =
  'input:not([disabled]):not([type="hidden"]):not([type="file"]):not([tabindex="-1"]), select:not([disabled]), textarea:not([disabled]), button:not([disabled]):not([tabindex="-1"]), [tabindex="0"]';

/** Focus the first focusable control inside (or equal to) `el`. Returns whether something took focus. */
export function focusWithin(el: Element): boolean {
  const target = el.matches('input,select,textarea,button') ? (el as HTMLElement) : (el.querySelector(FOCUSABLE) as HTMLElement | null);
  if (!target) return false;
  target.focus();
  return true;
}

/** Focus the first invalid control under `root` (document order). Works for custom widgets via `data-rhf-invalid`. */
export function focusFirstInvalid(root: ParentNode | null | undefined): boolean {
  if (!root) return false;
  for (const el of Array.from(root.querySelectorAll(INVALID))) {
    if (focusWithin(el)) return true;
  }
  return false;
}
