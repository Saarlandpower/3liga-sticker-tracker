import { useEffect } from 'preact/hooks'
import { actions, requestPersistence, useStore } from '../data/store'
import { Album } from './Album'
import { SnackbarHost, snack } from './components'
import { Icon, type IconName } from './icons'
import { Overview } from './Overview'
import { navigate, useRoute } from './router'
import { Scan } from './Scan'
import { Clubs, Settings } from './Settings'
import { Trade } from './Trade'

const TABS: [string, string, IconName][] = [
  ['overview', 'Übersicht', 'dashboard'],
  ['album', 'Album', 'grid'],
  ['scan', 'Scannen', 'camera'],
  ['trade', 'Tauschen', 'swap'],
]

export function App() {
  const route = useRoute()
  const settings = useStore((s) => s.settings)

  useEffect(() => {
    actions.ensureSeeded().catch((e) => snack(`Checkliste konnte nicht geladen werden: ${(e as Error).message}`))
    void requestPersistence()
  }, [])

  // Design anwenden (Hell/Dunkel/System + Farbschema)
  useEffect(() => {
    const mq = matchMedia('(prefers-color-scheme: dark)')
    const apply = () => {
      const dark = settings.themeMode === 'DUNKEL' || (settings.themeMode === 'SYSTEM' && mq.matches)
      document.documentElement.dataset.theme = dark ? 'dark' : 'light'
      document.documentElement.dataset.palette = settings.colorTheme === 'BSG' ? 'bsg' : 'standard'
      const bg = getComputedStyle(document.documentElement).getPropertyValue('--bg').trim()
      document.querySelector('meta[name="theme-color"]')?.setAttribute('content', bg || '#121417')
    }
    apply()
    mq.addEventListener('change', apply)
    return () => mq.removeEventListener('change', apply)
  }, [settings.themeMode, settings.colorTheme])

  const back = () => (history.length > 1 ? history.back() : navigate('overview', {}, true))
  const tab = route.name === 'settings' || route.name === 'clubs' ? null : route.name

  return (
    <>
      {route.name === 'album' ? <Album key={location.hash} params={route.params} />
        : route.name === 'scan' ? <Scan />
        : route.name === 'trade' ? <Trade />
        : route.name === 'settings' ? <Settings onBack={back} onClubs={() => navigate('clubs')} />
        : route.name === 'clubs' ? <Clubs onBack={back} />
        : <Overview />}
      <nav class="nav">
        {TABS.map(([name, label, icon]) => (
          <button key={name} class={tab === name ? 'on' : ''} aria-current={tab === name ? 'page' : undefined} onClick={() => navigate(name, {}, true)}>
            <span class="ind"><Icon name={icon} /></span>{label}
          </button>
        ))}
      </nav>
      <SnackbarHost />
    </>
  )
}
