/** 自绘内联 SVG 图标（不复制原 App 资源）。统一 currentColor，随主题走。 */
import type { SVGProps } from 'react';

export type IconName =
  | 'bills' | 'stats' | 'plus' | 'auto' | 'mine' | 'back' | 'search' | 'settings'
  | 'chevron' | 'close' | 'check' | 'trash' | 'edit' | 'wallet' | 'book' | 'tag'
  | 'calendar' | 'bell' | 'shield' | 'camera' | 'upload' | 'download' | 'filter'
  | 'more' | 'arrow' | 'moon' | 'sun' | 'eye' | 'lock' | 'gift' | 'heart'
  | 'clock' | 'refresh' | 'scan' | 'pet' | 'chart' | 'pie' | 'grid' | 'users'
  | 'link' | 'copy' | 'info' | 'warning' | 'folder' | 'file' | 'star' | 'coin'
  | 'transfer' | 'cycle' | 'receipt' | 'widget' | 'help' | 'logout' | 'phone'
  | 'sort' | 'image' | 'note' | 'minus' | 'menu';

/** 24×24 viewBox 路径表；stroke 由 Icon 组件统一设置。 */
const PATHS: Record<IconName, string> = {
  bills: 'M4 5.5h16v13a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5z|M4 9h16|M8 13h5|M8 16.5h8',
  stats: 'M4 19V9|M10 19V5|M16 19v-7|M2 19h20',
  plus: 'M12 5v14|M5 12h14',
  auto: 'M13 2 4.5 13.5H11l-1 8.5 8.5-11.5H12z',
  mine: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8|M4.5 20a7.5 7.5 0 0 1 15 0',
  back: 'M15 5l-7 7 7 7',
  search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14|M16.5 16.5 21 21',
  settings: 'M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7|M4 12h2|M18 12h2|M12 4v2|M12 18v2',
  chevron: 'M9 5l7 7-7 7',
  close: 'M6 6l12 12|M18 6 6 18',
  check: 'M4 12.5 9.5 18 20 6',
  trash: 'M4 7h16|M9 7V4.5h6V7|M6.5 7l1 13h9l1-13|M10 11v5|M14 11v5',
  edit: 'M4 20h4L19 9l-4-4L4 16z|M14 6l4 4',
  wallet: 'M3 7.5A2.5 2.5 0 0 1 5.5 5H19a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5.5A2.5 2.5 0 0 1 3 16.5z|M3 9h18|M16 13.5h3',
  book: 'M5 4.5A2.5 2.5 0 0 1 7.5 2H20v18H7.5A2.5 2.5 0 0 0 5 22z|M5 17.5h15',
  tag: 'M3 11.5V4a1 1 0 0 1 1-1h7.5L21 12.5 12.5 21z|M7.5 7.5h.01',
  calendar: 'M4 6.5h16v14H4z|M4 10h16|M8 3v4|M16 3v4',
  bell: 'M18 9a6 6 0 0 0-12 0c0 6-2 7-2 7h16s-2-1-2-7|M10 20a2 2 0 0 0 4 0',
  shield: 'M12 2 4.5 5.5v6c0 5 3.2 8.6 7.5 10.5 4.3-1.9 7.5-5.5 7.5-10.5v-6z|M9 12l2 2 4-4',
  camera: 'M3 8h4l1.5-2h7L17 8h4v11H3z|M12 16a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7',
  upload: 'M12 16V3|M7 8l5-5 5 5|M4 16v4h16v-4',
  download: 'M12 3v13|M7 11l5 5 5-5|M4 20h16',
  filter: 'M3 5h18l-7 8v6l-4 2v-8z',
  more: 'M5 12h.01|M12 12h.01|M19 12h.01',
  arrow: 'M5 12h14|M14 7l5 5-5 5',
  moon: 'M20 14.5A8.5 8.5 0 0 1 9.5 4 8.5 8.5 0 1 0 20 14.5',
  sun: 'M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10|M12 2v2|M12 20v2|M2 12h2|M20 12h2|M4.9 4.9l1.4 1.4|M17.7 17.7l1.4 1.4|M19.1 4.9l-1.4 1.4|M6.3 17.7l-1.4 1.4',
  eye: 'M2 12s3.8-6 10-6 10 6 10 6-3.8 6-10 6-10-6-10-6|M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6',
  lock: 'M6 11h12v10H6z|M9 11V7a3 3 0 0 1 6 0v4',
  gift: 'M3 9h18v4H3z|M5 13v8h14v-8|M12 9v12|M12 9S9 3 7 5s5 4 5 4|M12 9s3-6 5-4-5 4-5 4',
  heart: 'M12 20S3 14.5 3 8.5A4.5 4.5 0 0 1 12 6a4.5 4.5 0 0 1 9 2.5C21 14.5 12 20 12 20',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18|M12 7v5l3.5 2',
  refresh: 'M20 11a8 8 0 0 0-14.5-4.5L3 9|M3 4v5h5|M4 13a8 8 0 0 0 14.5 4.5L21 15|M21 20v-5h-5',
  scan: 'M4 8V5a1 1 0 0 1 1-1h3|M16 4h3a1 1 0 0 1 1 1v3|M20 16v3a1 1 0 0 1-1 1h-3|M8 20H5a1 1 0 0 1-1-1v-3|M3 12h18',
  pet: 'M12 14c-4 0-7 2.5-7 5h14c0-2.5-3-5-7-5|M7 8a1.8 1.8 0 1 0 0-3.6A1.8 1.8 0 0 0 7 8|M17 8a1.8 1.8 0 1 0 0-3.6A1.8 1.8 0 0 0 17 8|M9.5 6a1.8 1.8 0 1 0 0-3.6A1.8 1.8 0 0 0 9.5 6|M14.5 6a1.8 1.8 0 1 0 0-3.6A1.8 1.8 0 0 0 14.5 6',
  chart: 'M3 3v18h18|M7 16l4-5 3 3 5-7',
  pie: 'M12 3a9 9 0 1 0 9 9h-9z|M14 3v7h7',
  grid: 'M4 4h7v7H4z|M13 4h7v7h-7z|M4 13h7v7H4z|M13 13h7v7h-7z',
  users: 'M9 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8|M1.5 20a7.5 7.5 0 0 1 15 0|M17 5a4 4 0 0 1 0 8|M17 14a6 6 0 0 1 5.5 6',
  link: 'M10 14a4 4 0 0 0 6 .5l2.5-2.5a4 4 0 0 0-5.7-5.7L11 8|M14 10a4 4 0 0 0-6-.5L5.5 12a4 4 0 0 0 5.7 5.7L13 16',
  copy: 'M9 9h11v11H9z|M5 15H4V4h11v1',
  info: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18|M12 11v5|M12 8h.01',
  warning: 'M12 3 2 20h20z|M12 9v5|M12 17h.01',
  folder: 'M3 6a1 1 0 0 1 1-1h5l2 2h9a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z',
  file: 'M6 3h8l5 5v13H6z|M14 3v5h5|M9 13h7|M9 17h7',
  star: 'M12 3l2.8 5.7 6.2.9-4.5 4.4 1 6.2-5.5-2.9-5.5 2.9 1-6.2L3 9.6l6.2-.9z',
  coin: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18|M12 7v10|M9 10h5a2 2 0 0 1 0 4H9',
  transfer: 'M4 8h14|M15 5l3 3-3 3|M20 16H6|M9 13l-3 3 3 3',
  cycle: 'M20 8a8 8 0 0 0-14-3L4 7|M4 3v4h4|M4 16a8 8 0 0 0 14 3l2-2|M20 21v-4h-4',
  receipt: 'M6 3h12v18l-3-2-3 2-3-2-3 2z|M9 8h6|M9 12h6',
  widget: 'M4 4h7v7H4z|M13 4h7v4h-7z|M13 10h7v10h-7z|M4 13h7v7H4z',
  help: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18|M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.8.4-1 1-1 1.7|M12 17h.01',
  logout: 'M10 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h5|M14 8l4 4-4 4|M8 12h10',
  phone: 'M7 3h10v18H7z|M10 18.5h4',
  sort: 'M7 4v16|M4 7l3-3 3 3|M17 20V4|M14 17l3 3 3-3',
  image: 'M3 5h18v14H3z|M3 16l5-5 4 4 3-3 6 6|M8 9h.01',
  note: 'M5 4h14v16H5z|M8 9h8|M8 13h8|M8 17h5',
  minus: 'M5 12h14',
  menu: 'M4 7h16|M4 12h16|M4 17h16',
};

interface IconProps extends SVGProps<SVGSVGElement> {
  name: IconName;
  size?: number;
  strokeWidth?: number;
}

export function Icon({ name, size = 22, strokeWidth = 1.8, ...rest }: IconProps) {
  return (
    <svg
      width={size} height={size} viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth={strokeWidth}
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...rest}
    >
      {PATHS[name].split('|').map((d, i) => <path key={i} d={d} />)}
    </svg>
  );
}
