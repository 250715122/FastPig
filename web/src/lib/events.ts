import type { IndexStatus, SyncStatus } from './types'

export interface ServerEvent {
  type: string
  payload: unknown
}

type Listener = (e: ServerEvent) => void

/**
 * SSE 订阅。断线自动重连（指数退避），重连后由调用方重新拉取快照，
 * 避免漏掉断开期间发生的变更。
 */
export class EventStream {
  private es: EventSource | null = null
  private listeners = new Set<Listener>()
  private retry = 0
  private timer: number | null = null
  private stopped = false
  private lastHello: { index?: IndexStatus; sync?: SyncStatus; time?: number } | null = null

  on(fn: Listener): () => void {
    this.listeners.add(fn)
    return () => this.listeners.delete(fn)
  }

  get hello() {
    return this.lastHello
  }

  get connected(): boolean {
    return this.es?.readyState === EventSource.OPEN
  }

  start() {
    this.stopped = false
    this.open()
  }

  private open() {
    if (this.stopped) return
    this.es?.close()
    const es = new EventSource('/api/events')
    this.es = es

    es.onopen = () => {
      this.retry = 0
      this.emit({ type: 'open', payload: null })
    }
    es.onmessage = (ev) => this.dispatch('message', ev.data)
    es.onerror = () => {
      es.close()
      this.es = null
      this.emit({ type: 'closed', payload: null })
      if (this.stopped) return
      this.retry = Math.min(this.retry + 1, 6)
      const delay = Math.min(30_000, 800 * 2 ** (this.retry - 1))
      this.timer = window.setTimeout(() => this.open(), delay)
    }

    for (const name of ['hello', 'index', 'notes', 'sync', 'config', 'blocks']) {
      es.addEventListener(name, (ev) => this.dispatch(name, (ev as MessageEvent).data))
    }
  }

  private dispatch(type: string, raw: string) {
    let payload: unknown = raw
    try {
      payload = JSON.parse(raw)
    } catch {
      /* 心跳等非 JSON 负载 */
    }
    if (type === 'hello') {
      this.lastHello = payload as typeof this.lastHello
    }
    this.emit({ type, payload })
  }

  private emit(e: ServerEvent) {
    for (const fn of this.listeners) {
      try {
        fn(e)
      } catch (err) {
        console.error('[events] listener failed', err)
      }
    }
  }

  stop() {
    this.stopped = true
    if (this.timer !== null) window.clearTimeout(this.timer)
    this.timer = null
    this.es?.close()
    this.es = null
  }
}

export const events = new EventStream()