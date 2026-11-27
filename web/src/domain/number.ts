/** Stickernummern wie "1", "125", "S1", "FW3": optionales Buchstaben-Präfix + optionaler Zahlenteil. */
const PATTERN = /^([A-Z]*)(\d*)$/

export function normalize(raw: string): string {
  let cleaned = raw.trim().toUpperCase().replace(/ /g, '')
  // wie Kotlin removePrefix: nacheinander
  for (const p of ['#', 'NR.', 'NR']) if (cleaned.startsWith(p)) cleaned = cleaned.slice(p.length)
  const m = PATTERN.exec(cleaned)
  if (!m) return cleaned
  const [, prefix, digits] = m
  if (!digits) return prefix
  return prefix + (digits.replace(/^0+/, '') || '0')
}

export function split(number: string): { prefix: string; value: number | null } {
  const m = PATTERN.exec(number.toUpperCase())
  if (!m) return { prefix: number, value: null }
  return { prefix: m[1], value: m[2] ? parseInt(m[2], 10) : null }
}
