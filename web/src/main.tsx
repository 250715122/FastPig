import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import 'katex/dist/katex.min.css'
import './styles/theme.css'
import './styles/layout.css'
import './styles/components.css'
import './styles/editor.css'
import './styles/preview.css'
import App from './App'
import { applyTheme, useStore, wireEvents } from './lib/store'

applyTheme(useStore.getState().theme)
wireEvents()

const boot = document.getElementById('boot')
if (boot) boot.remove()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)