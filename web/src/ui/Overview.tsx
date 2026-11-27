import { useMemo, useState } from 'preact/hooks'
import { summarize } from '../domain/count'
import { CATEGORIES } from '../domain/model'
import { actions, useStore } from '../data/store'
import { countSummary, IconButton, ManualInputDialog, Progress, snack, TopBar } from './components'
import { Icon } from './icons'
import { navigate } from './router'

export function Overview() {
  const stickers = useStore((s) => s.stickers)
  const clubs = useStore((s) => s.clubs)
  const name = useStore((s) => s.checklistName)
  const [input, setInput] = useState(false)
  const known = useMemo(() => new Set(stickers.map((s) => s.number)), [stickers])
  const sum = summarize(stickers.map((s) => s.count))
  const groups = useMemo(() => {
    const by = new Map<number | null, number[]>()
    for (const s of stickers) by.set(s.clubId, [...(by.get(s.clubId) ?? []), s.count])
    const rows = clubs.filter((c) => by.has(c.id)).map((c) => ({ id: c.id, label: c.name, sum: summarize(by.get(c.id)!) }))
    if (by.has(null) && by.size > 1) rows.push({ id: -2, label: 'Ohne Verein', sum: summarize(by.get(null)!) })
    return rows
  }, [stickers, clubs])
  const cats = CATEGORIES.map((c) => ({ ...c, sum: summarize(stickers.filter((s) => s.category === c.id).map((s) => s.count)) }))
    .filter((c) => c.sum.total > 0)

  return (
    <div class="screen">
      <TopBar title="Übersicht"><IconButton icon="settings" label="Einstellungen" onClick={() => navigate('settings')} /></TopBar>
      <BackupReminder />
      <div class="card hero">
        {name && <div class="small" style={{ fontWeight: 600 }}>{name}</div>}
        <div class="row" style={{ alignItems: 'flex-end', marginTop: '6px' }}>
          <span class="big">{sum.owned}</span>
          <span style={{ fontSize: '22px', fontWeight: 600, paddingBottom: '4px' }}>von {sum.total}</span>
          <span class="grow" />
          <span style={{ fontSize: '30px', fontWeight: 700 }}>{sum.percent} %</span>
        </div>
        <Progress value={sum.progress} />
      </div>
      <div class="row" style={{ marginTop: '12px' }}>
        <div class="card stat grow" onClick={() => navigate('album', { filter: 'FEHLEND' })}>
          <div class="bar" style={{ background: 'var(--missing)', outline: '1px solid var(--outline)' }} />
          <div><div class="val">{sum.missing}</div><div>Fehlend</div></div>
        </div>
        <div class="card stat grow" onClick={() => navigate('album', { filter: 'DOPPELT' })}>
          <div class="bar" style={{ background: 'var(--dup)' }} />
          <div><div class="val" style={{ color: 'var(--dup)' }}>{sum.duplicates}</div><div>Doppelte</div></div>
        </div>
      </div>
      {groups.length > 0 && <div class="section-title">Fortschritt pro Verein</div>}
      {groups.map((g) => <GroupRow key={g.id} label={g.label} sum={g.sum} onClick={() => navigate('album', { club: String(g.id) })} />)}
      {cats.length > 1 && <div class="section-title">Nach Kategorie</div>}
      {cats.map((c) => <GroupRow key={c.id} label={c.label} sum={c.sum} />)}
      <button class="btn fab" onClick={() => setInput(true)}><Icon name="dialpad" />Nummern eingeben</button>
      {input && <ManualInputDialog known={known} onClose={() => setInput(false)} onConfirm={(n) => {
        setInput(false)
        snack(countSummary(actions.addNumbers(n)))
      }} />}
    </div>
  )
}

function GroupRow({ label, sum, onClick }: { label: string; sum: ReturnType<typeof summarize>; onClick?: () => void }) {
  return (
    <div style={{ padding: '8px 0', cursor: onClick ? 'pointer' : undefined }} onClick={onClick}>
      <div class="row" style={{ marginBottom: '6px' }}>
        <span class="grow" style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{label}</span>
        <span class="muted small">{sum.owned}/{sum.total}{sum.duplicates > 0 && ` · ${sum.duplicates} dopp.`}</span>
      </div>
      <Progress value={sum.progress} done={sum.missing === 0} />
    </div>
  )
}

/** Erinnerung an den Backup-Export – Safari kann Web-App-Daten nach längerer Nichtnutzung löschen. */
function BackupReminder() {
  const last = useStore((s) => s.lastBackupAt)
  const changed = useStore((s) => s.lastChangeAt)
  const days = 7 * 24 * 3600 * 1000
  if (!changed || (last && (last > changed || Date.now() - last < days))) return null
  return (
    <div class="banner" onClick={() => navigate('settings')}>
      <span class="grow small">
        {last ? 'Deine letzte Sicherung ist über eine Woche alt.' : 'Noch keine Sicherung vorhanden.'} Tipp hier und exportiere deinen Bestand – Browser können Web-App-Daten löschen.
      </span>
    </div>
  )
}
