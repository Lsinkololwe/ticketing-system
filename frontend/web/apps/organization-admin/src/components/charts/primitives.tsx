'use client';

/**
 * Data-viz primitives for the organizer dashboard.
 *
 * Every mark here is governed by docs/ORG_ADMIN_DASHBOARD_INFOGRAPHIC_SPEC.md.
 * The spec passed its conformance gate before this file was written; if you
 * change an encoding here, amend the spec first.
 *
 * Three rules these components exist to enforce, so call sites cannot break
 * them one screen at a time:
 *
 *   1. ZERO BASELINE. `ColumnSeries` derives every height as a percentage of
 *      the series maximum measured from zero. There is no `min`/`domain` prop,
 *      because a truncated bar baseline is a correctness failure, not a style
 *      option.
 *   2. DIRECT LABELS, NEVER A LEGEND. Every mark takes its own label and its
 *      own printed value. Nothing is distinguishable by hue alone, so the
 *      tiles survive grayscale and colour-vision deficiency.
 *   3. NO INK THAT ENCODES NOTHING. No gradients, shadows, borders or icons on
 *      data marks. All colour comes from the `--viz-*` token layer, which is
 *      contrast-checked against the bar track in both appearances.
 *
 * Presentation lives in global.css (`.viz-*`). The only value any of these
 * components writes inline is a percentage — because the percentage IS the
 * encoded datum.
 */

import type { ReactNode } from 'react';

// =============================================================================
// SHARED
// =============================================================================

export type Trend = 'up' | 'down' | 'neutral';

/** Clamp to [0, 100] so a bad denominator can never paint outside its track. */
function pct(value: number, total: number): number {
  if (!Number.isFinite(value) || !Number.isFinite(total) || total <= 0) return 0;
  return Math.min(100, Math.max(0, (value / total) * 100));
}

/**
 * Split "K 125,430" / "8,234" / "87%" into a lighter unit and the bold numeral
 * core, so the figure reads as one number rather than a wall of heavy glyphs.
 * Mirrors the design system's StatCard treatment.
 */
export function splitFigure(value: string): { prefix: string; core: string; suffix: string } {
  const match = String(value).match(/^([A-Za-z]{1,3}\s?)?([\d.,]+)(\s?[A-Za-z%]{1,3})?$/);
  if (!match) return { prefix: '', core: String(value), suffix: '' };
  return { prefix: (match[1] ?? '').trim(), core: match[2], suffix: (match[3] ?? '').trim() };
}

// =============================================================================
// TILE TITLE — the micro-label, and the smallest type on any tile
// =============================================================================

/**
 * The tile's title *and* its provenance line. Deliberately one component: the
 * spec's G8 gate requires source/range/units on every graphic, so making the
 * provenance a separate optional component would let call sites drop it.
 */
export function VizTitle({
  children,
  testId,
}: {
  /** "Ticket revenue · last 6 complete months · ZMW" — label, range, units. */
  children: ReactNode;
  testId: string;
}) {
  return (
    <span className="viz-title" data-testid={testId}>
      {children}
    </span>
  );
}

// =============================================================================
// HEADLINE FIGURE
// =============================================================================

export function VizFigure({
  value,
  delta,
  trend = 'neutral',
  testId,
}: {
  /** Pre-formatted, e.g. "K 125,430". */
  value: string;
  /** Percentage change as a number, e.g. 12.5. Omit when there is no comparison. */
  delta?: number | null;
  trend?: Trend;
  testId: string;
}) {
  const { prefix, core, suffix } = splitFigure(value);
  const showDelta = delta !== undefined && delta !== null && Number.isFinite(delta);

  return (
    <div className="viz-figure" data-testid={testId}>
      {prefix && <span className="viz-figure-unit">{prefix}</span>}
      <span className="viz-figure-core">{core}</span>
      {suffix && <span className="viz-figure-unit">{suffix}</span>}
      {showDelta && (
        <span className="viz-figure-delta" data-trend={trend} data-testid={`${testId}-delta`}>
          {trend === 'up' ? '+' : ''}
          {delta}%
        </span>
      )}
    </div>
  );
}

// =============================================================================
// B1 — COLUMN SERIES (change over time)
// =============================================================================

export interface ColumnPoint {
  /** Axis label, e.g. "Jul". Always rendered — this is the direct label. */
  label: string;
  value: number;
}

/**
 * Vertical columns on a zero baseline, one per discrete period.
 *
 * The final point is the focal mark by default: it takes the accent fill and a
 * bold accent label, separated from the pale non-focal columns at 3.75:1. That
 * separation is lightness, not hue, so the emphasis survives grayscale.
 */
export function ColumnSeries({
  points,
  /** Index of the focal column. Defaults to the most recent period. */
  focalIndex,
  testId,
}: {
  points: ColumnPoint[];
  focalIndex?: number;
  testId: string;
}) {
  if (points.length === 0) return null;

  const focal = focalIndex ?? points.length - 1;
  const max = Math.max(...points.map((p) => p.value), 0);
  // Explicit track count keeps columns and their labels on one shared grid, so
  // every label stays centred under its own column at any width.
  const columns = { gridTemplateColumns: `repeat(${points.length}, minmax(0, 1fr))` };

  return (
    <div data-testid={testId}>
      <div className="viz-columns" style={columns} role="list">
        {points.map((point, index) => (
          <div className="viz-column-slot" key={point.label} role="listitem">
            <div
              className="viz-column"
              data-focal={index === focal}
              data-testid={`${testId}-column-${index}`}
              // The only inline value in this file: the encoded magnitude.
              style={{ height: `${pct(point.value, max)}%` }}
              aria-label={`${point.label}: ${point.value.toLocaleString()}`}
            />
          </div>
        ))}
      </div>
      <div className="viz-column-labels" style={columns}>
        {points.map((point, index) => (
          <span className="viz-column-label" data-focal={index === focal} key={point.label}>
            {point.label}
          </span>
        ))}
      </div>
    </div>
  );
}

// =============================================================================
// B3 / B4 / B5 — SHARE BARS (composition, common left baseline)
// =============================================================================

export interface ShareRow {
  name: string;
  /** Raw count. The bar length is this over `total`. */
  value: number;
  /** Printed at the row end. Defaults to the percentage. */
  display?: string;
  /**
   * Marks the row as a non-focal reference series. Use ONLY where the tile
   * genuinely has one — never to fake a categorical colour encoding, which
   * would separate at 1.02:1 and read as noise.
   */
  reference?: boolean;
}

/**
 * Horizontal bars sharing one left baseline and one denominator.
 *
 * Length carries the quantity; the row label and the printed value carry the
 * category. Colour carries nothing, which is why every bar is the same accent
 * unless a row is explicitly a reference series.
 */
export function ShareBars({
  rows,
  /** The shared denominator. Every bar is a fraction of this. */
  total,
  testId,
}: {
  rows: ShareRow[];
  total: number;
  testId: string;
}) {
  return (
    <div data-testid={testId} role="list">
      {rows.map((row) => {
        const share = pct(row.value, total);
        return (
          <div className="viz-share-row" key={row.name} role="listitem">
            <span className="viz-share-name">{row.name}</span>
            <div className="viz-share-track">
              <div
                className="viz-share-fill"
                data-role={row.reference ? 'reference' : 'primary'}
                style={{ width: `${share}%` }}
                aria-label={`${row.name}: ${row.value.toLocaleString()} of ${total.toLocaleString()}`}
              />
            </div>
            <span className="viz-share-value">{row.display ?? `${Math.round(share)}%`}</span>
          </div>
        );
      })}
    </div>
  );
}

// =============================================================================
// B2 — BULLET METER (one value against a benchmark)
// =============================================================================

/**
 * A single bar split into elapsed and remaining, both directly labelled.
 *
 * Chosen over a gauge or a donut: length on a shared baseline beats angle, and
 * gauges are on the near-always-wrong list.
 */
export function BulletMeter({
  elapsed,
  total,
  elapsedLabel,
  remainingLabel,
  testId,
}: {
  elapsed: number;
  total: number;
  elapsedLabel: string;
  remainingLabel: string;
  testId: string;
}) {
  const done = pct(elapsed, total);

  return (
    <div
      className="viz-meter"
      data-testid={testId}
      role="img"
      aria-label={`${elapsedLabel} elapsed, ${remainingLabel}`}
    >
      <div className="viz-meter-segment" data-role="elapsed" style={{ width: `${done}%` }}>
        {elapsedLabel}
      </div>
      <div className="viz-meter-segment" data-role="remaining" style={{ width: `${100 - done}%` }}>
        {remainingLabel}
      </div>
    </div>
  );
}

// =============================================================================
// ANNOTATION — the finding, in words, beside the evidence
// =============================================================================

/**
 * The takeaway, stated at the graphic rather than in surrounding prose. Sits on
 * a 2px accent rule sharing the marks' left edge.
 *
 * `lead` is the finding; `children` is the supporting clause.
 */
export function VizInsight({
  lead,
  children,
  testId,
}: {
  lead: string;
  children: ReactNode;
  testId: string;
}) {
  return (
    <p className="viz-insight" data-testid={testId}>
      <strong>{lead}</strong>
      {children ? <> — {children}</> : null}
    </p>
  );
}

/** Footnote under a composition tile. Small and gray is correct. */
export function VizNote({ children, testId }: { children: ReactNode; testId: string }) {
  return (
    <p className="viz-note" data-testid={testId}>
      {children}
    </p>
  );
}
