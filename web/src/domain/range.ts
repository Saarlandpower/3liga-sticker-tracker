import { split } from './number'

/** Ab wie vielen aufeinanderfolgenden Nummern ein Bereich gebildet wird. */
export const MIN_RANGE = 2

export interface Range { first: string; last: string; size: number }
export const rangeLabel = (r: Range) => (r.size === 1 ? r.first : `${r.first}-${r.last}`)

function isSuccessor(a: string, b: string): boolean {
  const pa = split(a)
  const pb = split(b)
  return pa.value !== null && pb.value !== null && pa.prefix === pb.prefix && pb.value === pa.value + 1
}

export function groupRanges(numbers: string[]): Range[] {
  const result: Range[] = []
  let start = 0
  while (start < numbers.length) {
    let end = start
    while (end + 1 < numbers.length && isSuccessor(numbers[end], numbers[end + 1])) end++
    const size = end - start + 1
    if (size >= MIN_RANGE) result.push({ first: numbers[start], last: numbers[end], size })
    else for (let i = start; i <= end; i++) result.push({ first: numbers[i], last: numbers[i], size: 1 })
    start = end + 1
  }
  return result
}

/** [3, 17, 45, 46, 47, 48] → "3, 17, 45-48" */
export const formatRanges = (numbers: string[], separator = ', ') => groupRanges(numbers).map(rangeLabel).join(separator)

/** Doppelte mit Anzahl: (12,2),(88,1),(89,1) → "12 (2x), 88-89". Nur Einzel-Doppelte werden zu Bereichen. */
export function formatWithCounts(entries: [string, number][], separator = ', '): string {
  const parts: string[] = []
  let run: string[] = []
  const flush = () => { if (run.length) { parts.push(formatRanges(run, separator)); run = [] } }
  for (const [n, d] of entries) {
    if (d <= 0) continue
    if (d === 1) run.push(n)
    else { flush(); parts.push(`${n} (${d}x)`) }
  }
  flush()
  return parts.join(separator)
}
