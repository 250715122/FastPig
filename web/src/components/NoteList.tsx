import { useMemo, useState } from 'react'
import { useStore, type ListFilter, type SortKey } from '../lib/store'
import { relTime } from '../lib/format'
import { Icon, EmptyArt } from './Icons'
import type { NoteMeta } from '../lib/types'

const SORTS: Array<{ key: SortKey; label: string }> = [
  { key: 'updated', label: '最近修改' },
  { key: 'created', label: '最近创建' },
  { key: 'key', label: '命令 A→Z' },
  { key: 'blocks', label: '块数最多' },
  { key: 'domain', label: '按分类' },
]

const UNTAGGED = '未分类'

export function filterNotes(notes: NoteMeta[], f: ListFilter): NoteMeta[] {
  const q = f.text.trim().toLowerCase()
  const out = notes.filter((n) => {
    if (f.encryptedOnly && !n.encrypted) return false
    if (f.domain && (n.domain || UNTAGGED) !== f.domain) return false
    if (f.topic && (n.topic || UNTAGGED) !== f.topic) return false
    if (!q) return true
    const hay = [n.key, n.desc, n.title, n.domain, n.topic, ...(n.tags ?? []), ...(n.aliases ?? [])]
    return hay.some((v) => (v ?? '').toLowerCase().includes(q))
  })
  const dom = (n: NoteMeta) => n.domain || UNTAGGED
  const cmp: Record<SortKey, (a: NoteMeta, b: NoteMeta) => number> = {
    updated: (a, b) => b.updatedAt - a.updatedAt,
    created: (a, b) => b.createdAt - a.createdAt,
    key: (a, b) => a.key.localeCompare(b.key),
    blocks: (a, b) => b.blockCount - a.blockCount || b.updatedAt - a.updatedAt,
    domain: (a, b) => dom(a).localeCompare(dom(b), 'zh') || a.key.localeCompare(b.key),
  }
  return out.sort(cmp[f.sort])
}

export default function NoteList() {
  const notes = useStore((s) => s.notes)
  const deleted = useStore((s) => s.deleted)
  const filter = useStore((s) => s.filter)
  const setFilter = useStore((s) => s.setFilter)
  const currentKey = useStore((s) => s.currentKey)
  const openNote = useStore((s) => s.openNote)
  const selected = useStore((s) => s.selected)
  const selectMode = useStore((s) => s.selectMode)
  const toggleSelect = useStore((s) => s.toggleSelect)
  const selectAll = useStore((s) => s.selectAll)
  const clearSelection = useStore((s) => s.clearSelection)
  const setSelectMode = useStore((s) => s.setSelectMode)
  const removeMany = useStore((s) => s.removeMany)
  const removeNote = useStore((s) => s.removeNote)
  const restoreNote = useStore((s) => s.restoreNote)
  const views = useStore((s) => s.views)
  const activeViewId = useStore((s) => s.activeViewId)
  const saveView = useStore((s) => s.saveView)
  const applyView = useStore((s) => s.applyView)
  const deleteView = useStore((s) => s.deleteView)
  const tree = useStore((s) => s.tree)
  const unlockedKeys = useStore((s) => s.unlockedKeys)
  const setNewNoteOpen = useStore((s) => s.setNewNoteOpen)
  const mobile = useStore((s) => s.mobile)

  const [showFilters, setShowFilters] = useState(false)
  const [viewName, setViewName] = useState('')
  const [naming, setNaming] = useState(false)

  const source = filter.showDeleted ? deleted : notes
  const list = useMemo(() => filterNotes(source, filter), [source, filter])

  const domains = useMemo(() => {
    const set = new Set<string>()
    for (const n of notes) set.add(n.domain || UNTAGGED)
    return [...set].sort((a, b) => a.localeCompare(b, 'zh'))
  }, [notes])

  const topics = useMemo(() => {
    const set = new Set<string>()
    for (const n of notes) {
      if (!filter.domain || (n.domain || UNTAGGED) === filter.domain) set.add(n.topic || UNTAGGED)
    }
    return [...set].sort((a, b) => a.localeCompare(b, 'zh'))
  }, [notes, filter.domain])

  const allSelected = list.length > 0 && list.every((n) => selected.includes(n.key))

  return (
    <aside className="list-pane" aria-label="笔记列表">
      <div className="pane-head">
        <span className="pane-title">{filter.showDeleted ? '回收站' : '笔记'}</span>
        <span className="pane-count">{list.length}</span>
        <span className="spacer" />
        <button
          className={`icon-btn sm${showFilters ? ' on' : ''}`}
          onClick={() => setShowFilters((v) => !v)}
          title="筛选与排序"
          aria-label="筛选与排序"
          aria-expanded={showFilters}
        >
          <Icon name="filter" size={14} />
        </button>
        <button
          className={`icon-btn sm${selectMode ? ' on' : ''}`}
          onClick={() => setSelectMode(!selectMode)}
          title="批量选择"
          aria-label="批量选择"
          aria-pressed={selectMode}
        >
          <Icon name="check" size={14} />
        </button>
        <button className="icon-btn sm" onClick={() => setNewNoteOpen(true)} title="新建笔记" aria-label="新建笔记">
          <Icon name="plus" size={14} />
        </button>
        {mobile ? (
          <button
            className="icon-btn sm"
            onClick={() => useStore.setState({ showList: false })}
            title="收起"
            aria-label="收起列表"
          >
            <Icon name="x" size={14} />
          </button>
        ) : null}
      </div>

      <div className="list-tools">
        <div className="search-input">
          <Icon name="search" size={14} />
          <input
            className="input"
            value={filter.text}
            placeholder="过滤 key / 描述 / 标签…"
            onChange={(e) => setFilter({ text: e.target.value })}
            aria-label="过滤笔记"
          />
        </div>

        {showFilters ? (
          <>
            <div className="list-tools-row">
              <select
                className="select"
                value={filter.sort}
                onChange={(e) => setFilter({ sort: e.target.value as SortKey })}
                aria-label="排序方式"
              >
                {SORTS.map((s) => (
                  <option key={s.key} value={s.key}>
                    {s.label}
                  </option>
                ))}
              </select>
              <button
                className={`chip${filter.encryptedOnly ? ' on' : ''}`}
                onClick={() => setFilter({ encryptedOnly: !filter.encryptedOnly })}
                title="只看私密笔记"
                aria-pressed={filter.encryptedOnly}
              >
                <Icon name="lock" size={12} /> 私密
              </button>
            </div>
            <div className="list-tools-row">
              <select
                className="select"
                value={filter.domain}
                onChange={(e) => setFilter({ domain: e.target.value, topic: '' })}
                aria-label="按领域筛选"
              >
                <option value="">全部领域</option>
                {domains.map((d) => (
                  <option key={d} value={d}>
                    {d}
                  </option>
                ))}
              </select>
              <select
                className="select"
                value={filter.topic}
                onChange={(e) => setFilter({ topic: e.target.value })}
                aria-label="按主题筛选"
              >
                <option value="">全部主题</option>
                {topics.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div className="list-tools-row">
              <button
                className={`chip${filter.showDeleted ? ' on' : ''}`}
                onClick={() => setFilter({ showDeleted: !filter.showDeleted })}
                aria-pressed={filter.showDeleted}
              >
                <Icon name="trash" size={12} /> 回收站 {deleted.length ? `(${deleted.length})` : ''}
              </button>
              <span className="spacer" />
              {naming ? (
                <>
                  <input
                    className="input"
                    style={{ height: 24, fontSize: 11.5 }}
                    value={viewName}
                    autoFocus
                    placeholder="视图名"
                    onChange={(e) => setViewName(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && viewName.trim()) {
                        saveView(viewName.trim())
                        setViewName('')
                        setNaming(false)
                      }
                      if (e.key === 'Escape') setNaming(false)
                    }}
                    aria-label="视图名称"
                  />
                  <button
                    className="btn sm primary"
                    disabled={!viewName.trim()}
                    onClick={() => {
                      saveView(viewName.trim())
                      setViewName('')
                      setNaming(false)
                    }}
                  >
                    存
                  </button>
                </>
              ) : (
                <button className="chip" onClick={() => setNaming(true)} title="把当前筛选存成视图">
                  <Icon name="bookmark" size={12} /> 存为视图
                </button>
              )}
            </div>
            {views.length > 0 ? (
              <div className="chips">
                {views.map((v) => (
                  <span key={v.id} className={`chip${activeViewId === v.id ? ' on' : ''}`}>
                    <button onClick={() => applyView(v.id)} style={{ color: 'inherit' }} title={`应用视图 ${v.name}`}>
                      {v.name}
                    </button>
                    <button className="x" onClick={() => deleteView(v.id)} title="删除视图" aria-label={`删除视图 ${v.name}`}>
                      <Icon name="x" size={9} />
                    </button>
                  </span>
                ))}
              </div>
            ) : null}
          </>
        ) : views.length > 0 ? (
          <div className="chips">
            {views.map((v) => (
              <button
                key={v.id}
                className={`chip${activeViewId === v.id ? ' on' : ''}`}
                onClick={() => applyView(v.id)}
                title={`应用视图 ${v.name}`}
              >
                <Icon name="bookmark" size={11} /> {v.name}
              </button>
            ))}
          </div>
        ) : null}
      </div>

      {selectMode ? (
        <div className="bulk-bar">
          <button
            className="ni-check"
            aria-label={allSelected ? '取消全选' : '全选'}
            onClick={() => (allSelected ? clearSelection() : selectAll(list.map((n) => n.key)))}
          >
            <span className={`ni-check${allSelected ? ' on' : ''}`} style={{ border: 'none' }}>
              <Icon name="check" size={11} />
            </span>
          </button>
          <span>
            已选 <b>{selected.length}</b> / {list.length}
          </span>
          <span className="spacer" />
          <button className="btn sm" onClick={clearSelection}>
            取消
          </button>
          <button
            className="btn sm danger"
            disabled={selected.length === 0}
            onClick={() => void removeMany(selected)}
            title="删除所选（可撤销）"
          >
            <Icon name="trash" size={12} /> 删除
          </button>
        </div>
      ) : null}

      <div className="pane-body">
        {list.length === 0 ? (
          <div className="empty" style={{ padding: '34px 20px' }}>
            <EmptyArt kind="note" />
            <h3 style={{ fontSize: 15 }}>{filter.showDeleted ? '回收站是空的' : '没有匹配的笔记'}</h3>
            <p style={{ fontSize: 12 }}>
              {filter.showDeleted
                ? '删除的笔记会在这里保留，可随时恢复。'
                : notes.length === 0
                  ? '还没有笔记。新建一篇，首行写「key 描述」，正文用 # 分块。'
                  : '换个关键词，或清掉筛选条件。'}
            </p>
            {!filter.showDeleted && notes.length > 0 ? (
              <button className="btn sm" onClick={() => setFilter({ text: '', domain: '', topic: '', encryptedOnly: false })}>
                <Icon name="refresh" size={12} /> 清空筛选
              </button>
            ) : null}
            {!filter.showDeleted && notes.length === 0 ? (
              <button className="btn primary sm" onClick={() => setNewNoteOpen(true)}>
                <Icon name="plus" size={12} /> 新建笔记
              </button>
            ) : null}
          </div>
        ) : (
          list.map((n) => {
            const isSel = selected.includes(n.key)
            const locked = n.encrypted && !unlockedKeys.includes(n.key)
            return (
              <div
                key={n.key}
                className={`note-item${currentKey === n.key ? ' on' : ''}${isSel ? ' selected' : ''}`}
                role="button"
                tabIndex={0}
                onClick={() => (selectMode ? toggleSelect(n.key) : filter.showDeleted ? undefined : void openNote(n.key))}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault()
                    if (selectMode) toggleSelect(n.key)
                    else if (!filter.showDeleted) void openNote(n.key)
                  }
                }}
                aria-current={currentKey === n.key}
              >
                <div className="ni-row">
                  {selectMode ? (
                    <span className={`ni-check${isSel ? ' on' : ''}`}>
                      <Icon name="check" size={10} />
                    </span>
                  ) : null}
                  {n.encrypted ? (
                    <Icon name={locked ? 'lock' : 'unlock'} size={12} className="ni-lock" />
                  ) : null}
                  <span className="ni-key">{n.key}</span>
                  <span className="spacer" />
                  {filter.showDeleted ? (
                    <button
                      className="btn sm"
                      onClick={(e) => {
                        e.stopPropagation()
                        void restoreNote(n.key)
                      }}
                    >
                      <Icon name="undo" size={11} /> 恢复
                    </button>
                  ) : (
                    <button
                      className="icon-btn sm danger"
                      title="删除（60 秒内可撤销）"
                      aria-label={`删除 ${n.key}`}
                      onClick={(e) => {
                        e.stopPropagation()
                        void removeNote(n.key)
                      }}
                    >
                      <Icon name="trash" size={12} />
                    </button>
                  )}
                </div>
                <div className="ni-desc">{n.desc || n.title || '（无描述）'}</div>
                <div className="ni-meta">
                  <span className="ni-domain">{n.domain || UNTAGGED}</span>
                  {n.topic ? <span>· {n.topic}</span> : null}
                  <span className="spacer" />
                  <span title={`${n.blockCount} 个知识块`}>{n.blockCount} 块</span>
                  <span title={`版本 ${n.version}`}>v{n.version}</span>
                  <span title={new Date(n.updatedAt).toLocaleString()}>{relTime(n.updatedAt)}</span>
                </div>
              </div>
            )
          })
        )}
      </div>

      {tree ? (
        <div
          className="editor-foot"
          style={{ borderTop: '1px solid var(--line-soft)' }}
          title="知识库目录统计，不含派生的 all 视图；上方计数是当前列表的笔记数。"
        >
          <Icon name="tree" size={11} />
          <span>目录 {tree.noteCount} 篇</span>
          <span className="sep" />
          <span>{tree.topicCount} 主题</span>
          <span className="sep" />
          <span>{tree.blockCount} 块</span>
        </div>
      ) : null}
    </aside>
  )
}