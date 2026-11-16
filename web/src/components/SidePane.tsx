import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { useStore } from '../lib/store'
import { Icon, EmptyArt } from './Icons'
import { api } from '../lib/api'
import { extractToc } from '../lib/markdown'
import { useBlocks, parseLocalBlocks } from '../lib/blocks'
import { CHANNEL_CLASS, CHANNEL_LABEL, escapeHtml, highlightRanges, relTime, splitHeader } from '../lib/format'
import type { BlockSummary, SearchHit } from '../lib/types'

function markText(text: string, query: string): string {
  const ranges = highlightRanges(text, query)
  if (ranges.length === 0) return escapeHtml(text)
  ranges.sort((a, b) => a[0] - b[0])
  const merged: Array<[number, number]> = []
  for (const r of ranges) {
    const last = merged[merged.length - 1]
    if (last && r[0] <= last[1]) last[1] = Math.max(last[1], r[1])
    else merged.push([r[0], r[1]])
  }
  let out = ''
  let cursor = 0
  for (const [a, b] of merged) {
    out += escapeHtml(text.slice(cursor, a))
    out += `<mark>${escapeHtml(text.slice(a, b))}</mark>`
    cursor = b
  }
  out += escapeHtml(text.slice(cursor))
  return out
}

/* ==================== 大纲 ==================== */

function TocPanel() {
  const editorText = useStore((s) => s.editorText)
  const currentKey = useStore((s) => s.currentKey)
  const caretLine = useStore((s) => s.caretLine)
  const blocks = useBlocks()
  const detail = useStore((s) => s.detail)
  const unlockedKeys = useStore((s) => s.unlockedKeys)
  const requestJump = useStore((s) => s.requestJump)
  const openBlock = useStore((s) => s.openBlock)
  const setPrivateTarget = useStore((s) => s.setPrivateTarget)
  const [mode, setMode] = useState<'headings' | 'blocks'>('headings')
  const [q, setQ] = useState('')

  const body = splitHeader(editorText).rest
  const toc = useMemo(() => extractToc(body), [body])
  const locked = detail?.meta.encrypted ? !unlockedKeys.includes(detail.meta.key) : false

  const activeSlug = useMemo(() => {
    const line = caretLine - 1
    let cur: string | null = null
    for (const t of toc) {
      if (t.line <= line) cur = t.slug
      else break
    }
    return cur
  }, [toc, caretLine])

  const activeBlock = useMemo(() => {
    const line = caretLine - 1
    return blocks.find((b) => b.startLine <= line && b.endLine >= line) ?? null
  }, [blocks, caretLine])

  const filteredToc = useMemo(() => {
    const t = q.trim().toLowerCase()
    return t ? toc.filter((x) => x.text.toLowerCase().includes(t)) : toc
  }, [toc, q])

  const filteredBlocks = useMemo(() => {
    const t = q.trim().toLowerCase()
    return t ? blocks.filter((b) => b.title.toLowerCase().includes(t) || b.blockId.toLowerCase().includes(t)) : blocks
  }, [blocks, q])

  if (!currentKey) {
    return (
      <div className="empty" style={{ padding: '48px 22px' }}>
        <EmptyArt kind="tree" />
        <h3 style={{ fontSize: 16 }}>还没有打开笔记</h3>
        <p style={{ fontSize: 12.5 }}>打开一篇笔记后，这里会列出它的标题层级与知识块。</p>
      </div>
    )
  }

  if (locked) {
    return (
      <div className="empty" style={{ padding: '48px 22px' }}>
        <Icon name="lock" size={30} style={{ color: 'var(--vermilion)' }} />
        <h3 style={{ fontSize: 16 }}>大纲已上锁</h3>
        <p style={{ fontSize: 12.5 }}>解锁后才能看到这篇私密笔记的标题结构。</p>
        <button className="btn primary sm" onClick={() => setPrivateTarget({ mode: 'unlock', key: currentKey })}>
          <Icon name="unlock" size={13} /> 解锁
        </button>
      </div>
    )
  }

  return (
    <>
      <div className="pane-head" style={{ gap: 6 }}>
        <div className="seg" style={{ flex: 1 }}>
          <button className={mode === 'headings' ? 'on' : ''} onClick={() => setMode('headings')}>
            标题 {toc.length}
          </button>
          <button className={mode === 'blocks' ? 'on' : ''} onClick={() => setMode('blocks')}>
            知识块 {blocks.length}
          </button>
        </div>
      </div>
      <div className="search-head" style={{ padding: '8px 10px', borderBottom: '1px solid var(--line-soft)' }}>
        <div className="search-input">
          <Icon name="filter" size={13} />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={mode === 'headings' ? '筛选标题…' : '筛选标题或块 ID…'}
            aria-label="筛选大纲"
          />
          {q ? (
            <button className="icon-btn sm" onClick={() => setQ('')} aria-label="清空筛选">
              <Icon name="x" size={12} />
            </button>
          ) : null}
        </div>
      </div>
      <div className="pane-body">
        {mode === 'headings' ? (
          <div className="toc">
            {filteredToc.length === 0 ? (
              <div className="preview-empty" style={{ padding: '30px 12px' }}>
                {toc.length === 0 ? '这篇笔记还没有 # 标题' : '没有匹配的标题'}
              </div>
            ) : (
              filteredToc.map((t) => (
                <button
                  key={`${t.slug}-${t.line}`}
                  className={`toc-item${activeSlug === t.slug ? ' on' : ''}`}
                  data-level={t.level}
                  onClick={() => requestJump(t.line + 1)}
                  title={`第 ${t.line + 2} 行`}
                >
                  <span className="t">{t.text}</span>
                  <span className="ln">{t.line + 2}</span>
                </button>
              ))
            )}
          </div>
        ) : (
          <div className="toc">
            {filteredBlocks.length === 0 ? (
              <div className="preview-empty" style={{ padding: '30px 12px' }}>
                {blocks.length === 0
                  ? detail?.meta.encrypted
                    ? '这篇笔记还没有 # 标题'
                    : '保存后会自动补齐块 ID'
                  : '没有匹配的知识块'}
              </div>
            ) : (
              filteredBlocks.map((b) => (
                <div
                  key={b.blockId}
                  className={`toc-item${activeBlock?.blockId === b.blockId ? ' on' : ''}`}
                  data-level={Math.min(6, b.level)}
                  style={{ cursor: 'pointer' }}
                  onClick={() => requestJump(b.startLine + 1, 0, b.endLine + 1)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') requestJump(b.startLine + 1, 0, b.endLine + 1)
                  }}
                >
                  <span className="t">{b.title || '(无标题块)'}</span>
                  <button
                    className="icon-btn sm"
                    title={`块详情 ${b.blockId}`}
                    aria-label="打开块详情"
                    onClick={(e) => {
                      e.stopPropagation()
                      void openBlock(b.blockId)
                    }}
                  >
                    <Icon name="info" size={11} />
                  </button>
                  <span className="ln">{b.startLine + 2}</span>
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </>
  )
}

/* ==================== 知识库目录 ==================== */

function TreePanel() {
  const tree = useStore((s) => s.tree)
  const currentKey = useStore((s) => s.currentKey)
  const openNote = useStore((s) => s.openNote)
  const openBlock = useStore((s) => s.openBlock)
  const refreshTree = useStore((s) => s.refreshTree)
  const setFilter = useStore((s) => s.setFilter)
  const [open, setOpen] = useState<Set<string>>(new Set())
  const [blockCache, setBlockCache] = useState<Record<string, BlockSummary[]>>({})
  const [loading, setLoading] = useState<string | null>(null)
  const [q, setQ] = useState('')
  const [refreshing, setRefreshing] = useState(false)

  // 打开笔记时自动展开它所在的分类路径
  useEffect(() => {
    if (!tree || !currentKey) return
    for (const d of tree.domains) {
      for (const t of d.topics) {
        if (t.notes.some((n) => n.key === currentKey)) {
          setOpen((prev) => {
            const next = new Set(prev)
            next.add(`d:${d.name}`)
            next.add(`t:${d.name}/${t.topic}`)
            return next
          })
          return
        }
      }
    }
  }, [tree, currentKey])

  const toggle = (id: string) =>
    setOpen((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  const expandNote = async (key: string, id: string) => {
    toggle(id)
    const s0 = useStore.getState()
    const localNow =
      s0.detail?.meta.key === key && s0.detail.meta.encrypted && s0.unlockedKeys.includes(key)
    if (open.has(id) || (blockCache[key] && !localNow)) return
    setLoading(key)
    try {
      const list = localNow
        ? parseLocalBlocks(splitHeader(s0.editorText).rest, key)
        : await api.blocks(key)
      setBlockCache((prev) => ({ ...prev, [key]: list }))
    } catch {
      setBlockCache((prev) => ({ ...prev, [key]: [] }))
    } finally {
      setLoading(null)
    }
  }

  const needle = q.trim().toLowerCase()
  const domains = useMemo(() => {
    if (!tree) return []
    if (!needle) return tree.domains
    return tree.domains
      .map((d) => ({
        ...d,
        topics: d.topics
          .map((t) => ({
            ...t,
            notes: t.notes.filter(
              (n) =>
                n.key.toLowerCase().includes(needle) ||
                (n.title ?? '').toLowerCase().includes(needle) ||
                t.topic.toLowerCase().includes(needle) ||
                d.name.toLowerCase().includes(needle),
            ),
          }))
          .filter((t) => t.notes.length > 0),
      }))
      .filter((d) => d.topics.length > 0)
  }, [tree, needle])

  const doRefresh = async () => {
    setRefreshing(true)
    setBlockCache({})
    try {
      await refreshTree()
    } finally {
      setRefreshing(false)
    }
  }

  return (
    <>
      {tree ? (
        <div className="tree-stats">
          <div className="tree-stat">
            <b>{tree.noteCount}</b>
            <span>笔记</span>
          </div>
          <div className="tree-stat">
            <b>{tree.topicCount}</b>
            <span>主题</span>
          </div>
          <div className="tree-stat">
            <b>{tree.blockCount}</b>
            <span>知识块</span>
          </div>
        </div>
      ) : null}
      <div className="search-head" style={{ padding: '8px 10px' }}>
        <div className="row" style={{ gap: 6 }}>
          <div className="search-input" style={{ flex: 1 }}>
            <Icon name="search" size={13} />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="筛选分类 / 命令 / 标题…"
              aria-label="筛选目录"
            />
            {q ? (
              <button className="icon-btn sm" onClick={() => setQ('')} aria-label="清空筛选">
                <Icon name="x" size={12} />
              </button>
            ) : null}
          </div>
          <button className="icon-btn" title="刷新目录" aria-label="刷新目录" onClick={() => void doRefresh()}>
            <Icon name="refresh" size={14} className={refreshing ? 'spin' : ''} />
          </button>
        </div>
      </div>
      <div className="pane-body">
        {!tree ? (
          <div style={{ padding: 12 }}>
            {[0, 1, 2, 3, 4].map((i) => (
              <div key={i} className="skeleton" style={{ height: 22, margin: '7px 0', width: `${88 - i * 9}%` }} />
            ))}
          </div>
        ) : domains.length === 0 ? (
          <div className="preview-empty">没有匹配的分类</div>
        ) : (
          <div className="tree">
            {domains.map((d) => {
              const did = `d:${d.name}`
              const dOpen = open.has(did) || !!needle
              return (
                <div className="tree-domain" key={did}>
                  <button className="tree-node" onClick={() => toggle(did)} aria-expanded={dOpen}>
                    <span className={`tree-caret${dOpen ? ' open' : ''}`}>
                      <Icon name="chevronRight" size={11} />
                    </span>
                    <Icon name="folder" size={13} style={{ color: 'var(--violet)', flex: 'none' }} />
                    <span className="tree-label">{d.name}</span>
                    <span className="tree-count">{d.noteCount}</span>
                  </button>
                  {dOpen ? (
                    <div className="tree-children">
                      {d.topics.map((t) => {
                        const tid = `t:${d.name}/${t.topic}`
                        const tOpen = open.has(tid) || !!needle
                        return (
                          <div className="tree-topic" key={tid}>
                            <button className="tree-node" onClick={() => toggle(tid)} aria-expanded={tOpen}>
                              <span className={`tree-caret${tOpen ? ' open' : ''}`}>
                                <Icon name="chevronRight" size={11} />
                              </span>
                              <span className="tree-label">{t.topic}</span>
                              <span className="tree-count">{t.notes.length}</span>
                            </button>
                            {tOpen ? (
                              <div className="tree-children">
                                {t.notes.map((n) => {
                                  const nid = `n:${n.key}`
                                  const nOpen = open.has(nid)
                                  const list = blockCache[n.key]
                                  return (
                                    <div key={nid}>
                                      <div
                                        className={`tree-node tree-note${currentKey === n.key ? ' on' : ''}`}
                                        style={{ padding: 0 }}
                                      >
                                        <button
                                          className="tree-caret"
                                          style={{ visibility: n.blockCount > 0 ? 'visible' : 'hidden' }}
                                          aria-label={nOpen ? '收起知识块' : '展开知识块'}
                                          onClick={() => void expandNote(n.key, nid)}
                                        >
                                          <span className={`tree-caret${nOpen ? ' open' : ''}`}>
                                            <Icon name="chevronRight" size={10} />
                                          </span>
                                        </button>
                                        <button
                                          className="tree-node"
                                          style={{ flex: 1, minWidth: 0, padding: '4px 4px' }}
                                          onClick={() => void openNote(n.key)}
                                          title={n.title}
                                        >
                                          {n.encrypted ? (
                                            <Icon name="lock" size={11} style={{ color: 'var(--vermilion)', flex: 'none' }} />
                                          ) : null}
                                          <span className="tree-label">{n.key}</span>
                                          <span className="tree-count">{n.blockCount}</span>
                                        </button>
                                      </div>
                                      {nOpen ? (
                                        <div className="tree-blocks">
                                          {loading === n.key ? (
                                            <div className="tree-block">
                                              <Icon name="refresh" size={11} className="spin" /> 载入知识块…
                                            </div>
                                          ) : list && list.length > 0 ? (
                                            list.map((b) => (
                                              <button
                                                key={b.blockId}
                                                className="tree-block"
                                                onClick={() => {
                                                  void (async () => {
                                                    if (n.encrypted && currentKey !== n.key) await openNote(n.key)
                                                    await openBlock(b.blockId)
                                                  })()
                                                }}
                                                title={`${b.blockId} · 第 ${b.startLine + 2} 行`}
                                              >
                                                <span className="lvl">H{b.level}</span>
                                                <span className="tree-label">{b.title || '(无标题)'}</span>
                                              </button>
                                            ))
                                          ) : (
                                            <div className="tree-block">
                                              {n.encrypted ? '私密笔记解锁后可展开' : '暂无块 ID，保存一次即可补齐'}
                                            </div>
                                          )}
                                        </div>
                                      ) : null}
                                    </div>
                                  )
                                })}
                              </div>
                            ) : null}
                          </div>
                        )
                      })}
                    </div>
                  ) : null}
                </div>
              )
            })}
            <div className="divider" style={{ margin: '12px 4px' }} />
            <button
              className="tree-node"
              onClick={() => {
                setFilter({ domain: '', topic: '', text: '' })
                useStore.getState().toggleList()
              }}
            >
              <Icon name="list" size={13} />
              <span className="tree-label">在列表里查看全部笔记</span>
            </button>
          </div>
        )}
      </div>
    </>
  )
}

/* ==================== 混合检索 ==================== */

const EXAMPLES = ['mysql 主从延迟', 'redis 内存淘汰', 'jvm 频繁 full gc', 'rabbitmq 消息堆积']

function HitRow({ hit, query }: { hit: SearchHit; query: string }) {
  const activeHit = useStore((s) => s.activeHit)
  const openNote = useStore((s) => s.openNote)
  const openBlock = useStore((s) => s.openBlock)
  const [expanded, setExpanded] = useState(false)
  const on = activeHit === hit.docId
  const title = hit.sectionTitle || hit.h1Title || hit.noteDesc || hit.noteKey

  return (
    <div
      className={`hit${on ? ' on' : ''}${hit.status === 'deprecated' ? ' deprecated' : ''}`}
      onClick={() => {
        useStore.setState({ activeHit: hit.docId })
        void openNote(hit.noteKey, hit.startLine)
      }}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter') void openNote(hit.noteKey, hit.startLine)
      }}
    >
      <div className="hit-top">
        <span className="hit-key">{hit.noteKey}</span>
        <span className="hit-title">{title}</span>
        {hit.status === 'deprecated' ? <span className="badge amber">已废弃</span> : null}
      </div>
      {hit.path ? <div className="hit-path">{hit.path}</div> : null}
      <div
        className={`hit-body${expanded ? ' full' : ''}`}
        style={{ whiteSpace: 'pre-wrap' }}
        dangerouslySetInnerHTML={{ __html: markText(hit.content, query) }}
      />
      <div className="hit-foot">
        <span className="channels">
          {hit.channels.map((c) => (
            <span key={c} className={`ch ${CHANNEL_CLASS[c] ?? ''}`} title={`${CHANNEL_LABEL[c] ?? c} 通道命中`}>
              {CHANNEL_LABEL[c] ?? c}
            </span>
          ))}
        </span>
        {hit.content.length > 180 ? (
          <button
            className="hit-expand"
            onClick={(e) => {
              e.stopPropagation()
              setExpanded((v) => !v)
            }}
          >
            {expanded ? '收起' : '展开全文'}
          </button>
        ) : null}
        {hit.blockId ? (
          <button
            className="hit-expand"
            title={hit.blockId}
            onClick={(e) => {
              e.stopPropagation()
              void openBlock(hit.blockId!)
            }}
          >
            块详情
          </button>
        ) : null}
        <span>{relTime(hit.updatedAt)}</span>
        <span className="hit-score">{hit.score.toFixed(4)}</span>
      </div>
    </div>
  )
}

function SearchPanel() {
  const query = useStore((s) => s.query)
  const tier = useStore((s) => s.tier)
  const searching = useStore((s) => s.searching)
  const result = useStore((s) => s.searchResult)
  const error = useStore((s) => s.searchError)
  const taxonomy = useStore((s) => s.taxonomy)
  const filter = useStore((s) => s.searchFilter)
  const indexStatus = useStore((s) => s.indexStatus)
  const setQuery = useStore((s) => s.setQuery)
  const setTier = useStore((s) => s.setTier)
  const runSearch = useStore((s) => s.runSearch)
  const setSearchFilter = useStore((s) => s.setSearchFilter)
  const clearSearch = useStore((s) => s.clearSearch)
  const rebuildIndex = useStore((s) => s.rebuildIndex)
  const [showFilters, setShowFilters] = useState(false)
  const [local, setLocal] = useState(query)
  const timer = useRef<number | null>(null)

  useEffect(() => setLocal(query), [query])

  const onType = (v: string) => {
    setLocal(v)
    setQuery(v)
    if (timer.current !== null) window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => void runSearch(v), 300)
  }

  useEffect(() => () => {
    if (timer.current !== null) window.clearTimeout(timer.current)
  }, [])

  const domains = taxonomy?.domains ?? []
  const topics = useMemo(() => {
    const d = domains.find((x) => x.name === filter.domain)
    return d ? d.topics.map((t) => t.name) : domains.flatMap((x) => x.topics.map((t) => t.name))
  }, [domains, filter.domain])

  const activeFilters = Object.values(filter).filter(Boolean).length

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      if (timer.current !== null) window.clearTimeout(timer.current)
      void runSearch(local)
    } else if (e.key === 'Escape') {
      clearSearch()
      setLocal('')
    } else if (e.key === 'ArrowDown' && result && result.hits.length > 0) {
      e.preventDefault()
      const i = result.hits.findIndex((h) => h.docId === useStore.getState().activeHit)
      const next = result.hits[Math.min(result.hits.length - 1, i + 1)]
      useStore.setState({ activeHit: next.docId })
      document.getElementById(`hit-${next.docId}`)?.scrollIntoView({ block: 'nearest' })
    } else if (e.key === 'ArrowUp' && result && result.hits.length > 0) {
      e.preventDefault()
      const i = result.hits.findIndex((h) => h.docId === useStore.getState().activeHit)
      const next = result.hits[Math.max(0, i - 1)]
      useStore.setState({ activeHit: next.docId })
      document.getElementById(`hit-${next.docId}`)?.scrollIntoView({ block: 'nearest' })
    }
  }

  return (
    <>
      <div className="search-head">
        <div className="search-input">
          <Icon name="search" size={14} />
          <input
            id="fp-search-input"
            value={local}
            onChange={(e) => onType(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="检索知识块：命令、报错、概念…"
            aria-label="混合检索"
            autoComplete="off"
            spellCheck={false}
          />
          {searching ? <Icon name="refresh" size={13} className="spin" /> : null}
          {local ? (
            <button
              className="icon-btn sm"
              aria-label="清空检索"
              onClick={() => {
                clearSearch()
                setLocal('')
              }}
            >
              <Icon name="x" size={12} />
            </button>
          ) : null}
        </div>
        <div className="row" style={{ gap: 6 }}>
          <div className="seg" style={{ flex: 1 }}>
            <button className={tier === 'human' ? 'on' : ''} onClick={() => setTier('human')} title="摘要 + 每篇配额，给人读">
              人用档位
            </button>
            <button className={tier === 'ai' ? 'on' : ''} onClick={() => setTier('ai')} title="完整正文 + 来源字段，给 Agent 用">
              AI 档位
            </button>
          </div>
          <button
            className={`icon-btn${showFilters || activeFilters ? ' on' : ''}`}
            title="过滤条件"
            aria-label="过滤条件"
            aria-expanded={showFilters}
            onClick={() => setShowFilters((v) => !v)}
          >
            <Icon name="filter" size={14} />
          </button>
        </div>
        {showFilters ? (
          <div className="search-filters">
            <select
              className="select"
              style={{ flex: '1 1 46%' }}
              value={filter.domain}
              onChange={(e) => setSearchFilter({ domain: e.target.value, topic: '' })}
              aria-label="领域"
            >
              <option value="">全部领域</option>
              {domains.map((d) => (
                <option key={d.name} value={d.name}>
                  {d.name}
                </option>
              ))}
            </select>
            <select
              className="select"
              style={{ flex: '1 1 46%' }}
              value={filter.topic}
              onChange={(e) => setSearchFilter({ topic: e.target.value })}
              aria-label="主题"
            >
              <option value="">全部主题</option>
              {topics.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
            <select
              className="select"
              style={{ flex: '1 1 46%' }}
              value={filter.noteType}
              onChange={(e) => setSearchFilter({ noteType: e.target.value })}
              aria-label="笔记类型"
            >
              <option value="">全部类型</option>
              {(taxonomy?.noteTypes ?? []).map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
            <select
              className="select"
              style={{ flex: '1 1 46%' }}
              value={filter.layer}
              onChange={(e) => setSearchFilter({ layer: e.target.value })}
              aria-label="知识层级"
            >
              <option value="">全部层级</option>
              {(taxonomy?.layers ?? []).map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
            <select
              className="select"
              style={{ flex: '1 1 46%' }}
              value={filter.visibility}
              onChange={(e) => setSearchFilter({ visibility: e.target.value })}
              aria-label="可见性"
            >
              <option value="">全部可见性</option>
              {(taxonomy?.visibilities ?? []).map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
            {activeFilters > 0 ? (
              <button
                className="btn sm ghost"
                onClick={() => setSearchFilter({ domain: '', topic: '', noteType: '', visibility: '', layer: '' })}
              >
                清除过滤
              </button>
            ) : null}
          </div>
        ) : null}
        {result ? (
          <div className="search-meta">
            <span style={{ color: 'var(--cyan)' }}>{result.total} 条</span>
            <span>{result.tookMs}ms</span>
            <span>topK {result.topK}</span>
            <span>候选 {result.candidates}</span>
            {result.perNoteQuota > 0 ? <span>每篇 ≤{result.perNoteQuota}</span> : <span>每篇不限</span>}
            {result.fullBody ? <span style={{ color: 'var(--magenta)' }}>完整正文</span> : null}
            <span className="channels" style={{ marginLeft: 'auto' }}>
              {Object.entries(result.weights).map(([k, v]) => (
                <span key={k} className={`ch ${CHANNEL_CLASS[k.toUpperCase()] ?? ''}`} title={`${k} 通道权重`}>
                  {CHANNEL_LABEL[k.toUpperCase()] ?? k} {v}
                </span>
              ))}
            </span>
          </div>
        ) : null}
      </div>

      <div className="pane-body">
        {error ? (
          <div style={{ padding: 12 }}>
            <div className="callout danger">
              <Icon name="alert" size={15} />
              <div>
                <b>检索失败</b>
                <div>{error}</div>
              </div>
            </div>
          </div>
        ) : null}

        {result && !result.available ? (
          <div style={{ padding: 12 }}>
            <div className="callout warn">
              <Icon name="alert" size={15} />
              <div>
                <b>语义通道不可用</b>
                <div>{result.message ?? '向量索引尚未就绪，当前只走精确与关键词通道。'}</div>
                <button className="btn sm" style={{ marginTop: 8 }} onClick={() => void rebuildIndex()}>
                  <Icon name="refresh" size={12} /> 重建索引
                </button>
              </div>
            </div>
          </div>
        ) : null}

        {searching && !result ? (
          <div style={{ padding: 12 }}>
            {[0, 1, 2].map((i) => (
              <div key={i} className="skeleton" style={{ height: 74, margin: '0 0 9px' }} />
            ))}
          </div>
        ) : null}

        {result && result.hits.length === 0 && !searching ? (
          <div className="empty" style={{ padding: '40px 20px' }}>
            <EmptyArt kind="search" />
            <h3 style={{ fontSize: 16 }}>没有命中</h3>
            <p style={{ fontSize: 12.5 }}>
              换个说法试试：语义通道能理解近义表达，精确通道认命令与报错原文。
            </p>
          </div>
        ) : null}

        {!result && !searching ? (
          <div className="empty" style={{ padding: '40px 20px' }}>
            <EmptyArt kind="search" />
            <h3 style={{ fontSize: 16 }}>三通道混合检索</h3>
            <p style={{ fontSize: 12.5 }}>
              精确匹配命令与报错串、BM25 关键词、向量语义，三路结果用 RRF 融合。
              命中会标出是哪几路找到的。
            </p>
            <div className="col" style={{ alignItems: 'stretch', width: '100%', maxWidth: 260 }}>
              <span className="eyebrow" style={{ textAlign: 'center' }}>试试这些</span>
              {EXAMPLES.map((x) => (
                <button key={x} className="chip" style={{ justifyContent: 'center' }} onClick={() => onType(x)}>
                  <Icon name="sparkles" size={11} />
                  {x}
                </button>
              ))}
            </div>
            {indexStatus && !indexStatus.available ? (
              <div className="callout warn" style={{ textAlign: 'left' }}>
                <Icon name="alert" size={14} />
                <div>
                  <b>向量索引未就绪</b>
                  <div>{indexStatus.message}</div>
                </div>
              </div>
            ) : null}
          </div>
        ) : null}

        {result?.hits.map((h) => (
          <div key={h.docId} id={`hit-${h.docId}`}>
            <HitRow hit={h} query={result.query} />
          </div>
        ))}
      </div>
    </>
  )
}

/* ==================== 侧栏外壳 ==================== */

export default function SidePane() {
  const sideTab = useStore((s) => s.sideTab)
  const setSideTab = useStore((s) => s.setSideTab)
  const toggleSide = useStore((s) => s.toggleSide)
  const mobile = useStore((s) => s.mobile)
  const result = useStore((s) => s.searchResult)
  const blocks = useBlocks()

  const count = sideTab === 'search' ? (result?.hits.length ?? 0) : sideTab === 'toc' ? blocks.length : 0

  return (
    <aside className="side-pane" aria-label="侧栏">
      <div className="side-tabs" role="tablist">
        {(
          [
            ['toc', 'toc', '大纲'],
            ['tree', 'tree', '目录'],
            ['search', 'search', '检索'],
          ] as const
        ).map(([tab, icon, label]) => (
          <button
            key={tab}
            role="tab"
            aria-selected={sideTab === tab}
            data-tab={tab}
            className={`side-tab${sideTab === tab ? ' on' : ''}`}
            onClick={() => setSideTab(tab)}
          >
            <Icon name={icon} size={13} />
            {label}
            {tab === 'search' && count > 0 ? <span className="pane-count">{count}</span> : null}
          </button>
        ))}
        {mobile ? (
          <button className="icon-btn" style={{ flex: 'none', width: 32 }} onClick={toggleSide} aria-label="收起侧栏">
            <Icon name="x" size={15} />
          </button>
        ) : null}
      </div>
      {sideTab === 'toc' ? <TocPanel /> : sideTab === 'tree' ? <TreePanel /> : <SearchPanel />}
    </aside>
  )
}