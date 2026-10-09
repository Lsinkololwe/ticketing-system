import type { SVGProps } from 'react';
import { cx } from './utils';

/**
 * Inline icon set (24 grid, 1.8 round stroke: the same family the design uses).
 * No font or CDN at runtime. Add a glyph by adding a path string here.
 */
const PATHS = {
  add: 'M12 5v14M5 12h14',
  close: 'M6 6l12 12M18 6L6 18',
  menu: 'M4 7h16M4 12h16M4 17h16',
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4',
  'chevron-left': 'M15 6l-6 6 6 6',
  'chevron-right': 'M9 6l6 6-6 6',
  'chevron-down': 'M6 9l6 6 6-6',
  'chevron-up': 'M6 15l6-6 6 6',
  'first-page': 'M18 6l-6 6 6 6M6 6v12',
  'last-page': 'M6 6l6 6-6 6M18 6v12',
  'arrow-left': 'M19 12H5M11 6l-6 6 6 6',
  'arrow-right': 'M5 12h14M13 6l6 6-6 6',
  'arrow-up': 'M12 19V5M6 11l6-6 6 6',
  'arrow-down': 'M12 5v14M6 13l6 6 6-6',
  sort: 'M8 5v14M4 9l4-4 4 4M16 19V5M12 15l4 4 4-4',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  'check-circle': 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM8 12.5l3 3 5-6',
  more: 'M6 12h.01M12 12h.01M18 12h.01',
  'more-vert': 'M12 6h.01M12 12h.01M12 18h.01',
  edit: 'M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4',
  delete: 'M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13M10 11v6M14 11v6',
  calendar: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4',
  ticket: 'M3 9a2 2 0 0 0 0 4v4h18v-4a2 2 0 0 1 0-4V5H3zM14 5v14',
  user: 'M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM4 20c1-4 4-6 8-6s7 2 8 6',
  users: 'M9 5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7zM2.5 19c.8-3.4 3.2-5 6.5-5s5.700 1.600 6.500 5M17 6a3 3 0 0 1 0 6M18 14c2 .5 3.200 2 3.500 5',
  settings:
    'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM4 12l2-.5.5-1.200-1-1.800 1.800-1.800 1.800 1 1.200-.5L11 4h2l.7 2.200 1.200.5 1.800-1L18.500 7.500l-1 1.800.5 1.200L20 12v0l-2 .5-.5 1.200 1 1.800-1.800 1.800-1.800-1-1.200.5L13 20h-2l-.7-2.200-1.200-.5-1.800 1L5.500 16.500l1-1.800-.5-1.200z',
  dashboard: 'M4 4h7v9H4zM13 4h7v5h-7zM13 11h7v9h-7zM4 15h7v5H4z',
  money: 'M3 7h18v10H3zM12 9.500a2.500 2.500 0 1 0 0 5 2.500 2.500 0 0 0 0-5zM6 12h.01M18 12h.01',
  wallet: 'M4 7h15a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1zM4 7l12-3v3M16 13h2',
  bank: 'M3 10l9-6 9 6M5 10v8M9.500 10v8M14.500 10v8M19 10v8M3 20h18',
  list: 'M8 7h12M8 12h12M8 17h12M4 7h.01M4 12h.01M4 17h.01',
  image: 'M4 5h16v14H4zM4 16l5-5 4 4 3-3 4 4M9 9.500a1 1 0 1 0 0-.01',
  bell: 'M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0',
  sun: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.500 1.500M17.500 17.500L19 19M5 19l1.500-1.500M17.500 6.500L19 5',
  moon: 'M20 14.500A8 8 0 0 1 9.500 4 8 8 0 1 0 20 14.500z',
  swap: 'M5 8h14l-4-4M19 16H5l4 4',
  logout: 'M10 4H5v16h5M15 8l4 4-4 4M19 12H9',
  filter: 'M4 5h16l-6 8v6l-4-2v-4z',
  download: 'M12 4v11M7 11l5 5 5-5M5 20h14',
  upload: 'M12 16V5M7 9l5-5 5 5M5 20h14',
  info: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v6M12 7.500v.01',
  warning: 'M12 4l9 16H3zM12 10v4M12 17v.01',
  error: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 7v6M12 16.500v.01',
  clock: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 7v5l3 2',
  location: 'M12 21s7-6 7-11a7 7 0 0 0-14 0c0 5 7 11 7 11zM12 7.500a2.500 2.500 0 1 0 0 5 2.500 2.500 0 0 0 0-5z',
  share: 'M18 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM6 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM18 22a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM8.700 10.500l6.600-3.500M8.700 13.500l6.600 3.500',
  qr: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h3v3h-3zM20 14v.01M14 20h.01M17 17h3v3M7 7h.01M17 7h.01M7 17h.01',
  mail: 'M3 6h18v12H3zM3 7l9 6 9-6',
  phone: 'M6 3h4l1.500 5-2.500 1.500a11 11 0 0 0 5.500 5.500L16 12.500l5 1.500v4a2 2 0 0 1-2 2A16 16 0 0 1 4 5a2 2 0 0 1 2-2z',
  eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 9.500a2.500 2.500 0 1 0 0 5 2.500 2.500 0 0 0 0-5z',
  copy: 'M9 9h11v11H9zM5 15V4h11',
  cart: 'M3 4h2l2 11h11l2-8H7M9 20h.01M17 20h.01',
  heart: 'M12 20s-8-5-8-11a4.500 4.500 0 0 1 8-2.500A4.500 4.500 0 0 1 20 9c0 6-8 11-8 11z',
  receipt: 'M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6',
  shield: 'M12 3l8 3v6c0 5-3.500 8-8 9-4.500-1-8-4-8-9V6z',
  chart: 'M4 20V4M4 20h16M8 16v-5M12 16V8M16 16v-8',
  building: 'M5 21V5l7-2 7 2v16M3 21h18M9 9h.01M15 9h.01M9 13h.01M15 13h.01M10 21v-4h4v4',
  file: 'M6 3h8l5 5v13H6zM14 3v5h5M9 13h6M9 17h6',
  link: 'M10 14a4 4 0 0 0 5.700 0l3-3a4 4 0 0 0-5.700-5.700l-1 1M14 10a4 4 0 0 0-5.700 0l-3 3a4 4 0 0 0 5.700 5.700l1-1',
  refresh: 'M20 5v5h-5M4 19v-5h5M19 10a7 7 0 0 0-12-3L4 10M5 14a7 7 0 0 0 12 3l3-3',
  lock: 'M6 11h12v9H6zM8 11V8a4 4 0 0 1 8 0v3',
  star: 'M12 3l2.800 6 6.200.7-4.600 4.300 1.300 6.300L12 17.200 6.300 20.300l1.300-6.300L3 9.700 9.200 9z',
  play: 'M8 5l11 7-11 7z',
  save: 'M5 4h12l3 3v13H4V4h1zM8 4v5h7V4M8 20v-6h8v6',
  history: 'M4 12a8 8 0 1 0 3-6.200M4 4v4h4M12 8v5l3 2',
  tag: 'M3 12V4h8l10 10-8 8zM7.500 8h.01',
  flag: 'M5 21V4M5 5h13l-2 4 2 4H5',
  key: 'M14 10a5 5 0 1 0-4 5l1 1h2v2h2v2h3v-3l-4-4zM8 9.500v.01',
  server: 'M4 4h16v6H4zM4 14h16v6H4zM8 7h.01M8 17h.01',
  pulse: 'M3 12h4l3-8 4 16 3-8h4',
  card: 'M5 6h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2zM3 10h18M16 15h2',
  cog: 'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2',
  inbox: 'M4 13l2-8h12l2 8M4 13v6h16v-6M4 13h5l1 2h4l1-2h5',
} as const;

export type IconName = keyof typeof PATHS;

export interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'name'> {
  name: IconName;
  /** Accessible name. Omit for decorative icons (aria-hidden). */
  label?: string;
}

/** Inline SVG icon sized by the surrounding component (or `--m3-icon-*`). */
export function Icon({ name, label, className, ...rest }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={cx('m3-icon', className)}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      focusable="false"
      {...rest}
    >
      <path d={PATHS[name]} />
    </svg>
  );
}

export const iconNames = Object.keys(PATHS) as IconName[];
