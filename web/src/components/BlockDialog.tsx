import { useEffect, useMemo, useRef, useState, type MouseEvent } from 'react'
import { useStore } from '../lib/store'
import { Icon } from './Icons'
import { renderMarkdown, renderMermaidIn } from '../lib/markdown'
import { byteLength } from '../lib/format'

const STATUS: Record<string, { label: string; cls: string; icon: string }> = {
  draft: { label: '草稿', cls: '', icon: 'edit' },
  verified: { label: '已验证', cls: 'acid', icon: 'check' },
  deprecated: { label: '已废弃', cls: 'amber', icon: 'alert' },
  archived: { label: '已归档', cls: 'violet', icon: 'bookmark' },
}

export default function BlockDialog() {
  const block = useStore((s) => s.blockDetail)
  const currentKey = useStore((s) => s.currentKey)
  const theme = useStore((s) => s.theme)
  const notes = useStore((s) => s.notes)
  const setBlockDetail = useStore((s) => s.setBlockDetail)
  const requestJump = useStore((s) => s.requestJump)
  const openNote = useStore((s) => s.openNote)
  const setViewMode = useStore((s) => s.setViewMode)
  const toast = useStore((s) => s.toast)

  const [mode, setMode] = useState<'source' | 'render'>('render')
  const [copied, setCopied] = useState<string | null>(null)
  const renderRef = useRef<HTMLDivElement>(null)

  const html = useMemo(
    () => (block ? renderMarkdown(block.body, block.noteKey).html : ''),
    [block],
  )

  useEffect(() => {
    if (mode === 'render' && renderRef.current) void renderMermaidIn(renderRef.current, theme)
  }, [html, mode, theme])

  useEffect(() => {
    setMode('render')
    setCopied(null)
  }, [block?.blockId])

  if (!block) return null

  const close = () => setBlockDetail(null)
  const status = STATUS[block.status ?? 'draft'] ?? STATUS.draft
  const note = notes.find((n) => n.key === block.noteKey)
  const lines = block.body.split('\n').length

  const copy = async (text: string, what: string) => {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(what)
      window.setTimeout(() => setCopied((c) => (c === what ? null : c)), 1600)
    } catch {
      toast({ kind: 'warn', text: '复制失败', detail: '浏览器拒绝了剪贴板访问，请手动选中复制。' })
    }
  }

  const jump = () => {
    setViewMode('split')
    requestJump(block.startLine + 1, 0, block.endLine + 1)
    close()
  }

  const onRenderClick = (e: MouseEvent<HTMLDivElement>) => {
    const btn = (e.target as HTMLElement).closest<HTMLButtonElement>('[data-copy]')
    if (!btn) return
    const code = btn.closest('.code-wrap')?.querySelector('code')?.textContent ?? ''
    void copy(code, 'code')
    btn.textContent = '已复制'
    btn.classList.add('done')
    window.setTimeout(() => {
      btn.textContent = '复制'
      btn.classList.remove('done')
    }, 1400)
  }

  return (
    <div className="overlay" onClick={close}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-label="知识块详情">
        <div className="modal-head">
          <h2>
            <Icon name="layers" size={17} style={{ color: 'var(--violet)' }} />
            知识块
          </h2>
          <span className="badge violet mono">H{block.level}</span>
          <span className={`badge ${status.cls}`}>
            <Icon name={status.icon} size={11} />
            {status.label}
          </span>
          <span className="spacer" />
          <button className="icon-btn" onClick={close} aria-label="关闭">
            <Icon name="x" size={16} />
          </button>
        </div>

        <div className="modal-body">
          <div className="block-detail">
            <div className="block-path">
              {block.path.map((p, i) => (
                <span key={`${p}-${i}`} className="row" style={{ gap: 5 }}>
                  {i > 0 ? <Icon name="chevronRight" size={11} className="sep" /> : null}
                  <span style={i === block.path.length - 1 ? { color: 'var(--text)', fontWeight: 600 } : undefined}>
                    {p}
                  </span>
                </span>
              ))}
            </div>

            <div className="row" style={{ gap: 7 }}>
              <span className="mono faint" style={{ fontSize: 12 }}>
                {'#'.repeat(Math.max(1, Math.min(6, block.level)))}
              </span>
              <b style={{ fontSize: 14.5, fontFamily: 'var(--font-display)' }}>{block.title || '(无标题)'}</b>
            </div>

            <div className="row" style={{ gap: 6 }}>
              <span className="badge cyan mono" style={{ maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {block.blockId}
              </span>
              <button
                className="icon-btn sm"
                title="复制块 ID"
                aria-label="复制块 ID"
                onClick={() => void copy(block.blockId, 'id')}
              >
                <Icon name={copied === 'id' ? 'check' : 'copy'} size={13} />
              </button>
              <span className="spacer" />
              <div className="seg">
                <button className={mode === 'render' ? 'on' : ''} onClick={() => setMode('render')}>
                  <Icon name="eye" size={12} /> 渲染
                </button>
                <button className={mode === 'source' ? 'on' : ''} onClick={() => setMode('source')}>
                  <Icon name="code" size={12} /> 源码
                </button>
              </div>
            </div>

            {mode === 'source' ? (
              <div className="block-body">{block.body || '(这个块没有正文)'}</div>
            ) : (
              <div
                className="preview-inner block-render"
                ref={renderRef}
                onClick={onRenderClick}
                dangerouslySetInnerHTML={{ __html: html }}
              />
            )}

            <dl className="kv">
              <dt>所属笔记</dt>
              <dd>
                <button
                  className="hit-key"
                  style={{ padding: 0 }}
                  onClick={() => {
                    close()
                    if (block.noteKey !== currentKey) void openNote(block.noteKey)
                  }}
                  title="打开这篇笔记"
                >
                  {block.noteKey}
                </button>
                {note?.desc ? <span className="faint"> · {note.desc}</span> : null}
                {note?.encrypted ? (
                  <span className="badge private" style={{ marginLeft: 6 }}>
                    <Icon name="lock" size={10} /> 私密
                  </span>
                ) : null}
              </dd>
              <dt>编辑器行</dt>
              <dd>
                {block.startLine + 1} – {block.endLine + 1}
              </dd>
              <dt>文件行</dt>
              <dd>
                {block.fileStartLine} – {block.fileEndLine}
              </dd>
              <dt>正文规模</dt>
              <dd>
                {lines} 行 · {byteLength(block.body)} 字节
              </dd>
              <dt>文件</dt>
              <dd>{block.file ?? '—'}</dd>
            </dl>

            {block.status === 'deprecated' ? (
              <div className="callout warn">
                <Icon name="alert" size={15} />
                <div>这个块标记为已废弃，人用档位检索会按降权系数压低它的排名，但仍可被召回。</div>
              </div>
            ) : null}
          </div>
        </div>

        <div className="modal-foot">
          <button className="btn ghost sm" onClick={() => void copy(block.body, 'body')}>
            <Icon name={copied === 'body' ? 'check' : 'copy'} size={13} />
            {copied === 'body' ? '已复制正文' : '复制正文'}
          </button>
          <span className="spacer" />
          <button className="btn ghost" onClick={close}>
            关闭
          </button>
          <button className="btn primary" onClick={jump}>
            <Icon name="target" size={14} /> 跳到编辑器
          </button>
        </div>
      </div>
    </div>
  )
}