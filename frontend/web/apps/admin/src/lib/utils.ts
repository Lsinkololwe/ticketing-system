/**
 * Utility Functions
 *
 * Following shadcn/ui best practices for utility functions.
 * @see https://ui.shadcn.com/docs/installation/manual
 */

import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Merge class names with Tailwind CSS conflict resolution
 *
 * Uses clsx for conditional class joining and tailwind-merge
 * to intelligently resolve Tailwind CSS class conflicts.
 *
 * @example
 * cn('px-2 py-1', condition && 'bg-red-500', 'px-4')
 * // Returns: 'py-1 bg-red-500 px-4' (px-4 overrides px-2)
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
