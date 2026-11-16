import { useEffect, useState } from 'react'
import { useStore } from '../lib/store'
import { Icon } from './Icons'
import { duration, relTime } from '../lib/format'

export default function StatusBar() {
  const connection = useStore((s) => s.connection)
  const system = useStore((s) => s.system)
  const indexStatus = useStore((s) => s.indexStatus)
  const syncStatus = useStore((s) => s.syncStatus)
  const notes = useStore((s) => s.notes)
  const detail = useStore((s) => s.detail)
  const dirty = useStore((s) => s.dirty)
  const saving = useStore((s) => s.saving)
  const lastSavedAt = useStore((s) => s.lastSavedAt)
  const unlockedKeys = useStore((s) => s.unlockedKeys)
  const setSyncOpen = useStore((s) => s.setSyncOpen)
  const setSettings = useStore((s) => s.setSettings)
  const rebuildIndex = useStore((s) => s.rebuildIndex)
  const [, tick] = useState(0)

  // 相对时间与运行时长要自己走字
  useEffect(() => {
    const t = window.setInterval(() => tick((v) => v + 1), 15_000)
    return () => window.clearInterval(t)
  }, [])

  const conn =
    connection === 'online'
      ? { cls: 'live', icon: 'wifi', text: '已连接' }
      : connection === 'reconnecting'
        ? { cls: 'busy', icon: 'wifiOff', text: '重连中' }
        : { cls: 'bad', icon: 'wifiOff', text: '离线' }

  const pct =
    indexStatus && indexStatus.total > 0 ? Math.round((indexStatus.current / indexStatus.total) * 100) : 0
  const locked = detail?.meta.encrypted ? !unlockedKeys.includes(detail.meta.key) : false

  return (
    <footer className="statusbar">
      <span className={`status-pill ${conn.cls}`} title={`事件流：${conn.text}`}>
        <span className="pulse-dot" />
        {conn.text}
      </span>

      <span className="status-group status-hide-sm">
        <Icon name="file" size={12} />
        {notes.length} 篇
      </span>
      <span className="status-group status-hide-sm">
        <Icon name="layers" size={12} />
        {system?.blockCount ?? 0} 块
      </span>

      <span className="status-sep status-hide-sm" />

      {indexStatus?.rebuilding ? (
        <span className="status-pill busy" title={indexStatus.message}>
          <Icon name="refresh" size={11} className="spin" />
          索引 {indexStatus.current}/{indexStatus.total}
          <span className="progress-mini">
            <i style={{ width: `${pct}%` }} />
          </span>
        </span>
      ) : indexStatus?.available ? (
        <button
          className="status-pill live"
          title={`向量索引就绪 · ${indexStatus.provider} · ${indexStatus.dimension} 维 · 点击重建`}
          onClick={() => void rebuildIndex()}
        >
          <Icon name="database" size={11} />
          索引 {indexStatus.indexedCount}
        </button>
      ) : (
        <button
          className="status-pill bad"
          title={indexStatus?.message ?? '索引不可用'}
          onClick={() => void rebuildIndex()}
        >
          <Icon name="alert" size={11} />
          索引未就绪
        </button>
      )}

      {indexStatus?.schemaOutdated ? (
        <span className="status-pill busy status-hide-sm" title="索引结构版本落后，重建后生效">
          结构待升级
        </span>
      ) : null}

      <span className="status-group grow">
        {detail ? (
          <>
            <span className="status-sep" />
            <span style={{ color: 'var(--cyan)' }}>{detail.meta.key}</span>
            <span className="status-hide-sm">v{detail.meta.version}</span>
            <span className="status-hide-sm">{detail.meta.blockCount} 块</span>
            {locked ? (
              <span style={{ color: 'var(--vermilion)' }}>
                <Icon name="lock" size={11} /> 锁定
              </span>
            ) : null}
            <span className="status-sep" />
            {saving ? (
              <span style={{ color: 'var(--amber)' }}>保存中…</span>
            ) : dirty ? (
              <span style={{ color: 'var(--amber)' }}>● 未保存</span>
            ) : (
              <span style={{ color: 'var(--acid)' }}>✓ 已保存 {lastSavedAt ? relTime(lastSavedAt) : ''}</span>
            )}
          </>
        ) : (
          <span className="faint">未打开笔记</span>
        )}
      </span>

      <button
        className={`status-pill${syncStatus?.enabled && syncStatus.configured ? '' : ' status-hide-sm'}`}
        title={syncStatus?.message ?? '云同步状态'}
        onClick={() => setSyncOpen(true)}
      >
        <Icon name={syncStatus?.enabled ? 'cloudUp' : 'cloudDown'} size={11} />
        {syncStatus?.enabled
          ? syncStatus.configured
            ? system?.lastSyncAt
              ? `同步 ${relTime(system.lastSyncAt)}`
              : '同步就绪'
            : '未配置'
          : '同步关闭'}
      </button>

      <span className="status-group status-hide-sm" title={`Java ${system?.javaVersion ?? '—'}`}>
        <Icon name="cpu" size={12} />
        {system ? duration(Date.now() - system.startedAt) : '—'}
      </span>

      <button className="status-pill" title="设置" onClick={() => setSettings(true)}>
        <Icon name="settings" size={11} />
        {system?.autosaveSeconds ?? 10}s 自动保存
      </button>
    </footer>
  )
}