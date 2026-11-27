import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { decide, pickBest } from '../domain/ocr'
import { playFeedback, unlockAudio } from '../data/feedback'
import { getOcrWorker, grabFrame, recognizeTokens } from '../data/ocrEngine'
import { actions, useStore } from '../data/store'
import { useCamera } from './camera'
import { IconButton, ManualInputDialog, TopBar } from './components'
import { Icon } from './icons'

interface Entry { id: number; number: string; newCount: number; manual: boolean }
const label = (e: { number: string; newCount: number }) =>
  e.newCount === 1 ? `#${e.number} – NEU` : `#${e.number} – doppelt (jetzt ${e.newCount}x)`

// Scan-Rahmen relativ zur Kameraansicht: mittig, etwa im Seitenverhältnis eines Stickers
function frameFor(w: number, h: number) {
  let fw = w * 0.66
  let fh = fw * 1.3
  if (fh > h * 0.72) { fh = h * 0.72; fw = fh / 1.3 }
  return { x: (w - fw) / 2 / w, y: (h - fh) / 2 / h - 0.06, w: fw / w, h: fh / h }
}

const BURST = [0, 180, 360] // ms – drei Bilder pro Auslösen
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

let session: Entry[] = [] // bleibt beim Tab-Wechsel erhalten
let nextId = 1

export function Scan() {
  const stickers = useStore((s) => s.stickers)
  const known = useMemo(() => new Set(stickers.map((s) => s.number)), [stickers])
  const cam = useCamera(true)
  const boxRef = useRef<HTMLDivElement>(null)
  const [size, setSize] = useState({ w: 300, h: 400 })
  const [busy, setBusy] = useState(false)
  const [ocrLoad, setOcrLoad] = useState<number | null>(0)
  const [overlay, setOverlay] = useState<{ text: string; kind: 'new' | 'dup' | 'info' } | null>(null)
  const [entries, setEntries] = useState<Entry[]>(session)
  const [input, setInput] = useState(false)
  const overlayTimer = useRef<number>()
  const update = (next: Entry[]) => { session = next; setEntries(next) }

  useEffect(() => {
    const el = boxRef.current
    if (!el) return
    const ro = new ResizeObserver(() => setSize({ w: el.clientWidth, h: el.clientHeight }))
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  // Texterkennung vorab laden (beim ersten Mal ~10 MB, danach aus dem Cache)
  useEffect(() => {
    getOcrWorker((p, s) => { if (s.includes('loading')) setOcrLoad(p) }).then(() => setOcrLoad(null)).catch(() => setOcrLoad(null))
  }, [])

  const show = (o: { text: string; kind: 'new' | 'dup' | 'info' }, ms = 1800) => {
    setOverlay(o)
    clearTimeout(overlayTimer.current)
    overlayTimer.current = window.setTimeout(() => setOverlay(null), ms)
  }

  const count = (number: string, manual: boolean) => {
    const n = actions.changeCount(number, 1)
    if (n === null) return
    const e = { id: nextId++, number, newCount: n, manual }
    update([e, ...session])
    playFeedback(n === 1)
    show({ text: label(e), kind: n === 1 ? 'new' : 'dup' })
  }

  const trigger = async () => {
    unlockAudio()
    const video = cam.videoRef.current
    if (busy || !video || !cam.ready) return
    setBusy(true)
    try {
      const frame = frameFor(size.w, size.h)
      const canvases: HTMLCanvasElement[] = []
      for (let i = 0; i < BURST.length; i++) {
        if (i > 0) await sleep(BURST[i] - BURST[i - 1])
        canvases.push(grabFrame(video, frame, size))
      }
      const samples: (string | null)[] = []
      for (const c of canvases) {
        const tokens = await recognizeTokens(c)
        samples.push(pickBest(tokens, { left: 0, top: 0, right: c.width, bottom: c.height }, known)?.number ?? null)
      }
      const r = decide(samples)
      if (r.kind === 'sure') count(r.number, false)
      else if (r.kind === 'unsure') show({ text: r.best ? `Nicht eindeutig (#${r.best}?) – ruhig halten und nochmal auslösen` : 'Nicht eindeutig – nochmal auslösen', kind: 'info' }, 2500)
      else show({ text: 'Keine Nummer erkannt – Rückseite mit Nummer in den Rahmen halten', kind: 'info' }, 2500)
    } catch (e) {
      show({ text: `Texterkennung fehlgeschlagen: ${(e as Error).message}`, kind: 'info' }, 3500)
    } finally {
      setBusy(false)
    }
  }

  const undo = (e: Entry) => {
    actions.changeCount(e.number, -1)
    update(session.filter((x) => x.id !== e.id))
  }

  const f = frameFor(size.w, size.h)
  const neu = entries.filter((e) => e.newCount === 1).length

  return (
    <div class="screen">
      <TopBar title="Scannen">
        <IconButton icon="dialpad" label="Nummern manuell eingeben" onClick={() => setInput(true)} />
      </TopBar>
      <div class="camera" ref={boxRef} onClick={(e) => { if (e.target === e.currentTarget || (e.target as HTMLElement).tagName === 'VIDEO') void trigger() }}>
        <video ref={cam.videoRef} playsInline muted />
        {cam.error ? (
          <CameraProblem kind={cam.error.kind} message={cam.error.message} onRetry={cam.retry} onManual={() => setInput(true)} />
        ) : (
          <>
            <div class={`frame ${busy ? 'busy' : ''}`} style={{ left: `${f.x * 100}%`, top: `${f.y * 100}%`, width: `${f.w * 100}%`, height: `${f.h * 100}%` }} />
            <div class="hint">
              {ocrLoad !== null ? `Texterkennung wird geladen … ${Math.round(ocrLoad * 100)} %` : busy ? 'Stillhalten …' : 'Rückseite in den Rahmen halten und auslösen'}
            </div>
            <button class="shutter" aria-label="Auslösen – Sticker zählen" disabled={busy || !cam.ready} onClick={(e) => { e.stopPropagation(); void trigger() }}>
              {busy && <span class="spinner" style={{ display: 'block', margin: 'auto' }} />}
            </button>
            {cam.torch.supported && (
              <button class="cam-btn" style={{ right: '10px' }} aria-label={cam.torch.on ? 'Taschenlampe aus' : 'Taschenlampe an'}
                onClick={(e) => { e.stopPropagation(); void cam.toggleTorch() }}><Icon name="flash" /></button>
            )}
          </>
        )}
        {overlay && <div class={`overlay ${overlay.kind}`}>{overlay.text}</div>}
      </div>

      <div class="row" style={{ marginTop: '12px' }}>
        <div class="grow" style={{ fontWeight: 600 }}>
          {entries.length ? `Diese Sitzung: ${entries.length} Sticker · ${neu} neu · ${entries.length - neu} doppelt` : 'Scan-Sitzung'}
        </div>
        {entries.length > 0 && <button class="btn text" onClick={() => update([])}>Leeren</button>}
      </div>
      {!entries.length && (
        <p class="muted small">Sticker-Rückseite in den Rahmen halten und auslösen (Knopf oder Tipp ins Bild). Gezählt wird nur, wenn die Nummer auf mehreren Bildern eindeutig erkannt wurde.</p>
      )}
      {entries.map((e) => (
        <div key={e.id} class="list-item" style={{ cursor: 'default' }}>
          <span class="dot" style={{ background: e.newCount === 1 ? 'var(--owned)' : 'var(--dup)' }} />
          <div class="grow">{label(e)}{e.manual && <div class="small muted">manuell eingegeben</div>}</div>
          <IconButton icon="undo" label="Rückgängig" onClick={() => undo(e)} />
        </div>
      ))}
      {input && <ManualInputDialog known={known} onClose={() => setInput(false)} onConfirm={(n) => { setInput(false); unlockAudio(); n.forEach((x) => count(x, true)) }} />}
    </div>
  )
}

function CameraProblem({ kind, message, onRetry, onManual }: { kind: string; message: string; onRetry: () => void; onManual: () => void }) {
  const ios = /iPhone|iPad|iPod/.test(navigator.userAgent)
  return (
    <div style={{ position: 'absolute', inset: 0, background: 'var(--surface)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '24px', textAlign: 'center', gap: '10px' }}>
      <div style={{ fontWeight: 600, fontSize: '17px' }}>{kind === 'denied' ? 'Kamerazugriff benötigt' : 'Kamera nicht verfügbar'}</div>
      <div class="small">
        {kind === 'denied'
          ? ios
            ? 'Du hast den Kamerazugriff abgelehnt. Erlaube ihn unter Einstellungen → Safari → Kamera (bzw. über „aA“ in der Adressleiste → Website-Einstellungen) und tippe dann auf „Erneut versuchen“. Die Bilder werden nur auf dem Gerät ausgewertet.'
            : 'Du hast den Kamerazugriff abgelehnt. Erlaube ihn über das Schloss-Symbol in der Adressleiste → Berechtigungen → Kamera und tippe dann auf „Erneut versuchen“. Die Bilder werden nur auf dem Gerät ausgewertet.'
          : message}
      </div>
      <button class="btn" onClick={onRetry}>Erneut versuchen</button>
      <button class="btn outline" onClick={onManual}>Stattdessen manuell eingeben</button>
    </div>
  )
}
