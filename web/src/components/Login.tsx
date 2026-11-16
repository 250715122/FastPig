import { useEffect, useState } from 'react'
import { useStore } from '../lib/store'
import { Icon, Logo } from './Icons'

function strengthOf(pw: string): number {
  if (!pw) return 0
  let score = 0
  if (pw.length >= 6) score++
  if (pw.length >= 10) score++
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) score++
  if (/\d/.test(pw) && /[^\w\s]/.test(pw)) score++
  return Math.min(4, score)
}

const STRENGTH_TEXT = ['太短', '偏弱', '一般', '较强', '很强']

export default function Login() {
  const auth = useStore((s) => s.auth)
  const boot = useStore((s) => s.boot)
  const login = useStore((s) => s.login)
  const setup = useStore((s) => s.setup)
  const toast = useStore((s) => s.toast)

  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [show, setShow] = useState(false)

  const setupMode = auth?.setupRequired ?? false

  useEffect(() => {
    if (!auth) void boot()
  }, [auth, boot])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    if (setupMode) {
      if (password.length < 6) {
        setError('登录口令至少 6 位')
        return
      }
      if (password !== confirm) {
        setError('两次输入的口令不一致')
        return
      }
    } else if (!password) {
      setError('请输入登录口令')
      return
    }
    setBusy(true)
    try {
      if (setupMode) await setup(password)
      else await login(password)
      setPassword('')
      setConfirm('')
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err)
      setError(msg)
      toast({ kind: 'error', text: setupMode ? '设置失败' : '登录失败', detail: msg })
    } finally {
      setBusy(false)
    }
  }

  const score = strengthOf(password)

  return (
    <div className="login-wrap">
      <div className="login-card">
        <div className="login-logo">
          <Logo size={46} />
          <div>
            <h1>
              Fast<em>Pig</em>
            </h1>
            <p>Knowledge Workbench</p>
          </div>
        </div>

        <h2>{setupMode ? '首次使用 · 设置登录口令' : '解锁知识工作台'}</h2>
        <p className="lede">
          {setupMode
            ? '这台服务还没有登录口令。设置后局域网内的其他设备需要凭口令访问；口令只以 PBKDF2 校验值形式存盘，不存明文。'
            : '输入登录口令以访问这台机器上的知识库。私密笔记另有一层端到端加密，服务端看不到明文。'}
        </p>

        <form className="login-form" onSubmit={submit}>
          <div className="field">
            <label htmlFor="fp-login-pw">
              <Icon name="key" size={13} /> 登录口令
            </label>
            <div style={{ position: 'relative' }}>
              <input
                id="fp-login-pw"
                className={`input mono${error ? ' invalid' : ''}`}
                type={show ? 'text' : 'password'}
                value={password}
                autoFocus
                autoComplete={setupMode ? 'new-password' : 'current-password'}
                placeholder={setupMode ? '至少 6 位' : '••••••••'}
                onChange={(e) => setPassword(e.target.value)}
                style={{ paddingRight: 34 }}
              />
              <button
                type="button"
                className="icon-btn sm"
                onClick={() => setShow((v) => !v)}
                title={show ? '隐藏口令' : '显示口令'}
                aria-label={show ? '隐藏口令' : '显示口令'}
                style={{ position: 'absolute', right: 3, top: 3 }}
              >
                <Icon name={show ? 'eye' : 'lock'} size={14} />
              </button>
            </div>
            {setupMode ? (
              <>
                <div className="strength" aria-hidden="true">
                  {[1, 2, 3, 4].map((i) => (
                    <i key={i} className={score >= i ? `on-${score}` : ''} />
                  ))}
                </div>
                <div className="field-hint">强度：{STRENGTH_TEXT[score]}</div>
              </>
            ) : null}
          </div>

          {setupMode ? (
            <div className="field">
              <label htmlFor="fp-login-pw2">
                <Icon name="shield" size={13} /> 确认口令
              </label>
              <input
                id="fp-login-pw2"
                className="input mono"
                type={show ? 'text' : 'password'}
                value={confirm}
                autoComplete="new-password"
                placeholder="再输入一次"
                onChange={(e) => setConfirm(e.target.value)}
              />
            </div>
          ) : null}

          {error ? (
            <div className="callout danger" role="alert">
              <Icon name="alert" size={15} />
              <div>{error}</div>
            </div>
          ) : null}

          <button className="btn primary lg block" type="submit" disabled={busy}>
            {busy ? <Icon name="refresh" size={15} className="spin" /> : <Icon name={setupMode ? 'shield' : 'unlock'} size={15} />}
            {busy ? '处理中…' : setupMode ? '设置并进入' : '进入工作台'}
          </button>
        </form>

        <div className="login-foot">
          <div className="row">
            <Icon name="shield" size={13} />
            <span>私密笔记在浏览器内用 AES-GCM 解密，口令与明文不出本机。</span>
          </div>
          <div className="row">
            <Icon name="database" size={13} />
            <span>笔记以 Markdown 单文件存盘，与桌面端共用同一份数据。</span>
          </div>
          {setupMode && !auth?.loopback ? (
            <div className="callout warn">
              <Icon name="alert" size={15} />
              <div>首次设置口令只允许从本机（localhost）发起。请在这台机器上打开页面完成设置。</div>
            </div>
          ) : null}
          {!setupMode ? (
            <div className="row">
              <Icon name="info" size={13} />
              <span>
                忘记口令？在本机运行 <code className="mono">WebMain --web-password=新口令</code> 重置。
              </span>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  )
}