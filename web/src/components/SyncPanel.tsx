import { useEffect, useState } from 'react'
import { useStore } from '../lib/store'
import { Icon } from './Icons'
import { api } from '../lib/api'
import { absDate, duration, relTime } from '../lib/format'

const PROVIDER_LABEL: Record<string, string> = {
  nutstore: '坚果云 WebDAV',
  none: '未启用',
}

export default function SyncPanel() {
  const sync = useStore((s) => s.syncStatus)
  const system = useStore((s) => s.system)
  const config = useStore((s) => s.config)
  const notes = useStore((s) => s.notes)
  const setSyncOpen = useStore((s) => s.setSyncOpen)
  const setSettings = useStore((s) => s.setSettings)
  const doSync = useStore((s) => s.doSync)
  const refreshSystem = useStore((s) => s.refreshSystem)

  const [busy, setBusy] = useState<'upload' | 'pull' | null>(null)
  const [refreshing, setRefreshing] = useState(false)

  useEffect(() => {
    void api
      .syncStatus()
      .then((s) => useStore.setState({ syncStatus: s }))
      .catch(() => undefined)
  }, [])

  const close = () => setSyncOpen(false)
  const webSyncOn = config?.web.syncEnabled ?? false
  const ready = !!sync?.enabled && !!sync?.configured && webSyncOn
  const privateCount = notes.filter((n) => n.encrypted).length
  const plainCount = notes.length - privateCount

  const run = async (dir: 'upload' | 'pull') => {
    setBusy(dir)
    try {
      await doSync(dir)
    } finally {
      setBusy(null)
    }
  }

  const refresh = async () => {
    setRefreshing(true)
    try {
      const [s] = await Promise.all([api.syncStatus(), refreshSystem()])
      useStore.setState({ syncStatus: s })
    } catch {
      /* 状态刷新失败不阻塞面板 */
    } finally {
      setRefreshing(false)
    }
  }

  const blocker = !sync
    ? '正在读取同步状态…'
    : !sync.enabled
      ? '云同步总开关是关闭的，服务端不会连接坚果云。'
      : !sync.configured
        ? '坚果云凭据还没填全，需要账号与应用授权密码。'
        : !webSyncOn
          ? '网页端默认不参与云同步，避免浏览器会话意外触发上传。'
          : null

  return (
    <div className="overlay" onClick={close}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-label="云同步">
        <div className="modal-head">
          <h2>
            <Icon name="cloudUp" size={17} style={{ color: 'var(--cyan)' }} />
            云同步
          </h2>
          <span className="badge violet">{PROVIDER_LABEL[sync?.provider ?? ''] ?? sync?.provider ?? '—'}</span>
          <span className="spacer" />
          <button className="icon-btn" onClick={close} aria-label="关闭">
            <Icon name="x" size={16} />
          </button>
        </div>

        <div className="modal-body">
          <div className="col" style={{ gap: 14 }}>
            {blocker ? (
              <div className={`callout ${sync && !sync.configured ? 'warn' : 'info'}`}>
                <Icon name={sync && !sync.configured ? 'alert' : 'info'} size={15} />
                <div>
                  <b>{blocker}</b>
                  <div>在设置 → 云同步 / 网页端里打开对应开关后，这里的两个按钮才会生效。</div>
                </div>
              </div>
            ) : (
              <div className="callout acid">
                <Icon name="check" size={15} />
                <div>
                  <b>同步通道就绪</b>
                  <div>
                    上次同步 {system?.lastSyncAt ? `${relTime(system.lastSyncAt)}（${absDate(system.lastSyncAt)}）` : '还没有记录'}。
                    上传只推送本地更新的笔记，拉取会用云端版本覆盖本地同名笔记。
                  </div>
                </div>
              </div>
            )}

            <div className="sync-grid">
              <div className="sync-card">
                <h4>
                  <Icon name="cloudUp" size={15} style={{ color: 'var(--cyan)' }} />
                  上传到云端
                </h4>
                <p>
                  把本地更新的 note.md、assets 与词表推到坚果云。
                  {sync?.autoUploadMinutes ? `桌面端另有每 ${sync.autoUploadMinutes} 分钟的定时上传。` : '定时上传当前是关闭的。'}
                </p>
                <button
                  className="btn primary block"
                  disabled={!ready || busy !== null}
                  onClick={() => void run('upload')}
                >
                  <Icon name={busy === 'upload' ? 'refresh' : 'upload'} size={14} className={busy === 'upload' ? 'spin' : ''} />
                  {busy === 'upload' ? '上传中…' : '立即上传'}
                </button>
              </div>

              <div className="sync-card">
                <h4>
                  <Icon name="cloudDown" size={15} style={{ color: 'var(--violet)' }} />
                  从云端拉取
                </h4>
                <p>
                  下载云端较新的笔记并覆盖本地。拉取完成后列表、目录树与当前笔记会自动刷新。
                </p>
                <button
                  className="btn block"
                  disabled={!ready || busy !== null}
                  onClick={() => void run('pull')}
                >
                  <Icon name={busy === 'pull' ? 'refresh' : 'download'} size={14} className={busy === 'pull' ? 'spin' : ''} />
                  {busy === 'pull' ? '拉取中…' : '立即拉取'}
                </button>
              </div>
            </div>

            <div className="callout warn">
              <Icon name="shield" size={15} />
              <div>
                <b>上云的是文件原文，不是浏览器里的明文</b>
                <div>
                  当前 {privateCount} 篇私密笔记以密文形态同步，坚果云拿不到正文；
                  另外 {plainCount} 篇是明文笔记，会原样上传。
                  如果明文笔记里写着口令、连接串或个人信息，请先在笔记抽屉里启用加密再同步。
                </div>
              </div>
            </div>

            <dl className="kv">
              <dt>云服务商</dt>
              <dd>{sync?.provider ?? '—'}</dd>
              <dt>云端路径</dt>
              <dd>{sync?.remotePath || '—'}</dd>
              <dt>凭据</dt>
              <dd>{sync?.configured ? '已配置' : '未配置'}</dd>
              <dt>定时上传</dt>
              <dd>{sync?.autoUploadMinutes ? `每 ${sync.autoUploadMinutes} 分钟` : '已禁用'}</dd>
              <dt>启动 / 退出</dt>
              <dd>
                {sync?.syncOnStart ? '启动时下载' : '启动不下载'} · {sync?.syncOnExit ? '退出时上传' : '退出不上传'}
              </dd>
              <dt>网页端参与</dt>
              <dd>{webSyncOn ? '已允许' : '已禁止'}</dd>
              <dt>上次同步</dt>
              <dd>{system?.lastSyncAt ? absDate(system.lastSyncAt) : '—'}</dd>
              <dt>服务运行</dt>
              <dd>{system ? duration(Date.now() - system.startedAt) : '—'}</dd>
            </dl>

            {sync?.message ? (
              <div className="callout">
                <Icon name="activity" size={15} />
                <div>{sync.message}</div>
              </div>
            ) : null}
          </div>
        </div>

        <div className="modal-foot">
          <button className="btn ghost sm" onClick={() => void refresh()} disabled={refreshing}>
            <Icon name="refresh" size={13} className={refreshing ? 'spin' : ''} />
            刷新状态
          </button>
          <button
            className="btn ghost sm"
            onClick={() => {
              close()
              setSettings(true)
            }}
          >
            <Icon name="settings" size={13} /> 同步设置
          </button>
          <span className="spacer" />
          <button className="btn" onClick={close}>
            关闭
          </button>
        </div>
      </div>
    </div>
  )
}