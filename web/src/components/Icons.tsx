import type { SVGProps } from 'react'

const P: Record<string, string> = {
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14ZM20 20l-4.2-4.2',
  plus: 'M12 5v14M5 12h14',
  save: 'M5 4h11l3 3v13H5zM8 4v5h7V4M8 20v-6h8v6',
  trash: 'M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6',
  undo: 'M4 9h11a5 5 0 0 1 0 10h-6M4 9l4-4M4 9l4 4',
  lock: 'M6 11h12v9H6zM9 11V8a3 3 0 0 1 6 0v3M12 15v2',
  unlock: 'M6 11h12v9H6zM9 11V8a3 3 0 0 1 5.6-1.5M12 15v2',
  key: 'M14.5 4a5.5 5.5 0 1 0-4.2 9L4 19.3V21h2v-1h2v-2h2l1.3-1.3a5.5 5.5 0 0 0 1.2-12.7ZM16 9h.01',
  settings:
    'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 1 1-4 0v-.1A1.6 1.6 0 0 0 7.5 19.4l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.6 1.6 0 0 0 3 15H3a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.6 7.5l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1A1.6 1.6 0 0 0 9 3.6V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1A1.6 1.6 0 0 0 20.4 9H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 2Z',
  cloudUp: 'M7 18a4 4 0 0 1-.6-8A6 6 0 0 1 18 9.5a3.5 3.5 0 0 1-.5 8.5M12 21V12M9 15l3-3 3 3',
  cloudDown: 'M7 16a4 4 0 0 1-.6-8A6 6 0 0 1 18 7.5a3.5 3.5 0 0 1-.5 8.5M12 12v9M9 18l3 3 3-3',
  tree: 'M6 4v12a2 2 0 0 0 2 2h3M6 8h5M14 6h6v4h-6zM14 16h6v4h-6zM11 4h.01',
  list: 'M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01',
  toc: 'M4 6h16M7 12h13M10 18h10M4 12h.01M4 18h.01',
  chevronRight: 'M9 5l7 7-7 7',
  chevronDown: 'M5 9l7 7 7-7',
  chevronLeft: 'M15 5l-7 7 7 7',
  chevronUp: 'M5 15l7-7 7 7',
  x: 'M6 6l12 12M18 6L6 18',
  check: 'M4 12.5l5 5L20 6.5',
  copy: 'M9 9h11v11H9zM5 15H4V4h11v1',
  edit: 'M4 20h4L20 8l-4-4L4 16zM14 6l4 4',
  eye: 'M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7ZM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z',
  columns: 'M4 4h16v16H4zM12 4v16',
  panelLeft: 'M4 4h16v16H4zM9 4v16',
  panelRight: 'M4 4h16v16H4zM15 4v16',
  sun: 'M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10ZM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
  moon: 'M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z',
  command: 'M9 6a3 3 0 1 0-3 3h12a3 3 0 1 0-3-3v12a3 3 0 1 0 3-3H6a3 3 0 1 0 3 3Z',
  sparkles: 'M12 3l1.8 4.7L18.5 9.5l-4.7 1.8L12 16l-1.8-4.7L5.5 9.5l4.7-1.8ZM18 15l.9 2.3L21 18l-2.1.8L18 21l-.9-2.2L15 18l2.1-.7Z',
  tag: 'M3 12V4h8l9 9-8 8ZM7.5 7.5h.01',
  folder: 'M3 6h6l2 2h10v11H3z',
  file: 'M6 3h8l4 4v14H6zM14 3v4h4',
  clock: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18ZM12 7v5l3 2',
  layers: 'M12 3l9 5-9 5-9-5ZM3 13l9 5 9-5M3 17l9 5 9-5',
  shield: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6ZM9 12l2 2 4-4',
  alert: 'M12 4l9 16H3ZM12 10v4M12 17h.01',
  info: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18ZM12 11v5M12 8h.01',
  refresh: 'M20 11a8 8 0 1 0-1.6 5.6M20 5v6h-6',
  maximize: 'M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5',
  minimize: 'M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5',
  filter: 'M3 5h18l-7 8v6l-4 2v-8Z',
  sort: 'M7 4v16M4 17l3 3 3-3M17 20V4M14 7l3-3 3 3',
  more: 'M6 12h.01M12 12h.01M18 12h.01',
  arrowRight: 'M4 12h16M14 6l6 6-6 6',
  book: 'M4 4h7a3 3 0 0 1 3 3v13a2.5 2.5 0 0 0-2.5-2.5H4ZM20 4h-7a3 3 0 0 0-3 3v13a2.5 2.5 0 0 1 2.5-2.5H20Z',
  cpu: 'M6 6h12v12H6zM9 9h6v6H9zM9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M2 15h4M18 9h4M18 15h4',
  database: 'M12 3c4.4 0 8 1.3 8 3s-3.6 3-8 3-8-1.3-8-3 3.6-3 8-3ZM4 6v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3',
  zap: 'M13 2L4 14h7l-1 8 9-12h-7Z',
  hash: 'M5 9h14M5 15h14M10 3L8 21M16 3l-2 18',
  terminal: 'M4 5h16v14H4zM8 10l2.5 2.5L8 15M13 15h4',
  image: 'M3 5h18v14H3zM3 16l5-5 4 4 3-3 6 6M15.5 8.5h.01',
  link: 'M10 13a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7L11.5 5.8M14 11a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1.5-1.5',
  bold: 'M7 4h6a4 4 0 0 1 0 8H7zM7 12h7a4 4 0 0 1 0 8H7z',
  italic: 'M10 4h8M6 20h8M14 4l-4 16',
  code: 'M8 6l-5 6 5 6M16 6l5 6-5 6M13 4l-2 16',
  quote: 'M7 7H4v6h3c0 2-1 3-3 3M17 7h-3v6h3c0 2-1 3-3 3',
  table: 'M3 5h18v14H3zM3 10h18M3 15h18M9 5v14M15 5v14',
  heading: 'M6 4v16M18 4v16M6 12h12',
  keyboard: 'M3 6h18v12H3zM7 10h.01M11 10h.01M15 10h.01M17 10h.01M7 14h10',
  logout: 'M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10',
  activity: 'M3 12h4l3 8 4-16 3 8h4',
  target: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18ZM12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM12 12h.01',
  compass: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18ZM15.5 8.5l-2 5-5 2 2-5Z',
  bookmark: 'M6 3h12v18l-6-4-6 4Z',
  star: 'M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1L3.2 9.5l6.1-.9Z',
  grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  play: 'M7 4l12 8-12 8Z',
  pause: 'M8 5v14M16 5v14',
  download: 'M12 3v12M8 11l4 4 4-4M4 20h16',
  upload: 'M12 21V9M8 13l4-4 4 4M4 4h16',
  external: 'M14 4h6v6M20 4l-9 9M18 14v6H4V6h6',
  wifi: 'M2 8.5a15 15 0 0 1 20 0M5.5 12a10 10 0 0 1 13 0M9 15.5a5 5 0 0 1 6 0M12 19h.01',
  wifiOff: 'M2 8.5a15 15 0 0 1 6-3.3M12 5c3.7 0 7.2 1.2 10 3.5M5.5 12a10 10 0 0 1 4-2.3M14 10.7c1.7.4 3.2 1.2 4.5 2.3M9 15.5a5 5 0 0 1 5.4-.9M12 19h.01M3 3l18 18',
  history: 'M3 12a9 9 0 1 0 3-6.7M3 4v5h5M12 8v4l3 2',
  split: 'M4 4h16v16H4zM12 4v16M8 9l-2 3 2 3M16 9l2 3-2 3',
  wrap: 'M4 6h16M4 12h13a3 3 0 0 1 0 6h-3M7 15l-3 3 3 3M4 18h6',
}

export type IconName = keyof typeof P | string

interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'name'> {
  name: IconName
  size?: number
}

export function Icon({ name, size = 16, ...rest }: IconProps) {
  const d = P[name] ?? P.info
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      <path d={d} />
    </svg>
  )
}

/** 品牌标记：猪鼻 + 三道速度线，呼应"快捷命令一击即达"。 */
export function Logo({ size = 30 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="fp-snout" x1="8" y1="8" x2="40" y2="40" gradientUnits="userSpaceOnUse">
          <stop stopColor="#22d3ee" />
          <stop offset="0.55" stopColor="#4c8dff" />
          <stop offset="1" stopColor="#9a7bff" />
        </linearGradient>
        <linearGradient id="fp-speed" x1="0" y1="24" x2="18" y2="24" gradientUnits="userSpaceOnUse">
          <stop stopColor="#a3e635" stopOpacity="0" />
          <stop offset="1" stopColor="#a3e635" />
        </linearGradient>
      </defs>
      <path d="M2 17h11M0 24h13M2 31h11" stroke="url(#fp-speed)" strokeWidth="2.4" strokeLinecap="round" />
      <rect x="15" y="10" width="30" height="28" rx="12" fill="url(#fp-snout)" />
      <rect x="15" y="10" width="30" height="28" rx="12" stroke="#e9efff" strokeOpacity="0.22" strokeWidth="1" />
      <ellipse cx="25" cy="24" rx="3.1" ry="4.4" fill="#04121a" fillOpacity="0.82" />
      <ellipse cx="35" cy="24" rx="3.1" ry="4.4" fill="#04121a" fillOpacity="0.82" />
      <path d="M20 15c2-1.6 4-2.2 6-2" stroke="#fff" strokeOpacity="0.4" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  )
}

/** 空态插画：命令行卡片 + 检索光束 */
export function EmptyArt({ kind = 'note' }: { kind?: 'note' | 'search' | 'tree' }) {
  return (
    <svg className="empty-art" viewBox="0 0 160 160" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="ea-g" x1="20" y1="20" x2="140" y2="140" gradientUnits="userSpaceOnUse">
          <stop stopColor="var(--cyan)" />
          <stop offset="0.5" stopColor="var(--violet)" />
          <stop offset="1" stopColor="var(--magenta)" />
        </linearGradient>
      </defs>
      <circle cx="80" cy="80" r="62" stroke="var(--line)" strokeWidth="1" strokeDasharray="3 6" />
      <circle cx="80" cy="80" r="44" stroke="var(--line-soft)" strokeWidth="1" />
      {kind === 'search' ? (
        <>
          <circle cx="72" cy="72" r="26" stroke="url(#ea-g)" strokeWidth="3" />
          <path d="M91 91l24 24" stroke="url(#ea-g)" strokeWidth="4" strokeLinecap="round" />
          <path d="M60 72h24M60 80h16" stroke="var(--muted)" strokeWidth="2" strokeLinecap="round" />
        </>
      ) : kind === 'tree' ? (
        <>
          <path d="M52 44v72M52 66h22M52 96h22" stroke="var(--line-strong)" strokeWidth="2" strokeLinecap="round" />
          <rect x="74" y="56" width="42" height="20" rx="5" stroke="url(#ea-g)" strokeWidth="2" />
          <rect x="74" y="86" width="42" height="20" rx="5" stroke="var(--violet)" strokeWidth="2" />
          <circle cx="52" cy="44" r="5" fill="var(--cyan)" />
        </>
      ) : (
        <>
          <rect x="38" y="42" width="84" height="76" rx="9" stroke="url(#ea-g)" strokeWidth="2" />
          <path d="M38 60h84" stroke="var(--line-strong)" strokeWidth="1.5" />
          <circle cx="48" cy="51" r="2.4" fill="var(--vermilion)" />
          <circle cx="57" cy="51" r="2.4" fill="var(--amber)" />
          <circle cx="66" cy="51" r="2.4" fill="var(--acid)" />
          <path d="M50 74l8 8-8 8M66 90h22" stroke="var(--cyan)" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M50 104h44" stroke="var(--line-strong)" strokeWidth="2" strokeLinecap="round" />
        </>
      )}
    </svg>
  )
}