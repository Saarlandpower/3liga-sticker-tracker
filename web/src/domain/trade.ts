import { duplicates } from './count'
import type { Sticker } from './model'
import { parseManualInput } from './manualInput'
import { formatRanges, formatWithCounts } from './range'

export interface TradeList { missing: string[]; duplicates: Map<string, number>; checklistSize: number | null }
export interface TradeMatch { iGive: string[]; iGet: string[]; checklistMismatch: boolean }
export const pairs = (m: TradeMatch): [string, string][] => m.iGive.slice(0, m.iGet.length).map((g, i) => [g, m.iGet[i]])

export function tradeListFrom(stickers: Sticker[]): TradeList {
  const sorted = [...stickers].sort((a, b) => a.position - b.position)
  return {
    missing: sorted.filter((s) => s.count <= 0).map((s) => s.number),
    duplicates: new Map(sorted.filter((s) => duplicates(s.count) > 0).map((s) => [s.number, duplicates(s.count)])),
    checklistSize: stickers.length,
  }
}

const sumValues = (m: Map<string, number>) => [...m.values()].reduce((a, b) => a + b, 0)

/** Teilbarer Text: "Fehlen (6): 3, 17, 45-48 / Doppelt (5): 12 (2x), 88-89". */
export function shareText(list: TradeList, title = 'Meine Tauschliste – 3. Liga Sticker'): string {
  const missing = list.missing.length ? formatRanges(list.missing) : 'keine – Album komplett!'
  const dups = list.duplicates.size ? formatWithCounts([...list.duplicates]) : 'keine'
  return `${title}\n\nFehlen (${list.missing.length}): ${missing}\n\nDoppelt (${sumValues(list.duplicates)}): ${dups}`
}

export const PREFIX = '3LST'
export const VERSION = 1

function encodeDuplicates(entries: [string, number][]): string {
  const parts: string[] = []
  let run: string[] = []
  const flush = () => { if (run.length) { parts.push(formatRanges(run, ',')); run = [] } }
  for (const [n, d] of entries) {
    if (d <= 0) continue
    if (d === 1) run.push(n)
    else { flush(); parts.push(`${n}x${d}`) }
  }
  flush()
  return parts.join(',')
}

/** QR-Inhalt, kompatibel zur Android-App: "3LST:1|N:400|M:3,17,45-48|D:12x2,88-89". */
export function encodeTrade(list: TradeList): string {
  let s = `${PREFIX}:${VERSION}`
  if (list.checklistSize !== null) s += `|N:${list.checklistSize}`
  s += `|M:${formatRanges(list.missing, ',')}`
  s += `|D:${encodeDuplicates([...list.duplicates])}`
  return s
}

export function decodeTrade(payload: string): TradeList {
  const text = payload.trim()
  if (!text.startsWith(`${PREFIX}:`)) throw new Error('Das ist kein Tausch-Code des Sticker-Trackers.')
  const fields = new Map<string, string>()
  for (const f of text.split('|')) {
    const i = f.indexOf(':')
    if (i < 0) fields.set(f, '')
    else fields.set(f.slice(0, i), f.slice(i + 1))
  }
  const version = parseInt(fields.get(PREFIX) ?? '0', 10) || 0
  if (version < 1 || version > VERSION) throw new Error('Tausch-Code aus einer neueren App-Version – bitte App aktualisieren.')
  const missing = [...new Set(parseManualInput(fields.get('M') ?? '').numbers)]
  const dups = new Map<string, number>()
  for (const n of parseManualInput(fields.get('D') ?? '').numbers) dups.set(n, (dups.get(n) ?? 0) + 1)
  const size = parseInt(fields.get('N') ?? '', 10)
  return { missing, duplicates: dups, checklistSize: Number.isNaN(size) ? null : size }
}

export function matchTrade(mine: TradeList, theirs: TradeList, order: string[]): TradeMatch {
  const pos = new Map(order.map((n, i) => [n, i]))
  const byPos = (a: string, b: string) => (pos.get(a) ?? Infinity) - (pos.get(b) ?? Infinity)
  const theirMissing = new Set(theirs.missing)
  const myMissing = new Set(mine.missing)
  return {
    iGive: [...mine.duplicates.keys()].filter((n) => theirMissing.has(n)).sort(byPos),
    iGet: [...theirs.duplicates.keys()].filter((n) => myMissing.has(n)).sort(byPos),
    checklistMismatch: mine.checklistSize !== null && theirs.checklistSize !== null && mine.checklistSize !== theirs.checklistSize,
  }
}
