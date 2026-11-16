import { useMemo } from 'react'
import { useStore } from './store'
import { splitHeader } from './format'
import type { BlockDetail, BlockSummary, NoteDetail } from './types'

const KU = /^<!--\s*ku:([A-Za-z0-9_-]+)\s*-->\s*$/
const HEAD = /^(#{1,6})\s+(.*)$/
const FENCE = /^\s*(```|~~~)/

/** 私密笔记的正文只在浏览器里存在，块结构必须由明文本地切分。 */
export function parseLocalBlocks(body: string, noteKey: string): BlockSummary[] {
  const lines = body.split('\n')
  const heads: Array<{ line: number; level: number; title: string }> = []
  let inFence = false
  lines.forEach((ln, i) => {
    if (FENCE.test(ln)) {
      inFence = !inFence
      return
    }
    if (inFence) return
    const m = HEAD.exec(ln)
    if (m) heads.push({ line: i, level: m[1].length, title: m[2].trim() })
  })
  return heads.map((h, idx) => {
    const endLine = idx + 1 < heads.length ? heads[idx + 1].line - 1 : lines.length - 1
    let blockId = ''
    for (let i = h.line + 1; i <= Math.min(h.line + 2, endLine); i++) {
      const m = KU.exec(lines[i] ?? '')
      if (m) {
        blockId = m[1]
        break
      }
    }
    return {
      blockId: blockId || `local:${h.line}`,
      noteKey,
      title: h.title,
      level: h.level,
      startLine: h.line,
      endLine,
    }
  })
}

export function localBlockDetail(b: BlockSummary, all: BlockSummary[], detail: NoteDetail, editorText: string): BlockDetail {
  const body = splitHeader(editorText).rest
  const lines = body.split('\n')
  const chain: string[] = []
  const stack: Array<{ level: number; title: string }> = []
  for (const x of all) {
    if (x.startLine >= b.startLine) break
    while (stack.length && stack[stack.length - 1].level >= x.level) stack.pop()
    stack.push({ level: x.level, title: x.title })
  }
  for (const s of stack) chain.push(s.title)
  const meta = detail.meta
  return {
    blockId: b.blockId,
    noteKey: b.noteKey,
    title: b.title,
    level: b.level,
    path: [meta.domain || '未分类', meta.topic || '未分主题', ...chain, b.title],
    body: lines.slice(b.startLine, b.endLine + 1).join('\n'),
    startLine: b.startLine,
    endLine: b.endLine,
    status: null,
    file: `notes/${b.noteKey}/note.md`,
    fileStartLine: b.startLine + 2,
    fileEndLine: b.endLine + 2,
  }
}

export function useBlocks(): BlockSummary[] {
  const serverBlocks = useStore((s) => s.blocks)
  const editorText = useStore((s) => s.editorText)
  const detail = useStore((s) => s.detail)
  const unlockedKeys = useStore((s) => s.unlockedKeys)
  const key = detail?.meta.key ?? ''
  const local = !!detail?.meta.encrypted && unlockedKeys.includes(key)
  return useMemo(
    () => (local ? parseLocalBlocks(splitHeader(editorText).rest, key) : serverBlocks),
    [local, editorText, key, serverBlocks],
  )
}