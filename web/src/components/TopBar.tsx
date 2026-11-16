import { useEffect, useRef, useState } from 'react'
import { useStore } from '../lib/store'
import { Icon, Logo } from './Icons'

function markMatch(text: string, q: string) {
  if (!q) return text
  const i = text.toLowerCase().indexOf(q.toLowerCase())
  if (i < 0) return text
  return (
    <>
      {text.slice(0, i)}
      <mark>{text.slice(i, i + q.length)}</mark>
      {text.slice(i + q.length)}
    </>
  )
}

export default function TopBar() {
  const notes = useStore((s) => s.notes)
  const currentKey = useStore((s) => s.currentKey)
  const dirty = useStore((s) => s.dirty)
  const saving = useStore((s) => s.saving)
  const detail = useStore((s) => s.detail)
  const suggestions = useStore((s) => s.suggestions)
  const loadSuggestions = useStore((s) => s.loadSuggestions)
  const openNote = useStore((s) => s.openNote)
  const runSearch = useStore((s) => s.runSearch)
  const setQuery = useStore((s) => s.setQuery)
  const setNewNoteOpen = useStore((s) => s.setNewNoteOpen)
  const save = useStore((s) => s.save)
  const setDrawer = useStore((s) => s.setDrawer)
  const drawerOpen = useStore((s) => s.drawerOpen)
  const setPalette = useStore((s) => s.setPalette)
  const setSyncOpen = useStore((s) => s.setSyncOpen)
  const setSettings = useStore((s) => s.setSettings)
  const logout = useStore((s) => s.logout)
  const viewMode = useStore((s) => s.viewMode)
  const setViewMode = useStore((s) => s.setViewMode)
  const mobile = useStore((s) => s.mobile)
  const toggleList = useStore((s) => s.toggleList)
  const connection = useStore((s) => s.connection)

  const [text, setText] = useState('')
  const [open, setOpen] = useState(false)
  const [cursor, setCursor] = useState(0)
  const boxRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const timer = useRef<number | null>(null)

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  useEffect(() => {
    if (timer.current !== null) window.clearTimeout(timer.current)
    if (!text.trim()) {
      loadSuggestions('')
      return
    }
    timer.current = window.setTimeout(() => void loadSuggestions(text), 130)
    return () => {
      if (timer.current !== null) window.clearTimeout(timer.current)
    }
  }, [text, loadSuggestions])

  useEffect(() => setCursor(0), [suggestions])

  const pick = (key: string) => {
    setOpen(false)
    setText('')
    void loadSuggestions('')
    void openNote(key)
  }

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setOpen(true)
      setCursor((c) => Math.min(c + 1, Math.max(0, suggestions.length - 1)))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setCursor((c) => Math.max(0, c - 1))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      if (open && suggestions[cursor]) pick(suggestions[cursor].key)
      else if (text.trim()) {
        setOpen(false)
        setQuery(text.trim())
        void runSearch(text.trim())
        setText('')
      }
    } else if (e.key === 'Escape') {
      setOpen(false)
      inputRef.current?.blur()
    }
  }

  const unlockedKeys = useStore((s) => s.unlockedKeys)
  const locked = detail?.meta.encrypted ? !unlockedKeys.includes(detail.meta.key) : false

  return (
    <header className="topbar">
      {mobile ? (
        <button className="icon-btn" onClick={toggleList} title="笔记列表" aria-label="笔记列表">
          <Icon name="list" size={18} />
        </button>
      ) : null}

      <div className="brand">
        <Logo size={30} />
        <div className="brand-text">
          <span className="brand-name">
            Fast<em>Pig</em>
          </span>
          <span className="brand-sub">Knowledge Web</span>
        </div>
      </div>

      <div className="omni" ref={boxRef}>
        <Icon name="terminal" size={15} style={{ color: 'var(--cyan)', flex: 'none' }} />
        <input
          ref={inputRef}
          id="fp-omni"
          value={text}
          placeholder="输入拼音前缀直达笔记，或输入问题做混合检索…"
          onChange={(e) => {
            setText(e.target.value)
            setOpen(true)
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          aria-label="快捷命令联想与检索"
          aria-expanded={open && suggestions.length > 0}
          autoComplete="off"
          spellCheck={false}
        />
        {text ? (
          <button
            className="icon-btn sm"
            onClick={() => {
              setText('')
              void loadSuggestions('')
              inputRef.current?.focus()
            }}
            title="清空"
            aria-label="清空"
          >
            <Icon name="x" size={13} />
          </button>
        ) : (
          <span className="omni-kbd">
            <kbd>Ctrl</kbd>
            <kbd>K</kbd>
          </span>
        )}

        {open && suggestions.length > 0 ? (
          <div className="suggest-pop" role="listbox">
            {suggestions.map((s, i) => (
              <button
                key={s.key}
                className={`suggest-item${i === cursor ? ' cursor' : ''}`}
                role="option"
                aria-selected={i === cursor}
                onMouseEnter={() => setCursor(i)}
                onClick={() => pick(s.key)}
              >
                <span className="sk">{markMatch(s.key, text)}</span>
                <span className="sd">{markMatch(s.desc, text)}</span>
                {s.encrypted ? <Icon name="lock" size={12} style={{ color: 'var(--vermilion)', flex: 'none' }} /> : null}
                <span className="badge" style={{ marginLeft: 'auto' }}>
                  {s.blockCount} 块
                </span>
                {s.domain ? <span className="badge violet">{s.domain}</span> : null}
              </button>
            ))}
            <div className="palette-foot" style={{ borderTop: '1px solid var(--line-soft)', marginTop: 4 }}>
              <span>
                <kbd>↑</kbd> <kbd>↓</kbd> 选择
              </span>
              <span>
                <kbd>Enter</kbd> {suggestions.length ? '打开' : '检索'}
              </span>
              <span className="spacer" />
              <span>{suggestions.length} 条联想 · 共 {notes.length} 篇</span>
            </div>
          </div>
        ) : null}
      </div>

      <div className="topbar-actions">
        {connection !== 'online' ? (
          <span className="badge amber" title="与服务端的实时连接已断开，正在重连">
            <Icon name="wifiOff" size={11} /> 重连中
          </span>
        ) : null}

        {!mobile ? (
          <div className="seg" role="group" aria-label="视图模式">
            <button
              className={viewMode === 'editor' ? 'on' : ''}
              onClick={() => setViewMode('editor')}
              title="只看编辑器 (Alt+F)"
              aria-pressed={viewMode === 'editor'}
            >
              <Icon name="edit" size={13} />
            </button>
            <button
              className={viewMode === 'split' ? 'on' : ''}
              onClick={() => setViewMode('split')}
              title="编辑 + 预览"
              aria-pressed={viewMode === 'split'}
            >
              <Icon name="columns" size={13} />
            </button>
            <button
              className={viewMode === 'preview' ? 'on' : ''}
              onClick={() => setViewMode('preview')}
              title="只看预览 (Alt+P)"
              aria-pressed={viewMode === 'preview'}
            >
              <Icon name="eye" size={13} />
            </button>
          </div>
        ) : null}

        <button className="icon-btn" onClick={() => setNewNoteOpen(true)} title="新建笔记 (Ctrl+Alt+N)" aria-label="新建笔记">
          <Icon name="plus" size={17} />
        </button>

        <button
          className={`icon-btn${dirty ? ' on' : ''}`}
          onClick={() => void save(true)}
          disabled={!currentKey && !text && !dirty}
          title={dirty ? '保存 (Ctrl+S)' : '已保存'}
          aria-label="保存"
        >
          <Icon name={saving ? 'refresh' : 'save'} size={17} className={saving ? 'spin' : ''} />
        </button>

        {currentKey ? (
          <button
            className={`icon-btn${drawerOpen ? ' on' : ''}`}
            onClick={() => setDrawer(!drawerOpen)}
            title="笔记属性与分类"
            aria-label="笔记属性"
          >
            <Icon name={locked ? 'lock' : 'tag'} size={17} />
          </button>
        ) : null}

        {!mobile ? (
          <>
            <button className="icon-btn" onClick={() => setSyncOpen(true)} title="云同步（坚果云）" aria-label="云同步">
              <Icon name="cloudUp" size={17} />
            </button>
            <button className="icon-btn" onClick={() => setPalette(true)} title="命令面板 (Ctrl+K)" aria-label="命令面板">
              <Icon name="command" size={17} />
            </button>
            <button className="icon-btn" onClick={() => setSettings(true)} title="设置" aria-label="设置">
              <Icon name="settings" size={17} />
            </button>
            <button className="icon-btn danger" onClick={() => void logout()} title="退出登录" aria-label="退出登录">
              <Icon name="logout" size={17} />
            </button>
          </>
        ) : null}
      </div>
    </header>
  )
}