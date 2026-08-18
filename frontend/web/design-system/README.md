# MyTicketZM Design System — import record

Provenance and conformance notes for the design system imported from Claude Design.

- **Project:** `MyTicketZM Design System`
- **Design-system id:** `af157d5b-36fd-4e93-a690-0aa633d73e5c`
- **Imported from:** https://claude.ai/design/p/03cea541-469f-44d2-aa91-a5c6f5456295
- **Screen driving the org-admin redesign:** `Org Admin - Dashboard.dc.html`

## Where the tokens actually live

`libs/shared/src/styles/design-tokens.css` is the **runtime source of truth** for all
three apps. It is not a copy of the imported `tokens/*.css` — it is a better version of
them, and it should stay that way:

| | Imported `tokens/colors.css` | `libs/shared/src/styles/design-tokens.css` |
|---|---|---|
| Radix scales (gray/teal/iris/jade/status) | hand-generated **approximations** of the Radix curve | the **real** `@radix-ui/themes` scales, supplied at runtime by `<Theme>` |
| Copper scale | alpha ramp mixed down from already-pale steps | alpha ramp derived from the saturated `--copper-9`, matching Radix's own curve |
| Everything else (roles, type, spacing, effects, brand contexts) | same values | same values |

The imported readme says as much itself: *"Radix color scales are approximated, not
copied byte-for-byte… Swap in the real `@radix-ui/colors` package values if pixel-exact
parity matters."* We already did. **Do not replace the shared token layer with the
imported `colors.css`** — it would be a downgrade.

## Contrast corrections

The imported system fails WCAG 2.1 contrast in five measured places. These are corrected
additively in `apps/organization-admin/src/app/global.css` §2b — Radix scales untouched,
so admin and ticketing are unaffected.

| token / pair | imported | corrected |
|---|---|---|
| `--color-money-text` (light) | 3.88:1 | 4.68:1 |
| `--status-warning-11` (light) | 3.48:1 | 4.71:1 |
| `--status-success-11` (light) | 4.13:1 | 4.74:1 |
| micro-label (dark) | 3.97:1 | 8.79:1 |
| bar mark vs its track | 2.32:1 | 4.68:1 |

The design's own dashboard mock also separates focal from non-focal segment bars by hue
alone at **1.36:1** — invisible in grayscale and to a deuteranope. The implementation
does not reproduce that; see `docs/ORG_ADMIN_DASHBOARD_INFOGRAPHIC_SPEC.md` §A4.

## Component contracts

The imported system declares 15 components with fixed prop sets. `component-contracts.json`
in this directory is the machine-readable form, lifted from the design project's
`_adherence.oxlintrc.json`. `apps/organization-admin/src/components/ui/*` implements them;
the conformance test asserts the implementations accept exactly the declared props.

## Re-importing

1. Pull the project with the `DesignSync` tool (`list_files`, then `get_file`).
2. Diff `_adherence.oxlintrc.json` against `component-contracts.json` here — that is the
   contract, and the only part that must not drift.
3. Re-measure contrast before adopting any new colour value. The imported system has
   shipped failing values once already.
