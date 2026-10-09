/** The product mark (accent tile with a cut-out circle). Decorative; pair with the product name as text. */
export function Brand({ className }: { className?: string }) {
  // No import from the 'use client' utils module: this renders in Server Components (login, unauthorized).
  return <span className={className ? `m3-brandmark ${className}` : 'm3-brandmark'} aria-hidden="true" />;
}
