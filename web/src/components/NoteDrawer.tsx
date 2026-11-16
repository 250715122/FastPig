import { useEffect, useMemo, useState } from 'react'
import { useStore } from '../lib/store'
import { Icon } from './Icons'
import { api } from '../lib/api'
import { absDate, bytes } from '../lib/format'
import { useBlocks } from '../lib/blocks'

function TagInput({
  value,
  onChange,
  placeholder,
  icon,
}: {
  value: string[]
  onChange: (next: string[]) => void
  placeholder: string
  icon: string
}) {
  const [draft, setDraft] = useState('')

  const commit = (raw: string) => {
    const parts = raw
      .split(/[,，\s]+/)
      .map((x) => x.trim())
      .filter(Boolean)
    if (parts.length === 0) return
    const next = [...value]
    for (const p of parts) if (!next.includes(p)) next.push(p)
    onChange(next)
    setDraft('')
  }

  return (
    <div className="tag-input">
      <Icon name={icon} size={12} style={{ color: 'var(--faint)', flex: 'none' }} />
      {value.map((t) => (
        <span key={t} className="chip">
          {t}
          <button
            className="toast-x"
            style={{ width: 14, height: 14 }}
            aria-label={`移除 ${t}`}
            onClick={() => onChange(value.filter((x) => x !== t))}
          >
            <Icon name="x" size={9} />
          </button>
        </span>
      ))}
      <input
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        placeholder={value.length === 0 ? placeholder : ''}
        aria-label={placeholder}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ',' || e.key === '，') {
            e.preventDefault()
            commit(draft)
          } else if (e.key === 'Backspace' && !draft && value.length > 0) {
            onChange(value.slice(0, -1))
          }
        }}
        onBlur={() => commit(draft)}
      />
    </div>
  )
}

export default function NoteDrawer() {
  const detail = useStore((s) => s.detail)
  const taxonomy = useStore((s) => s.taxonomy)
  const system = useStore((s) => s.system)
  const unlockedKeys = useStore((s) => s.unlockedKeys)
  const setDrawer = useStore((s) => s.setDrawer)
  const updateMeta = useStore((s) => s.updateMeta)
  const guess = useStore((s) => s.guess)
  const setPrivateTarget = useStore((s) => s.setPrivateTarget)
  const lockNote = useStore((s) => s.lockNote)
  const removeNote = useStore((s) => s.removeNote)
  const restoreNote = useStore((s) => s.restoreNote)
  const toast = useStore((s) => s.toast)
  const blocks = useBlocks()

  const meta = detail?.meta
  const [desc, setDesc] = useState(meta?.desc ?? '')
  const [customTopic, setCustomTopic] = useState(false)
  const [topicDraft, setTopicDraft] = useState('')
  const [guessing, setGuessing] = useState(false)
  const [confirmDel, setConfirmDel] = useState(false)

  useEffect(() => {
    setDesc(meta?.desc ?? '')
    setConfirmDel(false)
    setCustomTopic(false)
  }, [meta?.key, meta?.desc])

  const domains = taxonomy?.domains ?? []
  const topics = useMemo(() => {
    const d = domains.find((x) => x.name === meta?.domain)
    return d ? d.topics.map((t) => t.name) : []
  }, [domains, meta?.domain])

  if (!detail || !meta) {
    return (
      <>
        <div className="drawer-scrim" onClick={() => setDrawer(false)} />
        <aside className="drawer" role="dialog" aria-label="笔记信息">
          <div className="drawer-head">
            <h2 style={{ margin: 0, fontSize: 15 }}>笔记信息</h2>
            <span className="spacer" />
            <button className="icon-btn" onClick={() => setDrawer(false)} aria-label="关闭">
              <Icon name="x" size={16} />
            </button>
          </div>
          <div className="drawer-body">
            <div className="preview-empty">先打开一篇笔记。</div>
          </div>
        </aside>
      </>
    )
  }

  const unlocked = unlockedKeys.includes(meta.key)
  const locked = meta.encrypted && !unlocked
  const filePath = `${system?.notesDir ?? 'notes'}/${meta.key}/note.md`

  const commitDesc = () => {
    const v = desc.trim()
    if (v === meta.desc) return
    void updateMeta({ desc: v, title: v || meta.key })
  }

  const copy = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text)
      toast({ kind: 'success', text: `已复制${label}`, ttl: 1500 })
    } catch {
      toast({ kind: 'warn', text: '复制失败', detail: '浏览器拒绝了剪贴板访问。' })
    }
  }

  return (
    <>
      <div className="drawer-scrim" onClick={() => setDrawer(false)} />
      <aside className="drawer" role="dialog" aria-label="笔记信息">
        <div className="drawer-head">
          <Icon name="info" size={17} style={{ color: 'var(--cyan)' }} />
          <div style={{ minWidth: 0 }}>
            <h2 style={{ margin: 0, fontSize: 15, fontFamily: 'var(--font-display)' }}>笔记信息</h2>
            <div className="mono faint" style={{ fontSize: 11 }}>{meta.key}</div>
          </div>
          <span className="spacer" />
          <button className="icon-btn" onClick={() => setDrawer(false)} aria-label="关闭抽屉">
            <Icon name="x" size={16} />
          </button>
        </div>

        <div className="drawer-body">
          <section className="drawer-section">
            <span className="eyebrow">
              <Icon name="zap" size={11} /> 快捷命令
            </span>
            <div className="field">
              <label className="field-label" htmlFor="nd-key">key（不可修改）</label>
              <div className="row">
                <input id="nd-key" className="input mono" value={meta.key} readOnly aria-readonly="true" />
                <button className="icon-btn" title="复制 key" aria-label="复制 key" onClick={() => void copy(meta.key, ' key')}>
                  <Icon name="copy" size={14} />
                </button>
              </div>
              <span className="field-hint">改 key 等于换一篇笔记：请新建后删除旧的，避免块 ID 与云端版本错乱。</span>
            </div>
            <div className="field">
              <label className="field-label" htmlFor="nd-desc">描述</label>
              <input
                id="nd-desc"
                className="input"
                value={desc}
                disabled={locked}
                onChange={(e) => setDesc(e.target.value)}
                onBlur={commitDesc}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault()
                    commitDesc()
                  }
                }}
                placeholder="一句话说明这篇笔记讲什么"
              />
              <span className="field-hint">描述同时作为标题，回车即保存。</span>
            </div>
          </section>

          <section className="drawer-section">
            <span className="eyebrow">
              <Icon name="folder" size={11} /> 分类
            </span>
            <div className="form-grid two">
              <div className="field">
                <label className="field-label" htmlFor="nd-domain">领域</label>
                <select
                  id="nd-domain"
                  className="select"
                  value={meta.domain ?? ''}
                  disabled={locked}
                  onChange={(e) => void updateMeta({ domain: e.target.value || null, topic: null })}
                >
                  <option value="">未分类</option>
                  {domains.map((d) => (
                    <option key={d.name} value={d.name}>
                      {d.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label className="field-label" htmlFor="nd-topic">主题</label>
                {customTopic ? (
                  <input
                    id="nd-topic"
                    className="input"
                    autoFocus
                    value={topicDraft}
                    onChange={(e) => setTopicDraft(e.target.value)}
                    onBlur={() => {
                      const v = topicDraft.trim()
                      setCustomTopic(false)
                      if (v) void updateMeta({ topic: v })
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') (e.target as HTMLInputElement).blur()
                      if (e.key === 'Escape') setCustomTopic(false)
                    }}
                    placeholder="自定义主题名"
                  />
                ) : (
                  <select
                    id="nd-topic"
                    className="select"
                    value={meta.topic ?? ''}
                    disabled={locked || !meta.domain}
                    onChange={(e) => {
                      if (e.target.value === '__custom') {
                        setTopicDraft(meta.topic ?? '')
                        setCustomTopic(true)
                      } else {
                        void updateMeta({ topic: e.target.value || null })
                      }
                    }}
                  >
                    <option value="">未指定</option>
                    {topics.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                    {meta.topic && !topics.includes(meta.topic) ? (
                      <option value={meta.topic}>{meta.topic}（自定义）</option>
                    ) : null}
                    <option value="__custom">自定义…</option>
                  </select>
                )}
              </div>
              <div className="field">
                <label className="field-label" htmlFor="nd-type">笔记类型</label>
                <select
                  id="nd-type"
                  className="select"
                  value={meta.noteType ?? ''}
                  disabled={locked}
                  onChange={(e) => void updateMeta({ noteType: e.target.value || null })}
                >
                  <option value="">未指定</option>
                  {(taxonomy?.noteTypes ?? []).map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label className="field-label" htmlFor="nd-layer">知识层级</label>
                <select
                  id="nd-layer"
                  className="select"
                  value={meta.layer ?? ''}
                  disabled={locked}
                  onChange={(e) => void updateMeta({ layer: e.target.value || null })}
                >
                  <option value="">未指定</option>
                  {(taxonomy?.layers ?? []).map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field full">
                <label className="field-label" htmlFor="nd-vis">可见性</label>
                <select
                  id="nd-vis"
                  className="select"
                  value={meta.visibility ?? 'normal'}
                  disabled={locked}
                  onChange={(e) => void updateMeta({ visibility: e.target.value })}
                >
                  {(taxonomy?.visibilities ?? ['normal', 'private']).map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <button
              className="btn acid block"
              disabled={locked || guessing}
              onClick={() => {
                setGuessing(true)
                void guess().finally(() => setGuessing(false))
              }}
            >
              <Icon name={guessing ? 'refresh' : 'sparkles'} size={14} className={guessing ? 'spin' : ''} />
              {guessing ? '正在猜分类…' : '自动猜一个分类'}
            </button>
          </section>

          <section className="drawer-section">
            <span className="eyebrow">
              <Icon name="tag" size={11} /> 标签与别名
            </span>
            <TagInput
              value={meta.tags ?? []}
              onChange={(next) => void updateMeta({ tags: next })}
              placeholder="加标签，回车确认"
              icon="tag"
            />
            <TagInput
              value={meta.aliases ?? []}
              onChange={(next) => void updateMeta({ aliases: next })}
              placeholder="加别名（拼音 / 缩写 / 英文名）"
              icon="command"
            />
            <span className="field-hint">别名会进精确通道，敲缩写也能直达这篇笔记。</span>
          </section>

          <section className="drawer-section">
            <span className="eyebrow">
              <Icon name="shield" size={11} /> 加密
            </span>
            {meta.encrypted ? (
              <>
                <div className="callout info">
                  <Icon name={locked ? 'lock' : 'unlock'} size={15} />
                  <div>
                    <b>{locked ? '锁定中' : '已解锁'}</b>
                    <div>
                      正文以 AES-GCM 密文落盘并同步到云端。
                      {detail.crypto?.masterWrappedDek ? '支持主密码恢复。' : '没有主密码恢复信息。'}
                    </div>
                  </div>
                </div>
                <div className="row wrap">
                  {locked ? (
                    <button className="btn primary sm" onClick={() => setPrivateTarget({ mode: 'unlock', key: meta.key })}>
                      <Icon name="unlock" size={13} /> 解锁
                    </button>
                  ) : (
                    <>
                      <button className="btn sm" onClick={() => lockNote(meta.key)}>
                        <Icon name="lock" size={13} /> 锁定
                      </button>
                      <button className="btn sm" onClick={() => setPrivateTarget({ mode: 'change', key: meta.key })}>
                        <Icon name="key" size={13} /> 改密码
                      </button>
                      <button className="btn sm danger" onClick={() => setPrivateTarget({ mode: 'disable', key: meta.key })}>
                        <Icon name="alert" size={13} /> 取消加密
                      </button>
                    </>
                  )}
                </div>
              </>
            ) : (
              <>
                <div className="callout warn">
                  <Icon name="alert" size={15} />
                  <div>
                    <b>明文存储</b>
                    <div>这篇笔记的正文是明文，会原样同步到云端。含口令、凭据的内容建议启用加密。</div>
                  </div>
                </div>
                <button className="btn primary sm" onClick={() => setPrivateTarget({ mode: 'enable', key: meta.key })}>
                  <Icon name="shield" size={13} /> 启用端到端加密
                </button>
              </>
            )}
          </section>

          {detail.assets && detail.assets.length > 0 ? (
            <section className="drawer-section">
              <span className="eyebrow">
                <Icon name="image" size={11} /> 图片资源 {detail.assets.length}
              </span>
              <div className="col" style={{ gap: 6 }}>
                {detail.assets.map((a) => (
                  <div key={a} className="row" style={{ gap: 8 }}>
                    <img
                      src={api.assetUrl(meta.key, a)}
                      alt={a}
                      style={{
                        width: 42,
                        height: 42,
                        objectFit: 'cover',
                        borderRadius: 'var(--r-sm)',
                        border: '1px solid var(--line)',
                        flex: 'none',
                      }}
                    />
                    <span className="mono truncate" style={{ flex: 1, fontSize: 11 }}>{a}</span>
                    <button
                      className="icon-btn sm"
                      title="复制 Markdown 引用"
                      aria-label="复制 Markdown 引用"
                      onClick={() => void copy(`![${a}](assets/${a})`, '图片引用')}
                    >
                      <Icon name="copy" size={12} />
                    </button>
                  </div>
                ))}
              </div>
            </section>
          ) : null}

          <section className="drawer-section">
            <span className="eyebrow">
              <Icon name="file" size={11} /> 文件与版本
            </span>
            <dl className="kv">
              <dt>路径</dt>
              <dd>{filePath}</dd>
              <dt>版本</dt>
              <dd>v{meta.version}</dd>
              <dt>知识块</dt>
              <dd>
                {meta.blockCount} 块{blocks.length > 0 ? `（已载入 ${blocks.length}）` : ''}
              </dd>
              <dt>正文大小</dt>
              <dd>{bytes(new TextEncoder().encode(detail.bodyMd ?? '').length)}</dd>
              <dt>创建</dt>
              <dd>{absDate(meta.createdAt)}</dd>
              <dt>更新</dt>
              <dd>{absDate(meta.updatedAt)}</dd>
              <dt>内容哈希</dt>
              <dd>{meta.contentHash ?? '—'}</dd>
              <dt>笔记 ID</dt>
              <dd>{meta.id}</dd>
            </dl>
            <button className="btn sm ghost block" onClick={() => void copy(filePath, '文件路径')}>
              <Icon name="copy" size={12} /> 复制文件路径
            </button>
          </section>

          <section className="drawer-section">
            <span className="eyebrow">
              <Icon name="trash" size={11} /> 危险操作
            </span>
            {meta.deleted ? (
              <button className="btn acid block" onClick={() => void restoreNote(meta.key)}>
                <Icon name="undo" size={14} /> 恢复这篇笔记
              </button>
            ) : confirmDel ? (
              <div className="col">
                <div className="callout danger">
                  <Icon name="alert" size={15} />
                  <div>
                    <b>确认删除 {meta.key}？</b>
                    <div>删除后 60 秒内可在提示条里撤销，之后进回收站。</div>
                  </div>
                </div>
                <div className="row">
                  <button
                    className="btn danger"
                    onClick={() => {
                      setDrawer(false)
                      void removeNote(meta.key)
                    }}
                  >
                    <Icon name="trash" size={14} /> 删除
                  </button>
                  <button className="btn ghost" onClick={() => setConfirmDel(false)}>
                    取消
                  </button>
                </div>
              </div>
            ) : (
              <button className="btn danger block" onClick={() => setConfirmDel(true)}>
                <Icon name="trash" size={14} /> 删除笔记
              </button>
            )}
          </section>
        </div>
      </aside>
    </>
  )
}