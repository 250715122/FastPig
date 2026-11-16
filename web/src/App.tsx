import { useEffect, type CSSProperties } from 'react'
import { useStore } from './lib/store'
import { Icon, Logo } from './components/Icons'
import Login from './components/Login'
import TopBar from './components/TopBar'
import NoteList from './components/NoteList'
import MainPane from './components/MainPane'
import SidePane from './components/SidePane'
import StatusBar from './components/StatusBar'
import Toasts from './components/Toasts'
import CommandPalette from './components/CommandPalette'
import NoteDrawer from './components/NoteDrawer'
import Settings from './components/Settings'
import SyncPanel from './components/SyncPanel'
import PrivateDialog from './components/PrivateDialog'
import NewNoteDialog from './components/NewNoteDialog'
import BlockDialog from './components/BlockDialog'

function Background() {
  return (
    <div className="app-bg" aria-hidden="true">
      <div className="aurora" />
      <div className="aurora b" />
    </div>
  )
}

function Rail() {
  const sideTab = useStore((s) => s.sideTab)
  const showSide = useStore((s) => s.showSide)
  const showList = useStore((s) => s.showList)
  const setSideTab = useStore((s) => s.setSideTab)
  const toggleList = useStore((s) => s.toggleList)
  const toggleSide = useStore((s) => s.toggleSide)
  const setSettings = useStore((s) => s.setSettings)
  const setSyncOpen = useStore((s) => s.setSyncOpen)
  const setPalette = useStore((s) => s.setPalette)
  const setNewNoteOpen = useStore((s) => s.setNewNoteOpen)
  const theme = useStore((s) => s.theme)
  const setTheme = useStore((s) => s.setTheme)
  const indexStatus = useStore((s) => s.indexStatus)
  const mobile = useStore((s) => s.mobile)

  const tab = (t: 'toc' | 'tree' | 'search', icon: string, label: string) => (
    <button
      className={`rail-btn ${showSide && sideTab === t ? 'on' : ''}`}
      onClick={() => (showSide && sideTab === t && !mobile ? toggleSide() : setSideTab(t))}
      title={label}
      aria-label={label}
      aria-pressed={showSide && sideTab === t}
    >
      <Icon name={icon} size={19} />
      {t === 'search' && indexStatus?.rebuilding ? <span className="dot" /> : null}
    </button>
  )

  return (
    <nav className="rail" aria-label="主导航">
      <button className="rail-btn" onClick={toggleList} title="笔记列表 (Alt+L)" aria-label="笔记列表" aria-pressed={showList}>
        <Icon name="list" size={19} />
      </button>
      <button className="rail-btn" onClick={() => setNewNoteOpen(true)} title="新建笔记 (Ctrl+Alt+N)" aria-label="新建笔记">
        <Icon name="plus" size={19} />
      </button>
      <div className="rail-sep" />
      {tab('toc', 'toc', '大纲 (Alt+1)')}
      {tab('tree', 'tree', '知识库目录 (Alt+2)')}
      {tab('search', 'search', '混合检索 (Alt+3)')}
      <div className="rail-sep" />
      <button className="rail-btn" onClick={() => setPalette(true)} title="命令面板 (Ctrl+K)" aria-label="命令面板">
        <Icon name="command" size={19} />
      </button>
      <button className="rail-btn" onClick={() => setSyncOpen(true)} title="云同步" aria-label="云同步">
        <Icon name="cloudUp" size={19} />
      </button>
      <div className="rail-spacer" />
      <button
        className="rail-btn"
        onClick={() => setTheme(theme === 'ink' ? 'paper' : 'ink')}
        title={theme === 'ink' ? '切换到暖纸浅色' : '切换到深墨蓝深色'}
        aria-label="切换主题"
      >
        <Icon name={theme === 'ink' ? 'sun' : 'moon'} size={19} />
      </button>
      <button className="rail-btn" onClick={() => setSettings(true)} title="设置" aria-label="设置">
        <Icon name="settings" size={19} />
      </button>
    </nav>
  )
}

function BootScreen() {
  const bootError = useStore((s) => s.bootError)
  const boot = useStore((s) => s.boot)
  return (
    <div className="login-wrap">
      <div className="login-card" style={{ textAlign: 'center' }}>
        <div className="login-logo" style={{ justifyContent: 'center' }}>
          <Logo size={44} />
          <div>
            <h1>
              Fast<em>Pig</em>
            </h1>
            <p>Knowledge Workbench</p>
          </div>
        </div>
        {bootError ? (
          <>
            <div className="callout danger" style={{ textAlign: 'left' }}>
              <Icon name="alert" size={16} />
              <div>
                <b>装载失败</b>
                <div>{bootError}</div>
              </div>
            </div>
            <button className="btn primary block lg" style={{ marginTop: 16 }} onClick={() => void boot()}>
              <Icon name="refresh" size={15} /> 重试
            </button>
          </>
        ) : (
          <>
            <div className="skeleton" style={{ height: 12, width: '70%', margin: '0 auto 10px' }} />
            <div className="skeleton" style={{ height: 12, width: '48%', margin: '0 auto 18px' }} />
            <p className="lede" style={{ margin: 0 }}>
              正在装载笔记、分类词表与向量索引…
            </p>
          </>
        )}
      </div>
    </div>
  )
}

export default function App() {
  const authed = useStore((s) => s.authed)
  const booting = useStore((s) => s.booting)
  const boot = useStore((s) => s.boot)
  const showList = useStore((s) => s.showList)
  const showSide = useStore((s) => s.showSide)
  const mobile = useStore((s) => s.mobile)
  const setMobile = useStore((s) => s.setMobile)
  const listW = useStore((s) => s.listW)
  const sideW = useStore((s) => s.sideW)
  const settingsOpen = useStore((s) => s.settingsOpen)
  const paletteOpen = useStore((s) => s.paletteOpen)
  const syncOpen = useStore((s) => s.syncOpen)
  const privateTarget = useStore((s) => s.privateTarget)
  const newNoteOpen = useStore((s) => s.newNoteOpen)
  const blockDetail = useStore((s) => s.blockDetail)
  const drawerOpen = useStore((s) => s.drawerOpen)

  useEffect(() => {
    void boot()
  }, [boot])

  useEffect(() => {
    const mq = window.matchMedia('(max-width: 900px)')
    const apply = () => setMobile(mq.matches)
    apply()
    mq.addEventListener('change', apply)
    return () => mq.removeEventListener('change', apply)
  }, [setMobile])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const s = useStore.getState()
      const mod = e.ctrlKey || e.metaKey

      if (e.key === 'Escape') {
        if (s.paletteOpen) s.setPalette(false)
        else if (s.settingsOpen) s.setSettings(false)
        else if (s.syncOpen) s.setSyncOpen(false)
        else if (s.newNoteOpen) s.setNewNoteOpen(false)
        else if (s.privateTarget) s.setPrivateTarget(null)
        else if (s.blockDetail) s.setBlockDetail(null)
        else if (s.drawerOpen) s.setDrawer(false)
        else if (s.mobile && (s.showList || s.showSide)) {
          useStore.setState({ showList: false, showSide: false })
        }
        return
      }

      if (mod && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        s.setPalette(!s.paletteOpen)
        return
      }
      if (mod && e.key.toLowerCase() === 's') {
        e.preventDefault()
        if (s.authed) void s.save(true)
        return
      }
      if (mod && e.altKey && e.key.toLowerCase() === 'n') {
        e.preventDefault()
        s.setNewNoteOpen(true)
        return
      }
      if (mod && e.shiftKey && e.key.toLowerCase() === 'f') {
        e.preventDefault()
        s.setSideTab('search')
        window.setTimeout(() => document.getElementById('fp-search-input')?.focus(), 40)
        return
      }
      if (e.altKey && !mod) {
        const k = e.key.toLowerCase()
        if (k === 'p') {
          e.preventDefault()
          s.setViewMode(s.viewMode === 'preview' ? 'split' : 'preview')
        } else if (k === 'f') {
          e.preventDefault()
          s.setViewMode(s.viewMode === 'editor' ? 'split' : 'editor')
        } else if (k === 'l') {
          e.preventDefault()
          s.toggleList()
        } else if (k === 'r') {
          e.preventDefault()
          s.toggleSide()
        } else if (k === '1') {
          e.preventDefault()
          s.setSideTab('toc')
        } else if (k === '2') {
          e.preventDefault()
          s.setSideTab('tree')
        } else if (k === '3') {
          e.preventDefault()
          s.setSideTab('search')
        }
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  useEffect(() => {
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      if (useStore.getState().dirty) {
        e.preventDefault()
        e.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [])

  if (!authed) {
    return (
      <>
        <Background />
        <Login />
      </>
    )
  }

  if (booting) {
    return (
      <>
        <Background />
        <BootScreen />
      </>
    )
  }

  const workspaceClass = ['workspace', showList ? '' : 'no-list', showSide ? '' : 'no-side'].filter(Boolean).join(' ')

  return (
    <>
      <Background />
      <div className="app">
        <TopBar />
        <div
          className={workspaceClass}
          style={
            mobile
              ? undefined
              : ({ '--list-w': `${listW}px`, '--side-w': `${sideW}px` } as CSSProperties)
          }
        >
          <Rail />
          <NoteList />
          <MainPane />
          <SidePane />
        </div>
        <StatusBar />
        {mobile && (showList || showSide) ? (
          <div
            className="scrim-mobile"
            onClick={() => useStore.setState({ showList: false, showSide: false })}
            aria-hidden="true"
          />
        ) : null}
      </div>

      <Toasts />
      {paletteOpen ? <CommandPalette /> : null}
      {drawerOpen ? <NoteDrawer /> : null}
      {settingsOpen ? <Settings /> : null}
      {syncOpen ? <SyncPanel /> : null}
      {newNoteOpen ? <NewNoteDialog /> : null}
      {privateTarget ? <PrivateDialog /> : null}
      {blockDetail ? <BlockDialog /> : null}
    </>
  )
}