import { fireEvent, screen } from '@testing-library/react';

/** Opens a row's overflow menu (RowActions) and returns its menu item. */
export function menuItem(trigger: string | RegExp, item: string | RegExp) {
  // Close any menu left open by a previous call.
  if (screen.queryByRole('menu')) fireEvent.keyDown(screen.getByRole('menu'), { key: 'Escape' });
  fireEvent.click(screen.getByRole('button', { name: trigger }));
  return screen.getByRole('menuitem', { name: item });
}
/** Opens the row's overflow menu and chooses an item. */
export function menuAction(trigger: string | RegExp, item: string | RegExp) {
  fireEvent.click(menuItem(trigger, item));
}
