// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { BarChart, DonutChart, HorizontalBars, KpiCard, KpiGrid, LineChart, Sparkline, StatCard } from '../Insights';

const data = [{ label: 'Jan', value: 10 }, { label: 'Feb', value: 30 }];

describe('KPI', () => {
  it('KpiCard is a labelled group and states trend in text', () => {
    render(<KpiGrid><KpiCard label="Revenue" value="K 10" icon="money" trend={{ text: '+5% vs May', direction: 'up' }} spark={[1, 2, 3]} /></KpiGrid>);
    expect(screen.getByRole('group', { name: 'Revenue' })).toBeInTheDocument();
    expect(screen.getByText('+5% vs May')).toBeInTheDocument();
  });
  it('StatCard shows progress when given', () => {
    render(<StatCard label="Checked in" value="50%" progress={50} />);
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '50');
  });
});

describe('charts are accessible without colour or hover', () => {
  it('BarChart: each bar labelled, table fallback', () => {
    render(<BarChart title="Revenue" data={data} format={(n) => `K ${n}`} />);
    expect(screen.getByRole('img', { name: 'Feb: K 30' })).toBeInTheDocument();
    expect(screen.getByText('View as table')).toBeInTheDocument();
  });
  it('BarChart with zeros does not crash', () => {
    render(<BarChart title="Empty" data={[{ label: 'Jan', value: 0 }]} />);
    expect(screen.getByRole('img', { name: 'Jan: 0' })).toBeInTheDocument();
  });
  it('LineChart renders a figure', () => {
    render(<LineChart title="Trend" labels={['a', 'b']} series={[{ label: 'S', values: [1, 2] }]} />);
    expect(screen.getByRole('figure')).toBeInTheDocument();
  });
  it('DonutChart lists its slices in a table', () => {
    render(<DonutChart title="Mix" data={data} />);
    expect(screen.getByRole('figure')).toBeInTheDocument();
    expect(screen.getByText('View as table')).toBeInTheDocument();
  });
  it('HorizontalBars prints each label', () => {
    render(<HorizontalBars title="Tiers" data={data} />);
    expect(screen.getByText('Jan')).toBeInTheDocument();
  });
  it('Sparkline is labelled when asked', () => {
    render(<Sparkline values={[1, 2, 3]} label="Last 3 days" />);
    expect(screen.getByRole('img', { name: 'Last 3 days' })).toBeInTheDocument();
  });
});
