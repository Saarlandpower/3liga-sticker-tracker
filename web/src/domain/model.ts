// Datenmodell – entspricht der Android-App (domain/Sticker.kt, Category.kt).

export type Category = 'SPIELER' | 'TRAINER' | 'WAPPEN' | 'MANNSCHAFT' | 'SONDER'

export const CATEGORIES: { id: Category; label: string }[] = [
  { id: 'SPIELER', label: 'Spieler' },
  { id: 'TRAINER', label: 'Trainer' },
  { id: 'WAPPEN', label: 'Wappen' },
  { id: 'MANNSCHAFT', label: 'Mannschaftsfoto' },
  { id: 'SONDER', label: 'Sonder/Glitzer' },
]

export const categoryLabel = (c: Category) => CATEGORIES.find((x) => x.id === c)?.label ?? c

/** Tolerantes Parsen aus Checklisten-Dateien (deutsch/englisch, Groß-/Kleinschreibung egal). */
export function parseCategory(raw: string | null | undefined): Category {
  const v = (raw ?? '').trim().toLowerCase()
  if (!v) return 'SPIELER'
  if (v.startsWith('spiel') || v === 'player') return 'SPIELER'
  if (v.startsWith('trainer') || v === 'coach' || v === 'headcoach') return 'TRAINER'
  if (v.startsWith('wappen') || v === 'badge' || v === 'logo' || v === 'crest') return 'WAPPEN'
  if (v.startsWith('mannschaft') || v.startsWith('team')) return 'MANNSCHAFT'
  if (v.startsWith('sonder') || v.startsWith('glitzer') || v.startsWith('special') || v.startsWith('glitter') || v === 'foil')
    return 'SONDER'
  const exact = CATEGORIES.find((c) => c.id.toLowerCase() === v)
  return exact ? exact.id : 'SPIELER'
}

export interface Club {
  id: number
  name: string
  position: number
}

export interface Sticker {
  number: string
  position: number
  name: string
  clubId: number | null
  category: Category
  count: number
}

export type StickerStatus = 'FEHLT' | 'VORHANDEN' | 'DOPPELT'
