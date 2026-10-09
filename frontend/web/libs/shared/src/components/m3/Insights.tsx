'use client';

import { useId, type CSSProperties, type ReactNode } from 'react';
import { m3ChartColors } from '../../styles/m3.tokens';
import { Icon, type IconName } from './icons';
import { LinearProgress } from './Display';

/* --------------------------------------------------------------- KPI + Stat */

export interface KpiCardProps {
  label: string;
  value: ReactNode;
  icon?: IconName;
  /** Small change chip, e.g. { text: '43%', direction: 'up' }. */
  trend?: { text: string; direction: 'up' | 'down' };
  /** Caption beside the trend chip. */
  caption?: ReactNode;
  /** Optional sparkline series (rendered top right). */
  spark?: number[];
  /** Action under the delta line (a "View" link button), inside the tile. */
  action?: ReactNode;
}

/**
 * Headline number tile (KPI). Label above value, optional icon tile, trend chip
 * and sparkline. Trend direction is stated in text as well as colour.
 */
export function KpiCard({ label, value, icon, trend, caption, spark, action }: KpiCardProps) {
  return (
    <div className="m3-kpi" data-noicon={icon ? undefined : 'true'} role="group" aria-label={label}>
      {icon ? (
        <span className="m3-kpi__icon" aria-hidden="true">
          <Icon name={icon} />
        </span>
      ) : null}
      <span className="m3-kpi__label">{label}</span>
      <b className="m3-kpi__value">{value}</b>
      {trend || caption ? (
        <span className="m3-kpi__delta">
          {trend ? (
            <span className="m3-kpi__trend" data-direction={trend.direction === 'down' ? 'down' : undefined}>
              <span aria-hidden="true">{trend.direction === 'up' ? '▲' : '▼'}</span>
              <span className="m3-sr-only">{trend.direction === 'up' ? 'Up ' : 'Down '}</span>
              {trend.text}
            </span>
          ) : null}
          {caption ? <span>{caption}</span> : null}
        </span>
      ) : null}
      {spark && spark.length > 1 ? <Sparkline values={spark} className="m3-kpi__spark" /> : null}
      {action ? <div className="m3-kpi__action">{action}</div> : null}
    </div>
  );
}

export interface KpiGridProps {
  children: ReactNode;
  /** Fixed column counts (wide / medium <= 1000 / narrow <= 560); default is auto-fit by minimum tile width. */
  columns?: { wide: number; medium: number; narrow: number };
  /** Flat tonal tiles (summary strips above tables) instead of bordered cards. */
  flat?: boolean;
}
export function KpiGrid({ children, columns, flat }: KpiGridProps) {
  return (
    <div
      className="m3-kpis"
      data-flat={flat ? 'true' : undefined}
      data-cols={columns ? 'fixed' : undefined}
      style={columns ? ({ '--m3-kpis-wide': columns.wide, '--m3-kpis-medium': columns.medium, '--m3-kpis-narrow': columns.narrow } as CSSProperties) : undefined}
    >
      {children}
    </div>
  );
}

export interface StatCardProps {
  label: string;
  value: ReactNode;
  /** 0-100 progress bar under the value. */
  progress?: number;
}
/** Compact stat tile (smaller than KpiCard), optional progress. */
export function StatCard({ label, value, progress }: StatCardProps) {
  return (
    <div className="m3-stat">
      <span className="m3-stat__label">{label}</span>
      <b className="m3-stat__value">{value}</b>
      {progress !== undefined ? <LinearProgress value={progress} label={`${label} progress`} /> : null}
    </div>
  );
}

/* --------------------------------------------------------------- chart base */

export interface ChartPoint {
  label: string;
  value: number;
}

function niceMax(max: number): number {
  if (max <= 0) return 1;
  const exp = Math.pow(10, Math.floor(Math.log10(max)));
  const f = max / exp;
  const nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
  return nice * exp;
}

export interface ChartFrameProps {
  title: string;
  /** One-sentence summary for screen readers (what the chart shows). */
  description?: string;
  legend?: Array<{ label: string; color: string }>;
  /** Data for the accessible "view as table" disclosure. */
  table?: { columns: string[]; rows: Array<Array<string | number>> };
  children: ReactNode;
}

/**
 * Figure wrapper for every chart: caption, legend, and an always-available
 * data table so no value is exposed by colour or hover alone.
 */
export function ChartFrame({ title, description, legend, table, children }: ChartFrameProps) {
  const id = useId();
  return (
    <figure style={{ margin: 0 }} aria-labelledby={`${id}-t`} aria-describedby={description ? `${id}-d` : undefined}>
      <figcaption className="m3-sr-only" id={`${id}-t`}>
        {title}
      </figcaption>
      {description ? (
        <span className="m3-sr-only" id={`${id}-d`}>
          {description}
        </span>
      ) : null}
      {legend && legend.length > 0 ? (
        <ul className="m3-legend" aria-label="Legend">
          {legend.map((l) => (
            <li key={l.label}>
              <i style={{ background: l.color }} />
              {l.label}
            </li>
          ))}
        </ul>
      ) : null}
      {children}
      {table ? (
        <details>
          <summary className="m3-link">View as table</summary>
          <table className="m3-table" data-density="compact">
            <thead>
              <tr>
                {table.columns.map((c) => (
                  <th key={c} scope="col">
                    {c}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {table.rows.map((r, i) => (
                <tr key={i}>
                  {r.map((cell, j) => (
                    <td key={j} data-align={j > 0 ? 'end' : undefined}>
                      {cell}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </details>
      ) : null}
    </figure>
  );
}

/* ----------------------------------------------------------------- BarChart */

export interface BarChartProps {
  title: string;
  description?: string;
  data: ChartPoint[];
  /** Format for axis ticks, value labels and the table. */
  format?: (n: number) => string;
  /** Label the tallest bar. Default true. */
  labelMax?: boolean;
  seriesLabel?: string;
}

const W = 720;
const H = 270;
const PAD = { l: 64, r: 8, t: 14, b: 28 };

/** Vertical bars, horizontal gridlines, zero baseline. Each bar is keyboard-focusable and labelled. */
export function BarChart({
  title,
  description,
  data,
  format = (n) => String(n),
  labelMax = true,
  seriesLabel = 'Value',
}: BarChartProps) {
  const max = niceMax(Math.max(0, ...data.map((d) => d.value)));
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => f * max);
  const iw = W - PAD.l - PAD.r;
  const ih = H - PAD.t - PAD.b;
  const step = iw / Math.max(1, data.length);
  const bw = Math.min(28, step * 0.6);
  const y = (v: number) => PAD.t + ih - (v / max) * ih;
  const maxIndex = data.reduce((m, d, i) => (d.value > data[m].value ? i : m), 0);
  const every = data.length > 8 ? 2 : 1;
  return (
    <ChartFrame
      title={title}
      description={description}
      legend={[{ label: seriesLabel, color: m3ChartColors[0] }]}
      table={{ columns: ['Period', seriesLabel], rows: data.map((d) => [d.label, format(d.value)]) }}
    >
      <svg className="m3-chart" viewBox={`0 0 ${W} ${H}`} role="group" aria-label={title}>
        {ticks.map((t) => (
          <g key={t}>
            <line className="m3-chart__grid" x1={PAD.l} x2={W - PAD.r} y1={y(t)} y2={y(t)} />
            <text x={PAD.l - 8} y={y(t) + 4} textAnchor="end">
              {format(t)}
            </text>
          </g>
        ))}
        {data.map((d, i) => {
          const x = PAD.l + step * i + (step - bw) / 2;
          const h = Math.max(d.value > 0 ? 2 : 0, (d.value / max) * ih);
          return (
            <g key={d.label} className="m3-chart__group" tabIndex={0} role="img" aria-label={`${d.label}: ${format(d.value)}`}>
              <rect className="m3-chart__hit" x={PAD.l + step * i} y={PAD.t} width={step} height={ih} />
              <rect className="m3-chart__mark" x={x} y={PAD.t + ih - h} width={bw} height={h} rx={3} fill={m3ChartColors[0]} />
              {labelMax && i === maxIndex && d.value > 0 ? (
                <text className="m3-chart__cap" x={x + bw / 2} y={PAD.t + ih - h - 6} textAnchor="middle">
                  {format(d.value)}
                </text>
              ) : null}
              {i % every === 0 ? (
                <text x={x + bw / 2} y={H - 8} textAnchor="middle">
                  {d.label}
                </text>
              ) : null}
            </g>
          );
        })}
      </svg>
    </ChartFrame>
  );
}

/* ---------------------------------------------------------------- LineChart */

export interface LineSeries {
  label: string;
  values: number[];
}
export interface LineChartProps {
  title: string;
  description?: string;
  labels: string[];
  series: LineSeries[];
  format?: (n: number) => string;
}

const DASH = ['0', '6 4', '2 3', '10 3 2 3', '1 5'];

/** Multi-series line chart. Series differ by colour AND dash pattern; every x position is a focusable group announcing all values. */
export function LineChart({ title, description, labels, series, format = (n) => String(n) }: LineChartProps) {
  const max = niceMax(Math.max(0, ...series.flatMap((s) => s.values)));
  const iw = W - PAD.l - PAD.r;
  const ih = H - PAD.t - PAD.b;
  const step = iw / Math.max(1, labels.length - 1);
  const x = (i: number) => PAD.l + step * i;
  const y = (v: number) => PAD.t + ih - (v / max) * ih;
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => f * max);
  return (
    <ChartFrame
      title={title}
      description={description}
      legend={series.map((s, i) => ({ label: s.label, color: m3ChartColors[i % m3ChartColors.length] }))}
      table={{
        columns: ['Period', ...series.map((s) => s.label)],
        rows: labels.map((l, i) => [l, ...series.map((s) => format(s.values[i] ?? 0))]),
      }}
    >
      <svg className="m3-chart" viewBox={`0 0 ${W} ${H}`} role="group" aria-label={title}>
        {ticks.map((t) => (
          <g key={t}>
            <line className="m3-chart__grid" x1={PAD.l} x2={W - PAD.r} y1={y(t)} y2={y(t)} />
            <text x={PAD.l - 8} y={y(t) + 4} textAnchor="end">
              {format(t)}
            </text>
          </g>
        ))}
        {series.map((s, si) => (
          <polyline
            key={s.label}
            fill="none"
            stroke={m3ChartColors[si % m3ChartColors.length]}
            strokeWidth={2.5}
            strokeDasharray={DASH[si % DASH.length]}
            strokeLinejoin="round"
            points={s.values.map((v, i) => `${x(i)},${y(v)}`).join(' ')}
          />
        ))}
        {labels.map((l, i) => (
          <g
            key={l}
            className="m3-chart__group"
            tabIndex={0}
            role="img"
            aria-label={`${l}: ${series.map((s) => `${s.label} ${format(s.values[i] ?? 0)}`).join(', ')}`}
          >
            <rect className="m3-chart__hit" x={x(i) - step / 2} y={PAD.t} width={step} height={ih} />
            {series.map((s, si) => (
              <circle
                key={s.label}
                className="m3-chart__mark"
                cx={x(i)}
                cy={y(s.values[i] ?? 0)}
                r={3.5}
                fill={m3ChartColors[si % m3ChartColors.length]}
              />
            ))}
            {i % (labels.length > 8 ? 2 : 1) === 0 ? (
              <text x={x(i)} y={H - 8} textAnchor="middle">
                {l}
              </text>
            ) : null}
          </g>
        ))}
      </svg>
    </ChartFrame>
  );
}

/* ---------------------------------------------------------------- DonutChart */

export interface DonutChartProps {
  title: string;
  description?: string;
  data: ChartPoint[];
  format?: (n: number) => string;
  /** Text in the centre. Default: the total. */
  centre?: ReactNode;
}

/** Donut with a legend that lists value and share for each slice (so colour is never the only channel). */
export function DonutChart({ title, description, data, format = (n) => String(n), centre }: DonutChartProps) {
  const total = data.reduce((s, d) => s + d.value, 0) || 1;
  const R = 54;
  const C = 2 * Math.PI * R;
  let offset = 0;
  return (
    <ChartFrame
      title={title}
      description={description}
      table={{
        columns: ['Segment', 'Value', 'Share'],
        rows: data.map((d) => [d.label, format(d.value), `${((d.value / total) * 100).toFixed(1)}%`]),
      }}
    >
      <div className="m3-donut">
        <svg className="m3-chart" viewBox="0 0 140 140" role="img" aria-label={`${title}: ${data.map((d) => `${d.label} ${format(d.value)}`).join(', ')}`}>
          <g transform="rotate(-90 70 70)">
            {data.map((d, i) => {
              const len = (d.value / total) * C;
              const el = (
                <circle
                  key={d.label}
                  cx={70}
                  cy={70}
                  r={R}
                  fill="none"
                  stroke={m3ChartColors[i % m3ChartColors.length]}
                  strokeWidth={22}
                  strokeDasharray={`${Math.max(0, len - 1.5)} ${C - Math.max(0, len - 1.5)}`}
                  strokeDashoffset={-offset}
                />
              );
              offset += len;
              return el;
            })}
          </g>
          <text x={70} y={74} textAnchor="middle" className="m3-chart__cap">
            {centre ?? format(total)}
          </text>
        </svg>
        <ul className="m3-donut__legend">
          {data.map((d, i) => (
            <li key={d.label}>
              <i style={{ background: m3ChartColors[i % m3ChartColors.length] }} />
              <span>{d.label}</span>
              <b className="m3-num">{format(d.value)}</b>
              <em>{((d.value / total) * 100).toFixed(1)}%</em>
            </li>
          ))}
        </ul>
      </div>
    </ChartFrame>
  );
}

/* ------------------------------------------------------- HBars + Sparkline */

export interface HorizontalBarsProps {
  title: string;
  data: ChartPoint[];
  format?: (n: number) => string;
}
/** Ranked horizontal bars with the value printed at the end of each row. */
export function HorizontalBars({ title, data, format = (n) => String(n) }: HorizontalBarsProps) {
  const max = Math.max(1, ...data.map((d) => d.value));
  return (
    <ul className="m3-hbars" aria-label={title}>
      {data.map((d) => (
        <li key={d.label}>
          <span>{d.label}</span>
          <span className="m3-hbars__track" aria-hidden="true">
            <span className="m3-hbars__fill" style={{ width: `${(d.value / max) * 100}%` }} />
          </span>
          <b>{format(d.value)}</b>
        </li>
      ))}
    </ul>
  );
}

export interface SparklineProps {
  values: number[];
  className?: string;
  /** Provide when the sparkline carries meaning on its own. */
  label?: string;
}
/** Tiny trend line, decorative unless `label` is given. */
export function Sparkline({ values, className, label }: SparklineProps) {
  const max = Math.max(...values);
  const min = Math.min(...values);
  const span = max - min || 1;
  const pts = values.map((v, i) => `${(i / (values.length - 1)) * 100},${28 - ((v - min) / span) * 24}`).join(' ');
  return (
    <svg
      className={className}
      viewBox="0 0 100 32"
      preserveAspectRatio="none"
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      <polyline points={pts} fill="none" stroke={m3ChartColors[0]} strokeWidth={2} strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
    </svg>
  );
}
