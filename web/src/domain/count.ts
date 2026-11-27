import type { StickerStatus } from './model'

/** Zähllogik: 0 = fehlt, 1 = vorhanden, >1 = doppelt (Doppelte = anzahl - 1). */
export const status = (count: number): StickerStatus => (count <= 0 ? 'FEHLT' : count === 1 ? 'VORHANDEN' : 'DOPPELT')
export const duplicates = (count: number) => Math.max(0, count - 1)
export const increment = (count: number, by = 1) => Math.max(0, Math.max(0, count) + by)
export const decrement = (count: number, by = 1) => Math.max(0, count - by)

export interface Summary {
  total: number
  owned: number
  missing: number
  duplicates: number
  progress: number
  percent: number
}

export function summarize(counts: number[]): Summary {
  const owned = counts.filter((c) => c > 0).length
  const total = counts.length
  return {
    total,
    owned,
    missing: total - owned,
    duplicates: counts.reduce((s, c) => s + duplicates(c), 0),
    progress: total === 0 ? 0 : owned / total,
    percent: total === 0 ? 0 : Math.floor((owned * 100) / total),
  }
}
