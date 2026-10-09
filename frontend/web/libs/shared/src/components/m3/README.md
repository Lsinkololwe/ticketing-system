# M3 component library

Material Design 3 components shared by the buyer, organizer and platform-admin apps. No Radix, no Tailwind classes for design: styling is `m3.*.css` plus `data-*` attributes.

## Setup (once per app)
1. In the app's `global.css`: `@import '../../../../libs/shared/src/styles/m3.css';` (before Tailwind layers if Tailwind remains for layout utilities only).
2. On `<html>` set `data-app="buyer|organizer|platform"` (`m3HtmlAttributes(app, scheme)` returns the attributes). `data-theme="light|dark"` forces a scheme; without it `prefers-color-scheme` applies.
3. Wrap the tree in `SnackbarProvider`.
4. Import from `@pml.tickets/shared`. Component list and props: `API.md`.

## Rules
- Measurements and colours come only from `--m3-*` tokens (`styles/m3.tokens.css`, per-app overrides in `m3.themes.css`). Never write raw `px` or hex in app code or components; `styles/m3.lint.test.ts` fails the build if you do.
- Buyer density (48px buttons, 15px body) and console density (34px buttons, 14px body) differ by `data-app`, not by props.
- Table rows are never clickable. `DataTable` has no `onRowClick`; put `Button`/`IconButton`/`RowMenu` in the `rowActions` column.
- Every input needs a visible `label`; errors go in `errorText` (announced with `role=alert`); helper text in `helperText`.
- Icon-only controls need a `label` (`IconButton`, `Fab`). Icons are decorative unless given `label`.
- Status is always text plus tone (`StatusPill`); never colour alone. Charts always carry a "View as table" disclosure.
- Dialogs, side sheets and menus trap focus, close on Escape and return focus to the opener. Use `ConfirmDialog` for destructive confirmations; use `useSnackbar().show({ message, actionLabel: 'Undo', onAction })` for results.
- Errors from the API go through `ErrorState` (retry only when the server says `retryable`).
- Routing: pass your router link (e.g. `next/link`) as `linkAs` to navigation components and `EventCard`.
- Money: format with `formatKwacha` (`K 125,430`), absent value is an em dash, enums via `humanizeEnum`/`StatusPill`.
- New generic components go in a new file (`extra-<app>.tsx`), with tests, exported from `index.ts`. Do not fork a component inside an app.

## Tests
`__tests__/*.test.tsx` cover every export (jsdom, Testing Library). `styles/m3.tokens.test.ts` asserts the measurements taken from the prototype (button 34/28px console, 48/36px buyer, field 40/48px, rail 76px...). `styles/m3.lint.test.ts` enforces the no-raw-values rule.
