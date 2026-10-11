import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AccessSettingUp } from './AccessSettingUp';

describe('AccessSettingUp', () => {
  it('names the organization and offers a re-check through sign-in', () => {
    render(<AccessSettingUp organizationName="Showstop Live Events" />);

    expect(screen.getByRole('heading', { name: /setting up your access/i })).toBeTruthy();
    expect(screen.getByText(/joined Showstop Live Events/i)).toBeTruthy();
    expect(screen.getByRole('link', { name: /check again/i }).getAttribute('href')).toBe('/login?next=%2Fdashboard');
  });

  it('still reads sensibly when the organization name is not known', () => {
    render(<AccessSettingUp organizationName={null} />);

    expect(screen.getByText(/joined the organization/i)).toBeTruthy();
  });
});
