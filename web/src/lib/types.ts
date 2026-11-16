export interface NoteMeta {
  id: string
  key: string
  title: string
  desc: string
  domain: string | null
  topic: string | null
  noteType: string | null
  layer: string | null
  visibility: string | null
  aliases: string[] | null
  tags: string[] | null
  encrypted: boolean
  locked: boolean
  deleted: boolean
  createdAt: number
  updatedAt: number
  version: number
  blockCount: number
  contentHash: string | null
  folder: string | null
}

export interface CryptoPayload {
  cipherIv: string | null
  pwdSalt: string | null
  pwdIv: string | null
  pwdWrappedDek: string | null
  masterSalt: string | null
  masterIv: string | null
  masterWrappedDek: string | null
}

export interface NoteDetail {
  meta: NoteMeta
  bodyMd: string | null
  cipherBody: string | null
  crypto: CryptoPayload | null
  assets: string[] | null
}

export interface BlockInsertion {
  line: number
  text: string
}

export interface BlockIdResult {
  body: string | null
  assigned: number
  reassigned: number
  insertions: BlockInsertion[]
}

export interface SaveOutcome {
  note: NoteDetail
  ids: BlockIdResult
  created: boolean
}

export interface BlockSummary {
  blockId: string
  noteKey: string
  title: string
  level: number
  startLine: number
  endLine: number
}

export interface BlockDetail {
  blockId: string
  noteKey: string
  title: string
  level: number
  path: string[]
  body: string
  startLine: number
  endLine: number
  status: string | null
  file: string | null
  fileStartLine: number
  fileEndLine: number
}

export interface Suggestion {
  key: string
  desc: string
  domain: string | null
  topic: string | null
  encrypted: boolean
  blockCount: number
  match: string
}

export type Channel = 'EXACT' | 'BM25' | 'VECTOR'

export interface SearchHit {
  docId: string
  noteKey: string
  noteDesc: string
  blockId: string | null
  h1Title: string | null
  sectionTitle: string | null
  path: string | null
  startLine: number
  content: string
  status: string | null
  updatedAt: number
  channels: Channel[]
  score: number
}

export interface SearchResponse {
  query: string
  tier: 'human' | 'ai'
  hits: SearchHit[]
  total: number
  tookMs: number
  available: boolean
  message: string | null
  weights: Record<string, number>
  topK: number
  candidates: number
  perNoteQuota: number
  fullBody: boolean
}

export interface TaxonomyTopic {
  name: string
  aliases: string[]
}

export interface TaxonomyDomain {
  name: string
  topics: TaxonomyTopic[]
}

export interface Taxonomy {
  version: number
  domains: TaxonomyDomain[]
  noteTypes: string[]
  layers: string[]
  visibilities: string[]
}

export interface TreeNote {
  key: string
  title: string
  domain: string | null
  topic: string | null
  encrypted: boolean
  blockCount: number
}

export interface TreeTopic {
  domain: string
  topic: string
  notes: TreeNote[]
}

export interface TreeDomain {
  name: string
  topics: TreeTopic[]
  noteCount: number
}

export interface TreeSnapshot {
  domains: TreeDomain[]
  noteCount: number
  topicCount: number
  blockCount: number
}

export interface ConfigItem {
  key: string
  label: string
  category: string
  categoryLabel: string
  value: string
  def: string
  type: 'string' | 'int' | 'double' | 'boolean'
  min: number | null
  max: number | null
  needsReindex: boolean
}

export interface IndexStatus {
  state: string
  message: string
  available: boolean
  indexedCount: number
  schemaOutdated: boolean
  rebuilding: boolean
  current: number
  total: number
  modelDownloaded: boolean
  provider: string
  dimension: number
  weights: Record<string, number>
}

export interface SyncStatus {
  enabled: boolean
  configured: boolean
  provider: string
  remotePath: string
  autoUploadMinutes: number
  syncOnStart: boolean
  syncOnExit: boolean
  message: string | null
}

export interface WebInfo {
  enabled: boolean
  bind: string
  port: number
  sessionTtl: number
  syncEnabled: boolean
  sessions: number
  subscribers: number
}

export interface ConfigResponse {
  items: ConfigItem[]
  categories: string[]
  masterPasswordSet: boolean
  masterSalt: string
  masterVerifier: string
  web: WebInfo
  index: IndexStatus
}

export interface SystemInfo {
  app: string
  baseDir: string
  notesDir: string
  dbPath: string
  noteCount: number
  topicCount: number
  domainCount: number
  blockCount: number
  javaVersion: string
  startedAt: number
  uptimeMs: number
  lastSyncAt: number
  index: IndexStatus
  sync: SyncStatus
  masterPasswordSet: boolean
  autosaveSeconds: number
  previewSectionOnKb: number
  previewSectionOffKb: number
}

export interface AuthStatus {
  authenticated: boolean
  setupRequired: boolean
  loopback: boolean
  masterPasswordSet: boolean
}

export interface DeleteOutcome {
  key: string
  undoUntil: number
}

export interface AssetOutcome {
  name: string
  url: string
  size: number
}

export interface GuessResult {
  domain: string | null
  topic: string | null
  source: string
}

export interface SyncOutcome {
  ok: boolean
  message: string
  tookMs: number
}

export interface SaveRequest {
  key?: string
  desc?: string
  title?: string
  bodyMd?: string
  domain?: string | null
  topic?: string | null
  noteType?: string | null
  layer?: string | null
  visibility?: string | null
  aliases?: string[]
  tags?: string[]
  expectedVersion?: number
  encrypted?: boolean
  cipherIv?: string | null
  pwdSalt?: string | null
  pwdIv?: string | null
  pwdWrappedDek?: string | null
  masterSalt?: string | null
  masterIv?: string | null
  masterWrappedDek?: string | null
  assets?: string[]
  plaintextHash?: string
}