import { type Category, CATEGORIES, type Club, type Sticker, parseCategory } from './model'
import { normalize } from './number'

/** Format der Komplett-Sicherung – identisch zur Android-App, Dateien lassen sich austauschen. */
export const APP_ID = '3liga-sticker-tracker'

export interface BackupFile {
  app: string
  formatVersion: number
  exportedAt: string
  checklistName: string
  clubs: { id: number; name: string }[]
  stickers: { number: string; name: string; clubId: number | null; category: string; count: number }[]
}

export function encodeBackup(exportedAt: string, checklistName: string, clubs: Club[], stickers: Sticker[]): string {
  const file: BackupFile = {
    app: APP_ID,
    formatVersion: 1,
    exportedAt,
    checklistName,
    clubs: [...clubs].sort((a, b) => a.position - b.position).map((c) => ({ id: c.id, name: c.name })),
    stickers: [...stickers].sort((a, b) => a.position - b.position)
      .map((s) => ({ number: s.number, name: s.name, clubId: s.clubId, category: s.category, count: s.count })),
  }
  return JSON.stringify(file, null, 4)
}

export function decodeBackup(text: string): BackupFile {
  let file: Partial<BackupFile>
  try { file = JSON.parse(text.replace(/^﻿/, '')) } catch { throw new Error('Keine gültige Sicherungsdatei.') }
  if (!file || typeof file !== 'object' || !Array.isArray(file.stickers)) throw new Error('Keine gültige Sicherungsdatei.')
  if ((file.app ?? APP_ID) !== APP_ID) throw new Error('Die Datei stammt nicht vom Sticker-Tracker.')
  if (!file.stickers.length) throw new Error('Die Sicherung enthält keine Sticker.')
  return {
    app: APP_ID,
    formatVersion: file.formatVersion ?? 1,
    exportedAt: file.exportedAt ?? '',
    checklistName: file.checklistName ?? '',
    clubs: Array.isArray(file.clubs) ? file.clubs : [],
    stickers: file.stickers,
  }
}

export function backupToDomain(file: BackupFile): { clubs: Club[]; stickers: Sticker[] } {
  const clubs = file.clubs.map((c, i) => ({ id: c.id, name: c.name, position: i }))
  const ids = new Set(clubs.map((c) => c.id))
  const seen = new Set<string>()
  const stickers: Sticker[] = []
  for (const s of file.stickers) {
    const number = normalize(String(s.number ?? ''))
    if (!number || seen.has(number)) continue
    seen.add(number)
    const cat = CATEGORIES.some((c) => c.id === s.category) ? (s.category as Category) : parseCategory(s.category)
    stickers.push({
      number,
      position: stickers.length,
      name: s.name ?? '',
      clubId: s.clubId !== null && s.clubId !== undefined && ids.has(s.clubId) ? s.clubId : null,
      category: cat,
      count: Math.max(0, Number(s.count) || 0),
    })
  }
  return { clubs, stickers }
}
