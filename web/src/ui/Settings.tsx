import type { ComponentChildren } from 'preact'
import { useRef, useState } from 'preact/hooks'
import { backupToDomain, type BackupFile, decodeBackup, encodeBackup } from '../domain/backup'
import { type MergeResult, mergeChecklist, type ParsedChecklist, parseChecklist, placeholders } from '../domain/checklist'
import { parseManualInput } from '../domain/manualInput'
import { formatRanges } from '../domain/range'
import { type ColorTheme, actions, getState, loadBundledChecklist, type ThemeMode, useStore } from '../data/store'
import { Dialog, snack, TopBar } from './components'

const readFile = (f: File) => f.text()

export function Settings({ onBack, onClubs }: { onBack: () => void; onClubs: () => void }) {
  const s = useStore((x) => x)
  const [preview, setPreview] = useState<{ title: string; parsed: ParsedChecklist; result: MergeResult } | null>(null)
  const [restore, setRestore] = useState<BackupFile | null>(null)
  const [placeholder, setPlaceholder] = useState<string | null>(null)
  const [reset, setReset] = useState(false)
  const checklistInput = useRef<HTMLInputElement>(null)
  const backupInput = useRef<HTMLInputElement>(null)

  const showPreview = (title: string, parsed: ParsedChecklist) =>
    setPreview({ title, parsed, result: mergeChecklist(getState().stickers, getState().clubs, parsed) })

  const exportBackup = async () => {
    const stamp = new Date().toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short' })
    const text = encodeBackup(stamp, s.checklistName, s.clubs, s.stickers)
    const name = `sticker-tracker-backup-${new Date().toISOString().slice(0, 10)}.json`
    const file = new File([text], name, { type: 'application/json' })
    try {
      // iPhone: Teilen-Menü → „In Dateien sichern“
      if (navigator.canShare?.({ files: [file] })) await navigator.share({ files: [file], title: 'Sticker-Tracker Sicherung' })
      else {
        const a = document.createElement('a')
        a.href = URL.createObjectURL(file)
        a.download = name
        a.click()
        setTimeout(() => URL.revokeObjectURL(a.href), 5000)
      }
      actions.markBackedUp()
      snack(`Bestand exportiert (${s.stickers.length} Sticker).`)
    } catch (e) {
      if ((e as DOMException).name !== 'AbortError') snack(`Export fehlgeschlagen: ${(e as Error).message}`)
    }
  }

  return (
    <div class="screen">
      <TopBar title="Einstellungen" onBack={onBack} />
      <div class="label">Checkliste</div>
      <Item title={s.checklistName || 'Checkliste'} sub={`${s.stickers.length} Sticker`} />
      <Item title="Checkliste importieren" sub="JSON- oder CSV-Datei – vorhandene Anzahlen bleiben erhalten" onClick={() => checklistInput.current?.click()} />
      <Item title="Platzhalter-Liste erzeugen" sub="Nummern 1 bis N, wenn die echte Liste noch fehlt" onClick={() => setPlaceholder('423')} />
      <Item title="Mitgelieferte Checkliste laden" sub="Checkliste 2026/27 neu einlesen" onClick={async () => {
        try { const p = await loadBundledChecklist(); showPreview(p.name ?? 'Mitgelieferte Checkliste', p) } catch (e) { snack((e as Error).message) }
      }} />
      <Item title="Vereine bearbeiten" sub="Namen ändern und Nummern zuordnen" onClick={onClubs} />
      <input ref={checklistInput} type="file" accept=".json,.csv,.txt,application/json,text/csv,text/plain" hidden onChange={async (e) => {
        const f = (e.target as HTMLInputElement).files?.[0]
        ;(e.target as HTMLInputElement).value = ''
        if (!f) return
        try { const p = parseChecklist(await readFile(f), f.name); showPreview(p.name ?? f.name.replace(/\.[^.]+$/, ''), p) } catch (err) { snack(`Import fehlgeschlagen: ${(err as Error).message}`) }
      }} />

      <div class="label">Datensicherung</div>
      <Item title="Bestand exportieren" sub={s.lastBackupAt ? `Zuletzt: ${new Date(s.lastBackupAt).toLocaleDateString('de-DE')}` : 'Noch nie gesichert'} onClick={exportBackup} />
      <Item title="Bestand importieren" sub="Stand aus einer Export-Datei wiederherstellen (auch von der Android-App)" onClick={() => backupInput.current?.click()} />
      <p class="small muted">Die Daten liegen nur in diesem Browser. Safari kann Daten von Web-Apps löschen, die einige Wochen nicht geöffnet wurden – exportiere deinen Bestand daher regelmäßig.</p>
      <input ref={backupInput} type="file" accept=".json,application/json" hidden onChange={async (e) => {
        const f = (e.target as HTMLInputElement).files?.[0]
        ;(e.target as HTMLInputElement).value = ''
        if (!f) return
        try { setRestore(decodeBackup(await readFile(f))) } catch (err) { snack(`Import fehlgeschlagen: ${(err as Error).message}`) }
      }} />

      <div class="label">Scannen</div>
      <Toggle title="Ton bei Treffer" on={s.settings.sound} set={(v) => actions.updateSettings({ sound: v })} />
      <Toggle title="Vibration bei Treffer" sub="nicht auf iPhones verfügbar" on={s.settings.vibration} set={(v) => actions.updateSettings({ vibration: v })} />

      <div class="label">Darstellung</div>
      <div class="chips">
        {([['SYSTEM', 'System'], ['DUNKEL', 'Dunkel'], ['HELL', 'Hell']] as [ThemeMode, string][]).map(([m, l]) =>
          <button key={m} class={`chip ${s.settings.themeMode === m ? 'on' : ''}`} onClick={() => actions.updateSettings({ themeMode: m })}>{l}</button>)}
      </div>
      <div class="small muted" style={{ marginTop: '8px' }}>Farbschema</div>
      <div class="chips">
        {([['STANDARD', 'Standard', ['#2e7d32', '#ef6c00', '#121417']], ['BSG', 'Blau-Schwarz-Gelb', ['#0057b8', '#000', '#ffd400']]] as [ColorTheme, string, string[]][]).map(([t, l, cols]) => (
          <button key={t} class={`chip ${s.settings.colorTheme === t ? 'on' : ''}`} onClick={() => actions.updateSettings({ colorTheme: t })}>
            {cols.map((c) => <span key={c} style={{ display: 'inline-block', width: '12px', height: '12px', borderRadius: '50%', background: c, border: '1px solid rgba(255,255,255,.6)', marginRight: '-3px', verticalAlign: '-1px' }} />)}
            <span style={{ marginLeft: '10px' }}>{l}</span>
          </button>
        ))}
      </div>

      <div class="label">Auf dem Home-Bildschirm</div>
      <p class="small muted">iPhone: In Safari auf „Teilen“ tippen → „Zum Home-Bildschirm“. Android: Browser-Menü → „App installieren“. Danach startet der Sticker-Tracker wie eine App und funktioniert auch offline.</p>

      <div class="label">Gefahrenzone</div>
      <Item title="Alle Anzahlen zurücksetzen" sub="Setzt jeden Sticker auf „fehlt“" onClick={() => setReset(true)} />

      <p class="small muted" style={{ marginTop: '24px' }}>
        Version {__APP_VERSION__} · alle Daten bleiben auf diesem Gerät · <a href="datenschutz.html" style={{ color: 'var(--primary)' }}>Datenschutz</a><br /><br />
        Inoffizielle Fan-App. Keine Verbindung zu Panini, dem DFB oder den Vereinen. Alle Marken- und Vereinsnamen gehören ihren jeweiligen Inhabern.
      </p>

      {preview && (
        <Dialog title="Checkliste übernehmen?" onClose={() => setPreview(null)} actions={<>
          <button class="btn text" onClick={() => setPreview(null)}>Abbrechen</button>
          <button class="btn text" onClick={() => {
            actions.applyMerge(preview.result, preview.title)
            snack(`Checkliste übernommen: ${preview.result.stickers.length} Sticker, ${preview.result.keptCounts} Bestände erhalten.`)
            setPreview(null)
          }}>Übernehmen</button>
        </>}>
          <b>{preview.title}</b>
          <ul style={{ paddingLeft: '20px' }}>
            <li>{preview.result.stickers.length} Sticker</li>
            <li>{preview.result.clubs.length} Vereine{preview.parsed.clubs.length === 0 && !preview.parsed.stickers.some((x) => x.clubName) ? ' (unverändert)' : ''}</li>
            <li>{preview.result.keptCounts} vorhandene Anzahlen bleiben erhalten</li>
          </ul>
          {preview.result.droppedWithCount.length > 0 && (
            <p style={{ color: '#ff6b6b' }}>Achtung: {preview.result.droppedWithCount.length} Sticker mit Bestand sind nicht in der neuen Liste und werden entfernt: {preview.result.droppedWithCount.slice(0, 15).join(', ')}</p>
          )}
          {preview.parsed.warnings.slice(0, 5).map((w) => <div key={w} class="small muted">– {w}</div>)}
        </Dialog>
      )}
      {restore && (
        <Dialog title="Bestand wiederherstellen?" onClose={() => setRestore(null)} actions={<>
          <button class="btn text" onClick={() => setRestore(null)}>Abbrechen</button>
          <button class="btn text" onClick={() => {
            const { clubs, stickers } = backupToDomain(restore)
            actions.replaceAll(clubs, stickers, restore.checklistName)
            snack(`Bestand wiederhergestellt (${stickers.length} Sticker).`)
            setRestore(null)
          }}>Ersetzen</button>
        </>}>
          <p>Die Sicherung vom {restore.exportedAt || 'unbekannten Datum'} enthält {restore.stickers.length} Sticker
            ({restore.stickers.filter((x) => x.count > 0).length} vorhanden). Der aktuelle Stand wird vollständig ersetzt.</p>
        </Dialog>
      )}
      {placeholder !== null && (
        <Dialog title="Platzhalter-Liste" onClose={() => setPlaceholder(null)} actions={<>
          <button class="btn text" onClick={() => setPlaceholder(null)}>Abbrechen</button>
          <button class="btn text" onClick={() => {
            const n = parseInt(placeholder, 10)
            if (!(n >= 1 && n <= 5000)) { snack('Bitte eine Anzahl zwischen 1 und 5000 angeben.'); return }
            setPlaceholder(null)
            showPreview(`Platzhalter 1–${n}`, { name: `Platzhalter 1–${n}`, clubs: [], stickers: placeholders(n), warnings: [] })
          }}>Weiter</button>
        </>}>
          <p>Wie viele Sticker hat das Album? Es werden die Nummern 1 bis N angelegt. Vorhandene Anzahlen bleiben erhalten.</p>
          <input class="field" inputMode="numeric" value={placeholder} onInput={(e) => setPlaceholder((e.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 4))} />
        </Dialog>
      )}
      {reset && (
        <Dialog title="Alle Anzahlen zurücksetzen?" onClose={() => setReset(false)} actions={<>
          <button class="btn text" onClick={() => setReset(false)}>Abbrechen</button>
          <button class="btn text danger" onClick={() => { actions.resetCounts(); setReset(false); snack('Alle Anzahlen wurden auf 0 gesetzt.') }}>Zurücksetzen</button>
        </>}>
          <p>Dein kompletter Bestand wird auf 0 gesetzt. Tipp: Vorher den Bestand exportieren.</p>
        </Dialog>
      )}
    </div>
  )
}

const Item = ({ title, sub, onClick }: { title: string; sub?: string; onClick?: () => void }) => (
  <div class="list-item" style={{ cursor: onClick ? 'pointer' : 'default' }} onClick={onClick} role={onClick ? 'button' : undefined}>
    <div class="grow"><div>{title}</div>{sub && <div class="small muted">{sub}</div>}</div>
  </div>
)

const Toggle = ({ title, sub, on, set }: { title: string; sub?: string; on: boolean; set: (v: boolean) => void }) => (
  <label class="list-item">
    <div class="grow"><div>{title}</div>{sub && <div class="small muted">{sub}</div>}</div>
    <input type="checkbox" class="switch" checked={on} onChange={(e) => set((e.target as HTMLInputElement).checked)} />
  </label>
)

export function Clubs({ onBack }: { onBack: () => void }) {
  const clubs = useStore((s) => s.clubs)
  const stickers = useStore((s) => s.stickers)
  const [edit, setEdit] = useState<{ id: number; name: string; numbers: string; original: string } | null>(null)
  const numbersOf = (id: number) => stickers.filter((s) => s.clubId === id).map((s) => s.number)
  return (
    <div class="screen">
      <TopBar title="Vereine" onBack={onBack} />
      <p class="small muted">Tippe auf einen Verein, um den Namen zu ändern oder ihm Stickernummern zuzuordnen (z. B. „21-38, S3“).</p>
      {clubs.map((c) => {
        const nums = numbersOf(c.id)
        return (
          <div key={c.id} class="list-item" onClick={() => { const r = formatRanges(nums); setEdit({ id: c.id, name: c.name, numbers: r, original: r }) }}>
            <div class="grow"><div>{c.name}</div><div class="small muted">{nums.length ? `${nums.length} Sticker: ${formatRanges(nums)}` : 'Keine Nummern zugeordnet'}</div></div>
          </div>
        )
      })}
      {edit && (
        <Dialog title="Verein bearbeiten" onClose={() => setEdit(null)} actions={<>
          <button class="btn text" onClick={() => setEdit(null)}>Abbrechen</button>
          <button class="btn text" onClick={() => {
            if (edit.name.trim()) actions.renameClub(edit.id, edit.name)
            if (edit.numbers.trim() !== edit.original) {
              const known = new Set(stickers.map((s) => s.number))
              const p = parseManualInput(edit.numbers, known)
              const wanted = [...new Set(p.numbers)]
              actions.assignClub(numbersOf(edit.id).filter((n) => !wanted.includes(n)), null)
              actions.assignClub(wanted, edit.id)
              snack(`${wanted.length} Nummern zugeordnet${p.invalid.length ? ` – unbekannt: ${p.invalid.slice(0, 8).join(', ')}` : ''}`)
            }
            setEdit(null)
          }}>Speichern</button>
        </>}>
          <label class="field-label">Name</label>
          <input class="field" value={edit.name} onInput={(e) => setEdit({ ...edit, name: (e.target as HTMLInputElement).value })} />
          <label class="field-label">Stickernummern</label>
          <input class="field" value={edit.numbers} placeholder="z. B. 21-38, S3" onInput={(e) => setEdit({ ...edit, numbers: (e.target as HTMLInputElement).value })} />
        </Dialog>
      )}
    </div>
  )
}

export type { ComponentChildren }
