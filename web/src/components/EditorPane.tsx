import { useEffect, useMemo, useRef, useState } from 'react'
import { Compartment, EditorSelection, EditorState, type Range } from '@codemirror/state'
import {
  EditorView,
  keymap,
  lineNumbers,
  highlightActiveLine,
  highlightActiveLineGutter,
  drawSelection,
  dropCursor,
  rectangularSelection,
  crosshairCursor,
  highlightSpecialChars,
  scrollPastEnd,
  placeholder,
  Decoration,
  type DecorationSet,
  ViewPlugin,
  type ViewUpdate,
} from '@codemirror/view'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { markdown, markdownLanguage, insertNewlineContinueMarkup, deleteMarkupBackward } from '@codemirror/lang-markdown'
import { syntaxHighlighting, HighlightStyle, indentUnit, bracketMatching, indentOnInput } from '@codemirror/language'
import { searchKeymap, highlightSelectionMatches, selectNextOccurrence, openSearchPanel } from '@codemirror/search'
import { closeBrackets, closeBracketsKeymap, autocompletion, completionKeymap } from '@codemirror/autocomplete'
import { tags as t } from '@lezer/highlight'
import { useStore } from '../lib/store'
import { Icon } from './Icons'
import { byteLength, countLines, visibleChars } from '../lib/format'
import { useBlocks } from '../lib/blocks'

const KU_RE = /<!--\s*ku:[^>]*-->/g

const mdHighlight = HighlightStyle.define([
  { tag: t.heading1, class: 'tok-heading1' },
  { tag: t.heading2, class: 'tok-heading2' },
  { tag: t.heading3, class: 'tok-heading3' },
  { tag: t.heading4, class: 'tok-heading4' },
  { tag: t.heading5, class: 'tok-heading5' },
  { tag: t.heading6, class: 'tok-heading6' },
  { tag: t.strong, class: 'tok-strong' },
  { tag: t.emphasis, class: 'tok-emphasis' },
  { tag: t.strikethrough, class: 'tok-strikethrough' },
  { tag: t.link, class: 'tok-link' },
  { tag: t.url, class: 'tok-url' },
  { tag: t.monospace, class: 'tok-monospace' },
  { tag: t.quote, class: 'tok-quote' },
  { tag: t.list, class: 'tok-list' },
  { tag: t.separator, class: 'tok-separator' },
  { tag: t.comment, class: 'tok-comment' },
  { tag: t.meta, class: 'tok-meta' },
  { tag: t.punctuation, class: 'tok-punctuation' },
  { tag: t.keyword, class: 'tok-keyword' },
  { tag: t.string, class: 'tok-string' },
  { tag: t.number, class: 'tok-number' },
  { tag: t.variableName, class: 'tok-variableName' },
  { tag: t.typeName, class: 'tok-typeName' },
  { tag: t.function(t.variableName), class: 'tok-function' },
  { tag: t.operator, class: 'tok-operator' },
  { tag: t.attributeName, class: 'tok-attributeName' },
  { tag: t.attributeValue, class: 'tok-attributeValue' },
  { tag: t.tagName, class: 'tok-tag' },
  { tag: t.invalid, class: 'tok-invalid' },
])

const firstLineValid = (text: string) => /^\S+\s+\S/.test(text)

/** 首行命令行高亮 + 块 ID 注释灰显，与桌面编辑器的视觉约定一致。 */
const markupPlugin = ViewPlugin.fromClass(
  class {
    decorations: DecorationSet

    constructor(view: EditorView) {
      this.decorations = this.build(view)
    }

    update(u: ViewUpdate) {
      if (u.docChanged || u.viewportChanged) this.decorations = this.build(u.view)
    }

    build(view: EditorView): DecorationSet {
      const doc = view.state.doc
      const ranges: Range<Decoration>[] = []
      if (doc.lines >= 1) {
        const first = doc.line(1)
        ranges.push(
          Decoration.line({ class: firstLineValid(first.text) ? 'cm-first-line' : 'cm-first-line-invalid' }).range(
            first.from,
          ),
        )
      }
      const text = doc.toString()
      KU_RE.lastIndex = 0
      let m: RegExpExecArray | null
      let guard = 0
      while ((m = KU_RE.exec(text)) !== null && guard++ < 20000) {
        ranges.push(Decoration.mark({ class: 'cm-ku' }).range(m.index, m.index + m[0].length))
      }
      return Decoration.set(ranges, true)
    }
  },
  { decorations: (v) => v.decorations },
)

const wrapCompartment = new Compartment()
const styleCompartment = new Compartment()

function surround(view: EditorView, before: string, after = before, fallback = '文本') {
  const { state } = view
  const changes = state.changeByRange((range) => {
    const selected = state.sliceDoc(range.from, range.to)
    const text = selected || fallback
    return {
      changes: { from: range.from, to: range.to, insert: before + text + after },
      range: selected
        ? EditorSelection.range(range.from + before.length, range.to + before.length)
        : EditorSelection.range(range.from + before.length, range.from + before.length + text.length),
    }
  })
  view.dispatch(changes, { scrollIntoView: true, userEvent: 'input' })
  view.focus()
}

function prefixLines(view: EditorView, prefix: string) {
  const { state } = view
  const changes = state.changeByRange((range) => {
    const from = state.doc.lineAt(range.from).from
    const to = state.doc.lineAt(range.to).to
    const block = state.sliceDoc(from, to)
    const next = block
      .split('\n')
      .map((l) => (l.startsWith(prefix) ? l.slice(prefix.length) : prefix + l))
      .join('\n')
    return { changes: { from, to, insert: next }, range: EditorSelection.range(from, from + next.length) }
  })
  view.dispatch(changes, { scrollIntoView: true, userEvent: 'input' })
  view.focus()
}

function insertBlock(view: EditorView, text: string) {
  const pos = view.state.selection.main.head
  const line = view.state.doc.lineAt(pos)
  const needNl = line.text.trim().length > 0
  const insert = (needNl ? '\n\n' : '') + text
  view.dispatch({
    changes: { from: line.to, insert },
    selection: { anchor: line.to + insert.length },
    scrollIntoView: true,
    userEvent: 'input',
  })
  view.focus()
}

/** 在下一行同一列追加一个光标，等价桌面的多光标编辑。 */
function addCursorBelow(view: EditorView): boolean {
  const sel = view.state.selection
  const last = sel.ranges[sel.ranges.length - 1]
  const line = view.state.doc.lineAt(last.head)
  if (line.number >= view.state.doc.lines) return false
  const next = view.state.doc.line(line.number + 1)
  const col = Math.min(last.head - line.from, next.length)
  view.dispatch({
    selection: EditorSelection.create([...sel.ranges, EditorSelection.cursor(next.from + col)], sel.ranges.length),
    scrollIntoView: true,
    userEvent: 'select',
  })
  return true
}

const TOOLS = [
  { icon: 'heading', title: '二级标题（知识块分隔）', run: (v: EditorView) => prefixLines(v, '## ') },
  { icon: 'bold', title: '加粗 Ctrl+B', run: (v: EditorView) => surround(v, '**') },
  { icon: 'italic', title: '斜体 Ctrl+I', run: (v: EditorView) => surround(v, '*') },
  { icon: 'code', title: '行内代码', run: (v: EditorView) => surround(v, '`', '`', 'code') },
  { icon: 'terminal', title: '代码块', run: (v: EditorView) => insertBlock(v, '```bash\n\n```') },
  { icon: 'quote', title: '引用', run: (v: EditorView) => prefixLines(v, '> ') },
  { icon: 'list', title: '无序列表', run: (v: EditorView) => prefixLines(v, '- ') },
  { icon: 'check', title: '任务列表', run: (v: EditorView) => prefixLines(v, '- [ ] ') },
  { icon: 'link', title: '链接', run: (v: EditorView) => surround(v, '[', '](https://)', '链接文字') },
  { icon: 'table', title: '表格', run: (v: EditorView) => insertBlock(v, '| 列 A | 列 B |\n| --- | --- |\n|  |  |') },
  { icon: 'sparkles', title: '数学公式', run: (v: EditorView) => insertBlock(v, '$$\nE = mc^2\n$$') },
]

export default function EditorPane() {
  const hostRef = useRef<HTMLDivElement>(null)
  const viewRef = useRef<EditorView | null>(null)
  const applyingRef = useRef(false)

  const editorText = useStore((s) => s.editorText)
  const jump = useStore((s) => s.jump)
  const detail = useStore((s) => s.detail)
  const currentKey = useStore((s) => s.currentKey)
  const composing = useStore((s) => s.composing)
  const dirty = useStore((s) => s.dirty)
  const saving = useStore((s) => s.saving)
  const loadingNote = useStore((s) => s.loadingNote)
  const unlockedKeys = useStore((s) => s.unlockedKeys)
  const setPrivateTarget = useStore((s) => s.setPrivateTarget)
  const config = useStore((s) => s.config)
  const system = useStore((s) => s.system)
  const save = useStore((s) => s.save)
  const blocks = useBlocks()
  const caretLine = useStore((s) => s.caretLine)
  const viewMode = useStore((s) => s.viewMode)

  const [wrap, setWrap] = useState(true)
  const [showKu, setShowKu] = useState(true)
  const [dragging, setDragging] = useState(false)
  const [ready, setReady] = useState(0)

  const cfgValue = useMemo(() => {
    const map = new Map((config?.items ?? []).map((i) => [i.key, i.value]))
    return {
      fontSize: Number(map.get('editor.font.size') ?? 14),
      lineHeight: Number(map.get('editor.line.height') ?? 1.6),
      tabSize: Number(map.get('editor.tab.size') ?? 4),
      fontName: map.get('editor.font.name') ?? 'JetBrains Mono',
    }
  }, [config])

  const locked = detail?.meta.encrypted ? !unlockedKeys.includes(detail.meta.key) : false

  const handleImages = async (files: File[], view: EditorView) => {
    const s = useStore.getState()
    if (!s.currentKey) {
      s.toast({ kind: 'warn', text: '请先保存笔记再插入图片', detail: '图片要落在该笔记的 assets 目录下。' })
      return
    }
    for (const file of files) {
      const name = await s.uploadAsset(file)
      if (!name) continue
      const pos = view.state.selection.main.head
      const md = `![${file.name.replace(/\.[^.]+$/, '')}](assets/${name})`
      view.dispatch({
        changes: { from: pos, insert: md },
        selection: { anchor: pos + md.length },
        scrollIntoView: true,
        userEvent: 'input',
      })
      s.toast({ kind: 'success', text: '图片已插入', detail: name, ttl: 2200 })
    }
    view.focus()
  }

  // ===== 建立编辑器（只建一次）=====
  useEffect(() => {
    const host = hostRef.current
    if (!host) return

    const view = new EditorView({
      parent: host,
      state: EditorState.create({
        doc: useStore.getState().editorText,
        extensions: [
          lineNumbers(),
          highlightActiveLineGutter(),
          highlightSpecialChars(),
          history(),
          drawSelection(),
          dropCursor(),
          rectangularSelection(),
          crosshairCursor(),
          EditorState.allowMultipleSelections.of(true),
          indentOnInput(),
          bracketMatching(),
          closeBrackets(),
          autocompletion(),
          highlightActiveLine(),
          highlightSelectionMatches(),
          scrollPastEnd(),
          wrapCompartment.of(EditorView.lineWrapping),
          styleCompartment.of([]),
          markdown({ base: markdownLanguage, addKeymap: true, completeHTMLTags: false }),
          syntaxHighlighting(mdHighlight),
          markupPlugin,
          placeholder('首行写「key 描述」，回车后开始正文。用 # 标题切分知识块，保存时自动补块 ID。'),
          keymap.of([
            ...closeBracketsKeymap,
            ...defaultKeymap,
            ...searchKeymap,
            ...historyKeymap,
            ...completionKeymap,
            indentWithTab,
            { key: 'Mod-b', run: (v) => (surround(v, '**'), true) },
            { key: 'Mod-i', run: (v) => (surround(v, '*'), true) },
            { key: 'Mod-d', run: selectNextOccurrence, preventDefault: true },
            { key: 'Mod-Alt-ArrowDown', run: addCursorBelow },
            { key: 'Enter', run: insertNewlineContinueMarkup },
            { key: 'Backspace', run: deleteMarkupBackward },
          ]),
          EditorView.updateListener.of((u) => {
            if (applyingRef.current) return
            if (u.docChanged) useStore.getState().setEditorText(u.state.doc.toString())
            if (u.selectionSet || u.docChanged) {
              useStore.getState().setCaretLine(u.state.doc.lineAt(u.state.selection.main.head).number)
            }
          }),
          EditorView.domEventHandlers({
            paste(event, view) {
              const images = Array.from(event.clipboardData?.items ?? []).filter(
                (i) => i.kind === 'file' && i.type.startsWith('image/'),
              )
              if (images.length === 0) return false
              event.preventDefault()
              void handleImages(
                images.map((i) => i.getAsFile()).filter((f): f is File => !!f),
                view,
              )
              return true
            },
            dragover(event) {
              if (Array.from(event.dataTransfer?.types ?? []).includes('Files')) {
                event.preventDefault()
                setDragging(true)
              }
              return false
            },
            dragleave() {
              setDragging(false)
              return false
            },
            drop(event, view) {
              const files = Array.from(event.dataTransfer?.files ?? []).filter((f) => f.type.startsWith('image/'))
              setDragging(false)
              if (files.length === 0) return false
              event.preventDefault()
              void handleImages(files, view)
              return true
            },
          }),
          EditorView.contentAttributes.of({ 'aria-label': 'Markdown 编辑器', spellcheck: 'false' }),
        ],
      }),
    })
    viewRef.current = view
    useStore.getState().setCaretLine(view.state.doc.lineAt(view.state.selection.main.head).number)
    setReady((n) => n + 1)

    return () => {
      view.destroy()
      viewRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // ===== 字体 / 行高 / Tab 宽度 =====
  useEffect(() => {
    const view = viewRef.current
    if (!view) return
    view.dispatch({
      effects: styleCompartment.reconfigure([
        EditorView.theme({
          '&': { fontSize: `${cfgValue.fontSize}px` },
          '.cm-scroller': {
            lineHeight: String(cfgValue.lineHeight),
            fontFamily: `'${cfgValue.fontName}','JetBrains Mono',Consolas,'Microsoft YaHei',monospace`,
          },
          '.cm-content': { tabSize: String(cfgValue.tabSize) },
        }),
        indentUnit.of(' '.repeat(cfgValue.tabSize)),
        EditorState.tabSize.of(cfgValue.tabSize),
      ]),
    })
  }, [cfgValue, ready])

  // ===== 外部文本变化：打开笔记 / 保存回填块 ID / 解锁 =====
  useEffect(() => {
    const view = viewRef.current
    if (!view) return
    const cur = view.state.doc.toString()
    if (cur === editorText) return
    applyingRef.current = true
    const head = Math.min(view.state.selection.main.head, editorText.length)
    view.dispatch({
      changes: { from: 0, to: cur.length, insert: editorText },
      selection: { anchor: head },
    })
    applyingRef.current = false
    useStore.getState().setCaretLine(view.state.doc.lineAt(view.state.selection.main.head).number)
  }, [editorText, ready])

  // ===== 跳转请求 =====
  useEffect(() => {
    const view = viewRef.current
    if (!view || !jump) return
    const lineNo = Math.min(Math.max(1, jump.line), view.state.doc.lines)
    const line = view.state.doc.line(lineNo)
    const anchor = line.from + Math.min(jump.ch, line.length)
    const head = jump.selectTo
      ? view.state.doc.line(Math.min(Math.max(1, jump.selectTo), view.state.doc.lines)).to
      : anchor
    view.dispatch({
      selection: { anchor, head },
      effects: EditorView.scrollIntoView(anchor, { y: 'center', yMargin: 60 }),
    })
    view.focus()
  }, [jump, ready])

  // ===== 自动保存：与桌面同口径，首行结构合法才触发 =====
  useEffect(() => {
    const seconds = system?.autosaveSeconds ?? 10
    if (seconds <= 0) return
    const id = window.setInterval(() => {
      const s = useStore.getState()
      if (!s.dirty || s.saving || !s.authed) return
      const nl = s.editorText.indexOf('\n')
      const first = (nl >= 0 ? s.editorText.slice(0, nl) : s.editorText).trim()
      if (!firstLineValid(first)) return
      if (s.detail?.meta.encrypted && !s.unlockedKeys.includes(s.detail.meta.key)) return
      void s.save(false)
    }, seconds * 1000)
    return () => window.clearInterval(id)
  }, [system?.autosaveSeconds])

  const chars = visibleChars(editorText)
  const lines = countLines(editorText)
  const size = byteLength(editorText)
  const activeBlock = blocks.find((b) => caretLine - 1 >= b.startLine && caretLine - 1 <= b.endLine)

  return (
    <section className="editor-pane" aria-label="编辑区">
      {saving || loadingNote ? (
        <div className="loading-bar">
          <i />
        </div>
      ) : null}

      <div className="editor-tools">
        {TOOLS.map((tool) => (
          <button
            key={tool.title}
            className="icon-btn sm"
            title={tool.title}
            aria-label={tool.title}
            disabled={locked}
            onClick={() => viewRef.current && tool.run(viewRef.current)}
          >
            <Icon name={tool.icon} size={13} />
          </button>
        ))}
        <span className="sep" />
        <button
          className={`icon-btn sm${wrap ? ' on' : ''}`}
          title={wrap ? '关闭自动换行' : '开启自动换行'}
          aria-label="自动换行"
          aria-pressed={wrap}
          onClick={() => {
            const next = !wrap
            setWrap(next)
            viewRef.current?.dispatch({
              effects: wrapCompartment.reconfigure(next ? EditorView.lineWrapping : []),
            })
          }}
        >
          <Icon name="wrap" size={13} />
        </button>
        <button
          className={`icon-btn sm${showKu ? ' on' : ''}`}
          title={showKu ? '隐藏块 ID 注释' : '显示块 ID 注释'}
          aria-label="块 ID 注释可见性"
          aria-pressed={showKu}
          onClick={() => setShowKu((v) => !v)}
        >
          <Icon name="hash" size={13} />
        </button>
        <button
          className="icon-btn sm"
          title="页内查找 (Ctrl+F)"
          aria-label="页内查找"
          onClick={() => viewRef.current && openSearchPanel(viewRef.current)}
        >
          <Icon name="search" size={13} />
        </button>
        <span className="spacer" />
        <button
          className="icon-btn sm"
          title="插入图片（也可直接粘贴或拖入）"
          aria-label="插入图片"
          disabled={locked || !currentKey}
          onClick={() => {
            const input = document.createElement('input')
            input.type = 'file'
            input.accept = 'image/*'
            input.multiple = true
            input.onchange = () => {
              const files = Array.from(input.files ?? [])
              if (files.length && viewRef.current) void handleImages(files, viewRef.current)
            }
            input.click()
          }}
        >
          <Icon name="image" size={13} />
        </button>
      </div>

      <div className={`editor-host${showKu ? '' : ' cm-ku-hidden'}`} ref={hostRef}>
        {dragging ? <div className="drop-hint">松手即可上传图片到 assets/</div> : null}
        {locked && detail ? (
          <div className="editor-locked">
            <div className="lock-ring">
              <Icon name="lock" size={30} />
            </div>
            <h3>这是一篇私密笔记</h3>
            <p>
              正文在浏览器内用 AES-GCM 解密。服务端只保存密文与包裹后的密钥，
              没有你的密码，谁也读不到内容。
            </p>
            <div className="row">
              <button className="btn primary" onClick={() => setPrivateTarget({ mode: 'unlock', key: detail.meta.key })}>
                <Icon name="unlock" size={14} /> 解锁笔记
              </button>
              {detail.crypto?.masterWrappedDek ? (
                <span className="badge private">
                  <Icon name="key" size={10} /> 支持主密码恢复
                </span>
              ) : null}
            </div>
            <span className="eyebrow">端到端加密 · 明文不出浏览器</span>
          </div>
        ) : null}
      </div>

      <div className="editor-foot">
        {dirty ? <span className="dirty-dot" title="有未保存的修改" /> : <span className="saved-dot" title="已保存" />}
        <span>{dirty ? '未保存' : '已保存'}</span>
        <span className="sep" />
        <span>
          行 {caretLine} / {lines}
        </span>
        <span className="sep" />
        <span>{chars} 字</span>
        <span className="sep" />
        <span>{(size / 1024).toFixed(1)} KB</span>
        {activeBlock ? (
          <>
            <span className="sep" />
            <span className="ellip" style={{ color: 'var(--violet)' }}>
              {activeBlock.title}
            </span>
          </>
        ) : null}
        <span className="spacer" />
        {composing ? <span className="ellip" style={{ color: 'var(--amber)' }}>新笔记 · 首行写 key 描述</span> : null}
        {currentKey && !composing ? (
          <span className="status-hide-sm">
            {blocks.length} 块 · v{detail?.meta.version ?? 1}
          </span>
        ) : null}
        <span className="sep" />
        <span>{viewMode === 'split' ? '分栏' : viewMode === 'editor' ? '编辑' : '预览'}</span>
        <button className="btn sm primary" onClick={() => void save(true)} disabled={saving || locked} title="保存 (Ctrl+S)">
          <Icon name={saving ? 'refresh' : 'save'} size={11} className={saving ? 'spin' : ''} />
          保存
        </button>
      </div>
    </section>
  )
}