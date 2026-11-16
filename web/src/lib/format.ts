export function relTime(ts: number): string {
  if (!ts) return '—'
  const diff = Date.now() - ts
  if (diff < 0) return '刚刚'
  const s = Math.floor(diff / 1000)
  if (s < 60) return '刚刚'
  const m = Math.floor(s / 60)
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  const d = Math.floor(h / 24)
  if (d < 30) return `${d} 天前`
  return absDate(ts)
}

export function absDate(ts: number): string {
  if (!ts) return '—'
  const d = new Date(ts)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

export function shortDate(ts: number): string {
  if (!ts) return '—'
  const d = new Date(ts)
  const p = (n: number) => String(n).padStart(2, '0')
  const now = new Date()
  const sameYear = d.getFullYear() === now.getFullYear()
  return sameYear
    ? `${p(d.getMonth() + 1)}-${p(d.getDate())}`
    : `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

export function duration(ms: number): string {
  if (ms < 1000) return `${Math.max(0, Math.round(ms))}ms`
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s}s`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}m ${s % 60}s`
  const h = Math.floor(m / 60)
  return `${h}h ${m % 60}m`
}

export function bytes(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(2)} MB`
}

export function byteLength(s: string): number {
  return new TextEncoder().encode(s ?? '').length
}

/** 桌面端正文首行是 "key desc"，编辑器行号要 +1 才对应文件行号。 */
export function editorLineToFileLine(line: number): number {
  return line + 1
}

export function splitHeader(body: string): { key: string; desc: string; rest: string } {
  const idx = body.indexOf('\n')
  const first = idx < 0 ? body : body.slice(0, idx)
  const rest = idx < 0 ? '' : body.slice(idx + 1)
  const m = /^(\S+)\s*(.*)$/.exec(first.trim())
  return { key: m?.[1] ?? '', desc: m?.[2] ?? '', rest }
}

export function joinHeader(key: string, desc: string, rest: string): string {
  return `${key} ${desc}\n${rest}`
}

/** 去掉 ku 注释与 front-matter 后的可读字数，用于状态栏。 */
export function visibleChars(body: string): number {
  return body
    .replace(/<!--\s*ku:[^>]*-->/g, '')
    .replace(/^---[\s\S]*?---\s*/, '')
    .replace(/\s/g, '').length
}

export function countLines(body: string): number {
  if (!body) return 0
  return body.split('\n').length
}

export function clamp(n: number, lo: number, hi: number): number {
  return Math.min(hi, Math.max(lo, n))
}

export function highlightRanges(text: string, query: string): Array<[number, number]> {
  if (!query.trim()) return []
  const terms = query
    .split(/[\s,，、]+/)
    .map((t) => t.trim())
    .filter((t) => t.length >= 1)
  const ranges: Array<[number, number]> = []
  const lower = text.toLowerCase()
  for (const term of terms) {
    const t = term.toLowerCase()
    let from = 0
    for (;;) {
      const i = lower.indexOf(t, from)
      if (i < 0) break
      ranges.push([i, i + t.length])
      from = i + t.length
      if (ranges.length > 400) return ranges
    }
  }
  return ranges
}

export function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export const CHANNEL_LABEL: Record<string, string> = {
  EXACT: '精确',
  BM25: '关键词',
  VECTOR: '语义',
}

export const CHANNEL_CLASS: Record<string, string> = {
  EXACT: 'ch-exact',
  BM25: 'ch-bm25',
  VECTOR: 'ch-vector',
}