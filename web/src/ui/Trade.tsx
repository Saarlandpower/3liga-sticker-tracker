import jsQR from 'jsqr'
import QRCode from 'qrcode'
import { useEffect, useMemo, useState } from 'preact/hooks'
import { formatRanges, formatWithCounts } from '../domain/range'
import { decodeTrade, encodeTrade, matchTrade, pairs, shareText, type TradeList, tradeListFrom } from '../domain/trade'
import { actions, useStore } from '../data/store'
import { useCamera } from './camera'
import { Dialog, snack, TopBar } from './components'
import { Icon } from './icons'

export function Trade() {
  const stickers = useStore((s) => s.stickers)
  const list = useMemo(() => tradeListFrom(stickers), [stickers])
  const order = useMemo(() => [...stickers].sort((a, b) => a.position - b.position).map((s) => s.number), [stickers])
  const [showQr, setShowQr] = useState(false)
  const [scanning, setScanning] = useState(false)
  const [partner, setPartner] = useState<TradeList | null>(null)
  const [confirm, setConfirm] = useState(false)
  const match = partner ? matchTrade(list, partner, order) : null
  const dupTotal = [...list.duplicates.values()].reduce((a, b) => a + b, 0)

  const share = async () => {
    const text = shareText(list)
    try {
      if (navigator.share) await navigator.share({ title: 'Tauschliste', text })
      else { await navigator.clipboard.writeText(text); snack('Tauschliste in die Zwischenablage kopiert.') }
    } catch (e) {
      if ((e as DOMException).name !== 'AbortError') {
        try { await navigator.clipboard.writeText(text); snack('Tauschliste in die Zwischenablage kopiert.') } catch { snack('Teilen nicht möglich.') }
      }
    }
  }

  return (
    <div class="screen">
      <TopBar title="Tauschen" />
      <div class="card">
        <div style={{ fontWeight: 600, fontSize: '17px', marginBottom: '8px' }}>Meine Tauschliste</div>
        <Labeled color="var(--missing)" title={`Fehlen (${list.missing.length})`} text={formatRanges(list.missing) || 'keine 🎉'} />
        <Labeled color="var(--dup)" title={`Doppelt (${dupTotal})`} text={formatWithCounts([...list.duplicates]) || 'keine'} />
        <button class="btn block" style={{ marginTop: '12px' }} onClick={share}><Icon name="share" />Tauschliste teilen</button>
      </div>
      <div class="card" style={{ marginTop: '12px' }}>
        <div style={{ fontWeight: 600, fontSize: '17px' }}>QR-Tausch</div>
        <p class="small muted">Zeige deinen Code – dein Tauschpartner scannt ihn mit seiner App (Android oder Web) und sieht sofort passende Tauschpaare. Oder scanne seinen Code.</p>
        <div class="row">
          <button class="btn tonal grow" onClick={() => setShowQr(true)}><Icon name="qr" />Mein Code</button>
          <button class="btn tonal grow" onClick={() => setScanning(true)}><Icon name="qrScan" />Code scannen</button>
        </div>
      </div>
      {match && (
        <div class="card" style={{ marginTop: '12px' }}>
          <div style={{ fontWeight: 600, fontSize: '17px' }}>Passende Tauschpaare</div>
          {match.checklistMismatch && <p class="small" style={{ color: '#ff6b6b' }}>Hinweis: Dein Partner nutzt eine andere Checkliste – Nummern könnten abweichen.</p>}
          <Labeled color="var(--dup)" title={`Du gibst (${match.iGive.length})`} text={formatRanges(match.iGive) || 'nichts Passendes'} />
          <Labeled color="var(--owned)" title={`Du bekommst (${match.iGet.length})`} text={formatRanges(match.iGet) || 'nichts Passendes'} />
          {pairs(match).length > 0 && (
            <>
              <div style={{ fontWeight: 600, margin: '12px 0 4px' }}>1:1-Tausch ({pairs(match).length} Paare)</div>
              {pairs(match).slice(0, 30).map(([g, r]) => (
                <div class="row" key={g} style={{ padding: '2px 0' }}>
                  <b style={{ color: 'var(--dup)', width: '64px' }}>#{g}</b><span style={{ width: '20px' }}><Icon name="arrow" /></span><b style={{ color: 'var(--owned)' }}>#{r}</b>
                </div>
              ))}
            </>
          )}
          <div class="row" style={{ marginTop: '12px' }}>
            {pairs(match).length > 0 && <button class="btn" onClick={() => setConfirm(true)}>Tausch buchen</button>}
            <button class="btn outline" onClick={() => setPartner(null)}>Schließen</button>
          </div>
        </div>
      )}
      {showQr && <QrDialog payload={encodeTrade(list)} missing={list.missing.length} dups={dupTotal} onClose={() => setShowQr(false)} />}
      {scanning && <QrScanner onClose={() => setScanning(false)} onResult={(text) => {
        setScanning(false)
        try { setPartner(decodeTrade(text)) } catch (e) { snack((e as Error).message) }
      }} />}
      {confirm && match && (
        <Dialog title="Tausch buchen?" onClose={() => setConfirm(false)} actions={<>
          <button class="btn text" onClick={() => setConfirm(false)}>Abbrechen</button>
          <button class="btn text" onClick={() => {
            const p = pairs(match)
            p.forEach(([give, get]) => { actions.changeCount(give, -1); actions.changeCount(get, 1) })
            setConfirm(false); setPartner(null)
            snack(`${p.length} Tauschpaare gebucht.`)
          }}>Buchen</button>
        </>}>
          <p>{pairs(match).length} Paare: Deine Doppelten {formatRanges(pairs(match).map((p) => p[0]))} werden um je 1 verringert,
            die Sticker {formatRanges(pairs(match).map((p) => p[1]))} als erhalten gezählt.</p>
        </Dialog>
      )}
    </div>
  )
}

const Labeled = ({ color, title, text }: { color: string; title: string; text: string }) => (
  <div class="row" style={{ alignItems: 'flex-start', margin: '6px 0' }}>
    <span style={{ width: '10px', height: '10px', borderRadius: '2px', background: color, marginTop: '6px', flex: 'none', outline: '1px solid var(--outline)' }} />
    <div><div style={{ fontWeight: 600, fontSize: '14px' }}>{title}</div><div>{text}</div></div>
  </div>
)

function QrDialog({ payload, missing, dups, onClose }: { payload: string; missing: number; dups: number; onClose: () => void }) {
  const [url, setUrl] = useState<string | null>(null)
  const [err, setErr] = useState(false)
  useEffect(() => {
    QRCode.toDataURL(payload, { errorCorrectionLevel: 'L', margin: 2, width: 720 }).then(setUrl).catch(() => setErr(true))
  }, [payload])
  return (
    <Dialog title="Mein Tausch-Code" onClose={onClose} actions={<button class="btn text" onClick={onClose}>Schließen</button>}>
      {url && <img class="qr" src={url} alt="QR-Code mit fehlenden und doppelten Stickern" />}
      {err && <p>Die Liste ist zu lang für einen QR-Code. Bitte „Tauschliste teilen“ nutzen.</p>}
      <p class="small muted" style={{ textAlign: 'center' }}>{missing} fehlend · {dups} doppelt</p>
    </Dialog>
  )
}

function QrScanner({ onClose, onResult }: { onClose: () => void; onResult: (t: string) => void }) {
  const cam = useCamera(true)
  useEffect(() => {
    if (!cam.ready) return
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d', { willReadFrequently: true })!
    let stop = false
    const tick = () => {
      if (stop) return
      const v = cam.videoRef.current
      if (v && v.videoWidth) {
        const k = Math.min(1, 800 / Math.max(v.videoWidth, v.videoHeight))
        canvas.width = Math.round(v.videoWidth * k)
        canvas.height = Math.round(v.videoHeight * k)
        ctx.drawImage(v, 0, 0, canvas.width, canvas.height)
        const img = ctx.getImageData(0, 0, canvas.width, canvas.height)
        const code = jsQR(img.data, img.width, img.height, { inversionAttempts: 'dontInvert' })
        if (code?.data) { stop = true; navigator.vibrate?.(40); onResult(code.data); return }
      }
      setTimeout(tick, 200)
    }
    tick()
    return () => { stop = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cam.ready])
  return (
    <div class="scrim center" style={{ background: '#000' }}>
      <div style={{ width: '100%', maxWidth: '520px' }}>
        <div class="camera" style={{ maxHeight: '70vh' }}>
          <video ref={cam.videoRef} playsInline muted />
          {cam.error && <div class="overlay info">{cam.error.kind === 'denied' ? 'Kamerazugriff abgelehnt – bitte in den Browser-Einstellungen erlauben.' : cam.error.message}</div>}
          {!cam.error && <div class="hint" style={{ bottom: '16px' }}>Tausch-Code des Partners in die Mitte halten</div>}
        </div>
        <button class="btn outline block" style={{ marginTop: '12px', color: '#fff' }} onClick={onClose}>Abbrechen</button>
      </div>
    </div>
  )
}
