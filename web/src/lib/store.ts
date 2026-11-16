import { create } from 'zustand'
import { md5 } from 'js-md5'
import { api, ApiError, setUnauthorizedHandler } from './api'
import { events } from './events'
import { splitHeader } from './format'
import { localBlockDetail, parseLocalBlocks } from './blocks'
import {
  decryptBody,
  deriveVerifier,
  encryptBody,
  extractAssetRefs,
  newDek,
  newMasterSalt,
  unwrapDek,
  verifierMatches,
  wrapDek,
  WrongPasswordError,
} from './crypto'
import type {
  AuthStatus,
  BlockDetail,
  BlockSummary,
  ConfigResponse,
  IndexStatus,
  NoteDetail,
  NoteMeta,
  SaveRequest,
  SearchResponse,
  Suggestion,
  SyncStatus,
  SystemInfo,
  Taxonomy,
  TreeSnapshot,
} from './types'

export type Theme = 'ink' | 'paper'
export type SideTab = 'toc' | 'tree' | 'search'
export type ViewMode = 'split' | 'editor' | 'preview'
export type SortKey = 'updated' | 'created' | 'key' | 'blocks' | 'domain'

export interface Toast {
  id: number
  kind: 'info' | 'success' | 'warn' | 'error'
  text: string
  detail?: string
  action?: { label: string; run: () => void }
  ttl: number
}

export interface SavedView {
  id: string
  name: string
  text: string
  domain: string
  topic: string
  sort: SortKey
  encryptedOnly: boolean
}

export interface ListFilter {
  text: string
  domain: string
  topic: string
  sort: SortKey
  encryptedOnly: boolean
  showDeleted: boolean
}

export interface JumpTarget {
  line: number
  ch: number
  nonce: number
  selectTo?: number
}

export interface PrivateTarget {
  mode: 'unlock' | 'enable' | 'change' | 'disable'
  key: string
}

/** DEK 与主密码只活在内存里，绝不写 localStorage / sessionStorage。 */
const dekVault = new Map<string, Uint8Array>()
let masterPassword: string | null = null

export function sessionMasterPassword(): string | null {
  return masterPassword
}

export function hasDek(key: string): boolean {
  return dekVault.has(key)
}

const LS = {
  theme: 'fp.theme',
  views: 'fp.views',
  sideTab: 'fp.sideTab',
  viewMode: 'fp.viewMode',
  listW: 'fp.listW',
  sideW: 'fp.sideW',
  lastKey: 'fp.lastKey',
}

function readLS<T>(k: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(k)
    return raw === null ? fallback : (JSON.parse(raw) as T)
  } catch {
    return fallback
  }
}

function writeLS(k: string, v: unknown) {
  try {
    localStorage.setItem(k, JSON.stringify(v))
  } catch {
    /* 隐私模式下写入失败不影响使用 */
  }
}

export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
  writeLS(LS.theme, theme)
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', theme === 'paper' ? '#f2eee3' : '#070a12')
}

let toastSeq = 1
let jumpSeq = 1
let noteReloadTimer: number | null = null

/** 登录成功后拉齐首屏快照并接上事件流。 */
async function loadSnapshots() {
  try {
    const [system, notes, taxonomy] = await Promise.all([api.system(), api.notes(false), api.taxonomy()])
    useStore.setState({
      system,
      notes,
      taxonomy,
      indexStatus: system.index,
      syncStatus: system.sync,
      booting: false,
      bootError: null,
      connection: 'online',
    })
    events.start()
    void useStore.getState().refreshTree()
    void useStore.getState().refreshConfig()
    const last = readLS<string | null>(LS.lastKey, null)
    if (last && notes.some((n) => n.key === last)) {
      void useStore.getState().openNote(last)
    }
  } catch (e) {
    useStore.setState({ booting: false, bootError: errText(e) })
  }
}

interface State {
  // 会话
  auth: AuthStatus | null
  authed: boolean
  booting: boolean
  bootError: string | null
  connection: 'online' | 'offline' | 'reconnecting'

  // 数据
  notes: NoteMeta[]
  deleted: NoteMeta[]
  tree: TreeSnapshot | null
  taxonomy: Taxonomy | null
  config: ConfigResponse | null
  system: SystemInfo | null
  indexStatus: IndexStatus | null
  syncStatus: SyncStatus | null

  // 当前笔记
  currentKey: string | null
  detail: NoteDetail | null
  editorText: string
  dirty: boolean
  saving: boolean
  loadingNote: boolean
  composing: boolean
  lastSavedAt: number
  saveError: string | null
  unlockedKeys: string[]
  blocks: BlockSummary[]
  jump: JumpTarget | null
  caretLine: number

  // 检索
  query: string
  tier: 'human' | 'ai'
  searching: boolean
  searchResult: SearchResponse | null
  searchError: string | null
  suggestions: Suggestion[]
  activeHit: string | null
  searchFilter: { domain: string; topic: string; noteType: string; visibility: string; layer: string }

  // 界面
  theme: Theme
  sideTab: SideTab
  viewMode: ViewMode
  showList: boolean
  showSide: boolean
  listW: number
  sideW: number
  drawerOpen: boolean
  settingsOpen: boolean
  paletteOpen: boolean
  syncOpen: boolean
  newNoteOpen: boolean
  privateTarget: PrivateTarget | null
  blockDetail: BlockDetail | null
  filter: ListFilter
  views: SavedView[]
  activeViewId: string | null
  selected: string[]
  selectMode: boolean
  toasts: Toast[]
  mobile: boolean

  // 动作
  boot: () => Promise<void>
  login: (password: string) => Promise<void>
  setup: (password: string) => Promise<void>
  logout: () => Promise<void>
  refreshNotes: () => Promise<void>
  refreshTree: () => Promise<void>
  refreshSystem: () => Promise<void>
  refreshConfig: () => Promise<void>
  openNote: (key: string, line?: number) => Promise<void>
  closeNote: () => void
  startNew: () => void
  setEditorText: (text: string) => void
  setCaretLine: (line: number) => void
  save: (manual?: boolean) => Promise<boolean>
  createNote: (key: string, desc: string, body: string) => Promise<boolean>
  removeNote: (key: string) => Promise<void>
  removeMany: (keys: string[]) => Promise<void>
  restoreNote: (key: string) => Promise<void>
  loadDeleted: () => Promise<void>
  loadBlocks: (key: string) => Promise<void>
  openBlock: (blockId: string) => Promise<void>
  requestJump: (line: number, ch?: number, selectTo?: number) => void

  unlock: (key: string, password: string, useMaster: boolean) => Promise<void>
  enableEncryption: (key: string, notePassword: string, master: string) => Promise<void>
  changeNotePassword: (key: string, newPassword: string) => Promise<void>
  disableEncryption: (key: string) => Promise<void>
  lockNote: (key: string) => void
  setMasterPassword: (password: string) => Promise<void>
  verifyMaster: (password: string) => Promise<boolean>

  runSearch: (q?: string) => Promise<void>
  setQuery: (q: string) => void
  setTier: (t: 'human' | 'ai') => void
  setSearchFilter: (patch: Partial<State['searchFilter']>) => void
  loadSuggestions: (prefix: string) => Promise<void>
  clearSearch: () => void

  setTheme: (t: Theme) => void
  setSideTab: (t: SideTab) => void
  setViewMode: (m: ViewMode) => void
  toggleList: () => void
  toggleSide: () => void
  setListW: (w: number) => void
  setSideW: (w: number) => void
  setDrawer: (open: boolean) => void
  setSettings: (open: boolean) => void
  setPalette: (open: boolean) => void
  setSyncOpen: (open: boolean) => void
  setNewNoteOpen: (open: boolean) => void
  setPrivateTarget: (t: PrivateTarget | null) => void
  setBlockDetail: (b: BlockDetail | null) => void
  setFilter: (patch: Partial<ListFilter>) => void
  saveView: (name: string) => void
  applyView: (id: string) => void
  deleteView: (id: string) => void
  toggleSelect: (key: string) => void
  selectAll: (keys: string[]) => void
  clearSelection: () => void
  setSelectMode: (on: boolean) => void
  setMobile: (on: boolean) => void

  toast: (t: Omit<Toast, 'id' | 'ttl'> & { ttl?: number }) => number
  dismissToast: (id: number) => void
  updateMeta: (patch: Partial<NoteMeta>) => Promise<void>
  guess: () => Promise<void>
  uploadAsset: (file: File) => Promise<string | null>
  doSync: (dir: 'upload' | 'pull') => Promise<void>
  rebuildIndex: () => Promise<void>
  updateConfig: (changes: Record<string, string>) => Promise<string[]>
}

const initialFilter: ListFilter = {
  text: '',
  domain: '',
  topic: '',
  sort: 'updated',
  encryptedOnly: false,
  showDeleted: false,
}

export const useStore = create<State>((set, get) => ({
  auth: null,
  authed: false,
  booting: true,
  bootError: null,
  connection: 'reconnecting',

  notes: [],
  deleted: [],
  tree: null,
  taxonomy: null,
  config: null,
  system: null,
  indexStatus: null,
  syncStatus: null,

  currentKey: null,
  detail: null,
  editorText: '',
  dirty: false,
  saving: false,
  loadingNote: false,
  composing: false,
  lastSavedAt: 0,
  saveError: null,
  unlockedKeys: [],
  blocks: [],
  jump: null,
  caretLine: 0,

  query: '',
  tier: 'human',
  searching: false,
  searchResult: null,
  searchError: null,
  suggestions: [],
  activeHit: null,
  searchFilter: { domain: '', topic: '', noteType: '', visibility: '', layer: '' },

  theme: readLS<Theme>(LS.theme, 'ink'),
  sideTab: readLS<SideTab>(LS.sideTab, 'toc'),
  viewMode: readLS<ViewMode>(LS.viewMode, 'split'),
  showList: true,
  showSide: true,
  listW: readLS<number>(LS.listW, 274),
  sideW: readLS<number>(LS.sideW, 316),
  drawerOpen: false,
  settingsOpen: false,
  paletteOpen: false,
  syncOpen: false,
  newNoteOpen: false,
  privateTarget: null,
  blockDetail: null,
  filter: initialFilter,
  views: readLS<SavedView[]>(LS.views, []),
  activeViewId: null,
  selected: [],
  selectMode: false,
  toasts: [],
  mobile: false,

  // ==================== 启动与会话 ====================

  async boot() {
    set({ booting: true, bootError: null })
    try {
      const auth = await api.authStatus()
      set({ auth, authed: auth.authenticated })
      if (!auth.authenticated) {
        set({ booting: false })
        return
      }
      await loadSnapshots()
    } catch (e) {
      set({ booting: false, bootError: e instanceof Error ? e.message : String(e) })
    }
  },

  async login(password) {
    await api.login(password)
    set({ authed: true, booting: true })
    await loadSnapshots()
  },

  async setup(password) {
    await api.setup(password)
    set({ authed: true, booting: true })
    await loadSnapshots()
  },

  async logout() {
    try {
      await api.logout()
    } finally {
      events.stop()
      dekVault.clear()
      masterPassword = null
      set({
        authed: false,
        booting: false,
        notes: [],
        deleted: [],
        currentKey: null,
        detail: null,
        editorText: '',
        dirty: false,
        unlockedKeys: [],
        config: null,
        system: null,
        tree: null,
        searchResult: null,
        auth: { authenticated: false, setupRequired: false, loopback: true, masterPasswordSet: false },
      })
      void api.authStatus().then((a) => set({ auth: a }))
    }
  },

  async refreshNotes() {
    const notes = await api.notes(false)
    set({ notes })
  },

  async refreshTree() {
    set({ tree: await api.tree() })
  },

  async refreshSystem() {
    const system = await api.system()
    set({ system, indexStatus: system.index, syncStatus: system.sync })
  },

  async refreshConfig() {
    const config = await api.config()
    set({ config, indexStatus: config.index })
  },

  // ==================== 笔记 ====================

  async openNote(key, line) {
    const s = get()
    if (s.dirty && s.currentKey && s.currentKey !== key) {
      const ok = await s.save(false)
      if (!ok) return
    }
    set({ loadingNote: true, saveError: null, composing: false })
    try {
      const detail = await api.note(key)
      const unlocked = dekVault.has(key)
      let body = detail.bodyMd ?? ''
      if (detail.meta.encrypted) {
        body = unlocked ? (await decryptWithVault(key, detail)) : ''
      }
      const text = `${detail.meta.key} ${detail.meta.desc}\n${body}`
      set({
        currentKey: key,
        detail,
        editorText: text,
        dirty: false,
        loadingNote: false,
        blocks: [],
        unlockedKeys: [...dekVault.keys()],
      })
      writeLS(LS.lastKey, key)
      if (line !== undefined) get().requestJump(line + 1)
      void get().loadBlocks(key)
      if (get().mobile) set({ showList: false })
    } catch (e) {
      set({ loadingNote: false })
      get().toast({ kind: 'error', text: '打开笔记失败', detail: errText(e) })
    }
  },

  closeNote() {
    const key = get().currentKey
    if (key) dekVault.delete(key)
    set({
      currentKey: null,
      detail: null,
      editorText: '',
      dirty: false,
      composing: false,
      blocks: [],
      unlockedKeys: [...dekVault.keys()],
      saveError: null,
    })
  },

  startNew() {
    const key = get().currentKey
    if (key && get().dirty) void get().save(false)
    if (key) dekVault.delete(key)
    set({
      currentKey: null,
      detail: null,
      editorText: '',
      dirty: false,
      composing: true,
      blocks: [],
      saveError: null,
      drawerOpen: false,
    })
  },

  setEditorText(text) {
    const s = get()
    if (text === s.editorText) return
    set({ editorText: text, dirty: true, saveError: null })
  },

  setCaretLine(line) {
    if (get().caretLine !== line) set({ caretLine: line })
  },

  async save(manual = true) {
    const s = get()
    if (s.saving) return false
    const text = s.editorText
    const nl = text.indexOf('\n')
    const first = nl >= 0 ? text.slice(0, nl) : text
    const body = nl >= 0 ? text.slice(nl + 1) : ''
    const m = /^(\S+)\s*(.*)$/.exec(first.trim())
    const key = m?.[1] ?? ''
    const desc = (m?.[2] ?? '').trim()

    if (!key) {
      if (manual) get().toast({ kind: 'warn', text: '首行需包含快捷命令', detail: '格式：key 空格 描述' })
      return false
    }

    if (!s.currentKey) {
      return get().createNote(key, desc, body)
    }

    if (key !== s.currentKey) {
      get().toast({
        kind: 'warn',
        text: '当前版本不支持修改 key',
        detail: '请新建笔记后删除旧笔记。',
      })
      return false
    }

    const meta = s.detail?.meta
    if (!meta) return false

    if (meta.encrypted && !dekVault.has(meta.key)) {
      get().toast({ kind: 'warn', text: '笔记处于锁定态', detail: '先解锁再保存，否则无法加密正文。' })
      set({ privateTarget: { mode: 'unlock', key: meta.key } })
      return false
    }

    set({ saving: true, saveError: null })
    try {
      const req: SaveRequest = {
        desc,
        title: desc || meta.title,
        expectedVersion: meta.version,
        domain: meta.domain,
        topic: meta.topic,
        noteType: meta.noteType,
        layer: meta.layer,
        visibility: meta.visibility,
        aliases: meta.aliases ?? [],
        tags: meta.tags ?? [],
      }

      if (meta.encrypted) {
        const dek = dekVault.get(meta.key)!
        const sealed = await encryptBody(body, dek)
        req.encrypted = true
        req.bodyMd = sealed.cipher
        req.cipherIv = sealed.iv
        req.assets = extractAssetRefs(body)
        req.plaintextHash = md5(body)
        const c = s.detail?.crypto
        req.pwdSalt = c?.pwdSalt ?? null
        req.pwdIv = c?.pwdIv ?? null
        req.pwdWrappedDek = c?.pwdWrappedDek ?? null
        req.masterSalt = c?.masterSalt ?? null
        req.masterIv = c?.masterIv ?? null
        req.masterWrappedDek = c?.masterWrappedDek ?? null
      } else {
        req.bodyMd = body
        req.plaintextHash = md5(body)
      }

      const out = await api.save(meta.key, req)
      applySaveResult(set, get, out.note, out.ids, key, desc, body)
      set({ saving: false, lastSavedAt: Date.now() })
      if (manual) {
        const added = out.ids.assigned + out.ids.reassigned
        get().toast({
          kind: 'success',
          text: added > 0 ? `已保存 · 补齐 ${added} 个块 ID` : '已保存',
          detail: `v${out.note.meta.version} · ${out.note.meta.blockCount} 块`,
          ttl: 2200,
        })
      }
      void get().refreshNotes()
      void get().loadBlocks(meta.key)
      return true
    } catch (e) {
      const msg = errText(e)
      set({ saving: false, saveError: msg })
      if (e instanceof ApiError && e.status === 409) {
        get().toast({ kind: 'warn', text: '保存被拒绝', detail: msg })
      } else {
        get().toast({ kind: 'error', text: '保存失败', detail: msg })
      }
      return false
    }
  },

  async createNote(key, desc, body) {
    set({ saving: true, saveError: null })
    try {
      const out = await api.create({
        key,
        desc,
        title: desc || key,
        bodyMd: body,
        plaintextHash: md5(body),
        tags: [],
        aliases: [],
      })
      applySaveResult(set, get, out.note, out.ids, key, desc, body)
      set({ saving: false, composing: false, lastSavedAt: Date.now(), newNoteOpen: false })
      get().toast({
        kind: 'success',
        text: `已新建 ${key}`,
        detail: out.note.meta.domain ? `分类：${out.note.meta.domain} / ${out.note.meta.topic}` : '未分类，可在右侧抽屉补分类',
      })
      await get().refreshNotes()
      void get().refreshTree()
      void get().loadBlocks(key)
      return true
    } catch (e) {
      const msg = errText(e)
      set({ saving: false, saveError: msg })
      get().toast({ kind: 'error', text: '新建失败', detail: msg })
      return false
    }
  },

  async removeNote(key) {
    const prev = get().notes
    set({ notes: prev.filter((n) => n.key !== key), selected: get().selected.filter((k) => k !== key) })
    if (get().currentKey === key) get().closeNote()
    try {
      const out = await api.remove(key)
      dekVault.delete(key)
      set({ unlockedKeys: [...dekVault.keys()] })
      get().toast({
        kind: 'warn',
        text: `已删除 ${key}`,
        detail: '60 秒内可撤销',
        ttl: 60_000,
        action: {
          label: '撤销',
          run: () => {
            void get().restoreNote(key)
          },
        },
      })
      void out
      void get().refreshTree()
    } catch (e) {
      set({ notes: prev })
      get().toast({ kind: 'error', text: '删除失败', detail: errText(e) })
    }
  },

  async removeMany(keys) {
    if (keys.length === 0) return
    const prev = get().notes
    set({ notes: prev.filter((n) => !keys.includes(n.key)), selected: [], selectMode: false })
    if (get().currentKey && keys.includes(get().currentKey!)) get().closeNote()
    const failed: string[] = []
    for (const k of keys) {
      try {
        await api.remove(k)
        dekVault.delete(k)
      } catch {
        failed.push(k)
      }
    }
    set({ unlockedKeys: [...dekVault.keys()] })
    if (failed.length) {
      set({ notes: prev.filter((n) => !keys.includes(n.key) || failed.includes(n.key)) })
      get().toast({ kind: 'error', text: `${failed.length} 篇删除失败`, detail: failed.join(', ') })
    }
    const done = keys.filter((k) => !failed.includes(k))
    if (done.length) {
      get().toast({
        kind: 'warn',
        text: `已删除 ${done.length} 篇笔记`,
        detail: '60 秒内可撤销',
        ttl: 60_000,
        action: {
          label: '全部撤销',
          run: () => {
            void (async () => {
              for (const k of done) await api.restore(k).catch(() => undefined)
              await get().refreshNotes()
              void get().refreshTree()
              get().toast({ kind: 'success', text: `已恢复 ${done.length} 篇` })
            })()
          },
        },
      })
    }
    void get().refreshTree()
  },

  async restoreNote(key) {
    try {
      await api.restore(key)
      await get().refreshNotes()
      void get().refreshTree()
      get().toast({ kind: 'success', text: `已恢复 ${key}`, ttl: 2200 })
    } catch (e) {
      get().toast({ kind: 'error', text: '恢复失败', detail: errText(e) })
    }
  },

  async loadDeleted() {
    set({ deleted: await api.notes(true) })
  },

  async loadBlocks(key) {
    try {
      const blocks = await api.blocks(key)
      if (get().currentKey === key) set({ blocks })
    } catch {
      /* 私密笔记锁定态下列块可能为空，不报错 */
    }
  },

  async openBlock(blockId) {
    const s = get()
    const det = s.detail
    if (det?.meta.encrypted && s.unlockedKeys.includes(det.meta.key)) {
      const all = parseLocalBlocks(splitHeader(s.editorText).rest, det.meta.key)
      const local = all.find((x) => x.blockId === blockId)
      if (local) {
        set({ blockDetail: localBlockDetail(local, all, det, s.editorText) })
        get().requestJump(local.startLine + 1, 0, local.endLine + 1)
        return
      }
    }
    try {
      const b = await api.block(blockId)
      set({ blockDetail: b })
      if (b.noteKey !== get().currentKey) {
        await get().openNote(b.noteKey)
      }
      get().requestJump(b.startLine + 1, 0, b.endLine + 1)
    } catch (e) {
      get().toast({ kind: 'error', text: '打开知识块失败', detail: errText(e) })
    }
  },

  requestJump(line, ch = 0, selectTo) {
    set({ jump: { line, ch, selectTo, nonce: jumpSeq++ } })
  },

  // ==================== 私密笔记 ====================

  async unlock(key, password, useMaster) {
    const detail = get().detail?.meta.key === key ? get().detail : await api.note(key)
    const c = detail?.crypto
    if (!detail?.meta.encrypted || !c) throw new Error('这篇笔记未加密')
    const wrapped = useMaster
      ? { salt: c.masterSalt ?? '', iv: c.masterIv ?? '', cipher: c.masterWrappedDek ?? '' }
      : { salt: c.pwdSalt ?? '', iv: c.pwdIv ?? '', cipher: c.pwdWrappedDek ?? '' }
    if (!wrapped.salt || !wrapped.cipher) {
      throw new Error(useMaster ? '这篇笔记没有主密码恢复信息' : '这篇笔记缺少密码校验信息')
    }
    const dek = await unwrapDek(wrapped, password)
    const plaintext = await decryptBody(detail.cipherBody ?? '', c.cipherIv ?? '', dek)
    dekVault.set(key, dek)
    if (useMaster) masterPassword = password
    set({
      unlockedKeys: [...dekVault.keys()],
      detail: { ...detail, bodyMd: plaintext },
      editorText: `${detail.meta.key} ${detail.meta.desc}\n${plaintext}`,
      dirty: false,
    })
    void get().loadBlocks(key)
  },

  async enableEncryption(key, notePassword, master) {
    const s = get()
    const detail = s.detail?.meta.key === key ? s.detail : await api.note(key)
    if (!detail) throw new Error('笔记不存在')
    if (detail.meta.encrypted) throw new Error('这篇笔记已加密')
    if (!master) throw new Error('启用加密前必须先设置主密码，否则忘记密码将无法恢复')

    const nl = s.editorText.indexOf('\n')
    const body = s.currentKey === key ? (nl >= 0 ? s.editorText.slice(nl + 1) : '') : (detail.bodyMd ?? '')
    const dek = newDek()
    const byPwd = await wrapDek(dek, notePassword)
    const byMaster = await wrapDek(dek, master)
    const sealed = await encryptBody(body, dek)

    const out = await api.save(key, {
      desc: detail.meta.desc,
      title: detail.meta.title,
      expectedVersion: detail.meta.version,
      encrypted: true,
      bodyMd: sealed.cipher,
      cipherIv: sealed.iv,
      pwdSalt: byPwd.salt,
      pwdIv: byPwd.iv,
      pwdWrappedDek: byPwd.cipher,
      masterSalt: byMaster.salt,
      masterIv: byMaster.iv,
      masterWrappedDek: byMaster.cipher,
      assets: extractAssetRefs(body),
      plaintextHash: md5(body),
      domain: detail.meta.domain,
      topic: detail.meta.topic,
      noteType: detail.meta.noteType,
      layer: detail.meta.layer,
      visibility: 'private',
      aliases: detail.meta.aliases ?? [],
      tags: detail.meta.tags ?? [],
    })

    dekVault.set(key, dek)
    masterPassword = master
    set({
      detail: { ...out.note, bodyMd: body },
      unlockedKeys: [...dekVault.keys()],
      dirty: false,
      lastSavedAt: Date.now(),
    })
    await get().refreshNotes()
    get().toast({ kind: 'success', text: '已启用端到端加密', detail: '明文只存在于这台浏览器，服务端只存密文。' })
  },

  async changeNotePassword(key, newPassword) {
    const dek = dekVault.get(key)
    if (!dek) throw new Error('请先解锁笔记再修改密码')
    const detail = get().detail
    if (!detail?.crypto) throw new Error('缺少加密参数')
    const byPwd = await wrapDek(dek, newPassword)
    const nl = get().editorText.indexOf('\n')
    const body = nl >= 0 ? get().editorText.slice(nl + 1) : ''
    const sealed = await encryptBody(body, dek)
    const out = await api.save(key, {
      desc: detail.meta.desc,
      title: detail.meta.title,
      expectedVersion: detail.meta.version,
      encrypted: true,
      bodyMd: sealed.cipher,
      cipherIv: sealed.iv,
      pwdSalt: byPwd.salt,
      pwdIv: byPwd.iv,
      pwdWrappedDek: byPwd.cipher,
      masterSalt: detail.crypto.masterSalt,
      masterIv: detail.crypto.masterIv,
      masterWrappedDek: detail.crypto.masterWrappedDek,
      assets: extractAssetRefs(body),
      plaintextHash: md5(body),
      visibility: detail.meta.visibility,
    })
    set({ detail: { ...out.note, bodyMd: body }, dirty: false })
    await get().refreshNotes()
    get().toast({ kind: 'success', text: '笔记密码已更新' })
  },

  async disableEncryption(key) {
    const dek = dekVault.get(key)
    if (!dek) throw new Error('请先解锁笔记')
    const detail = get().detail
    if (!detail) throw new Error('笔记不存在')
    const nl = get().editorText.indexOf('\n')
    const body = nl >= 0 ? get().editorText.slice(nl + 1) : (detail.bodyMd ?? '')
    const out = await api.save(key, {
      desc: detail.meta.desc,
      title: detail.meta.title,
      expectedVersion: detail.meta.version,
      encrypted: false,
      bodyMd: body,
      plaintextHash: md5(body),
      cipherIv: null,
      pwdSalt: null,
      pwdIv: null,
      pwdWrappedDek: null,
      masterSalt: null,
      masterIv: null,
      masterWrappedDek: null,
      assets: [],
      visibility: 'normal',
    })
    dekVault.delete(key)
    set({
      detail: out.note,
      editorText: `${out.note.meta.key} ${out.note.meta.desc}\n${body}`,
      dirty: false,
      unlockedKeys: [...dekVault.keys()],
    })
    await get().refreshNotes()
    get().toast({ kind: 'success', text: '已取消加密', detail: '正文将以明文落盘并参与云同步。' })
  },

  lockNote(key) {
    dekVault.delete(key)
    const detail = get().detail
    set({
      unlockedKeys: [...dekVault.keys()],
      detail: detail && detail.meta.key === key ? { ...detail, bodyMd: null } : detail,
      editorText:
        get().currentKey === key && detail
          ? `${detail.meta.key} ${detail.meta.desc}\n`
          : get().editorText,
      dirty: false,
    })
    get().toast({ kind: 'info', text: '已锁定，内存中的密钥已清除', ttl: 2200 })
  },

  async setMasterPassword(password) {
    const cfg = get().config ?? (await api.config())
    if (cfg.masterPasswordSet) {
      const ok = await get().verifyMaster(password)
      if (!ok) throw new WrongPasswordError()
      masterPassword = password
      return
    }
    const salt = newMasterSalt()
    const verifier = await deriveVerifier(password, salt)
    await api.updateConfig({}, { salt, verifier })
    masterPassword = password
    await get().refreshConfig()
    get().toast({ kind: 'success', text: '主密码已设置', detail: '它是忘记密码时唯一的恢复通道。' })
  },

  async verifyMaster(password) {
    const cfg = get().config ?? (await api.config())
    if (!cfg.masterPasswordSet || !cfg.masterSalt) return false
    const actual = await deriveVerifier(password, cfg.masterSalt)
    const ok = verifierMatches(cfg.masterVerifier, actual)
    if (ok) masterPassword = password
    return ok
  },

  // ==================== 检索 ====================

  async runSearch(q) {
    const query = (q ?? get().query).trim()
    set({ query })
    if (query.length < 2) {
      set({ searchResult: null, searchError: null, searching: false })
      return
    }
    set({ searching: true, searchError: null, sideTab: 'search', showSide: true })
    try {
      const f = get().searchFilter
      const filter: Record<string, string> = {}
      for (const [k, v] of Object.entries(f)) if (v) filter[k] = v
      const res = await api.search(query, get().tier, filter)
      set({ searchResult: res, searching: false, activeHit: res.hits[0]?.docId ?? null })
    } catch (e) {
      set({ searching: false, searchError: errText(e) })
    }
  },

  setQuery(q) {
    set({ query: q })
  },

  setTier(t) {
    set({ tier: t })
    if (get().query.trim().length >= 2) void get().runSearch()
  },

  setSearchFilter(patch) {
    set({ searchFilter: { ...get().searchFilter, ...patch } })
    if (get().query.trim().length >= 2) void get().runSearch()
  },

  async loadSuggestions(prefix) {
    if (!prefix.trim()) {
      set({ suggestions: [] })
      return
    }
    try {
      set({ suggestions: await api.suggest(prefix.trim()) })
    } catch {
      set({ suggestions: [] })
    }
  },

  clearSearch() {
    set({ query: '', searchResult: null, searchError: null, suggestions: [], activeHit: null })
  },

  // ==================== 界面 ====================

  setTheme(t) {
    applyTheme(t)
    set({ theme: t })
  },
  setSideTab(t) {
    set({ sideTab: t, showSide: true })
    writeLS(LS.sideTab, t)
    if (t === 'tree' && !get().tree) void get().refreshTree()
  },
  setViewMode(m) {
    set({ viewMode: m })
    writeLS(LS.viewMode, m)
  },
  toggleList() {
    set({ showList: !get().showList })
  },
  toggleSide() {
    set({ showSide: !get().showSide })
  },
  setListW(w) {
    const v = Math.round(Math.min(460, Math.max(200, w)))
    set({ listW: v })
    writeLS(LS.listW, v)
  },
  setSideW(w) {
    const v = Math.round(Math.min(520, Math.max(240, w)))
    set({ sideW: v })
    writeLS(LS.sideW, v)
  },
  setDrawer(open) {
    set({ drawerOpen: open })
  },
  setSettings(open) {
    set({ settingsOpen: open })
    if (open && !get().config) void get().refreshConfig()
  },
  setPalette(open) {
    set({ paletteOpen: open })
  },
  setSyncOpen(open) {
    set({ syncOpen: open })
    if (open) void api.syncStatus().then((s) => set({ syncStatus: s })).catch(() => undefined)
  },
  setNewNoteOpen(open) {
    set({ newNoteOpen: open })
  },
  setPrivateTarget(t) {
    set({ privateTarget: t })
  },
  setBlockDetail(b) {
    set({ blockDetail: b })
  },
  setFilter(patch) {
    set({ filter: { ...get().filter, ...patch }, activeViewId: null })
    if (patch.showDeleted) void get().loadDeleted()
  },
  saveView(name) {
    const f = get().filter
    const view: SavedView = {
      id: `v${Date.now()}`,
      name,
      text: f.text,
      domain: f.domain,
      topic: f.topic,
      sort: f.sort,
      encryptedOnly: f.encryptedOnly,
    }
    const views = [...get().views, view]
    set({ views, activeViewId: view.id })
    writeLS(LS.views, views)
    get().toast({ kind: 'success', text: `视图「${name}」已保存`, ttl: 2000 })
  },
  applyView(id) {
    const v = get().views.find((x) => x.id === id)
    if (!v) return
    set({
      activeViewId: id,
      filter: {
        ...get().filter,
        text: v.text,
        domain: v.domain,
        topic: v.topic,
        sort: v.sort,
        encryptedOnly: v.encryptedOnly,
      },
    })
  },
  deleteView(id) {
    const views = get().views.filter((v) => v.id !== id)
    set({ views, activeViewId: get().activeViewId === id ? null : get().activeViewId })
    writeLS(LS.views, views)
  },
  toggleSelect(key) {
    const sel = get().selected
    set({ selected: sel.includes(key) ? sel.filter((k) => k !== key) : [...sel, key], selectMode: true })
  },
  selectAll(keys) {
    set({ selected: keys, selectMode: true })
  },
  clearSelection() {
    set({ selected: [], selectMode: false })
  },
  setSelectMode(on) {
    set({ selectMode: on, selected: on ? get().selected : [] })
  },
  setMobile(on) {
    if (get().mobile === on) return
    set({ mobile: on, showList: on ? false : true, showSide: on ? false : true })
  },

  toast(t) {
    const id = toastSeq++
    const toast: Toast = { id, ttl: t.ttl ?? 4200, ...t }
    set({ toasts: [...get().toasts.slice(-4), toast] })
    if (toast.ttl > 0) {
      window.setTimeout(() => get().dismissToast(id), toast.ttl)
    }
    return id
  },

  dismissToast(id) {
    set({ toasts: get().toasts.filter((t) => t.id !== id) })
  },

  async updateMeta(patch) {
    const s = get()
    const meta = s.detail?.meta
    if (!meta) return
    const next = { ...meta, ...patch }
    set({ detail: { ...s.detail!, meta: next } })
    try {
      const nl = s.editorText.indexOf('\n')
      const body = nl >= 0 ? s.editorText.slice(nl + 1) : ''
      const req: SaveRequest = {
        desc: next.desc,
        title: next.title || next.desc,
        expectedVersion: meta.version,
        domain: next.domain,
        topic: next.topic,
        noteType: next.noteType,
        layer: next.layer,
        visibility: next.visibility,
        aliases: next.aliases ?? [],
        tags: next.tags ?? [],
      }
      if (meta.encrypted) {
        const dek = dekVault.get(meta.key)
        if (!dek) {
          set({ detail: s.detail })
          get().toast({ kind: 'warn', text: '锁定态下无法修改元数据', detail: '请先解锁笔记。' })
          return
        }
        const sealed = await encryptBody(body, dek)
        req.encrypted = true
        req.bodyMd = sealed.cipher
        req.cipherIv = sealed.iv
        req.assets = extractAssetRefs(body)
        req.plaintextHash = md5(body)
        req.pwdSalt = s.detail?.crypto?.pwdSalt ?? null
        req.pwdIv = s.detail?.crypto?.pwdIv ?? null
        req.pwdWrappedDek = s.detail?.crypto?.pwdWrappedDek ?? null
        req.masterSalt = s.detail?.crypto?.masterSalt ?? null
        req.masterIv = s.detail?.crypto?.masterIv ?? null
        req.masterWrappedDek = s.detail?.crypto?.masterWrappedDek ?? null
      } else {
        req.bodyMd = body
        req.plaintextHash = md5(body)
      }
      const out = await api.save(meta.key, req)
      set({ detail: { ...out.note, bodyMd: meta.encrypted ? body : out.note.bodyMd }, dirty: false })
      await get().refreshNotes()
      void get().refreshTree()
    } catch (e) {
      set({ detail: s.detail })
      get().toast({ kind: 'error', text: '元数据保存失败', detail: errText(e) })
    }
  },

  async guess() {
    const key = get().currentKey
    if (!key) return
    try {
      const g = await api.guess(key)
      if (!g.domain) {
        get().toast({ kind: 'info', text: '没有猜出分类', detail: '可手动在抽屉里选择领域与主题。' })
        return
      }
      await get().updateMeta({ domain: g.domain, topic: g.topic })
      get().toast({
        kind: 'success',
        text: `分类已补：${g.domain} / ${g.topic ?? '—'}`,
        detail: g.source === 'vector' ? '来自向量相似度' : '来自关键词匹配',
      })
    } catch (e) {
      get().toast({ kind: 'error', text: '分类猜测失败', detail: errText(e) })
    }
  },

  async uploadAsset(file) {
    const key = get().currentKey
    if (!key) {
      get().toast({ kind: 'warn', text: '请先保存笔记再插入图片' })
      return null
    }
    try {
      const out = await api.uploadAsset(key, file)
      return out.name
    } catch (e) {
      get().toast({ kind: 'error', text: '图片上传失败', detail: errText(e) })
      return null
    }
  },

  async doSync(dir) {
    try {
      const out = dir === 'upload' ? await api.syncUpload() : await api.syncPull()
      get().toast({
        kind: out.ok ? 'success' : 'warn',
        text: dir === 'upload' ? '上传完成' : '下载完成',
        detail: out.message,
      })
      if (dir === 'pull') {
        await get().refreshNotes()
        void get().refreshTree()
        if (get().currentKey) void get().openNote(get().currentKey!)
      }
      await get().refreshSystem()
    } catch (e) {
      get().toast({ kind: 'error', text: '同步失败', detail: errText(e) })
    }
  },

  async rebuildIndex() {
    try {
      await api.rebuildIndex()
      get().toast({ kind: 'info', text: '已开始重建索引', detail: '进度会在状态栏实时显示。' })
    } catch (e) {
      get().toast({ kind: 'error', text: '重建索引失败', detail: errText(e) })
    }
  },

  async updateConfig(changes) {
    const out = await api.updateConfig(changes)
    await get().refreshConfig()
    await get().refreshSystem()
    events.stop()
    events.start()
    return out.needsReindex
  },
}))

// ==================== 内部工具 ====================

type Set = (partial: Partial<State>) => void
type Get = () => State

async function decryptWithVault(key: string, detail: NoteDetail): Promise<string> {
  const dek = dekVault.get(key)
  if (!dek || !detail.crypto) return ''
  try {
    return await decryptBody(detail.cipherBody ?? '', detail.crypto.cipherIv ?? '', dek)
  } catch {
    dekVault.delete(key)
    return ''
  }
}

function errText(e: unknown): string {
  if (e instanceof ApiError) return e.message
  if (e instanceof WrongPasswordError) return '密码错误'
  if (e instanceof Error) return e.message
  return String(e)
}

/**
 * 保存成功后把服务端回填的块 ID 插进编辑区，并把光标按插入行数平移，
 * 与桌面 syncEditorAfterSave 的行为一致：不重新加载文本、不丢光标。
 */
function applySaveResult(
  set: Set,
  get: Get,
  note: NoteDetail,
  ids: { body: string | null; assigned: number; reassigned: number; insertions: Array<{ line: number; text: string }> },
  key: string,
  desc: string,
  plainBody: string,
) {
  const s = get()
  const insertions = ids.insertions ?? []
  let body = plainBody
  if (insertions.length > 0 && ids.body) {
    body = ids.body
  }

  const caret = s.caretLine
  const caretBodyLine = Math.max(0, caret - 1)
  const shift = insertions.filter((i) => i.line <= caretBodyLine).length
  const newCaret = caret + shift

  const dek = dekVault.get(key)
  const detail: NoteDetail = note.meta.encrypted && dek ? { ...note, bodyMd: plainBody } : note

  set({
    detail,
    currentKey: key,
    editorText: `${key} ${desc}\n${body}`,
    dirty: false,
    unlockedKeys: [...dekVault.keys()],
  })
  if (insertions.length > 0) {
    get().requestJump(newCaret, 0)
  }
}

// ==================== 事件接线 ====================

export function wireEvents() {
  setUnauthorizedHandler(() => {
    const s = useStore.getState()
    if (!s.authed) return
    events.stop()
    dekVault.clear()
    masterPassword = null
    useStore.setState({
      authed: false,
      booting: false,
      unlockedKeys: [],
      auth: { authenticated: false, setupRequired: false, loopback: true, masterPasswordSet: false },
    })
    s.toast({ kind: 'warn', text: '登录已失效', detail: '请重新输入登录口令。' })
  })

  events.on((e) => {
    const s = useStore.getState()
    switch (e.type) {
      case 'open':
        useStore.setState({ connection: 'online' })
        void s.refreshNotes()
        void s.refreshSystem()
        break
      case 'closed':
        useStore.setState({ connection: 'reconnecting' })
        break
      case 'hello': {
        const p = e.payload as { index?: IndexStatus; sync?: SyncStatus }
        useStore.setState({ connection: 'online', indexStatus: p.index ?? s.indexStatus, syncStatus: p.sync ?? s.syncStatus })
        break
      }
      case 'index':
        useStore.setState({ indexStatus: e.payload as IndexStatus })
        break
      case 'sync':
        useStore.setState({ syncStatus: e.payload as SyncStatus })
        break
      case 'notes': {
        if (noteReloadTimer !== null) window.clearTimeout(noteReloadTimer)
        noteReloadTimer = window.setTimeout(() => {
          noteReloadTimer = null
          void useStore.getState().refreshNotes()
          void useStore.getState().refreshTree()
        }, 350)
        break
      }
      case 'config':
        void s.refreshConfig()
        break
      default:
        break
    }
  })
}
