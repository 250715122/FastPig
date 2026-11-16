import type {
  AssetOutcome,
  AuthStatus,
  BlockDetail,
  BlockSummary,
  ConfigResponse,
  DeleteOutcome,
  GuessResult,
  NoteDetail,
  NoteMeta,
  SaveOutcome,
  SaveRequest,
  SearchResponse,
  Suggestion,
  SyncOutcome,
  SyncStatus,
  SystemInfo,
  Taxonomy,
  TreeSnapshot,
} from './types'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
    this.name = 'ApiError'
  }
}

/** 会话失效时由 store 挂上，避免 api 层反向依赖 UI。 */
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(path, {
      credentials: 'same-origin',
      ...init,
      headers: {
        ...(init?.body instanceof ArrayBuffer || init?.body instanceof Blob
          ? {}
          : { 'Content-Type': 'application/json' }),
        ...(init?.headers ?? {}),
      },
    })
  } catch {
    throw new ApiError(0, '无法连接服务端，请确认 FastPig 网页服务仍在运行')
  }

  if (res.status === 401) {
    onUnauthorized?.()
    throw new ApiError(401, '登录已失效，请重新登录')
  }

  const text = await res.text()
  if (!res.ok) {
    let message = text
    try {
      const parsed = JSON.parse(text) as { error?: string; message?: string }
      message = parsed.error ?? parsed.message ?? text
    } catch {
      /* 非 JSON 错误体，原样带出 */
    }
    throw new ApiError(res.status, message || `请求失败 (${res.status})`)
  }
  if (!text) return undefined as T
  return JSON.parse(text) as T
}

const enc = encodeURIComponent

export const api = {
  authStatus: () => request<AuthStatus>('/api/auth/status'),
  setup: (password: string) =>
    request<{ ok: boolean; message: string }>('/api/auth/setup', {
      method: 'POST',
      body: JSON.stringify({ password }),
    }),
  login: (password: string) =>
    request<{ ok: boolean; message: string }>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ password }),
    }),
  logout: () => request<{ ok: boolean }>('/api/auth/logout', { method: 'POST' }),
  resetPassword: (password: string) =>
    request<{ ok: boolean; message: string }>('/api/auth/password', {
      method: 'POST',
      body: JSON.stringify({ password }),
    }),

  notes: (deleted = false) => request<NoteMeta[]>(`/api/notes?deleted=${deleted}`),
  note: (key: string) => request<NoteDetail>(`/api/notes/${enc(key)}`),
  create: (req: SaveRequest) =>
    request<SaveOutcome>('/api/notes', { method: 'POST', body: JSON.stringify(req) }),
  save: (key: string, req: SaveRequest) =>
    request<SaveOutcome>(`/api/notes/${enc(key)}`, { method: 'PUT', body: JSON.stringify(req) }),
  remove: (key: string) => request<DeleteOutcome>(`/api/notes/${enc(key)}`, { method: 'DELETE' }),
  restore: (key: string) =>
    request<NoteDetail>(`/api/notes/${enc(key)}/restore`, { method: 'POST' }),
  blocks: (key: string) => request<BlockSummary[]>(`/api/notes/${enc(key)}/blocks`),
  guess: (key: string) => request<GuessResult>(`/api/notes/${enc(key)}/guess`, { method: 'POST' }),

  uploadAsset: async (key: string, file: File): Promise<AssetOutcome> => {
    const res = await fetch(`/api/notes/${enc(key)}/assets?name=${enc(file.name)}`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: await file.arrayBuffer(),
    })
    const text = await res.text()
    if (!res.ok) {
      let msg = text
      try {
        msg = (JSON.parse(text) as { error?: string }).error ?? text
      } catch {
        /* ignore */
      }
      throw new ApiError(res.status, msg || '图片上传失败')
    }
    return JSON.parse(text) as AssetOutcome
  },
  assetUrl: (key: string, name: string) => `/api/notes/${enc(key)}/assets/${enc(name)}`,

  block: (blockId: string) => request<BlockDetail>(`/api/blocks/${enc(blockId)}`),
  tree: () => request<TreeSnapshot>('/api/tree'),
  taxonomy: () => request<Taxonomy>('/api/taxonomy'),

  search: (q: string, tier: 'human' | 'ai', filter: Record<string, string> = {}) => {
    const params = new URLSearchParams({ q, tier, ...filter })
    return request<SearchResponse>(`/api/search?${params.toString()}`)
  },
  suggest: (prefix: string) => request<Suggestion[]>(`/api/suggest?prefix=${enc(prefix)}`),

  config: () => request<ConfigResponse>('/api/config'),
  updateConfig: (changes: Record<string, string>, master?: { salt: string; verifier: string }) =>
    request<{ ok: boolean; needsReindex: string[] }>('/api/config', {
      method: 'PUT',
      body: JSON.stringify({
        changes,
        masterSalt: master?.salt ?? null,
        masterVerifier: master?.verifier ?? null,
      }),
    }),

  syncStatus: () => request<SyncStatus>('/api/sync/status'),
  syncUpload: () => request<SyncOutcome>('/api/sync/upload', { method: 'POST' }),
  syncPull: () => request<SyncOutcome>('/api/sync/pull', { method: 'POST' }),

  indexStatus: () => request<import('./types').IndexStatus>('/api/index/status'),
  rebuildIndex: () => request<{ ok: boolean }>('/api/index/rebuild', { method: 'POST' }),
  system: () => request<SystemInfo>('/api/system'),
}