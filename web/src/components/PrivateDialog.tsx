import { useEffect, useMemo, useState } from 'react'
import { useStore, sessionMasterPassword } from '../lib/store'
import { Icon } from './Icons'
import { WrongPasswordError } from '../lib/crypto'

const TITLES: Record<string, { title: string; sub: string; icon: string }> = {
  unlock: { title: '解锁私密笔记', sub: '解密只在这台浏览器里发生', icon: 'unlock' },
  enable: { title: '启用端到端加密', sub: 'AES-256-GCM · 服务端只存密文', icon: 'shield' },
  change: { title: '修改笔记密码', sub: '数据密钥不变，只重新包裹', icon: 'key' },
  disable: { title: '取消加密', sub: '正文将改为明文落盘', icon: 'alert' },
}

function strengthOf(p: string): number {
  if (!p) return 0
  let n = 0
  if (p.length >= 8) n++
  if (p.length >= 12) n++
  if (/[a-z]/.test(p) && /[A-Z]/.test(p)) n++
  if (/\d/.test(p) && /[^\w\s]/.test(p)) n++
  return Math.min(4, n)
}

export default function PrivateDialog() {
  const target = useStore((s) => s.privateTarget)
  const detail = useStore((s) => s.detail)
  const config = useStore((s) => s.config)
  const setPrivateTarget = useStore((s) => s.setPrivateTarget)
  const setSettings = useStore((s) => s.setSettings)
  const unlock = useStore((s) => s.unlock)
  const enableEncryption = useStore((s) => s.enableEncryption)
  const changeNotePassword = useStore((s) => s.changeNotePassword)
  const disableEncryption = useStore((s) => s.disableEncryption)
  const setMasterPassword = useStore((s) => s.setMasterPassword)
  const toast = useStore((s) => s.toast)

  const [pwd, setPwd] = useState('')
  const [pwd2, setPwd2] = useState('')
  const [master, setMaster] = useState('')
  const [master2, setMaster2] = useState('')
  const [useMaster, setUseMaster] = useState(false)
  const [reuseSession, setReuseSession] = useState(true)
  const [show, setShow] = useState(false)
  const [ack, setAck] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const mode = target?.mode ?? 'unlock'
  const key = target?.key ?? ''
  const masterSet = config?.masterPasswordSet ?? false
  const sessionMaster = sessionMasterPassword()
  const hasRecovery = !!detail?.crypto?.masterWrappedDek

  useEffect(() => {
    setPwd('')
    setPwd2('')
    setMaster('')
    setMaster2('')
    setError(null)
    setAck(false)
    setUseMaster(false)
    setShow(false)
  }, [target?.key, target?.mode])

  const head = TITLES[mode]
  const strength = useMemo(() => strengthOf(pwd), [pwd])

  if (!target) return null

  const close = () => setPrivateTarget(null)

  const fail = (e: unknown) => {
    if (e instanceof WrongPasswordError) setError('密码错误：解不开数据密钥。')
    else setError(e instanceof Error ? e.message : String(e))
  }

  const submit = async () => {
    setError(null)
    setBusy(true)
    try {
      if (mode === 'unlock') {
        if (!pwd) throw new Error('请输入密码')
        await unlock(key, pwd, useMaster)
        toast({ kind: 'success', text: '已解锁', detail: '密钥只留在内存，锁定或退出即清除。' })
        close()
      } else if (mode === 'enable') {
        if (pwd.length < 6) throw new Error('笔记密码至少 6 位')
        if (pwd !== pwd2) throw new Error('两次输入的笔记密码不一致')
        let m = master
        if (masterSet) {
          m = reuseSession && sessionMaster ? sessionMaster : master
          if (!m) throw new Error('请输入主密码')
        } else {
          if (master.length < 8) throw new Error('主密码至少 8 位，它是忘记密码时唯一的恢复通道')
          if (master !== master2) throw new Error('两次输入的主密码不一致')
          await setMasterPassword(master)
          m = master
        }
        await enableEncryption(key, pwd, m)
        close()
      } else if (mode === 'change') {
        if (pwd.length < 6) throw new Error('新密码至少 6 位')
        if (pwd !== pwd2) throw new Error('两次输入的新密码不一致')
        await changeNotePassword(key, pwd)
        close()
      } else {
        if (!ack) throw new Error('请先勾选确认')
        await disableEncryption(key)
        close()
      }
    } catch (e) {
      fail(e)
    } finally {
      setBusy(false)
    }
  }

  const pwdField = (
    label: string,
    value: string,
    set: (v: string) => void,
    id: string,
    autoFocus = false,
  ) => (
    <div className="field">
      <label className="field-label" htmlFor={id}>
        {label}
      </label>
      <div className="row" style={{ gap: 6 }}>
        <input
          id={id}
          className="input mono"
          type={show ? 'text' : 'password'}
          value={value}
          autoFocus={autoFocus}
          autoComplete="off"
          onChange={(e) => set(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') void submit()
          }}
        />
        <button
          className="icon-btn"
          type="button"
          title={show ? '隐藏密码' : '显示密码'}
          aria-label={show ? '隐藏密码' : '显示密码'}
          onClick={() => setShow((v) => !v)}
        >
          <Icon name={show ? 'eye' : 'lock'} size={14} />
        </button>
      </div>
    </div>
  )

  return (
    <div className="overlay" onClick={close}>
      <div className="modal narrow" onClick={(e) => e.stopPropagation()} role="dialog" aria-label={head.title}>
        <div className="modal-head">
          <h2>
            <Icon name={head.icon} size={17} style={{ color: mode === 'disable' ? 'var(--danger)' : 'var(--cyan)' }} />
            {head.title}
          </h2>
          <span className="spacer" />
          <button className="icon-btn" onClick={close} aria-label="关闭">
            <Icon name="x" size={16} />
          </button>
        </div>

        <div className="modal-body">
          <div className="col" style={{ gap: 13 }}>
            <div className="row" style={{ gap: 8 }}>
              <span className="badge cyan mono">{key}</span>
              <span className="faint" style={{ fontSize: 11.5 }}>{head.sub}</span>
            </div>

            {mode === 'unlock' ? (
              <>
                {hasRecovery && masterSet ? (
                  <div className="seg">
                    <button className={!useMaster ? 'on' : ''} onClick={() => setUseMaster(false)}>
                      笔记密码
                    </button>
                    <button className={useMaster ? 'on' : ''} onClick={() => setUseMaster(true)}>
                      主密码恢复
                    </button>
                  </div>
                ) : null}
                {pwdField(useMaster ? '主密码' : '笔记密码', pwd, setPwd, 'pv-pwd', true)}
                <div className="callout info">
                  <Icon name="shield" size={15} />
                  <div>
                    PBKDF2-HMAC-SHA256 · 210000 次迭代派生密钥，AES-GCM 解密正文。
                    密码不会发往服务端，服务端也没有能力解开这篇笔记。
                  </div>
                </div>
                {!hasRecovery ? (
                  <div className="callout warn">
                    <Icon name="alert" size={15} />
                    <div>这篇笔记没有主密码恢复信息，忘记密码将无法找回正文。</div>
                  </div>
                ) : null}
              </>
            ) : null}

            {mode === 'enable' ? (
              <>
                {pwdField('笔记密码（至少 6 位）', pwd, setPwd, 'pv-pwd', true)}
                <div className="strength" aria-hidden="true">
                  {[1, 2, 3, 4].map((i) => (
                    <i key={i} className={strength >= i ? `on-${strength}` : ''} />
                  ))}
                </div>
                {pwdField('确认笔记密码', pwd2, setPwd2, 'pv-pwd2')}
                <div className="divider" />
                {masterSet ? (
                  reuseSession && sessionMaster ? (
                    <div className="callout acid">
                      <Icon name="check" size={15} />
                      <div>
                        <b>使用本次会话的主密码</b>
                        <div>数据密钥会同时被笔记密码和主密码包裹，忘记密码时可恢复。</div>
                      </div>
                    </div>
                  ) : (
                    pwdField('主密码', master, setMaster, 'pv-master')
                  )
                ) : (
                  <>
                    <div className="callout warn">
                      <Icon name="key" size={15} />
                      <div>
                        <b>先设置主密码</b>
                        <div>主密码是忘记密码时唯一的恢复通道，只存在这台浏览器与服务端的校验值里。</div>
                      </div>
                    </div>
                    {pwdField('主密码（至少 8 位）', master, setMaster, 'pv-master')}
                    {pwdField('确认主密码', master2, setMaster2, 'pv-master2')}
                  </>
                )}
                {masterSet ? (
                  <label className="switch-row">
                    <span className="txt" style={{ flex: 1 }}>
                      <b style={{ display: 'block', fontSize: 12.5 }}>复用本次会话的主密码</b>
                      <span style={{ display: 'block', fontSize: 11, color: 'var(--faint)' }}>
                        {sessionMaster ? '已经输入过，不必再输一遍' : '本次会话还没输入过主密码'}
                      </span>
                    </span>
                    <span className={`switch${reuseSession && sessionMaster ? ' on' : ''}`}>
                      <input
                        type="checkbox"
                        checked={reuseSession && !!sessionMaster}
                        disabled={!sessionMaster}
                        onChange={(e) => setReuseSession(e.target.checked)}
                      />
                    </span>
                  </label>
                ) : null}
              </>
            ) : null}

            {mode === 'change' ? (
              <>
                {pwdField('新笔记密码（至少 6 位）', pwd, setPwd, 'pv-pwd', true)}
                <div className="strength" aria-hidden="true">
                  {[1, 2, 3, 4].map((i) => (
                    <i key={i} className={strength >= i ? `on-${strength}` : ''} />
                  ))}
                </div>
                {pwdField('确认新密码', pwd2, setPwd2, 'pv-pwd2')}
                <div className="callout info">
                  <Icon name="info" size={15} />
                  <div>正文会用同一把数据密钥重新加密，主密码恢复通道保持不变。</div>
                </div>
              </>
            ) : null}

            {mode === 'disable' ? (
              <>
                <div className="callout danger">
                  <Icon name="alert" size={15} />
                  <div>
                    <b>取消加密后无法自动恢复</b>
                    <div>
                      正文将以明文写入 note.md，并按当前同步设置原样上传到坚果云。
                      如果这篇笔记里有口令、连接串或个人信息，请先确认这样做是安全的。
                    </div>
                  </div>
                </div>
                <label className="switch-row">
                  <span className="txt" style={{ flex: 1 }}>
                    <b style={{ display: 'block', fontSize: 12.5 }}>我知道正文会改为明文存储与同步</b>
                  </span>
                  <span className={`switch${ack ? ' on' : ''}`}>
                    <input type="checkbox" checked={ack} onChange={(e) => setAck(e.target.checked)} />
                  </span>
                </label>
              </>
            ) : null}

            {error ? (
              <div className="callout danger">
                <Icon name="alert" size={15} />
                <div>{error}</div>
              </div>
            ) : null}
          </div>
        </div>

        <div className="modal-foot">
          {mode === 'enable' && !masterSet ? (
            <button className="btn ghost sm" onClick={() => { close(); setSettings(true) }}>
              <Icon name="settings" size={13} /> 去设置里管理主密码
            </button>
          ) : null}
          <span className="spacer" />
          <button className="btn ghost" onClick={close} disabled={busy}>
            取消
          </button>
          <button
            className={`btn ${mode === 'disable' ? 'danger' : 'primary'}`}
            onClick={() => void submit()}
            disabled={busy || (mode === 'disable' && !ack)}
          >
            <Icon name={busy ? 'refresh' : head.icon} size={14} className={busy ? 'spin' : ''} />
            {busy ? '派生密钥中…' : mode === 'unlock' ? '解锁' : mode === 'enable' ? '启用加密' : mode === 'change' ? '更新密码' : '确认取消加密'}
          </button>
        </div>
      </div>
    </div>
  )
}