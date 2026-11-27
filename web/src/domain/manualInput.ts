import { normalize } from './number'

/** Parst "12, 45, 45, 201", "45-48", "S1-S3", "45x3". Wiederholungen bleiben erhalten. */
const RANGE = /^([A-Z]*)(\d+)-([A-Z]*)(\d+)$/
const REPEAT = /^(.+?)[X*](\d{1,2})$/
const MAX_RANGE = 1000

function expand(token: string): string[] | null {
  const rep = REPEAT.exec(token)
  if (rep) {
    const times = parseInt(rep[2], 10)
    const base = expand(rep[1])
    if (!base || times < 1 || times > 50) return null
    return Array.from({ length: times }, () => base).flat()
  }
  const r = RANGE.exec(token)
  if (r) {
    const [, p1, a, p2, b] = r
    if (p2 && p2 !== p1) return null
    const from = parseInt(a, 10)
    const to = parseInt(b, 10)
    if (to < from || to - from > MAX_RANGE) return null
    const out: string[] = []
    for (let i = from; i <= to; i++) out.push(p1 + i)
    return out
  }
  const n = normalize(token)
  return /^[A-Z]*\d+$|^[A-Z]+$/.test(n) ? [n] : null
}

export function parseManualInput(input: string, known?: Set<string>): { numbers: string[]; invalid: string[] } {
  const numbers: string[] = []
  const invalid: string[] = []
  const text = input.toUpperCase().replace(/\s*[-–]\s*/g, '-')
  for (const token of text.split(/[,;\s]+/).filter((t) => t.trim())) {
    const expanded = expand(token)
    if (!expanded) { invalid.push(token); continue }
    for (const n of expanded) (!known || known.has(n) ? numbers : invalid).push(n)
  }
  return { numbers, invalid: [...new Set(invalid)] }
}
