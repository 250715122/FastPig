/**
 * 浏览器端 E2E 加解密，与 com.gt.crypto.NoteCrypto 逐字段同口径：
 * PBKDF2-HMAC-SHA256 / 210000 轮 / 16 字节盐 / 256 位密钥，
 * AES-GCM / 12 字节 IV / 128 位标签，DEK 32 字节。
 * 服务端只搬运密文与包裹字段，明文和口令不出浏览器。
 */

const ITERATIONS = 210_000
const KEY_BITS = 256
const SALT_BYTES = 16
const IV_BYTES = 12
const DEK_BYTES = 32

export class WrongPasswordError extends Error {
  constructor() {
    super('密码错误')
    this.name = 'WrongPasswordError'
  }
}

const subtle = () => {
  if (!globalThis.crypto?.subtle) {
    throw new Error('当前环境不支持 WebCrypto，私密笔记无法解密（需 HTTPS 或 localhost）')
  }
  return globalThis.crypto.subtle
}

export function toB64(bytes: Uint8Array): string {
  let bin = ''
  for (let i = 0; i < bytes.length; i += 0x8000) {
    bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000))
  }
  return btoa(bin)
}

export function fromB64(b64: string): Uint8Array {
  const bin = atob(b64)
  const out = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i)
  return out
}

export function randomBytes(n: number): Uint8Array {
  const out = new Uint8Array(n)
  globalThis.crypto.getRandomValues(out)
  return out
}

const encode = (s: string) => new TextEncoder().encode(s)

async function deriveKey(password: string, salt: Uint8Array, usage: KeyUsage[]): Promise<CryptoKey> {
  const base = await subtle().importKey('raw', encode(password), 'PBKDF2', false, ['deriveKey'])
  return subtle().deriveKey(
    { name: 'PBKDF2', salt: salt as BufferSource, iterations: ITERATIONS, hash: 'SHA-256' },
    base,
    { name: 'AES-GCM', length: KEY_BITS },
    false,
    usage,
  )
}

async function deriveBits(password: string, salt: Uint8Array): Promise<Uint8Array> {
  const base = await subtle().importKey('raw', encode(password), 'PBKDF2', false, ['deriveBits'])
  const bits = await subtle().deriveBits(
    { name: 'PBKDF2', salt: salt as BufferSource, iterations: ITERATIONS, hash: 'SHA-256' },
    base,
    KEY_BITS,
  )
  return new Uint8Array(bits)
}

export interface WrappedDek {
  salt: string
  iv: string
  cipher: string
}

export function newDek(): Uint8Array {
  return randomBytes(DEK_BYTES)
}

export async function wrapDek(dek: Uint8Array, password: string): Promise<WrappedDek> {
  const salt = randomBytes(SALT_BYTES)
  const iv = randomBytes(IV_BYTES)
  const kek = await deriveKey(password, salt, ['encrypt'])
  const cipher = await subtle().encrypt(
    { name: 'AES-GCM', iv: iv as BufferSource, tagLength: 128 },
    kek,
    dek as BufferSource,
  )
  return { salt: toB64(salt), iv: toB64(iv), cipher: toB64(new Uint8Array(cipher)) }
}

export async function unwrapDek(wrapped: WrappedDek, password: string): Promise<Uint8Array> {
  if (!wrapped.salt || !wrapped.iv || !wrapped.cipher) {
    throw new Error('包裹的密钥不完整')
  }
  const kek = await deriveKey(password, fromB64(wrapped.salt), ['decrypt'])
  try {
    const out = await subtle().decrypt(
      { name: 'AES-GCM', iv: fromB64(wrapped.iv) as BufferSource, tagLength: 128 },
      kek,
      fromB64(wrapped.cipher) as BufferSource,
    )
    return new Uint8Array(out)
  } catch {
    throw new WrongPasswordError()
  }
}

export interface EncryptedBody {
  iv: string
  cipher: string
}

export async function encryptBody(plaintext: string, dek: Uint8Array): Promise<EncryptedBody> {
  const iv = randomBytes(IV_BYTES)
  const key = await subtle().importKey('raw', dek as BufferSource, 'AES-GCM', false, ['encrypt'])
  const out = await subtle().encrypt(
    { name: 'AES-GCM', iv: iv as BufferSource, tagLength: 128 },
    key,
    encode(plaintext ?? ''),
  )
  return { iv: toB64(iv), cipher: toB64(new Uint8Array(out)) }
}

export async function decryptBody(cipherB64: string, ivB64: string, dek: Uint8Array): Promise<string> {
  if (!cipherB64) return ''
  const key = await subtle().importKey('raw', dek as BufferSource, 'AES-GCM', false, ['decrypt'])
  try {
    const out = await subtle().decrypt(
      { name: 'AES-GCM', iv: fromB64(ivB64) as BufferSource, tagLength: 128 },
      key,
      fromB64(cipherB64) as BufferSource,
    )
    return new TextDecoder().decode(out)
  } catch {
    throw new WrongPasswordError()
  }
}

/** 主密码校验值：与桌面 deriveVerifier 同口径，只存派生结果。 */
export async function deriveVerifier(password: string, saltB64: string): Promise<string> {
  return toB64(await deriveBits(password, fromB64(saltB64)))
}

export function newMasterSalt(): string {
  return toB64(randomBytes(SALT_BYTES))
}

export function verifierMatches(expected: string, actual: string): boolean {
  if (!expected || !actual || expected.length !== actual.length) return false
  let diff = 0
  for (let i = 0; i < expected.length; i++) diff |= expected.charCodeAt(i) ^ actual.charCodeAt(i)
  return diff === 0
}

/** 正文里的图片引用清单，与 NoteEncryptionService.ASSET_REF_PATTERN 一致。 */
export function extractAssetRefs(markdown: string): string[] {
  const out: string[] = []
  const seen = new Set<string>()
  const re = /!\[.*?\]\(assets\/([^)]+)\)/g
  let m: RegExpExecArray | null
  while ((m = re.exec(markdown ?? '')) !== null) {
    if (!seen.has(m[1])) {
      seen.add(m[1])
      out.push(m[1])
    }
  }
  return out
}