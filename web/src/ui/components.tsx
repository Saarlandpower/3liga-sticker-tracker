import type { ComponentChildren } from 'preact'
import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { parseManualInput } from '../domain/manualInput'
import { Icon, type IconName } from './icons'

export function TopBar({ title, onBack, children }: { title: string; onBack?: () => void; children?: ComponentChildren }) {
  return (
    <div class="topbar">
      {onBack && <button class="icon-btn" aria-label="Zurück" onClick={onBack}><Icon name="back" /></button>}
      <h1>{title}</h1>
      {children}
    </div>
  )
}

export const IconButton = ({ icon, label, onClick }: { icon: IconName; label: string; onClick: () => void }) => (
  <button class="icon-btn" aria-label={label} title={label} onClick={onClick}><Icon name={icon} /></button>
)

export const Progress = ({ value, done }: { value: number; done?: boolean }) => (
  <div class="progress" role="progressbar" aria-valuenow={Math.round(value * 100)} aria-valuemin={0} aria-valuemax={100}>
    <div style={{ width: `${Math.max(value > 0 ? 3 : 0, value * 100)}%`, background: done ? 'var(--bar-done)' : undefined }} />
  </div>
)

export function Dialog({ title, onClose, children, actions }: { title: string; onClose: () => void; children: ComponentChildren; actions: ComponentChildren }) {
  return (
    <div class="scrim center" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div class="dialog" role="dialog" aria-label={title}>
        <h2>{title}</h2>
        {children}
        <div class="actions">{actions}</div>
      </div>
    </div>
  )
}

export function Sheet({ onClose, children }: { onClose: () => void; children: ComponentChildren }) {
  return (
    <div class="scrim" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div class="sheet" role="dialog">{children}</div>
    </div>
  )
}

// --- Snackbar (global) ---
export interface SnackMsg { text: string; action?: { label: string; run: () => void } }
let pushSnack: (m: SnackMsg) => void = () => {}
export const snack = (m: SnackMsg | string) => pushSnack(typeof m === 'string' ? { text: m } : m)

export function SnackbarHost() {
  const [msg, setMsg] = useState<SnackMsg | null>(null)
  const timer = useRef<number>()
  useEffect(() => {
    pushSnack = (m) => {
      setMsg(m)
      clearTimeout(timer.current)
      timer.current = window.setTimeout(() => setMsg(null), m.action ? 4000 : 3000)
    }
  }, [])
  if (!msg) return null
  return (
    <div class="snackbar" role="status">
      <span class="grow">{msg.text}</span>
      {msg.action && <button onClick={() => { msg.action!.run(); setMsg(null) }}>{msg.action.label}</button>}
    </div>
  )
}

/** Nummernblock für mehrere Nummern: "12, 45, 45, 201", "45-48", "S1". */
export function ManualInputDialog({ known, onClose, onConfirm }: { known: Set<string>; onClose: () => void; onConfirm: (n: string[]) => void }) {
  const [text, setText] = useState('')
  const parsed = useMemo(() => parseManualInput(text, known), [text, known])
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', ', ', '0', '⌫', '-', 'S', 'Leeren']
  const press = (k: string) => {
    if (k === '⌫') setText((t) => t.slice(0, -1))
    else if (k === 'Leeren') setText('')
    else setText((t) => t + k)
  }
  return (
    <Dialog title="Nummern eingeben" onClose={onClose} actions={<>
      <button class="btn text" onClick={onClose}>Abbrechen</button>
      <button class="btn text" disabled={!parsed.numbers.length} onClick={() => onConfirm(parsed.numbers)}>Zählen</button>
    </>}>
      <textarea class="field" rows={2} style={{ fontSize: '22px' }} value={text} placeholder="z. B. 12, 45, 45, 201"
        onInput={(e) => setText((e.target as HTMLTextAreaElement).value.toUpperCase())} />
      <div class="small" style={{ color: parsed.invalid.length ? '#ff6b6b' : 'var(--text-2)' }}>
        {parsed.numbers.length ? `${parsed.numbers.length} Sticker werden gezählt` : 'Noch keine gültige Nummer'}
        {parsed.invalid.length > 0 && ` · unbekannt: ${parsed.invalid.slice(0, 6).join(', ')}`}
      </div>
      <div class="keypad">
        {keys.map((k) => <button key={k} onClick={() => press(k)} style={k === 'Leeren' ? { fontSize: '15px' } : undefined}>{k.trim()}</button>)}
      </div>
      <div class="small muted" style={{ marginTop: '8px' }}>Bereiche mit „-“ (45-48). Für Kürzel wie FW3 ins Feld tippen und die Tastatur nutzen.</div>
    </Dialog>
  )
}

export function countSummary(results: { newCount: number }[]): string {
  const neu = results.filter((r) => r.newCount === 1).length
  return `${results.length} gezählt: ${neu} neu, ${results.length - neu} doppelt`
}
