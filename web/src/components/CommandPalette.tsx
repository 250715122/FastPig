import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { useStore } from '../lib/store'
import { Icon } from './Icons'
import { api } from '../lib/api'
import type { Suggestion } from '../lib/types'

interface Item {
  id: string
  group: string
  icon: string
  title: string
  sub: string
  kw: string
  run: () => void
}

export default function CommandPalette() {
  const setPalette = useStore((s) => s.setPalette)
  const notes = useStore((s) => s.notes)
  const detail = useStore((s) => s.detail)
  const theme = useStore((s) => s.theme)
  const config = useStore((s) => s.config)
  const indexStatus = useStore((s) => s.indexStatus)
  const unlockedKeys = useStore((s) => s.unlockedKeys)
  const [q, setQ] = useState('')
  const [cursor, setCursor] = useState(0)
  const [sugg, setSugg] = useState<Suggestion[]>([])
  const inputRef = useRef<HTMLInputElement>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const timer = useRef<number | null>(null)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    if (timer.current !== null) window.clearTimeout(timer.current)
    const text = q.trim()
    if (!text) {
      setSugg([])
      return
    }
    timer.current = window.setTimeout(() => {
      void api
        .suggest(text)
        .then(setSugg)
        .catch(() => setSugg([]))
    }, 140)
    return () => {
      if (timer.current !== null) window.clearTimeout(timer.current)
    }
  }, [q])

  const items = useMemo<Item[]>(() => {
    const s = useStore.getState()
    const close = () => setPalette(false)
    const act = (run: () => void) => () => {
      close()
      run()
    }
    const cur = s.detail?.meta
    const unlocked = cur ? s.unlockedKeys.includes(cur.key) : false

    const cmds: Item[] = [
      {
        id: 'new',
        group: '笔记',
        icon: 'plus',
        title: '新建笔记',
        sub: 'Ctrl+Alt+N · 选模板、填 key 与描述',
        kw: 'new create xinjian 新建',
        run: act(() => s.setNewNoteOpen(true)),
      },
      {
        id: 'compose',
        group: '笔记',
        icon: 'edit',
        title: '在编辑器里直接写',
        sub: '跳过表单，首行自己写「key 描述」',
        kw: 'compose blank editor 直接写 空白',
        run: act(s.startNew),
      },
      {
        id: 'save',
        group: '笔记',
        icon: 'save',
        title: '保存当前笔记',
        sub: 'Ctrl+S · 保存时自动补齐块 ID',
        kw: 'save baocun 保存',
        run: act(() => void s.save(true)),
      },
      {
        id: 'drawer',
        group: '笔记',
        icon: 'info',
        title: '笔记信息与分类',
        sub: cur ? `${cur.key} · 领域 / 主题 / 标签 / 别名` : '先打开一篇笔记',
        kw: 'meta info fenlei 分类 标签',
        run: act(() => s.setDrawer(true)),
      },
      {
        id: 'guess',
        group: '笔记',
        icon: 'sparkles',
        title: '猜一个分类',
        sub: '向量相似度 + 关键词匹配',
        kw: 'guess classify 分类 猜测',
        run: act(() => void s.guess()),
      },
      {
        id: 'copy',
        group: '笔记',
        icon: 'copy',
        title: '复制当前快捷命令',
        sub: cur?.key ?? '先打开一篇笔记',
        kw: 'copy key 复制 命令',
        run: act(() => {
          if (!cur) return
          void navigator.clipboard.writeText(cur.key).then(() => s.toast({ kind: 'success', text: `已复制 ${cur.key}`, ttl: 1500 }))
        }),
      },
      {
        id: 'close',
        group: '笔记',
        icon: 'x',
        title: '关闭当前笔记',
        sub: '回到空态',
        kw: 'close guanbi 关闭',
        run: act(s.closeNote),
      },
      {
        id: 'trash',
        group: '笔记',
        icon: 'trash',
        title: '打开回收站',
        sub: '已删除的笔记可恢复',
        kw: 'trash deleted huishouzhan 回收站',
        run: act(() => {
          s.setFilter({ showDeleted: true })
          if (!s.showList) s.toggleList()
        }),
      },
      {
        id: 'lock',
        group: '私密',
        icon: unlocked ? 'lock' : 'unlock',
        title: cur?.encrypted ? (unlocked ? '锁定当前笔记' : '解锁当前笔记') : '启用端到端加密',
        sub: cur?.encrypted ? '清除内存中的密钥' : 'AES-GCM · 明文不出浏览器',
        kw: 'lock unlock private encrypt 私密 加密 解锁',
        run: act(() => {
          if (!cur) return
          if (!cur.encrypted) s.setPrivateTarget({ mode: 'enable', key: cur.key })
          else if (unlocked) s.lockNote(cur.key)
          else s.setPrivateTarget({ mode: 'unlock', key: cur.key })
        }),
      },
      {
        id: 'master',
        group: '私密',
        icon: 'key',
        title: s.config?.masterPasswordSet ? '校验主密码' : '设置主密码',
        sub: '忘记笔记密码时唯一的恢复通道',
        kw: 'master password 主密码',
        run: act(() => s.setSettings(true)),
      },
      {
        id: 'toc',
        group: '视图',
        icon: 'toc',
        title: '大纲面板',
        sub: 'Alt+1',
        kw: 'toc outline dagang 大纲',
        run: act(() => s.setSideTab('toc')),
      },
      {
        id: 'tree',
        group: '视图',
        icon: 'tree',
        title: '知识库目录',
        sub: 'Alt+2 · 领域 / 主题 / 笔记 / 知识块',
        kw: 'tree directory mulu 目录',
        run: act(() => s.setSideTab('tree')),
      },
      {
        id: 'search',
        group: '视图',
        icon: 'search',
        title: '三通道混合检索',
        sub: 'Alt+3 · 精确 / 关键词 / 语义',
        kw: 'search jiansuo 检索 搜索',
        run: act(() => {
          s.setSideTab('search')
          window.setTimeout(() => document.getElementById('fp-search-input')?.focus(), 60)
        }),
      },
      {
        id: 'mode-editor',
        group: '视图',
        icon: 'edit',
        title: '只看编辑区',
        sub: 'Alt+F',
        kw: 'editor mode bianji 编辑',
        run: act(() => s.setViewMode('editor')),
      },
      {
        id: 'mode-split',
        group: '视图',
        icon: 'columns',
        title: '编辑 + 预览分栏',
        sub: '默认视图',
        kw: 'split mode fenlan 分栏',
        run: act(() => s.setViewMode('split')),
      },
      {
        id: 'mode-preview',
        group: '视图',
        icon: 'eye',
        title: '只看预览',
        sub: 'Alt+P',
        kw: 'preview mode yulan 预览',
        run: act(() => s.setViewMode('preview')),
      },
      {
        id: 'theme',
        group: '视图',
        icon: s.theme === 'ink' ? 'sun' : 'moon',
        title: s.theme === 'ink' ? '切换到暖纸浅色' : '切换到深墨蓝深色',
        sub: '主题只存在这台浏览器',
        kw: 'theme dark light zhuti 主题',
        run: act(() => s.setTheme(s.theme === 'ink' ? 'paper' : 'ink')),
      },
      {
        id: 'upload',
        group: '同步与索引',
        icon: 'cloudUp',
        title: '上传到坚果云',
        sub: '私密笔记上传的也是密文',
        kw: 'sync upload shangchuan 上传 同步',
        run: act(() => void s.doSync('upload')),
      },
      {
        id: 'pull',
        group: '同步与索引',
        icon: 'cloudDown',
        title: '从坚果云下载',
        sub: '按 version / deleted 判定，不覆盖更新的本地版本',
        kw: 'sync pull xiazai 下载 同步',
        run: act(() => void s.doSync('pull')),
      },
      {
        id: 'reindex',
        group: '同步与索引',
        icon: 'database',
        title: '重建向量索引',
        sub: s.indexStatus ? `当前 ${s.indexStatus.indexedCount} 块 · ${s.indexStatus.provider}` : '全量重建',
        kw: 'index rebuild chongjian 索引 重建',
        run: act(() => void s.rebuildIndex()),
      },
      {
        id: 'settings',
        group: '同步与索引',
        icon: 'settings',
        title: '打开设置',
        sub: '检索权重 / 分块 / 编辑器 / 网页端',
        kw: 'settings config shezhi 设置 配置',
        run: act(() => s.setSettings(true)),
      },
      {
        id: 'logout',
        group: '同步与索引',
        icon: 'logout',
        title: '退出登录',
        sub: '清除会话与内存中的密钥',
        kw: 'logout tuichu 退出',
        run: act(() => void s.logout()),
      },
    ]

    const needle = q.trim().toLowerCase()
    const matchedCmds = needle
      ? cmds.filter((c) => `${c.title} ${c.sub} ${c.kw}`.toLowerCase().includes(needle))
      : cmds

    const rank = (n: (typeof notes)[number]) => {
      if (!needle) return 0
      const key = n.key.toLowerCase()
      if (key.startsWith(needle)) return 0
      if (key.includes(needle)) return 1
      if ((n.desc ?? '').toLowerCase().includes(needle)) return 2
      if ((n.domain ?? '').toLowerCase().includes(needle) || (n.topic ?? '').toLowerCase().includes(needle)) return 3
      return -1
    }
    const localNotes = notes
      .map((n) => ({ n, r: rank(n) }))
      .filter((x) => x.r >= 0)
      .sort((a, b) => a.r - b.r || a.n.key.localeCompare(b.n.key))
      .slice(0, needle ? 8 : 5)
      .map((x) => x.n)

    const seen = new Set(localNotes.map((n) => n.key))
    const remote = sugg.filter((x) => !seen.has(x.key)).slice(0, 6)

    const noteItems: Item[] = [
      ...localNotes.map((n) => ({
        id: `note:${n.key}`,
        group: !needle ? '最近笔记' : n.key.toLowerCase().startsWith(needle) ? '笔记命中' : '相关笔记',
        icon: n.encrypted ? 'lock' : 'file',
        title: n.key,
        sub: `${n.desc || n.title}${n.domain ? ` · ${n.domain}/${n.topic ?? '—'}` : ''} · ${n.blockCount} 块`,
        kw: n.key,
        run: act(() => void s.openNote(n.key)),
      })),
      ...remote.map((x) => ({
        id: `sugg:${x.key}`,
        group: '拼音前缀直达',
        icon: x.encrypted ? 'lock' : 'zap',
        title: x.key,
        sub: `${x.desc} · 匹配 ${x.match} · ${x.blockCount} 块`,
        kw: x.key,
        run: act(() => void s.openNote(x.key)),
      })),
    ]

    const searchItem: Item[] = needle
      ? [
          {
            id: 'run-search',
            group: '检索',
            icon: 'search',
            title: `检索「${q.trim()}」`,
            sub: '三通道混合检索全部知识块',
            kw: needle,
            run: act(() => {
              s.setSideTab('search')
              void s.runSearch(q.trim())
            }),
          },
        ]
      : []

    // 前缀直达是 FastPig 的主路径：命中时压在「检索」之上，回车直接开笔记而不是发起搜索。
    const direct = noteItems.filter((it) => it.group !== '相关笔记')
    const related = noteItems.filter((it) => it.group === '相关笔记')
    return direct.length
      ? [...direct, ...searchItem, ...related, ...matchedCmds]
      : [...searchItem, ...related, ...matchedCmds]
  }, [q, notes, sugg, setPalette, detail, theme, config, indexStatus, unlockedKeys])

  useEffect(() => setCursor(0), [q])

  useEffect(() => {
    const el = listRef.current?.querySelector<HTMLElement>(`[data-idx="${cursor}"]`)
    el?.scrollIntoView({ block: 'nearest' })
  }, [cursor])

  const groups = useMemo(() => {
    const out: Array<{ name: string; items: Array<Item & { idx: number }> }> = []
    items.forEach((it, idx) => {
      const last = out[out.length - 1]
      if (last && last.name === it.group) last.items.push({ ...it, idx })
      else out.push({ name: it.group, items: [{ ...it, idx }] })
    })
    return out
  }, [items])

  const onKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setCursor((c) => (items.length ? (c + 1) % items.length : 0))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setCursor((c) => (items.length ? (c - 1 + items.length) % items.length : 0))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      items[cursor]?.run()
    } else if (e.key === 'Escape') {
      e.preventDefault()
      setPalette(false)
    }
  }

  return (
    <div className="overlay top" onClick={() => setPalette(false)}>
      <div className="palette" onClick={(e) => e.stopPropagation()} role="dialog" aria-label="命令面板">
        <div className="palette-input">
          <Icon name="command" size={17} style={{ color: 'var(--cyan)' }} />
          <input
            ref={inputRef}
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="敲命令、笔记 key 或拼音前缀…"
            aria-label="命令面板输入"
            autoComplete="off"
            spellCheck={false}
          />
          <span className="kbd">Esc</span>
        </div>
        <div className="palette-list" ref={listRef}>
          {items.length === 0 ? (
            <div className="preview-empty">没有匹配的命令或笔记</div>
          ) : (
            groups.map((g) => (
              <div key={g.name}>
                <div className="palette-group eyebrow">{g.name}</div>
                {g.items.map((it) => (
                  <button
                    key={it.id}
                    data-idx={it.idx}
                    className={`palette-item${cursor === it.idx ? ' cursor' : ''}`}
                    onMouseEnter={() => setCursor(it.idx)}
                    onClick={it.run}
                  >
                    <Icon name={it.icon} size={15} style={{ flex: 'none', color: 'var(--muted)' }} />
                    <span className="pi-main">
                      <span className="pi-title">
                        <span className="mono">{it.title}</span>
                      </span>
                      <span className="pi-sub">{it.sub}</span>
                    </span>
                    {cursor === it.idx ? <span className="kbd">↵</span> : null}
                  </button>
                ))}
              </div>
            ))
          )}
        </div>
        <div className="palette-foot">
          <span className="row" style={{ gap: 4 }}>
            <span className="kbd">↑</span>
            <span className="kbd">↓</span> 选择
          </span>
          <span className="row" style={{ gap: 4 }}>
            <span className="kbd">↵</span> 执行
          </span>
          <span className="spacer" />
          <span>{items.length} 项</span>
        </div>
      </div>
    </div>
  )
}