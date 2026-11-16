import { useCallback, useEffect, useMemo, useRef, useState, type MouseEvent } from 'react'
import { useStore } from '../lib/store'
import { Icon } from './Icons'
import { extractToc, renderMarkdown, renderMermaidIn, sectionRange } from '../lib/markdown'
import { byteLength, splitHeader } from '../lib/format'

/** 找到正文里离目标行最近的块元素，用于滚动同步与精确跳转。 */
function nearestBlock(root: HTMLElement, line: number): HTMLElement | null {
  const nodes = Array.from(root.querySelectorAll<HTMLElement>('[data-line]'))
  let best: HTMLElement | null = null
  let bestLine = -1
  for (const n of nodes) {
    const l = Number(n.dataset.line ?? '-1')
    if (Number.isNaN(l) || l > line) continue
    if (l >= bestLine) {
      bestLine = l
      best = n
    }
  }
  return best ?? nodes[0] ?? null
}

export default function PreviewPane() {
  const editorText = useStore((s) => s.editorText)
  const currentKey = useStore((s) => s.currentKey)
  const detail = useStore((s) => s.detail)
  const unlockedKeys = useStore((s) => s.unlockedKeys)
  const caretLine = useStore((s) => s.caretLine)
  const jump = useStore((s) => s.jump)
  const theme = useStore((s) => s.theme)
  const viewMode = useStore((s) => s.viewMode)
  const system = useStore((s) => s.system)
  const setPrivateTarget = useStore((s) => s.setPrivateTarget)
  const requestJump = useStore((s) => s.requestJump)
  const toast = useStore((s) => s.toast)

  const scrollRef = useRef<HTMLDivElement>(null)
  const innerRef = useRef<HTMLElement>(null)
  const shownKeyRef = useRef<string | null>(currentKey)
  const userScrollAt = useRef(0)
  const [shownBody, setShownBody] = useState('')
  const [fullOverride, setFullOverride] = useState(false)
  const [sectioned, setSectioned] = useState(false)
  const [progress, setProgress] = useState(0)
  const [lightbox, setLightbox] = useState<string | null>(null)

  const { key: liveKey, desc: liveDesc, rest: body } = splitHeader(editorText)
  const locked = detail?.meta.encrypted ? !unlockedKeys.includes(detail.meta.key) : false

  // 打字时节流渲染；换笔记时立刻刷新，避免闪出上一篇的内容
  useEffect(() => {
    if (shownKeyRef.current !== currentKey) {
      shownKeyRef.current = currentKey
      setShownBody(body)
      setFullOverride(false)
      setSectioned(false)
      setProgress(0)
      if (scrollRef.current) scrollRef.current.scrollTop = 0
      return
    }
    const t = window.setTimeout(() => setShownBody(body), 220)
    return () => window.clearTimeout(t)
  }, [body, currentKey])

  const sizeKb = useMemo(() => byteLength(shownBody) / 1024, [shownBody])
  const onKb = system?.previewSectionOnKb ?? 50
  const offKb = system?.previewSectionOffKb ?? 45

  useEffect(() => {
    if (sizeKb >= onKb) setSectioned(true)
    else if (sizeKb < offKb) setSectioned(false)
  }, [sizeKb, onKb, offKb])

  const fullToc = useMemo(() => extractToc(shownBody), [shownBody])
  const totalLines = useMemo(() => shownBody.split('\n').length, [shownBody])
  const caretBodyLine = Math.max(0, caretLine - 1)

  const range = useMemo(() => {
    if (!sectioned || fullOverride) return null
    return sectionRange(fullToc, caretBodyLine, totalLines)
  }, [sectioned, fullOverride, fullToc, caretBodyLine, totalLines])

  const offset = range?.from ?? 0
  const sliceTo = range?.to ?? -1
  const html = useMemo(() => {
    if (locked) return ''
    const src = sliceTo >= 0 ? shownBody.split('\n').slice(offset, sliceTo).join('\n') : shownBody
    return renderMarkdown(src, currentKey).html
  }, [shownBody, currentKey, offset, sliceTo, locked])

  // mermaid 本地渲染：失败自动降级为源码，不阻断预览
  useEffect(() => {
    const root = innerRef.current
    if (!root || locked) return
    void renderMermaidIn(root, theme)
  }, [html, theme, locked])

  // 编辑区光标 / 外部跳转 → 预览滚动
  useEffect(() => {
    if (locked || viewMode === 'editor') return
    const root = innerRef.current
    const scroller = scrollRef.current
    if (!root || !scroller) return
    if (Date.now() - userScrollAt.current < 1400) return
    const target = nearestBlock(root, caretBodyLine - offset)
    if (!target) return
    const top = target.offsetTop - scroller.clientHeight * 0.32
    scroller.scrollTo({ top: Math.max(0, top), behavior: 'auto' })
  }, [caretBodyLine, offset, locked, viewMode, html])

  useEffect(() => {
    if (!jump || locked) return
    const root = innerRef.current
    const scroller = scrollRef.current
    if (!root || !scroller) return
    const line = jump.line - 1 - offset
    if (line < 0) return
    const target = nearestBlock(root, line)
    if (!target) return
    userScrollAt.current = 0
    scroller.scrollTo({ top: Math.max(0, target.offsetTop - 70), behavior: 'smooth' })
    target.classList.add('fp-flash')
    window.setTimeout(() => target.classList.remove('fp-flash'), 1200)
  }, [jump, locked, offset])

  const onScroll = useCallback(() => {
    const el = scrollRef.current
    if (!el) return
    userScrollAt.current = Date.now()
    const max = el.scrollHeight - el.clientHeight
    setProgress(max > 0 ? Math.min(100, (el.scrollTop / max) * 100) : 0)
  }, [])

  const onClick = async (e: MouseEvent<HTMLElement>) => {
    const el = e.target as HTMLElement

    const copyBtn = el.closest<HTMLButtonElement>('.code-copy')
    if (copyBtn) {
      const code = copyBtn.closest('.code-wrap')?.querySelector('code')?.textContent ?? ''
      try {
        await navigator.clipboard.writeText(code)
        copyBtn.textContent = '已复制'
        copyBtn.classList.add('done')
        window.setTimeout(() => {
          copyBtn.textContent = '复制'
          copyBtn.classList.remove('done')
        }, 1400)
      } catch {
        toast({ kind: 'warn', text: '复制失败', detail: '浏览器拒绝了剪贴板访问。' })
      }
      return
    }

    const img = el.closest<HTMLImageElement>('.preview-inner img')
    if (img) {
      setLightbox(img.currentSrc || img.src)
      return
    }

    if (el.closest('a')) return

    const block = el.closest<HTMLElement>('[data-line]')
    if (block && viewMode !== 'preview') {
      const line = Number(block.dataset.line ?? '0') + offset
      requestJump(line + 1)
    }
  }

  if (locked && detail) {
    return (
      <section className="preview-pane" aria-label="预览区">
        <div className="empty">
          <div className="lock-ring" style={{ marginBottom: 4 }}>
            <Icon name="lock" size={26} />
          </div>
          <h3>预览已上锁</h3>
          <p>
            这篇笔记的正文是密文。解锁后预览、大纲与检索摘要都只在这台浏览器里生成，
            服务端拿不到明文。
          </p>
          <button className="btn primary" onClick={() => setPrivateTarget({ mode: 'unlock', key: detail.meta.key })}>
            <Icon name="unlock" size={14} /> 解锁后预览
          </button>
        </div>
      </section>
    )
  }

  return (
    <section className="preview-pane" ref={scrollRef} onScroll={onScroll} aria-label="预览区">
      <div
        aria-hidden="true"
        style={{
          position: 'sticky',
          top: 0,
          height: 2,
          zIndex: 21,
          background: 'var(--line-soft)',
          opacity: progress > 0.5 ? 1 : 0,
          transition: 'opacity 200ms var(--ease)',
        }}
      >
        <i
          style={{
            display: 'block',
            height: '100%',
            width: `${progress}%`,
            background: 'linear-gradient(90deg, var(--cyan), var(--violet), var(--magenta))',
          }}
        />
      </div>

      <article className="preview-inner" ref={innerRef} onClick={(e) => void onClick(e)}>
        {liveKey ? (
          <div className="preview-command">
            <Icon name="zap" size={15} style={{ color: 'var(--acid)' }} />
            <span className="pk">{liveKey}</span>
            <span className="pd">{liveDesc}</span>
            {detail?.meta.encrypted ? (
              <span className="badge private">
                <Icon name="shield" size={10} /> 端到端加密
              </span>
            ) : null}
            {detail?.meta.domain ? (
              <span className="badge violet">
                {detail.meta.domain}
                {detail.meta.topic ? ` · ${detail.meta.topic}` : ''}
              </span>
            ) : null}
            <span className="spacer" />
            <span className="eyebrow">{fullToc.length} 个标题 · {sizeKb.toFixed(1)} KB</span>
          </div>
        ) : null}

        {range ? (
          <div className="section-banner">
            <Icon name="layers" size={14} />
            <span>
              大文档分节预览 · 当前章节 <b>{range.title}</b>
            </span>
            <button onClick={() => setFullOverride(true)}>显示全文</button>
          </div>
        ) : null}

        {shownBody.trim() ? (
          <div dangerouslySetInnerHTML={{ __html: html }} />
        ) : (
          <div className="preview-empty">
            <Icon name="edit" size={22} style={{ opacity: 0.5, marginBottom: 8 }} />
            <div>左侧写点什么，这里会实时渲染。</div>
            <div className="eyebrow" style={{ marginTop: 8 }}>
              支持表格 · 代码高亮 · KaTeX 公式 · Mermaid 图表 · 任务列表
            </div>
          </div>
        )}

        {sectioned && fullOverride ? (
          <div className="section-banner" style={{ position: 'static', margin: '18px 0 0' }}>
            <Icon name="layers" size={14} />
            <span>已临时展开全文（{sizeKb.toFixed(1)} KB），滚动可能变慢</span>
            <button onClick={() => setFullOverride(false)}>回到分节</button>
          </div>
        ) : null}
      </article>

      {lightbox ? (
        <div className="lightbox" onClick={() => setLightbox(null)} role="dialog" aria-label="图片放大">
          <img src={lightbox} alt="放大预览" />
        </div>
      ) : null}
    </section>
  )
}