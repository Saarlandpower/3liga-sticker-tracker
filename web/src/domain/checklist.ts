import { type Category, type Club, type Sticker, parseCategory } from './model'
import { normalize } from './number'

export interface ParsedSticker { number: string; name: string; clubName: string | null; category: Category }
export interface ParsedChecklist {
  name: string | null
  clubs: string[]
  stickers: ParsedSticker[]
  warnings: string[]
}
export const hasClubInfo = (p: ParsedChecklist) => p.clubs.length > 0 || p.stickers.some((s) => s.clubName !== null)

export class ChecklistFormatError extends Error {}

const str = (o: Record<string, unknown>, k: string): string | null => {
  const v = o[k]
  if (v === null || v === undefined || typeof v === 'object') return null
  const s = String(v)
  return s.trim() ? s : null
}
const int = (o: Record<string, unknown>, k: string): number | null => {
  const v = o[k]
  return typeof v === 'number' && Number.isInteger(v) ? v : typeof v === 'string' && /^\d+$/.test(v) ? parseInt(v, 10) : null
}

export const placeholders = (count: number): ParsedSticker[] =>
  Array.from({ length: count }, (_, i) => ({ number: String(i + 1), name: '', clubName: null, category: 'SPIELER' as Category }))

export function parseChecklist(content: string, fileName?: string, defaultPlaceholderCount = 0): ParsedChecklist {
  const text = content.replace(/^﻿/, '').trim()
  if (!text) throw new ChecklistFormatError('Die Datei ist leer.')
  const looksJson = text.startsWith('{') || text.startsWith('[')
  const isCsvName = !!fileName && /\.(csv|txt)$/i.test(fileName)
  return looksJson && !isCsvName ? parseJson(text, defaultPlaceholderCount) : parseCsv(text)
}

export function parseJson(text: string, defaultPlaceholderCount = 0): ParsedChecklist {
  let root: unknown
  try { root = JSON.parse(text) } catch (e) { throw new ChecklistFormatError(`Ungültiges JSON: ${String((e as Error).message).slice(0, 120)}`) }
  let obj: Record<string, unknown>
  let arr: unknown[] | null
  if (Array.isArray(root)) { obj = {}; arr = root }
  else if (root && typeof root === 'object') { obj = root as Record<string, unknown>; arr = Array.isArray(obj.stickers) ? obj.stickers : null }
  else throw new ChecklistFormatError('JSON muss ein Objekt oder eine Liste sein.')

  const name = str(obj, 'name')
  const clubs = (Array.isArray(obj.clubs) ? obj.clubs : [])
    .map((el) => (typeof el === 'string' ? el : el && typeof el === 'object' ? str(el as Record<string, unknown>, 'name') : null))
    .map((s) => s?.trim() ?? '')
    .filter((s) => s)
  const warnings: string[] = []
  const stickers: ParsedSticker[] = []
  ;(arr ?? []).forEach((el, i) => {
    const s = el && typeof el === 'object' && !Array.isArray(el) ? (el as Record<string, unknown>) : null
    const raw = s ? str(s, 'number') ?? str(s, 'nummer') ?? str(s, 'nr') : typeof el === 'string' || typeof el === 'number' ? String(el) : null
    const number = raw ? normalize(raw) : ''
    if (!number) { warnings.push(`Eintrag ${i + 1}: keine Nummer – übersprungen`); return }
    let clubName = s ? str(s, 'club') ?? str(s, 'verein') : null
    if (!clubName && s) { const idx = int(s, 'clubIndex') ?? int(s, 'vereinIndex'); if (idx !== null) clubName = clubs[idx] ?? null }
    stickers.push({
      number,
      name: s ? str(s, 'name') ?? '' : '',
      clubName: clubName?.trim() || null,
      category: parseCategory(s ? str(s, 'category') ?? str(s, 'kategorie') : null),
    })
  })
  const placeholderCount = int(obj, 'placeholderCount') ?? defaultPlaceholderCount
  if (!stickers.length && placeholderCount > 0) stickers.push(...placeholders(placeholderCount))
  if (!stickers.length) throw new ChecklistFormatError('Die Checkliste enthält keine Sticker.')
  return finish(name, clubs, stickers, warnings)
}

export function splitCsvLine(line: string, delimiter: string): string[] {
  const out: string[] = []
  let sb = ''
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (c === '"' && inQuotes && line[i + 1] === '"') { sb += '"'; i++ }
    else if (c === '"') inQuotes = !inQuotes
    else if (c === delimiter && !inQuotes) { out.push(sb); sb = '' }
    else sb += c
  }
  out.push(sb)
  return out
}

export function parseCsv(text: string): ParsedChecklist {
  const lines = text.split('\n').map((l) => l.replace(/\r$/, '')).filter((l) => l.trim())
  if (!lines.length) throw new ChecklistFormatError('Die CSV-Datei ist leer.')
  const first = lines[0]
  const counts = [';', '\t', ','].map((d) => [d, first.split(d).length - 1] as const)
  const best = counts.reduce((a, b) => (b[1] > a[1] ? b : a))
  const delimiter = best[1] > 0 ? best[0] : ';'
  const rows = lines.map((l) => splitCsvLine(l, delimiter))
  const header = rows[0].map((h) => h.trim().toLowerCase())
  const numberHeaders = ['nummer', 'number', 'nr', 'nr.', 'no', '#']
  const hasHeader = header.some((h) => numberHeaders.includes(h))
  const idx = (names: string[], fallback: number) => (hasHeader ? header.findIndex((h) => names.includes(h)) : fallback)
  const iNum = idx(numberHeaders, 0)
  const iName = idx(['name', 'spieler', 'titel'], 1)
  const iClub = idx(['verein', 'club', 'klub', 'team'], 2)
  const iCat = idx(['kategorie', 'category', 'typ', 'type'], 3)
  const warnings: string[] = []
  const stickers: ParsedSticker[] = []
  rows.slice(hasHeader ? 1 : 0).forEach((cols, i) => {
    const raw = iNum >= 0 ? cols[iNum] : undefined
    const number = raw ? normalize(raw) : ''
    if (!number) { warnings.push(`Zeile ${i + 1 + (hasHeader ? 1 : 0)}: keine Nummer – übersprungen`); return }
    stickers.push({
      number,
      name: iName >= 0 ? (cols[iName] ?? '').trim() : '',
      clubName: iClub >= 0 ? (cols[iClub] ?? '').trim() || null : null,
      category: parseCategory(iCat >= 0 ? cols[iCat] : null),
    })
  })
  if (!stickers.length) throw new ChecklistFormatError('Die CSV-Datei enthält keine Sticker.')
  return finish(null, [], stickers, warnings)
}

function finish(name: string | null, clubs: string[], stickers: ParsedSticker[], warnings: string[]): ParsedChecklist {
  const seen = new Set<string>()
  const unique = stickers.filter((s) => {
    if (seen.has(s.number)) { warnings.push(`Nummer ${s.number} doppelt – nur erster Eintrag übernommen`); return false }
    seen.add(s.number)
    return true
  })
  const allClubs = [...new Set([...clubs, ...unique.map((s) => s.clubName).filter((c): c is string => !!c)])]
  return { name, clubs: allClubs, stickers: unique, warnings }
}

export interface MergeResult { clubs: Club[]; stickers: Sticker[]; droppedWithCount: string[]; keptCounts: number }

/** Neue Checkliste übernehmen; Anzahlen bleiben für alle Nummern erhalten, die auch in der neuen Liste stehen. */
export function mergeChecklist(existingStickers: Sticker[], existingClubs: Club[], parsed: ParsedChecklist): MergeResult {
  const old = new Map(existingStickers.map((s) => [s.number, s]))
  const keepClubs = !hasClubInfo(parsed)
  const clubs = keepClubs ? existingClubs : parsed.clubs.map((n, i) => ({ id: i + 1, name: n, position: i }))
  const clubIdByName = new Map(clubs.map((c) => [c.name.toLowerCase(), c.id]))
  let kept = 0
  const stickers: Sticker[] = parsed.stickers.map((p, i) => {
    const o = old.get(p.number)
    if (o && o.count > 0) kept++
    return {
      number: p.number,
      position: i,
      name: p.name || (keepClubs ? o?.name ?? '' : ''),
      clubId: keepClubs ? o?.clubId ?? null : p.clubName ? clubIdByName.get(p.clubName.toLowerCase()) ?? null : null,
      category: keepClubs && !p.name && o ? o.category : p.category,
      count: o?.count ?? 0,
    }
  })
  const numbers = new Set(stickers.map((s) => s.number))
  const droppedWithCount = existingStickers.filter((s) => s.count > 0 && !numbers.has(s.number)).map((s) => s.number)
  return { clubs, stickers, droppedWithCount, keptCounts: kept }
}
