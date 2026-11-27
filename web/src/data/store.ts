import { useEffect, useState } from 'preact/hooks'
import { type MergeResult, mergeChecklist, parseChecklist } from '../domain/checklist'
import type { Club, Sticker } from '../domain/model'

export type ThemeMode = 'SYSTEM' | 'DUNKEL' | 'HELL'
export type ColorTheme = 'STANDARD' | 'BSG'

export interface Settings {
  themeMode: ThemeMode
  colorTheme: ColorTheme
  sound: boolean
  vibration: boolean
}

export interface State {
  checklistName: string
  clubs: Club[]
  stickers: Sticker[]
  settings: Settings
  /** Zeitpunkt (ms) des letzten Backup-Exports – für die Erinnerung. */
  lastBackupAt: number | null
  /** Zeitpunkt (ms) der letzten Änderung am Bestand. */
  lastChangeAt: number | null
}

const KEY = 'sticker-tracker-v1'
const DEFAULT_SETTINGS: Settings = { themeMode: 'DUNKEL', colorTheme: 'STANDARD', sound: true, vibration: true }
const EMPTY: State = { checklistName: '', clubs: [], stickers: [], settings: DEFAULT_SETTINGS, lastBackupAt: null, lastChangeAt: null }

function load(): State {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return EMPTY
    const s = JSON.parse(raw) as Partial<State>
    return { ...EMPTY, ...s, settings: { ...DEFAULT_SETTINGS, ...s.settings } }
  } catch {
    return EMPTY
  }
}

let state: State = load()
const listeners = new Set<() => void>()

function set(next: State) {
  state = next
  try { localStorage.setItem(KEY, JSON.stringify(state)) } catch { /* Speicher voll/gesperrt – Zustand bleibt im RAM */ }
  listeners.forEach((l) => l())
}

export const getState = () => state

/** Hook: liefert einen Ausschnitt des Zustands und rendert bei Änderung neu. */
export function useStore<T>(select: (s: State) => T): T {
  const [value, setValue] = useState(() => select(state))
  useEffect(() => {
    const update = () => setValue(() => select(state))
    listeners.add(update)
    update()
    return () => { listeners.delete(update) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return value
}

const touched = (s: State): State => ({ ...s, lastChangeAt: Date.now() })

export const actions = {
  /** Beim ersten Start die mitgelieferte Checkliste laden. */
  async ensureSeeded() {
    if (state.stickers.length) return
    const parsed = await loadBundledChecklist()
    const r = mergeChecklist([], [], parsed)
    set({ ...state, clubs: r.clubs, stickers: r.stickers, checklistName: parsed.name ?? '' })
  },

  /** @returns neue Anzahl oder null, wenn die Nummer nicht in der Checkliste ist. */
  changeCount(number: string, delta: number): number | null {
    const i = state.stickers.findIndex((s) => s.number === number)
    if (i < 0) return null
    const stickers = state.stickers.slice()
    const count = Math.max(0, stickers[i].count + delta)
    stickers[i] = { ...stickers[i], count }
    set(touched({ ...state, stickers }))
    return count
  },

  addNumbers(numbers: string[]): { number: string; newCount: number }[] {
    const out: { number: string; newCount: number }[] = []
    for (const n of numbers) {
      const c = actions.changeCount(n, 1)
      if (c !== null) out.push({ number: n, newCount: c })
    }
    return out
  },

  updateSticker(s: Sticker) {
    set(touched({ ...state, stickers: state.stickers.map((x) => (x.number === s.number ? s : x)) }))
  },

  renameClub(id: number, name: string) {
    set({ ...state, clubs: state.clubs.map((c) => (c.id === id ? { ...c, name: name.trim() } : c)) })
  },

  assignClub(numbers: string[], clubId: number | null) {
    const set_ = new Set(numbers)
    set({ ...state, stickers: state.stickers.map((s) => (set_.has(s.number) ? { ...s, clubId } : s)) })
  },

  applyMerge(r: MergeResult, name: string | null) {
    set(touched({ ...state, clubs: r.clubs, stickers: r.stickers, checklistName: name ?? state.checklistName }))
  },

  replaceAll(clubs: Club[], stickers: Sticker[], checklistName: string) {
    set(touched({ ...state, clubs, stickers, checklistName }))
  },

  resetCounts() {
    set(touched({ ...state, stickers: state.stickers.map((s) => ({ ...s, count: 0 })) }))
  },

  updateSettings(patch: Partial<Settings>) {
    set({ ...state, settings: { ...state.settings, ...patch } })
  },

  markBackedUp() {
    set({ ...state, lastBackupAt: Date.now() })
  },
}

export async function loadBundledChecklist() {
  const res = await fetch(`${import.meta.env.BASE_URL}checklist.json`)
  if (!res.ok) throw new Error('Checkliste konnte nicht geladen werden.')
  return parseChecklist(await res.text(), 'checklist.json', 423)
}

/** Bittet den Browser, die Daten nicht automatisch zu löschen (wichtig auf iOS). */
export async function requestPersistence() {
  try { await navigator.storage?.persist?.() } catch { /* nicht unterstützt */ }
}
