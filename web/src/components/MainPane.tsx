import { useEffect, useRef, useState } from 'react'
import { useStore } from '../lib/store'
import { Icon, EmptyArt } from './Icons'
import EditorPane from './EditorPane'
import PreviewPane from './PreviewPane'
import { relTime } from '../lib/format'

function Grip({ side }: { side: 'list' | 'side' }) {
  const [dragging, setDragging] = useState(false)
  const start = useRef({ x: 0, w: 0 })

  useEffect(() => {
    if (!dragging) return
    const onMove = (e: PointerEvent) => {
      const dx = e.clientX - start.current.x
      if (side === 'list') useStore.getState().setListW(start.current.w + dx)
      else useStore.getState().setSideW(start.current.w - dx)
    }
    const onUp = () => setDragging(false)
    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', onUp)
    window.addEventListener('pointercancel', onUp)
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    return () => {
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', onUp)
      window.removeEventListener('pointercancel', onUp)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }
  }, [dragging, side])

  return (
    <div
      className={`grip${dragging ? ' dragging' : ''}`}
      style={side === 'list' ? { left: -4 } : { right: -4 }}
      role="separator"
      aria-orientation="vertical"
      aria-label={side === 'list' ? '调整列表宽度' : '调整侧栏宽度'}
      onPointerDown={(e) => {
        start.current = {
          x: e.clientX,
          w: side === 'list' ? useStore.getState().listW : useStore.getState().sideW,
        }
        e.preventDefault()
        setDragging(true)
      }}
      onDoubleClick={() => (side === 'list' ? useStore.getState().setListW(274) : useStore.getState().setSideW(316))}
    />
  )
}

function PrivateMenu() {
  const detail = useStore((s) => s.detail)
  const unlockedKeys = useStore((s) => s.unlockedKeys)
  const setPrivateTarget = useStore((s) => s.setPrivateTarget)
  const lockNote = useStore((s) => s.lockNote)
  const config = useStore((s) => s.config)
  const [open, setOpen] = useState(false)
  const wrapRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onDown = (e: PointerEvent) => {
      if (!wrapRef.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (!detail) return null
  const meta = detail.meta
  const unlocked = unlockedKeys.includes(meta.key)
  const masterSet = config?.masterPasswordSet ?? false

  const item = (icon: string, label: string, sub: string, run: () => void, danger = false) => (
    <button
      className="suggest-item"
      onClick={() => {
        setOpen(false)
        run()
      }}
    >
      <Icon name={icon} size={15} style={{ color: danger ? 'var(--danger)' : 'var(--cyan)', flex: 'none' }} />
      <span style={{ minWidth: 0 }}>
        <span className="sd" style={{ display: 'block', color: danger ? 'var(--danger)' : 'var(--text)', whiteSpace: 'normal' }}>
          {label}
        </span>
        <span className="sd" style={{ display: 'block', fontSize: 10.5 }}>
          {sub}
        </span>
      </span>
    </button>
  )

  return (
    <div ref={wrapRef} style={{ position: 'relative' }}>
      <button
        className={`icon-btn${meta.encrypted ? ' on' : ''}`}
        title={meta.encrypted ? (unlocked ? '私密笔记 · 已解锁' : '私密笔记 · 锁定中') : '端到端加密'}
        aria-label="私密笔记操作"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        style={meta.encrypted ? { color: unlocked ? 'var(--acid)' : 'var(--vermilion)' } : undefined}
      >
        <Icon name={meta.encrypted ? (unlocked ? 'unlock' : 'lock') : 'shield'} size={15} />
      </button>
      {open ? (
        <div className="suggest-pop" style={{ left: 'auto', right: 0, width: 252, top: 'calc(100% + 6px)' }}>
          <div className="palette-group eyebrow">端到端加密</div>
          {!meta.encrypted
            ? item('shield', '启用私密加密', masterSet ? 'AES-GCM · 明文不出浏览器' : '需先设置主密码', () =>
                setPrivateTarget({ mode: 'enable', key: meta.key }),
              )
            : null}
          {meta.encrypted && !unlocked
            ? item('unlock', '解锁笔记', '用笔记密码或主密码', () => setPrivateTarget({ mode: 'unlock', key: meta.key }))
            : null}
          {meta.encrypted && unlocked
            ? item('lock', '立即锁定', '清除内存中的密钥', () => lockNote(meta.key))
            : null}
          {meta.encrypted && unlocked
            ? item('key', '修改笔记密码', '重新包裹数据密钥', () => setPrivateTarget({ mode: 'change', key: meta.key }))
            : null}
          {meta.encrypted && unlocked
            ? item('alert', '取消加密', '正文改为明文落盘', () => setPrivateTarget({ mode: 'disable', key: meta.key }), true)
            : null}
          <div className="palette-foot" style={{ borderTop: '1px solid var(--line-soft)', padding: '8px 10px' }}>
            <Icon name="info" size={12} />
            <span>服务端只存密文与包裹密钥</span>
          </div>
        </div>
      ) : null}
    </div>
  )
}

function EmptyMain() {
  const notes = useStore((s) => s.notes)
  const setNewNoteOpen = useStore((s) => s.setNewNoteOpen)
  const openNote = useStore((s) => s.openNote)
  const setSideTab = useStore((s) => s.setSideTab)
  const setPalette = useStore((s) => s.setPalette)
  const recent = [...notes].sort((a, b) => b.updatedAt - a.updatedAt).slice(0, 5)

  return (
    <div className="empty">
      <EmptyArt kind="note" />
      <h3>敲一个快捷命令，知识立刻到手</h3>
      <p>
        顶栏输入拼音前缀（如 <span className="mono" style={{ color: 'var(--cyan)' }}>mysql</span>）直达笔记；
        或者用三通道混合检索，按语义找到那一段你记不清标题的知识块。
      </p>
      <div className="empty-keys">
        <span className="kbd">Ctrl</span>
        <span className="kbd">K</span>
        <span className="faint" style={{ fontSize: 11.5 }}>命令面板</span>
        <span className="status-sep" />
        <span className="kbd">Ctrl</span>
        <span className="kbd">Alt</span>
        <span className="kbd">N</span>
        <span className="faint" style={{ fontSize: 11.5 }}>新建笔记</span>
        <span className="status-sep" />
        <span className="kbd">Alt</span>
        <span className="kbd">3</span>
        <span className="faint" style={{ fontSize: 11.5 }}>混合检索</span>
      </div>
      <div className="row" style={{ marginTop: 6 }}>
        <button className="btn primary" onClick={() => setNewNoteOpen(true)}>
          <Icon name="plus" size={14} /> 新建笔记
        </button>
        <button className="btn" onClick={() => setPalette(true)}>
          <Icon name="command" size={14} /> 命令面板
        </button>
        <button className="btn ghost" onClick={() => setSideTab('tree')}>
          <Icon name="tree" size={14} /> 浏览目录
        </button>
      </div>
      {recent.length > 0 ? (
        <div className="col" style={{ marginTop: 18, alignItems: 'center', gap: 9 }}>
          <span className="eyebrow">最近编辑</span>
          <div className="row wrap" style={{ justifyContent: 'center' }}>
            {recent.map((n) => (
              <button key={n.key} className="chip" onClick={() => void openNote(n.key)} title={n.desc}>
                {n.encrypted ? <Icon name="lock" size={10} /> : null}
                <span className="mono">{n.key}</span>
                <span className="faint" style={{ fontSize: 10.5 }}>{relTime(n.updatedAt)}</span>
              </button>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  )
}

export default function MainPane() {
  const currentKey = useStore((s) => s.currentKey)
  const composing = useStore((s) => s.composing)
  const detail = useStore((s) => s.detail)
  const editorText = useStore((s) => s.editorText)
  const viewMode = useStore((s) => s.viewMode)
  const setViewMode = useStore((s) => s.setViewMode)
  const setDrawer = useStore((s) => s.setDrawer)
  const closeNote = useStore((s) => s.closeNote)
  const removeNote = useStore((s) => s.removeNote)
  const toast = useStore((s) => s.toast)
  const mobile = useStore((s) => s.mobile)
  const dirty = useStore((s) => s.dirty)
  const unlockedKeys = useStore((s) => s.unlockedKeys)
  const [confirmDel, setConfirmDel] = useState(false)

  const nl = editorText.indexOf('\n')
  const firstLine = nl >= 0 ? editorText.slice(0, nl) : editorText
  const m = /^(\S+)\s*(.*)$/.exec(firstLine.trim())
  const liveKey = m?.[1] ?? ''
  const liveDesc = (m?.[2] ?? '').trim()
  const meta = detail?.meta
  const locked = meta?.encrypted ? !unlockedKeys.includes(meta.key) : false

  useEffect(() => {
    setConfirmDel(false)
  }, [currentKey])

  const copyKey = async () => {
    const k = meta?.key ?? liveKey
    if (!k) return
    try {
      await navigator.clipboard.writeText(k)
      toast({ kind: 'success', text: `已复制 ${k}`, ttl: 1600 })
    } catch {
      toast({ kind: 'warn', text: '复制失败', detail: '浏览器拒绝了剪贴板访问，请手动选中复制。' })
    }
  }

  return (
    <main className="main-pane">
      {!mobile ? <Grip side="list" /> : null}
      {!mobile ? <Grip side="side" /> : null}

      {currentKey || composing ? (
        <>
          <header className="main-head">
            <div className="main-title">
              <span className="main-key">{meta?.key ?? (liveKey || '新笔记')}</span>
              <span className="main-desc truncate">{liveDesc || meta?.desc || '首行写「key 描述」'}</span>
            </div>
            {meta?.encrypted ? (
              <span className={`badge private${locked ? '' : ' acid'}`} title={locked ? '锁定中' : '已解锁'}>
                <Icon name={locked ? 'lock' : 'unlock'} size={10} />
                {locked ? '私密' : '已解锁'}
              </span>
            ) : null}
            {meta?.domain ? (
              <span className="badge violet status-hide-sm">
                <Icon name="folder" size={10} />
                {meta.domain}
                {meta.topic ? ` · ${meta.topic}` : ''}
              </span>
            ) : meta ? (
              <span className="badge amber status-hide-sm">未分类</span>
            ) : null}
            {dirty ? <span className="badge amber">未保存</span> : null}

            <div className="main-tools">
              <div className="seg" role="group" aria-label="视图模式">
                {(
                  [
                    ['editor', 'edit', '编辑 (Alt+F)'],
                    ['split', 'columns', '分栏'],
                    ['preview', 'eye', '预览 (Alt+P)'],
                  ] as const
                ).map(([mode, icon, label]) => (
                  <button
                    key={mode}
                    className={viewMode === mode ? 'on' : ''}
                    onClick={() => setViewMode(mode)}
                    title={label}
                    aria-label={label}
                    aria-pressed={viewMode === mode}
                  >
                    <Icon name={icon} size={13} />
                  </button>
                ))}
              </div>
              <span className="status-sep status-hide-sm" />
              <button className="icon-btn" title="复制快捷命令" aria-label="复制快捷命令" onClick={() => void copyKey()}>
                <Icon name="copy" size={15} />
              </button>
              <PrivateMenu />
              <button
                className="icon-btn"
                title="笔记信息与分类"
                aria-label="笔记信息与分类"
                disabled={!meta}
                onClick={() => setDrawer(true)}
              >
                <Icon name="info" size={15} />
              </button>
              {meta ? (
                confirmDel ? (
                  <span className="row" style={{ gap: 4 }}>
                    <button className="btn sm danger" onClick={() => void removeNote(meta.key)}>
                      确认删除
                    </button>
                    <button className="btn sm ghost" onClick={() => setConfirmDel(false)}>
                      取消
                    </button>
                  </span>
                ) : (
                  <button
                    className="icon-btn"
                    title="删除笔记（60 秒内可撤销）"
                    aria-label="删除笔记"
                    onClick={() => setConfirmDel(true)}
                  >
                    <Icon name="trash" size={15} />
                  </button>
                )
              ) : null}
              <button className="icon-btn" title="关闭笔记" aria-label="关闭笔记" onClick={closeNote}>
                <Icon name="x" size={15} />
              </button>
            </div>
          </header>

          <div className={`split mode-${viewMode}`}>
            <EditorPane />
            <PreviewPane />
          </div>
        </>
      ) : (
        <EmptyMain />
      )}
    </main>
  )
}