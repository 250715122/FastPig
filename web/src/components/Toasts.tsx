import { useStore } from '../lib/store'
import { Icon } from './Icons'

const ICONS: Record<string, string> = {
  info: 'info',
  success: 'check',
  warn: 'alert',
  error: 'alert',
}

export default function Toasts() {
  const toasts = useStore((s) => s.toasts)
  const dismiss = useStore((s) => s.dismissToast)

  if (toasts.length === 0) return null

  return (
    <div className="toasts" role="status" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className={`toast ${t.kind}`}>
          <span className="toast-icon">
            <Icon name={ICONS[t.kind] ?? 'info'} size={15} />
          </span>
          <div className="toast-main">
            <div className="toast-text">{t.text}</div>
            {t.detail ? <div className="toast-detail">{t.detail}</div> : null}
          </div>
          {t.action ? (
            <button
              className="toast-action"
              onClick={() => {
                t.action?.run()
                dismiss(t.id)
              }}
            >
              {t.action.label}
            </button>
          ) : null}
          <button className="toast-x" onClick={() => dismiss(t.id)} aria-label="关闭提示">
            <Icon name="x" size={12} />
          </button>
          {t.ttl > 0 ? (
            <span
              className="toast-life"
              style={{
                animationDuration: `${t.ttl}ms`,
                color:
                  t.kind === 'success'
                    ? 'var(--acid)'
                    : t.kind === 'warn'
                      ? 'var(--amber)'
                      : t.kind === 'error'
                        ? 'var(--danger)'
                        : 'var(--cyan)',
              }}
            />
          ) : null}
        </div>
      ))}
    </div>
  )
}