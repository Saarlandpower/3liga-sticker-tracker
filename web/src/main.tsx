import { render } from 'preact'
import { registerSW } from 'virtual:pwa-register'
import './styles.css'
import { App } from './ui/App'

render(<App />, document.getElementById('app')!)

// Service Worker: App + Texterkennung offline verfügbar; Updates werden beim nächsten Start aktiv.
registerSW({ immediate: true })
