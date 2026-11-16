import { useEffect, useMemo, useState } from 'react'
import { useStore } from '../lib/store'
import { Icon } from './Icons'

type Template = { id: string; label: string; icon: string; hint: string; lines: string[] }

const TEMPLATES: Template[] = [
  {
    id: 'blank',
    label: '空白',
    icon: 'file',
    hint: '只建文件，正文自己写。',
    lines: ['', ''],
  },
  {
    id: 'cheatsheet',
    label: '速查卡片',
    icon: 'zap',
    hint: '场景 / 命令 / 注意，最适合被拼音前缀直接调出来。',
    lines: [
      '# 场景',
      '',
      '什么时候会用到这条知识。',
      '',
      '# 命令',
      '',
      '```bash',
      '',
      '```',
      '',
      '# 注意',
      '',
      '- ',
      '',
    ],
  },
  {
    id: 'incident',
    label: '排障记录',
    icon: 'alert',
    hint: '现象 / 定位 / 根因 / 解决 / 复盘，五段式。',
    lines: [
      '# 现象',
      '',
      '报错原文、影响范围、发生时间。',
      '',
      '# 定位',
      '',
      '看了哪些日志、执行了哪些命令。',
      '',
      '# 根因',
      '',
      '',
      '# 解决',
      '',
      '```bash',
      '',
      '```',
      '',
      '# 复盘',
      '',
      '- 下次怎么更早发现：',
      '',
    ],
  },
  {
    id: 'cli',
    label: '命令清单',
    icon: 'terminal',
    hint: '命令 + 参数表格 + 踩坑，适合工具类知识。',
    lines: [
      '# 常用命令',
      '',
      '```bash',
      '',
      '```',
      '',
      '# 参数说明',
      '',
      '| 参数 | 含义 | 默认 |',
      '| --- | --- | --- |',
      '|  |  |  |',
      '',
      '# 坑',
      '',
      '- ',
      '',
    ],
  },
]

/** 与 NoteFileStorage.buildFolderName 一致：目录名只保留中英文、数字、下划线、短横线。 */
function folderNameOf(key: string): string {
  const safe = key.replace(/[^a-zA-Z0-9一-龥\-_]/g, '-')
  return safe || 'unnamed'
}

export default function NewNoteDialog() {
  const notes = useStore((s) => s.notes)
  const deleted = useStore((s) => s.deleted)
  const saving = useStore((s) => s.saving)
  const setNewNoteOpen = useStore((s) => s.setNewNoteOpen)
  const createNote = useStore((s) => s.createNote)
  const startNew = useStore((s) => s.startNew)
  const loadDeleted = useStore((s) => s.loadDeleted)

  const [key, setKey] = useState('')
  const [desc, setDesc] = useState('')
  const [tpl, setTpl] = useState('cheatsheet')
  const [body, setBody] = useState(TEMPLATES[1].lines.join('\n'))
  const [touched, setTouched] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    void loadDeleted().catch(() => undefined)
  }, [loadDeleted])

  const close = () => setNewNoteOpen(false)

  const trimmedKey = key.trim()
  const takenBy = useMemo(() => {
    if (!trimmedKey) return null
    if (notes.some((n) => n.key === trimmedKey)) return 'live'
    if (deleted.some((n) => n.key === trimmedKey)) return 'trash'
    return null
  }, [trimmedKey, notes, deleted])

  const tooLong = trimmedKey.length > 64
  const hasSpace = /\s/.test(trimmedKey)
  const folder = folderNameOf(trimmedKey)
  const rewritten = !!trimmedKey && folder !== trimmedKey
  const keyOk = !!trimmedKey && !tooLong && !hasSpace && !takenBy
  const template = TEMPLATES.find((t) => t.id === tpl) ?? TEMPLATES[0]

  const pickTemplate = (t: Template) => {
    setTpl(t.id)
    setBody(t.lines.join('\n'))
  }

  const submit = async () => {
    setTouched(true)
    setError(null)
    if (!trimmedKey) {
      setError('先起一个快捷命令，它是以后调出这篇笔记的入口。')
      return
    }
    if (hasSpace) {
      setError('快捷命令不能含空格或换行。')
      return
    }
    if (tooLong) {
      setError('快捷命令不超过 64 个字符。')
      return
    }
    if (takenBy === 'live') {
      setError(`「${trimmedKey}」已经存在，换一个，或者直接打开那篇笔记。`)
      return
    }
    if (takenBy === 'trash') {
      setError(`「${trimmedKey}」在回收站里，先恢复或彻底清理后再复用这个名字。`)
      return
    }
    const ok = await createNote(trimmedKey, desc.trim(), body)
    if (!ok) setError(useStore.getState().saveError ?? '新建失败，请看右下角提示。')
  }

  const onEnter = (e: { key: string; preventDefault: () => void }) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      void submit()
    }
  }

  return (
    <div className="overlay" onClick={close}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-label="新建笔记">
        <div className="modal-head">
          <h2>
            <Icon name="file" size={17} style={{ color: 'var(--acid)' }} />
            新建笔记
          </h2>
          <span className="spacer" />
          <button className="icon-btn" onClick={close} aria-label="关闭">
            <Icon name="x" size={16} />
          </button>
        </div>

        <div className="modal-body">
          <div className="col" style={{ gap: 14 }}>
            <div className="form-grid two">
              <div className="field">
                <label className="field-label" htmlFor="nn-key">
                  <Icon name="command" size={12} /> 快捷命令
                </label>
                <input
                  id="nn-key"
                  className={`input mono${touched && !keyOk ? ' invalid' : ''}`}
                  value={key}
                  autoFocus
                  autoComplete="off"
                  spellCheck={false}
                  placeholder="例如 mysqldump"
                  onChange={(e) => setKey(e.target.value)}
                  onKeyDown={onEnter}
                />
                <span className="field-hint">
                  顶栏输入它的前缀就能直达，支持中文；不能有空格，最长 64 字符。
                </span>
              </div>

              <div className="field">
                <label className="field-label" htmlFor="nn-desc">
                  <Icon name="tag" size={12} /> 描述（笔记标题）
                </label>
                <input
                  id="nn-desc"
                  className="input"
                  value={desc}
                  autoComplete="off"
                  placeholder="例如 MySQL 逻辑备份与恢复"
                  onChange={(e) => setDesc(e.target.value)}
                  onKeyDown={onEnter}
                />
                <span className="field-hint">留空则用快捷命令当标题，之后可在抽屉里改。</span>
              </div>
            </div>

            {takenBy ? (
              <div className="callout danger">
                <Icon name="alert" size={15} />
                <div>
                  {takenBy === 'live'
                    ? `「${trimmedKey}」已被占用。快捷命令是全局唯一的入口。`
                    : `「${trimmedKey}」还在回收站里，恢复它或换个名字。`}
                </div>
              </div>
            ) : rewritten ? (
              <div className="callout warn">
                <Icon name="folder" size={15} />
                <div>
                  目录名会把不支持的字符换成短横线：落盘为 <b className="mono">notes/{folder}/note.md</b>，
                  快捷命令本身仍按你输入的原样保存。
                </div>
              </div>
            ) : trimmedKey ? (
              <div className="callout info">
                <Icon name="folder" size={15} />
                <div>
                  将创建 <b className="mono">notes/{folder}/note.md</b>，保存时自动切块、分配块 ID 并写入索引。
                </div>
              </div>
            ) : null}

            <div className="field">
              <span className="field-label">
                <Icon name="layers" size={12} /> 正文模板
              </span>
              <div className="row wrap" style={{ gap: 6 }}>
                {TEMPLATES.map((t) => (
                  <button
                    key={t.id}
                    type="button"
                    className={`chip${tpl === t.id ? ' on' : ''}`}
                    onClick={() => pickTemplate(t)}
                  >
                    <Icon name={t.icon} size={12} />
                    {t.label}
                  </button>
                ))}
              </div>
              <span className="field-hint">{template.hint}</span>
            </div>

            <div className="field">
              <label className="field-label" htmlFor="nn-body">
                <Icon name="edit" size={12} /> 正文
                <span className="spacer" />
                <span className="mono" style={{ fontSize: 10.5, color: 'var(--faint)' }}>
                  {body.split('\n').length} 行 · {body.split('\n').filter((l) => /^#{1,6}\s/.test(l)).length} 个标题
                </span>
              </label>
              <textarea
                id="nn-body"
                className="textarea"
                value={body}
                rows={10}
                spellCheck={false}
                placeholder={'# 第一个知识块\n\n每个 # 标题都会被切成一个可独立检索的知识块。'}
                onChange={(e) => setBody(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
                    e.preventDefault()
                    void submit()
                  }
                }}
              />
              <span className="field-hint">
                正文不含首行「快捷命令 描述」，那一行由上面两个输入框拼出。Ctrl/⌘ + Enter 直接创建。
              </span>
            </div>

            <div className="callout">
              <Icon name="sparkles" size={15} />
              <div>
                创建后可以在右侧笔记抽屉里点「自动猜分类」补上领域 / 主题，让它出现在知识库目录树里。
              </div>
            </div>

            {error ? (
              <div className="callout danger">
                <Icon name="alert" size={15} />
                <div>{error}</div>
              </div>
            ) : null}
          </div>
        </div>

        <div className="modal-foot">
          <button
            className="btn ghost sm"
            onClick={() => {
              close()
              startNew()
            }}
            disabled={saving}
          >
            <Icon name="edit" size={13} /> 改为在编辑器里直接写
          </button>
          <span className="spacer" />
          <button className="btn ghost" onClick={close} disabled={saving}>
            取消
          </button>
          <button className="btn acid" onClick={() => void submit()} disabled={saving || !keyOk}>
            <Icon name={saving ? 'refresh' : 'check'} size={14} className={saving ? 'spin' : ''} />
            {saving ? '创建中…' : '创建笔记'}
          </button>
        </div>
      </div>
    </div>
  )
}