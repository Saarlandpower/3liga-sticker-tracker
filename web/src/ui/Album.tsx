import { useMemo, useRef, useState } from 'preact/hooks'
import { duplicates, status, summarize } from '../domain/count'
import { type Category, CATEGORIES, categoryLabel, type Sticker } from '../domain/model'
import { actions, useStore } from '../data/store'
import { countSummary, IconButton, ManualInputDialog, Sheet, snack, TopBar } from './components'
import { Icon } from './icons'
import { navigate } from './router'

type Filter = 'ALLE' | 'FEHLEND' | 'DOPPELT' | 'VEREIN' | 'KATEGORIE'
const TABS: [Filter, string][] = [['ALLE', 'Alle'], ['FEHLEND', 'Fehlend'], ['DOPPELT', 'Doppelte'], ['VEREIN', 'Nach Verein'], ['KATEGORIE', 'Kategorie']]
const CLUB_NONE = -2

export function Album({ params }: { params: Record<string, string> }) {
  const stickers = useStore((s) => s.stickers)
  const clubs = useStore((s) => s.clubs)
  const [filter, setFilter] = useState<Filter>((TABS.find((t) => t[0] === params.filter)?.[0]) ?? 'ALLE')
  const [clubId, setClubId] = useState<number | null>(params.club ? Number(params.club) : null)
  const [category, setCategory] = useState<Category | null>(null)
  const [search, setSearch] = useState('')
  const [detail, setDetail] = useState<string | null>(null)
  const [input, setInput] = useState(false)
  const clubName = (id: number | null) => (id === null ? null : clubs.find((c) => c.id === id)?.name ?? null)

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    const qn = q.replace(/^#/, '').toUpperCase()
    return stickers.filter((s) =>
      (clubId === null || (clubId === CLUB_NONE ? s.clubId === null : s.clubId === clubId)) &&
      (category === null || s.category === category) &&
      (filter === 'FEHLEND' ? s.count <= 0 : filter === 'DOPPELT' ? s.count > 1 : true) &&
      (!q || s.number === qn || s.number.startsWith(qn) || s.name.toLowerCase().includes(q) || (clubName(s.clubId)?.toLowerCase().includes(q) ?? false)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stickers, clubs, filter, clubId, category, search])

  const sections = useMemo(() => {
    const sec = (key: string, title: string, list: Sticker[]) => {
      const s = summarize(list.map((x) => x.count))
      return { key, title, sub: `${s.owned}/${s.total}`, list }
    }
    if (filter === 'VEREIN') {
      const out = clubs.map((c) => sec(`c${c.id}`, c.name, filtered.filter((s) => s.clubId === c.id))).filter((x) => x.list.length)
      const none = filtered.filter((s) => s.clubId === null)
      return none.length ? [...out, sec('none', 'Ohne Verein', none)] : out
    }
    if (filter === 'KATEGORIE')
      return CATEGORIES.map((c) => sec(c.id, c.label, filtered.filter((s) => s.category === c.id))).filter((x) => x.list.length)
    return [{ key: 'all', title: '', sub: '', list: filtered }]
  }, [filtered, filter, clubs])

  const tap = (s: Sticker) => {
    const n = actions.changeCount(s.number, 1)
    if (n === null) return
    snack({ text: n === 1 ? `#${s.number} – NEU` : `#${s.number} – doppelt (jetzt ${n}x)`, action: { label: 'Rückgängig', run: () => actions.changeCount(s.number, -1) } })
  }
  const detailSticker = detail ? stickers.find((s) => s.number === detail) : undefined

  return (
    <div class="screen">
      <TopBar title="Album"><IconButton icon="settings" label="Einstellungen" onClick={() => navigate('settings')} /></TopBar>
      <input class="search" type="search" placeholder="Nummer oder Spielername suchen" value={search}
        onInput={(e) => setSearch((e.target as HTMLInputElement).value)} />
      <div class="tabs" style={{ marginTop: '6px' }}>
        {TABS.map(([f, l]) => <button key={f} class={`tab ${filter === f ? 'on' : ''}`} onClick={() => setFilter(f)}>{l}</button>)}
      </div>
      <div class="chips">
        <select class={`chip ${clubId !== null ? 'on' : ''}`} value={clubId ?? ''} aria-label="Verein"
          onChange={(e) => { const v = (e.target as HTMLSelectElement).value; setClubId(v === '' ? null : Number(v)) }}>
          <option value="">Alle Vereine</option>
          {clubs.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          <option value={CLUB_NONE}>Ohne Verein</option>
        </select>
        <select class={`chip ${category ? 'on' : ''}`} value={category ?? ''} aria-label="Kategorie"
          onChange={(e) => setCategory(((e.target as HTMLSelectElement).value || null) as Category | null)}>
          <option value="">Alle Kategorien</option>
          {CATEGORIES.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
        </select>
        <span class="muted small" style={{ alignSelf: 'center' }}>{filtered.length} Sticker</span>
      </div>
      {!filtered.length ? (
        <p class="muted" style={{ textAlign: 'center', marginTop: '40px' }}>
          {search ? `Kein Sticker passt zu „${search}“.` : filter === 'FEHLEND' ? 'Nichts fehlt – Album komplett! 🎉' : filter === 'DOPPELT' ? 'Noch keine Doppelten.' : 'Keine Sticker.'}
        </p>
      ) : (
        <div class="grid" style={{ marginTop: '6px' }}>
          {sections.map((sec) => [
            sec.title && <div key={`h${sec.key}`} class="grid-head"><span>{sec.title}</span><span class="muted">{sec.sub}</span></div>,
            ...sec.list.map((s) => <Tile key={`${sec.key}-${s.number}`} s={s} onTap={() => tap(s)} onLong={() => setDetail(s.number)} />),
          ])}
        </div>
      )}
      <button class="btn fab" aria-label="Nummern eingeben" onClick={() => setInput(true)}><Icon name="dialpad" /></button>
      {detailSticker && <StickerSheet s={detailSticker} onClose={() => setDetail(null)} />}
      {input && <ManualInputDialog known={new Set(stickers.map((s) => s.number))} onClose={() => setInput(false)}
        onConfirm={(n) => { setInput(false); snack(countSummary(actions.addNumbers(n))) }} />}
    </div>
  )
}

/** Kachel: Tippen = +1, lange drücken = Detail. */
function Tile({ s, onTap, onLong }: { s: Sticker; onTap: () => void; onLong: () => void }) {
  const st = status(s.count)
  const timer = useRef<number>()
  const start = useRef<{ x: number; y: number } | null>(null)
  const longFired = useRef(false)
  const cancel = () => { clearTimeout(timer.current); start.current = null }
  const label = st === 'FEHLT' ? 'fehlt' : st === 'VORHANDEN' ? 'vorhanden' : `doppelt, ${s.count}x`
  return (
    <button class={`tile ${st}`} aria-label={`Sticker ${s.number}, ${label}`}
      onPointerDown={(e) => {
        start.current = { x: e.clientX, y: e.clientY }
        longFired.current = false
        timer.current = window.setTimeout(() => { longFired.current = true; navigator.vibrate?.(20); onLong() }, 450)
      }}
      onPointerMove={(e) => { if (start.current && Math.hypot(e.clientX - start.current.x, e.clientY - start.current.y) > 10) cancel() }}
      onPointerUp={() => { const wasTap = start.current && !longFired.current; cancel(); if (wasTap) onTap() }}
      onPointerCancel={cancel} onPointerLeave={cancel}
      onContextMenu={(e) => e.preventDefault()}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onTap() } }}>
      <span class="n" style={{ fontSize: s.number.length > 3 ? '15px' : undefined }}>{s.number}</span>
      {s.name && <span class="nm">{s.name}</span>}
      {st === 'DOPPELT' && <span class="badge">+{duplicates(s.count)}</span>}
    </button>
  )
}

function StickerSheet({ s, onClose }: { s: Sticker; onClose: () => void }) {
  const clubs = useStore((x) => x.clubs)
  const [edit, setEdit] = useState(false)
  const [name, setName] = useState(s.name)
  const [clubId, setClubId] = useState<number | null>(s.clubId)
  const [cat, setCat] = useState<Category>(s.category)
  const clubName = clubs.find((c) => c.id === s.clubId)?.name
  return (
    <Sheet onClose={onClose}>
      <h2>#{s.number}</h2>
      <div class="muted">{[s.name || null, clubName, categoryLabel(s.category)].filter(Boolean).join(' · ')}</div>
      <div class="row" style={{ justifyContent: 'center', margin: '20px 0' }}>
        <button class="btn tonal" style={{ width: '56px', height: '56px', fontSize: '26px' }} aria-label="Eins weniger"
          disabled={s.count <= 0} onClick={() => actions.changeCount(s.number, -1)}>−</button>
        <div style={{ width: '130px', textAlign: 'center' }}>
          <div style={{ fontSize: '40px', fontWeight: 700 }}>{s.count}</div>
          <div class="muted">{s.count === 0 ? 'fehlt' : s.count === 1 ? 'vorhanden' : `${s.count - 1} doppelt`}</div>
        </div>
        <button class="btn tonal" style={{ width: '56px', height: '56px', fontSize: '26px' }} aria-label="Eins mehr"
          onClick={() => actions.changeCount(s.number, 1)}>+</button>
      </div>
      {!edit ? (
        <button class="btn outline block" onClick={() => setEdit(true)}>Bearbeiten</button>
      ) : (
        <div>
          <label class="field-label">Name</label>
          <input class="field" value={name} onInput={(e) => setName((e.target as HTMLInputElement).value)} />
          <label class="field-label">Verein</label>
          <select class="field" value={clubId ?? ''} onChange={(e) => { const v = (e.target as HTMLSelectElement).value; setClubId(v ? Number(v) : null) }}>
            <option value="">Ohne Verein</option>
            {clubs.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <label class="field-label">Kategorie</label>
          <select class="field" value={cat} onChange={(e) => setCat((e.target as HTMLSelectElement).value as Category)}>
            {CATEGORIES.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
          </select>
          <button class="btn block" style={{ marginTop: '12px' }} onClick={() => {
            actions.updateSticker({ ...s, name: name.trim(), clubId, category: cat })
            setEdit(false)
          }}>Speichern</button>
        </div>
      )}
    </Sheet>
  )
}
