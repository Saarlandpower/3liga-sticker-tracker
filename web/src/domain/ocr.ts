import { normalize, split } from './number'

/** Rechteck in Pixelkoordinaten. */
export interface Box { left: number; top: number; right: number; bottom: number }
export interface OcrToken { text: string; box: Box }
export interface OcrMatch { number: string; raw: string; score: number; corrected: boolean }

const w = (b: Box) => b.right - b.left
const h = (b: Box) => b.bottom - b.top
const cx = (b: Box) => (b.left + b.right) / 2
const cy = (b: Box) => (b.top + b.bottom) / 2

/** Buchstaben, die OCR gern mit Ziffern verwechselt (identisch zur Android-App). */
const LOOKALIKES: Record<string, string> = {
  O: '0', Q: '0', D: '0', U: '0',
  I: '1', L: '1', '|': '1', '!': '1', J: '1', T: '7',
  Z: '2', S: '5', B: '8', G: '6', A: '4',
  o: '0', i: '1', l: '1', z: '2', s: '5', b: '6', g: '9', q: '9',
}
const MAX_LEN = 6
const TRIM = '#.,:;()[]-_"\'*°/'

export function prefixesOf(known: Iterable<string>): string[] {
  const set = new Set<string>()
  for (const n of known) { const p = split(n).prefix; if (p) set.add(p) }
  return [...set].sort((a, b) => b.length - a.length)
}

function trimChars(s: string): string {
  let a = 0
  let b = s.length
  while (a < b && TRIM.includes(s[a])) a++
  while (b > a && TRIM.includes(s[b - 1])) b--
  return s.slice(a, b)
}

export function clean(raw: string): string {
  let t = trimChars(raw.trim()).replace(/ /g, '')
  for (const p of ['NR.', 'NR', 'NO.', 'Nº', 'N°']) {
    if (t.length > p.length && t.toUpperCase().startsWith(p)) { t = trimChars(t.slice(p.length)); break }
  }
  return t
}

export function digitize(s: string): string | null {
  let out = ''
  for (const c of s) {
    if (c >= '0' && c <= '9') out += c
    else if (c in LOOKALIKES) out += LOOKALIKES[c]
    else if (c.toUpperCase() in LOOKALIKES) out += LOOKALIKES[c.toUpperCase()]
    else return null
  }
  return out
}

/** Ein OCR-Wort als Stickernummer lesen: [Nummer, korrigiert?] oder null. */
export function matchToken(raw: string, known: Set<string>, prefixes = prefixesOf(known)): [string, boolean] | null {
  const cleaned = clean(raw)
  if (!cleaned || cleaned.length > MAX_LEN) return null
  const upper = cleaned.toUpperCase()
  if (!/\d/.test(cleaned) && digitize(cleaned) === null && !prefixes.some((p) => upper.startsWith(p))) return null

  const exact = normalize(cleaned)
  if (known.has(exact) && /\d/.test(exact)) return [exact, false]

  for (const p of prefixes) {
    if (upper.length > p.length && upper.startsWith(p)) {
      const rest = digitize(cleaned.slice(p.length))
      if (rest === null) continue
      const n = normalize(p + rest)
      if (known.has(n)) return [n, true]
    }
  }
  const digits = digitize(cleaned)
  if (digits === null) return null
  const n = normalize(digits)
  return known.has(n) ? [n, n !== exact] : null
}

export function score(box: Box, frame: Box | null): number {
  if (h(box) <= 0) return 0
  if (!frame) return h(box)
  const x = cx(box)
  const y = cy(box)
  if (x < frame.left || x > frame.right || y < frame.top || y > frame.bottom) return 0
  const size = h(box) / h(frame)
  const dx = Math.abs(x - cx(frame)) / (w(frame) / 2)
  const dy = Math.abs(y - cy(frame)) / (h(frame) / 2)
  return size * (1 - 0.5 * Math.min(1, Math.max(0, Math.max(dx, dy))))
}

/** Plausibelste Stickernummer: im Rahmen, große Schrift, nah an der Mitte; exakte Treffer vor korrigierten. */
export function pickBest(tokens: OcrToken[], frame: Box | null, known: Set<string>): OcrMatch | null {
  if (!known.size) return null
  const prefixes = prefixesOf(known)
  let best: OcrMatch | null = null
  for (const t of tokens) {
    const m = matchToken(t.text, known, prefixes)
    if (!m) continue
    const s = score(t.box, frame) * (m[1] ? 0.8 : 1)
    if (s <= 0) continue
    if (!best || s > best.score) best = { number: m[0], raw: t.text, score: s, corrected: m[1] }
  }
  return best
}

export type VoteResult =
  | { kind: 'sure'; number: string; hits: number; frames: number }
  | { kind: 'unsure'; best: string | null; frames: number }
  | { kind: 'nothing' }

/** Mehrheitsentscheid über mehrere Bilder (wie ScanVote in der Android-App). */
export function decide(samples: (string | null)[], minHits = 2, minShareOfHits = 0.75, minShareOfFrames = 0.5): VoteResult {
  const hits = samples.filter((s): s is string => s !== null)
  if (!hits.length) return { kind: 'nothing' }
  const counts = new Map<string, number>()
  for (const s of hits) counts.set(s, (counts.get(s) ?? 0) + 1)
  let best = ''
  let n = 0
  for (const [k, v] of counts) if (v > n) { best = k; n = v }
  const sure = n >= minHits && n >= minShareOfHits * hits.length && n >= minShareOfFrames * samples.length
  return sure ? { kind: 'sure', number: best, hits: n, frames: samples.length } : { kind: 'unsure', best, frames: samples.length }
}
