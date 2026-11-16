import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/common'
import katex from 'katex'
import { escapeHtml } from './format'

export interface TocEntry {
  level: number
  text: string
  line: number
  slug: string
}

export interface RenderResult {
  html: string
  toc: TocEntry[]
  mermaidCount: number
}

const KU_COMMENT = /<!--\s*ku:[\s\S]*?-->/g

/** 标题锚点：保留中日韩字符，与桌面预览的锚点口径一致（可读、可跳转）。 */
export function slugify(text: string): string {
  const s = text
    .trim()
    .toLowerCase()
    .replace(/[`*_~[\]()#!]/g, '')
    .replace(/\s+/g, '-')
    .replace(/[^\p{Letter}\p{Number}\-_一-鿿]/gu, '')
  return s || 'section'
}

function mathToHtml(tex: string, display: boolean): string {
  try {
    return katex.renderToString(tex, {
      displayMode: display,
      throwOnError: false,
      strict: false,
      output: 'html',
      trust: false,
    })
  } catch {
    return `<code class="math-error">${escapeHtml(tex)}</code>`
  }
}

function createMd(assetBase: string | null): MarkdownIt {
  const md = new MarkdownIt({
    html: false,
    linkify: true,
    breaks: false,
    typographer: false,
    highlight(code, lang) {
      if (lang === 'mermaid') return ''
      if (lang && hljs.getLanguage(lang)) {
        try {
          return hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
        } catch {
          /* 落到自动检测 */
        }
      }
      try {
        return hljs.highlightAuto(code, ['bash', 'sql', 'java', 'python', 'javascript', 'yaml', 'json', 'xml', 'ini', 'powershell']).value
      } catch {
        return escapeHtml(code)
      }
    },
  })

  // ===== 数学：块级 $$ … $$ =====
  md.block.ruler.before(
    'fence',
    'math_block',
    (state, startLine, endLine, silent) => {
      const pos = state.bMarks[startLine] + state.tShift[startLine]
      const max = state.eMarks[startLine]
      if (pos + 2 > max) return false
      if (state.src.slice(pos, pos + 2) !== '$$') return false
      if (silent) return true

      let firstLine = state.src.slice(pos + 2, max)
      let lastLine = ''
      let next = startLine
      let closed = false

      const inlineClose = firstLine.indexOf('$$')
      if (inlineClose >= 0) {
        lastLine = firstLine.slice(0, inlineClose)
        firstLine = firstLine.slice(0, inlineClose)
        closed = true
      } else {
        for (next = startLine + 1; next < endLine; next++) {
          const p = state.bMarks[next] + state.tShift[next]
          const m = state.eMarks[next]
          const line = state.src.slice(p, m)
          const idx = line.indexOf('$$')
          if (idx >= 0) {
            lastLine = line.slice(0, idx)
            closed = true
            break
          }
        }
      }
      if (!closed) return false

      const tex = [firstLine, ...state.getLines(startLine + 1, next, state.tShift[startLine + 1] ?? 0, false), lastLine]
        .join('\n')
        .trim()

      const token = state.push('math_block', 'div', 0)
      token.block = true
      token.content = tex
      token.map = [startLine, next + 1]
      token.markup = '$$'
      state.line = next + 1
      return true
    },
    { alt: ['paragraph', 'reference', 'blockquote', 'list'] },
  )

  // ===== 数学：行内 $ … $ =====
  md.inline.ruler.after('escape', 'math_inline', (state, silent) => {
    const start = state.pos
    if (state.src[start] !== '$') return false
    if (state.src[start + 1] === '$') return false
    // 货币写法 "$5 和 $6" 不该被当成公式：开符号后不能紧跟空白
    if (/\s/.test(state.src[start + 1] ?? '')) return false

    let pos = start + 1
    let found = -1
    while (pos < state.posMax) {
      if (state.src[pos] === '$' && !/\s/.test(state.src[pos - 1] ?? '') && state.src[pos - 1] !== '\\') {
        found = pos
        break
      }
      if (state.src[pos] === '\n') break
      pos++
    }
    if (found < 0) return false
    const tex = state.src.slice(start + 1, found)
    if (!tex.trim() || /^\d+([.,]\d+)*$/.test(tex.trim())) return false
    if (silent) return true

    const token = state.push('math_inline', 'span', 0)
    token.content = tex
    token.markup = '$'
    state.pos = found + 1
    return true
  })

  md.renderer.rules.math_block = (tokens, idx) =>
    `<div class="math-block" data-line="${tokens[idx].map?.[0] ?? 0}">${mathToHtml(tokens[idx].content, true)}</div>`
  md.renderer.rules.math_inline = (tokens, idx) => mathToHtml(tokens[idx].content, false)

  // ===== 代码块：mermaid 交给客户端渲染，其余带语言标签与复制按钮 =====
  const defaultFence =
    md.renderer.rules.fence ??
    ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

  md.renderer.rules.fence = (tokens, idx, options, env, _self) => {
    const token = tokens[idx]
    const info = token.info.trim()
    const lang = info.split(/\s+/)[0] ?? ''
    const line = token.map?.[0] ?? 0

    if (lang === 'mermaid') {
      const n = (env as { mermaidCount?: number }).mermaidCount ?? 0
      ;(env as { mermaidCount?: number }).mermaidCount = n + 1
      return `<div class="mermaid-wrap" data-line="${line}"><pre class="mermaid-src" hidden>${escapeHtml(
        token.content,
      )}</pre><div class="mermaid" data-mermaid="${n}"></div></div>`
    }

    const highlighted = options.highlight?.(token.content, lang, '') ?? escapeHtml(token.content)
    const label = lang || 'text'
    return (
      `<div class="code-wrap" data-line="${line}">` +
      `<div class="code-head"><span class="code-lang mono">${escapeHtml(label)}</span>` +
      `<button class="code-copy" type="button" data-copy>复制</button></div>` +
      `<pre class="code-body"><code class="hljs language-${escapeHtml(label)}">${highlighted}</code></pre>` +
      `</div>`
    )
  }
  void defaultFence

  // ===== 块级元素挂 data-line，供滚动同步与精确跳转 =====
  const blockTags = new Set([
    'paragraph_open',
    'heading_open',
    'blockquote_open',
    'bullet_list_open',
    'ordered_list_open',
    'table_open',
    'hr',
    'list_item_open',
  ])
  const defaultRender =
    md.renderer.rules.text ?? ((tokens, idx, opts, _env, self) => self.renderToken(tokens, idx, opts))
  void defaultRender

  for (const name of blockTags) {
    const original = md.renderer.rules[name]
    md.renderer.rules[name] = (tokens, idx, options, env, self) => {
      const token = tokens[idx]
      if (token.map && token.nesting !== -1) {
        token.attrSet('data-line', String(token.map[0]))
      }
      if (name === 'heading_open') {
        const inline = tokens[idx + 1]
        const text = inline?.type === 'inline' ? inline.content : ''
        token.attrSet('id', slugify(text))
        token.attrSet('data-heading', String(Number(token.tag.slice(1))))
      }
      if (original) return original(tokens, idx, options, env, self)
      return self.renderToken(tokens, idx, options)
    }
  }

  // ===== 图片：assets/ 相对路径改写到接口，外链加 noopener =====
  const defaultImage =
    md.renderer.rules.image ??
    ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

  md.renderer.rules.image = (tokens, idx, options, env, self) => {
    const token = tokens[idx]
    const src = token.attrGet('src') ?? ''
    if (assetBase && src.startsWith('assets/')) {
      token.attrSet('src', `${assetBase}/${encodeURIComponent(src.slice('assets/'.length))}`)
      token.attrSet('loading', 'lazy')
    }
    token.attrSet('data-line', String(token.map?.[0] ?? 0))
    const alt = token.content || ''
    token.attrSet('alt', alt)
    const rendered = defaultImage(tokens, idx, options, env, self)
    return `<span class="img-wrap">${rendered}${alt ? `<span class="img-cap">${escapeHtml(alt)}</span>` : ''}</span>`
  }

  const defaultLinkOpen =
    md.renderer.rules.link_open ??
    ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))
  md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
    const token = tokens[idx]
    const href = token.attrGet('href') ?? ''
    if (/^https?:/i.test(href)) {
      token.attrSet('target', '_blank')
      token.attrSet('rel', 'noopener noreferrer')
    }
    return defaultLinkOpen(tokens, idx, options, env, self)
  }

  // ===== 任务列表 =====
  const defaultListItem =
    md.renderer.rules.list_item_open ??
    ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))
  md.renderer.rules.list_item_open = (tokens, idx, options, env, self) => {
    const inline = tokens[idx + 2]
    if (inline?.type === 'inline' && /^\[[ xX]\]\s+/.test(inline.content)) {
      const done = /^\[[xX]\]/.test(inline.content)
      inline.content = inline.content.replace(/^\[[ xX]\]\s+/, '')
      if (inline.children?.[0]?.type === 'text') {
        inline.children[0].content = inline.children[0].content.replace(/^\[[ xX]\]\s+/, '')
      }
      tokens[idx].attrSet('class', `task ${done ? 'done' : ''}`)
      const box = `<span class="task-box${done ? ' on' : ''}" aria-hidden="true">${done ? '✓' : ''}</span>`
      return defaultListItem(tokens, idx, options, env, self).replace(/^(<li[^>]*>)/, `$1${box}`)
    }
    return defaultListItem(tokens, idx, options, env, self)
  }

  // ===== 表格包一层，窄屏可横向滚动 =====
  const defaultTableOpen =
    md.renderer.rules.table_open ??
    ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))
  md.renderer.rules.table_open = (tokens, idx, options, env, self) =>
    `<div class="table-wrap">${defaultTableOpen(tokens, idx, options, env, self)}`
  const defaultTableClose =
    md.renderer.rules.table_close ??
    ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))
  md.renderer.rules.table_close = (tokens, idx, options, env, self) =>
    `${defaultTableClose(tokens, idx, options, env, self)}</div>`

  return md
}

const mdCache = new Map<string, MarkdownIt>()

function mdFor(assetBase: string | null): MarkdownIt {
  const key = assetBase ?? ''
  let md = mdCache.get(key)
  if (!md) {
    md = createMd(assetBase)
    mdCache.set(key, md)
  }
  return md
}

/** 从源码抽标题，行号是正文行号（不含首行 key desc）。 */
export function extractToc(body: string): TocEntry[] {
  const out: TocEntry[] = []
  const lines = body.split('\n')
  let inFence = false
  let fenceMark = ''
  const used = new Map<string, number>()

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const fence = /^\s{0,3}(`{3,}|~{3,})/.exec(line)
    if (fence) {
      if (!inFence) {
        inFence = true
        fenceMark = fence[1][0]
      } else if (fence[1][0] === fenceMark) {
        inFence = false
      }
      continue
    }
    if (inFence) continue
    const m = /^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line)
    if (!m) continue
    const text = m[2].replace(KU_COMMENT, '').replace(/[*_`]/g, '').trim()
    if (!text) continue
    let slug = slugify(text)
    const n = used.get(slug) ?? 0
    used.set(slug, n + 1)
    if (n > 0) slug = `${slug}-${n}`
    out.push({ level: m[1].length, text, line: i, slug })
  }
  return out
}

export function renderMarkdown(body: string, noteKey: string | null): RenderResult {
  const cleaned = body.replace(KU_COMMENT, '')
  const assetBase = noteKey ? `/api/notes/${encodeURIComponent(noteKey)}/assets` : null
  const env: { mermaidCount?: number } = { mermaidCount: 0 }
  const html = mdFor(assetBase).render(cleaned, env)
  return { html, toc: extractToc(cleaned), mermaidCount: env.mermaidCount ?? 0 }
}

let mermaidPromise: Promise<typeof import('mermaid').default> | null = null

function loadMermaid() {
  if (!mermaidPromise) {
    mermaidPromise = import('mermaid').then((m) => {
      const mermaid = m.default
      mermaid.initialize({
        startOnLoad: false,
        securityLevel: 'strict',
        theme: document.documentElement.dataset.theme === 'paper' ? 'neutral' : 'dark',
        fontFamily: 'var(--font-ui)',
        flowchart: { curve: 'basis', htmlLabels: true },
        sequence: { useMaxWidth: true },
        gantt: { useMaxWidth: true },
      })
      return mermaid
    })
  }
  return mermaidPromise
}

/** 把容器里的 .mermaid 占位渲染成 SVG；失败时降级为源码展示，不阻断预览。 */
export async function renderMermaidIn(root: HTMLElement, theme: string): Promise<void> {
  const nodes = Array.from(root.querySelectorAll<HTMLElement>('.mermaid'))
  if (nodes.length === 0) return
  let mermaid: Awaited<ReturnType<typeof loadMermaid>>
  try {
    mermaid = await loadMermaid()
  } catch {
    for (const node of nodes) fallbackMermaid(node)
    return
  }
  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'strict',
    theme: theme === 'paper' ? 'neutral' : 'dark',
    fontFamily: "'Space Grotesk','Segoe UI','Microsoft YaHei',sans-serif",
    flowchart: { curve: 'basis', htmlLabels: true },
    sequence: { useMaxWidth: true },
    gantt: { useMaxWidth: true },
  })

  for (let i = 0; i < nodes.length; i++) {
    const node = nodes[i]
    const src = node.parentElement?.querySelector<HTMLPreElement>('.mermaid-src')?.textContent ?? ''
    if (!src.trim()) continue
    if (node.dataset.rendered === theme) continue
    try {
      const { svg } = await mermaid.render(`fp-mmd-${Date.now()}-${i}`, src)
      node.innerHTML = svg
      node.dataset.rendered = theme
      node.classList.remove('failed')
      const svgEl = node.querySelector('svg')
      if (svgEl) {
        svgEl.style.maxWidth = '100%'
        svgEl.removeAttribute('height')
      }
    } catch {
      fallbackMermaid(node)
    }
  }
}

function fallbackMermaid(node: HTMLElement) {
  const src = node.parentElement?.querySelector<HTMLPreElement>('.mermaid-src')?.textContent ?? ''
  node.classList.add('failed')
  node.innerHTML = `<div class="mermaid-fail"><span class="eyebrow">图表渲染失败</span><pre class="mono">${escapeHtml(src)}</pre></div>`
}

/** 大文档分节：只渲染光标所在章节，与桌面 50/45KB 阈值同口径。 */
export function sectionRange(
  toc: TocEntry[],
  caretLine: number,
  totalLines: number,
): { from: number; to: number; title: string } | null {
  const tops = toc.filter((t) => t.level <= 2)
  if (tops.length < 2) return null
  let idx = 0
  for (let i = 0; i < tops.length; i++) {
    if (tops[i].line <= caretLine) idx = i
  }
  const from = tops[idx].line
  const to = idx + 1 < tops.length ? tops[idx + 1].line : totalLines
  return { from, to, title: tops[idx].text }
}