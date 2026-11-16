import { useEffect, useMemo, useState } from 'react'
import { useStore } from '../lib/store'
import { Icon } from './Icons'
import { api } from '../lib/api'
import { absDate, duration } from '../lib/format'
import type { ConfigItem } from '../lib/types'

const SELECTS: Record<string, string[]> = {
  'index.analyzer': ['standard', 'smartcn'],
  'ui.theme': ['light', 'dark'],
  'cloud.provider': ['nutstore'],
  'editor.font.name': ['JetBrains Mono', 'Cascadia Code', 'Consolas', 'Microsoft YaHei', 'monospace'],
}

const ICONS: Record<string, string> = {
  云同步: 'cloudUp',
  编辑器: 'edit',
  界面: 'grid',
  安全: 'shield',
  快捷键: 'keyboard',
  检索: 'search',
  索引: 'database',
  知识库: 'book',
  网页端: 'external',
  关于: 'info',
}

const SUBS: Record<string, string> = {
  云同步: '坚果云 WebDAV。私密笔记上传的也是密文，云端拿不到明文。',
  编辑器: '字号、行高、自动保存与预览分节阈值，改完立刻生效。',
  界面: '桌面端窗口与提示时长；网页端主题在左侧导航轨切换。',
  快捷键: '桌面端全局热键。网页端快捷键见命令面板底部。',
  检索: '三通道权重与候选规模。权重改动立即影响下一次检索。',
  索引: '分词器、块正文长度与模型参数。标注「需重建」的项改完要重建索引才生效。',
  知识库: '分块粒度与块 ID 策略，决定知识块怎么切、怎么编号。',
  网页端: '监听地址、端口与会话有效期。局域网可访问意味着同一网段的人都能打开登录页。',
}

function ConfigRow({
  item,
  value,
  onChange,
  onReset,
}: {
  item: ConfigItem
  value: string
  onChange: (v: string) => void
  onReset: () => void
}) {
  const changed = value !== item.value
  const options = SELECTS[item.key]
  const numeric = item.type === 'int' || item.type === 'double'
  const hasRange = numeric && item.min !== null && item.max !== null && item.max > item.min
  const step = item.type === 'double' ? (item.max !== null && item.max <= 10 ? 0.05 : 0.1) : 1

  return (
    <div className="cfg-row">
      <div>
        <div className="cfg-label">
          {item.label}
          {item.needsReindex ? <span className="badge amber">需重建</span> : null}
          {changed ? <span className="badge cyan">待应用</span> : null}
        </div>
        <div className="cfg-key">{item.key}</div>
      </div>
      <div className="cfg-control">
        {item.type === 'boolean' ? (
          <span className={`switch${value === 'true' ? ' on' : ''}`}>
            <input
              type="checkbox"
              checked={value === 'true'}
              aria-label={item.label}
              onChange={(e) => onChange(e.target.checked ? 'true' : 'false')}
            />
          </span>
        ) : options ? (
          <select className="select" value={value} aria-label={item.label} onChange={(e) => onChange(e.target.value)}>
            {options.map((o) => (
              <option key={o} value={o}>
                {o}
              </option>
            ))}
            {value && !options.includes(value) ? <option value={value}>{value}</option> : null}
          </select>
        ) : hasRange ? (
          <div className="cfg-range">
            <input
              type="range"
              min={item.min ?? 0}
              max={item.max ?? 100}
              step={step}
              value={Number(value)}
              aria-label={item.label}
              onChange={(e) => onChange(e.target.value)}
            />
            <input
              className="input mono"
              type="number"
              style={{ width: 70, height: 26, flex: 'none' }}
              min={item.min ?? undefined}
              max={item.max ?? undefined}
              step={step}
              value={value}
              aria-label={`${item.label} 数值`}
              onChange={(e) => onChange(e.target.value)}
            />
          </div>
        ) : (
          <input
            className={`input${numeric ? ' mono' : ''}`}
            type={numeric ? 'number' : 'text'}
            value={value}
            aria-label={item.label}
            onChange={(e) => onChange(e.target.value)}
          />
        )}
        <button
          className="cfg-reset"
          title={`恢复默认：${item.def}`}
          aria-label={`恢复默认 ${item.label}`}
          disabled={value === item.def}
          onClick={onReset}
        >
          <Icon name="undo" size={12} />
        </button>
      </div>
    </div>
  )
}

function SecuritySection() {
  const config = useStore((s) => s.config)
  const setMasterPassword = useStore((s) => s.setMasterPassword)
  const verifyMaster = useStore((s) => s.verifyMaster)
  const logout = useStore((s) => s.logout)
  const toast = useStore((s) => s.toast)
  const setSettings = useStore((s) => s.setSettings)
  const [master, setMaster] = useState('')
  const [master2, setMaster2] = useState('')
  const [login, setLogin] = useState('')
  const [login2, setLogin2] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const set = config?.masterPasswordSet ?? false

  const run = async (id: string, fn: () => Promise<void>) => {
    setBusy(id)
    setErr(null)
    try {
      await fn()
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(null)
    }
  }

  return (
    <>
      <h3 className="settings-section-title">
        <Icon name="shield" size={16} style={{ color: 'var(--vermilion)' }} /> 安全
      </h3>
      <p className="settings-section-sub">
        两套口令互不相干：登录口令保护这个网页服务，主密码只用于恢复私密笔记的数据密钥。
        两者都不会以明文离开这台浏览器。
      </p>

      <div className="col" style={{ gap: 14 }}>
        <div className="callout info">
          <Icon name="lock" size={15} />
          <div>
            <b>主密码 {set ? '已设置' : '未设置'}</b>
            <div>
              {set
                ? '忘记笔记密码时，用主密码解开被它包裹的数据密钥。主密码本身无法修改：改了它，所有已加密笔记的恢复通道都会失效，必须逐篇重新加密。'
                : '启用私密笔记前必须先设置主密码，否则忘记密码就再也打不开正文。'}
            </div>
          </div>
        </div>

        {set ? (
          <div className="form-grid two">
            <div className="field full">
              <label className="field-label" htmlFor="st-master">校验主密码（校验通过后本次会话可直接用于恢复）</label>
              <input
                id="st-master"
                className="input mono"
                type="password"
                value={master}
                autoComplete="off"
                onChange={(e) => setMaster(e.target.value)}
              />
            </div>
            <div className="full row">
              <button
                className="btn primary"
                disabled={!master || busy === 'verify'}
                onClick={() =>
                  void run('verify', async () => {
                    const ok = await verifyMaster(master)
                    if (!ok) throw new Error('主密码不正确')
                    setMaster('')
                    toast({ kind: 'success', text: '主密码校验通过', detail: '本次会话可用它恢复私密笔记。' })
                  })
                }
              >
                <Icon name={busy === 'verify' ? 'refresh' : 'key'} size={14} className={busy === 'verify' ? 'spin' : ''} />
                校验并记住
              </button>
            </div>
          </div>
        ) : (
          <div className="form-grid two">
            <div className="field">
              <label className="field-label" htmlFor="st-m1">主密码（至少 8 位）</label>
              <input
                id="st-m1"
                className="input mono"
                type="password"
                value={master}
                autoComplete="off"
                onChange={(e) => setMaster(e.target.value)}
              />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="st-m2">确认主密码</label>
              <input
                id="st-m2"
                className="input mono"
                type="password"
                value={master2}
                autoComplete="off"
                onChange={(e) => setMaster2(e.target.value)}
              />
            </div>
            <div className="full row">
              <button
                className="btn primary"
                disabled={busy === 'set'}
                onClick={() =>
                  void run('set', async () => {
                    if (master.length < 8) throw new Error('主密码至少 8 位')
                    if (master !== master2) throw new Error('两次输入不一致')
                    await setMasterPassword(master)
                    setMaster('')
                    setMaster2('')
                  })
                }
              >
                <Icon name={busy === 'set' ? 'refresh' : 'shield'} size={14} className={busy === 'set' ? 'spin' : ''} />
                设置主密码
              </button>
            </div>
          </div>
        )}

        <div className="divider" />

        <h3 className="settings-section-title" style={{ fontSize: 13.5 }}>
          <Icon name="key" size={15} style={{ color: 'var(--amber)' }} /> 登录口令
        </h3>
        <p className="settings-section-sub" style={{ marginBottom: 10 }}>
          重置后当前会话立即失效，需要用新口令重新登录。忘记口令时可在服务端用
          <span className="mono"> --web-password=新口令 </span>
          启动参数重置。
        </p>
        <div className="form-grid two">
          <div className="field">
            <label className="field-label" htmlFor="st-l1">新登录口令（至少 8 位）</label>
            <input
              id="st-l1"
              className="input mono"
              type="password"
              value={login}
              autoComplete="off"
              onChange={(e) => setLogin(e.target.value)}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="st-l2">确认新登录口令</label>
            <input
              id="st-l2"
              className="input mono"
              type="password"
              value={login2}
              autoComplete="off"
              onChange={(e) => setLogin2(e.target.value)}
            />
          </div>
          <div className="full row">
            <button
              className="btn danger"
              disabled={busy === 'login'}
              onClick={() =>
                void run('login', async () => {
                  if (login.length < 8) throw new Error('登录口令至少 8 位')
                  if (login !== login2) throw new Error('两次输入不一致')
                  await api.resetPassword(login)
                  setSettings(false)
                  await logout()
                })
              }
            >
              <Icon name={busy === 'login' ? 'refresh' : 'refresh'} size={14} className={busy === 'login' ? 'spin' : ''} />
              重置并重新登录
            </button>
            <button className="btn ghost" onClick={() => void logout()}>
              <Icon name="logout" size={14} /> 退出登录
            </button>
          </div>
        </div>

        {err ? (
          <div className="callout danger">
            <Icon name="alert" size={15} />
            <div>{err}</div>
          </div>
        ) : null}
      </div>
    </>
  )
}

function AboutSection() {
  const system = useStore((s) => s.system)
  const indexStatus = useStore((s) => s.indexStatus)
  const config = useStore((s) => s.config)
  const rebuildIndex = useStore((s) => s.rebuildIndex)
  const refreshSystem = useStore((s) => s.refreshSystem)

  const stats = [
    { label: '笔记', value: system?.noteCount ?? 0, tint: 'var(--cyan)' },
    { label: '领域', value: system?.domainCount ?? 0, tint: 'var(--violet)' },
    { label: '主题', value: system?.topicCount ?? 0, tint: 'var(--magenta)' },
    { label: '知识块', value: system?.blockCount ?? 0, tint: 'var(--acid)' },
  ]

  return (
    <>
      <h3 className="settings-section-title">
        <Icon name="info" size={16} style={{ color: 'var(--cyan)' }} /> 关于这个知识库
      </h3>
      <p className="settings-section-sub">
        服务端跑在这台机器上，笔记以 Markdown 单文件存储，块级索引与向量检索都在本地完成。
      </p>

      <div className="stat-grid">
        {stats.map((s) => (
          <div key={s.label} className="stat-card" style={{ ['--tint' as string]: s.tint }}>
            <b>{s.value}</b>
            <span>{s.label}</span>
          </div>
        ))}
      </div>

      <div className="divider" style={{ margin: '16px 0' }} />

      <dl className="kv" style={{ gridTemplateColumns: '110px minmax(0,1fr)' }}>
        <dt>应用</dt>
        <dd>{system?.app ?? '—'}</dd>
        <dt>数据目录</dt>
        <dd>{system?.baseDir ?? '—'}</dd>
        <dt>笔记目录</dt>
        <dd>{system?.notesDir ?? '—'}</dd>
        <dt>数据库</dt>
        <dd>{system?.dbPath ?? '—'}</dd>
        <dt>Java</dt>
        <dd>{system?.javaVersion ?? '—'}</dd>
        <dt>启动时间</dt>
        <dd>{system ? absDate(system.startedAt) : '—'}</dd>
        <dt>已运行</dt>
        <dd>{system ? duration(Date.now() - system.startedAt) : '—'}</dd>
        <dt>上次同步</dt>
        <dd>{system?.lastSyncAt ? absDate(system.lastSyncAt) : '从未'}</dd>
        <dt>索引状态</dt>
        <dd>{indexStatus?.message ?? '—'}</dd>
        <dt>向量提供方</dt>
        <dd>
          {indexStatus?.provider ?? '—'} · {indexStatus?.dimension ?? 0} 维 · 模型
          {indexStatus?.modelDownloaded ? '已就绪' : '未下载'}
        </dd>
        <dt>已索引块</dt>
        <dd>{indexStatus?.indexedCount ?? 0}</dd>
        <dt>网页会话</dt>
        <dd>
          {config?.web.sessions ?? 0} 个登录 · {config?.web.subscribers ?? 0} 个事件订阅
        </dd>
      </dl>

      <div className="row" style={{ marginTop: 16 }}>
        <button className="btn primary" onClick={() => void rebuildIndex()}>
          <Icon name="database" size={14} /> 重建向量索引
        </button>
        <button className="btn ghost" onClick={() => void refreshSystem()}>
          <Icon name="refresh" size={14} /> 刷新状态
        </button>
      </div>

      {indexStatus?.rebuilding ? (
        <div className="callout warn" style={{ marginTop: 12 }}>
          <Icon name="refresh" size={15} className="spin" />
          <div>
            正在重建 {indexStatus.current}/{indexStatus.total}
            <div className="progress-mini" style={{ width: '100%', marginTop: 6 }}>
              <i
                style={{
                  width: `${indexStatus.total > 0 ? Math.round((indexStatus.current / indexStatus.total) * 100) : 0}%`,
                }}
              />
            </div>
          </div>
        </div>
      ) : null}
    </>
  )
}

export default function Settings() {
  const config = useStore((s) => s.config)
  const setSettings = useStore((s) => s.setSettings)
  const updateConfig = useStore((s) => s.updateConfig)
  const rebuildIndex = useStore((s) => s.rebuildIndex)
  const refreshConfig = useStore((s) => s.refreshConfig)
  const toast = useStore((s) => s.toast)
  const [tab, setTab] = useState('检索')
  const [draft, setDraft] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [reindexKeys, setReindexKeys] = useState<string[]>([])

  useEffect(() => {
    if (!config) void refreshConfig()
  }, [config, refreshConfig])

  const groups = useMemo(() => {
    const map = new Map<string, ConfigItem[]>()
    for (const it of config?.items ?? []) {
      const list = map.get(it.categoryLabel) ?? []
      list.push(it)
      map.set(it.categoryLabel, list)
    }
    return map
  }, [config])

  const nav = useMemo(() => {
    const labels = (config?.categories ?? []).filter((c) => groups.has(c))
    return [...labels, '安全', '关于']
  }, [config, groups])

  useEffect(() => {
    if (!nav.includes(tab) && nav.length > 0) setTab(nav[0])
  }, [nav, tab])

  const current = groups.get(tab) ?? []
  const valueOf = (it: ConfigItem) => draft[it.key] ?? it.value
  const pending = current.filter((it) => draft[it.key] !== undefined && draft[it.key] !== it.value)
  const allPending = (config?.items ?? []).filter((it) => draft[it.key] !== undefined && draft[it.key] !== it.value)
  const pendingIn = (label: string) =>
    (groups.get(label) ?? []).filter((it) => draft[it.key] !== undefined && draft[it.key] !== it.value).length

  const apply = async () => {
    if (allPending.length === 0) return
    setBusy(true)
    try {
      const changes: Record<string, string> = {}
      for (const it of allPending) changes[it.key] = draft[it.key]
      const needs = await updateConfig(changes)
      setDraft({})
      setReindexKeys(needs)
      toast({
        kind: 'success',
        text: `已应用 ${allPending.length} 项配置`,
        detail: needs.length > 0 ? `${needs.length} 项需要重建索引后生效` : '立即生效',
      })
    } catch (e) {
      toast({ kind: 'error', text: '配置保存失败', detail: e instanceof Error ? e.message : String(e) })
    } finally {
      setBusy(false)
    }
  }

  const web = config?.web
  const lanUrl = web ? `http://${web.bind === '0.0.0.0' ? '<本机局域网 IP>' : web.bind}:${web.port}` : ''

  return (
    <div className="overlay" onClick={() => setSettings(false)}>
      <div className="modal wide" onClick={(e) => e.stopPropagation()} role="dialog" aria-label="设置">
        <div className="modal-head">
          <h2>
            <Icon name="settings" size={17} style={{ color: 'var(--cyan)' }} /> 设置
          </h2>
          <span className="sub">改动先攒在本地，点应用才写回服务端</span>
          <span className="spacer" />
          {allPending.length > 0 ? <span className="badge amber">{allPending.length} 项待应用</span> : null}
          <button className="icon-btn" onClick={() => setSettings(false)} aria-label="关闭设置">
            <Icon name="x" size={16} />
          </button>
        </div>

        <div className="settings">
          <nav className="settings-nav" aria-label="设置分类">
            {nav.map((c) => (
              <button key={c} className={tab === c ? 'on' : ''} onClick={() => setTab(c)}>
                <Icon name={ICONS[c] ?? 'settings'} size={14} />
                {c}
                <span className="n" style={pendingIn(c) > 0 ? { color: 'var(--amber)' } : undefined}>
                  {pendingIn(c) > 0 ? `+${pendingIn(c)}` : (groups.get(c)?.length ?? '')}
                </span>
              </button>
            ))}
          </nav>

          <div className="settings-main">
            {tab === '安全' ? (
              <SecuritySection />
            ) : tab === '关于' ? (
              <AboutSection />
            ) : (
              <>
                <h3 className="settings-section-title">
                  <Icon name={ICONS[tab] ?? 'settings'} size={16} style={{ color: 'var(--cyan)' }} /> {tab}
                  {pending.length > 0 ? <span className="badge amber">{pending.length} 项待应用</span> : null}
                </h3>
                <p className="settings-section-sub">{SUBS[tab] ?? '这些配置与桌面端共用同一份 config.properties。'}</p>

                {tab === '网页端' && web ? (
                  <div className="col" style={{ gap: 10, marginBottom: 14 }}>
                    <div className="callout info">
                      <Icon name="external" size={15} />
                      <div>
                        <b>当前访问地址</b>
                        <div className="mono">{lanUrl}</div>
                        <div>
                          {web.sessions} 个登录会话 · {web.subscribers} 个事件订阅 · 会话有效期 {web.sessionTtl} 分钟
                        </div>
                      </div>
                    </div>
                    {web.bind === '0.0.0.0' ? (
                      <div className="callout warn">
                        <Icon name="alert" size={15} />
                        <div>
                          <b>局域网可访问</b>
                          <div>
                            同一网段的设备都能打开登录页。未加密笔记里的口令、连接串会以明文经过 HTTP 传输，
                            含凭据的笔记请启用端到端加密，或把监听地址改回 127.0.0.1。
                          </div>
                        </div>
                      </div>
                    ) : null}
                  </div>
                ) : null}

                {tab === '检索' ? <WeightBars items={config?.items ?? []} draft={draft} /> : null}

                {current.length === 0 ? (
                  <div className="preview-empty">这个分类下没有可配置项。</div>
                ) : (
                  current.map((it) => (
                    <ConfigRow
                      key={it.key}
                      item={it}
                      value={valueOf(it)}
                      onChange={(v) => setDraft((d) => ({ ...d, [it.key]: v }))}
                      onReset={() => setDraft((d) => ({ ...d, [it.key]: it.def }))}
                    />
                  ))
                )}
              </>
            )}

            {reindexKeys.length > 0 ? (
              <div className="callout warn" style={{ marginTop: 18 }}>
                <Icon name="alert" size={15} />
                <div>
                  <b>这些项要重建索引才生效</b>
                  <div className="mono" style={{ fontSize: 11 }}>{reindexKeys.join(', ')}</div>
                  <button
                    className="btn sm primary"
                    style={{ marginTop: 8 }}
                    onClick={() => {
                      setReindexKeys([])
                      void rebuildIndex()
                    }}
                  >
                    <Icon name="database" size={12} /> 立即重建
                  </button>
                </div>
              </div>
            ) : null}
          </div>
        </div>

        <div className="modal-foot">
          <button
            className="btn ghost sm"
            disabled={allPending.length === 0}
            onClick={() => setDraft({})}
          >
            <Icon name="undo" size={13} /> 放弃改动
          </button>
          <span className="spacer" />
          <span className="faint" style={{ fontSize: 11.5 }}>
            {allPending.length > 0 ? `${allPending.length} 项待应用` : '配置已是最新'}
          </span>
          <button className="btn primary" disabled={allPending.length === 0 || busy} onClick={() => void apply()}>
            <Icon name={busy ? 'refresh' : 'check'} size={14} className={busy ? 'spin' : ''} />
            应用
          </button>
        </div>
      </div>
    </div>
  )
}

function WeightBars({ items, draft }: { items: ConfigItem[]; draft: Record<string, string> }) {
  const get = (key: string) => {
    const it = items.find((x) => x.key === key)
    if (!it) return 0
    return Number(draft[key] ?? it.value)
  }
  const rows = [
    { label: '精确', key: 'search.rrf.weight.exact', cls: 'ch-exact', color: 'var(--ch-exact)' },
    { label: '关键词', key: 'search.rrf.weight.bm25', cls: 'ch-bm25', color: 'var(--ch-bm25)' },
    { label: '语义', key: 'search.rrf.weight.vector', cls: 'ch-vector', color: 'var(--ch-vector)' },
  ]
  const max = Math.max(1, ...rows.map((r) => get(r.key)))

  return (
    <div className="col" style={{ gap: 7, marginBottom: 16 }}>
      <span className="eyebrow">RRF 通道权重</span>
      {rows.map((r) => (
        <div key={r.key} className="row" style={{ gap: 9 }}>
          <span className={`ch ${r.cls}`} style={{ width: 52, justifyContent: 'center' }}>
            {r.label}
          </span>
          <span
            style={{
              flex: 1,
              height: 7,
              borderRadius: 4,
              background: 'var(--surface-3)',
              overflow: 'hidden',
            }}
          >
            <i
              style={{
                display: 'block',
                height: '100%',
                width: `${(get(r.key) / max) * 100}%`,
                background: r.color,
                transition: 'width 220ms var(--ease)',
              }}
            />
          </span>
          <span className="mono" style={{ fontSize: 11.5, color: r.color, width: 40, textAlign: 'right' }}>
            {get(r.key).toFixed(2)}
          </span>
        </div>
      ))}
    </div>
  )
}