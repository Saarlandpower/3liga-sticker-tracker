import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { backupToDomain, decodeBackup, encodeBackup } from '../src/domain/backup'
import { mergeChecklist, parseChecklist, placeholders } from '../src/domain/checklist'
import { duplicates, status, summarize } from '../src/domain/count'
import { parseManualInput } from '../src/domain/manualInput'
import type { Sticker } from '../src/domain/model'
import { parseCategory } from '../src/domain/model'
import { decide, matchToken, pickBest } from '../src/domain/ocr'
import { formatRanges, formatWithCounts } from '../src/domain/range'
import { decodeTrade, encodeTrade, matchTrade, pairs, shareText, tradeListFrom } from '../src/domain/trade'

const st = (number: string, position: number, count: number, extra: Partial<Sticker> = {}): Sticker =>
  ({ number, position, name: '', clubId: null, category: 'SPIELER', count, ...extra })

describe('Zähllogik', () => {
  it('Status und Doppelte', () => {
    expect([0, -3, 1, 2, 7].map(status)).toEqual(['FEHLT', 'FEHLT', 'VORHANDEN', 'DOPPELT', 'DOPPELT'])
    expect([0, 1, 2, 5].map(duplicates)).toEqual([0, 0, 1, 4])
  })
  it('Zusammenfassung', () => {
    expect(summarize([0, 1, 2, 3, 0])).toMatchObject({ total: 5, owned: 3, missing: 2, duplicates: 3, percent: 60 })
    expect(summarize([]).percent).toBe(0)
  })
})

describe('Bereiche', () => {
  it('fasst fortlaufende Nummern zusammen', () => {
    expect(formatRanges(['3', '17', '45', '46', '47', '48'])).toBe('3, 17, 45-48')
    expect(formatRanges(['1', '2', '5'])).toBe('1-2, 5')
    expect(formatRanges(['399', '400', 'S1', 'S2', 'S3', 'FW1'])).toBe('399-400, S1-S3, FW1')
    expect(formatRanges(['5', '4', '3'])).toBe('5, 4, 3')
    expect(formatRanges([])).toBe('')
  })
  it('Doppelte mit Anzahl', () => {
    expect(formatWithCounts([['12', 2], ['88', 1], ['89', 1], ['90', 1], ['100', 1], ['150', 3]])).toBe('12 (2x), 88-90, 100, 150 (3x)')
  })
})

describe('Manuelle Eingabe', () => {
  const known = new Set([...Array.from({ length: 300 }, (_, i) => String(i + 1)), 'S1', 'S2', 'S3', 'FW3'])
  it('Liste mit Wiederholungen', () => expect(parseManualInput('12, 45, 45, 201', known).numbers).toEqual(['12', '45', '45', '201']))
  it('Bereiche und Wiederholung', () => {
    expect(parseManualInput('45-48', known).numbers).toEqual(['45', '46', '47', '48'])
    expect(parseManualInput('45 – 46', known).numbers).toEqual(['45', '46'])
    expect(parseManualInput('s1-s3', known).numbers).toEqual(['S1', 'S2', 'S3'])
    expect(parseManualInput('45x3', known).numbers).toEqual(['45', '45', '45'])
  })
  it('Kürzel, Nullen, #', () => expect(parseManualInput('fw3, 007, #12', known).numbers).toEqual(['FW3', '7', '12']))
  it('Unbekanntes wird gemeldet', () => {
    const r = parseManualInput('5, 999, abc-, 12', known)
    expect(r.numbers).toEqual(['5', '12'])
    expect(r.invalid).toEqual(['999', 'ABC-'])
  })
})

describe('OCR-Nummern', () => {
  const known = new Set(Array.from({ length: 400 }, (_, i) => String(i + 1)))
  const withSpecials = new Set([...known, 'S1', 'S2', 'FW3'])
  const m = (raw: string, k = known) => matchToken(raw, k)?.[0] ?? null
  it('Störungen werden korrigiert', () => {
    expect(['I25', 'l25', '|25', '1OO', '4O', 'S5', '2B', 'ZZ'].map((x) => m(x))).toEqual(['125', '125', '125', '100', '40', '55', '28', '22'])
    expect(matchToken('I25', known)).toEqual(['125', true])
    expect(matchToken('125', known)).toEqual(['125', false])
  })
  it('Satzzeichen und Präfixe', () => expect(['#125', '125.', '(125)', 'Nr.12', 'No.12'].map((x) => m(x))).toEqual(['125', '125', '125', '12', '12']))
  it('Ungültiges wird abgelehnt', () => expect(['401', '2025', '0', '1234567', 'PANINI', 'Liga', 'Italy', '', '--'].map((x) => m(x))).toEqual(Array(9).fill(null)))
  it('Sonderkürzel', () => {
    expect(['S1', 's1', 'SI', 'FW3', 'fw3', 'S7'].map((x) => m(x, withSpecials))).toEqual(['S1', 'S1', 'S1', 'FW3', 'FW3', '57'])
  })
  it('größte, mittige Zahl im Rahmen gewinnt', () => {
    const frame = { left: 100, top: 100, right: 500, bottom: 700 }
    const best = pickBest([
      { text: '12', box: { left: 120, top: 120, right: 160, bottom: 140 } },
      { text: '125', box: { left: 240, top: 330, right: 360, bottom: 430 } },
      { text: '300', box: { left: 10, top: 10, right: 200, bottom: 90 } },
    ], frame, known)
    expect(best?.number).toBe('125')
  })
  it('Mehrheitsentscheid', () => {
    expect(decide(['125', '125', '125', null, '125'])).toEqual({ kind: 'sure', number: '125', hits: 4, frames: 5 })
    expect(decide(['125', '126', '125', '126'])).toEqual({ kind: 'unsure', best: '125', frames: 4 })
    expect(decide(['12'])).toEqual({ kind: 'unsure', best: '12', frames: 1 })
    expect(decide([null, null])).toEqual({ kind: 'nothing' })
  })
})

describe('Checkliste', () => {
  it('JSON mit Vereinen und Kategorien', () => {
    const p = parseChecklist(JSON.stringify({ name: 'T', clubs: ['A', { name: 'B' }], stickers: [
      { number: '1', club: 'A', category: 'Wappen' }, { number: '002', club: 'B' }, { number: 's1', category: 'Glitzer' },
      { number: '9', club: 'A', category: 'Trainer' }] }))
    expect(p.stickers.map((s) => s.number)).toEqual(['1', '2', 'S1', '9'])
    expect(p.stickers.map((s) => s.category)).toEqual(['WAPPEN', 'SPIELER', 'SONDER', 'TRAINER'])
    expect(p.clubs).toEqual(['A', 'B'])
  })
  it('Platzhalter und Duplikate', () => {
    expect(parseChecklist('{"placeholderCount":3,"stickers":[]}').stickers.length).toBe(3)
    const d = parseChecklist('{"stickers":[{"number":"1"},{"number":"01"},{"number":"2"}]}')
    expect(d.stickers.map((s) => s.number)).toEqual(['1', '2'])
    expect(d.warnings.length).toBe(1)
  })
  it('CSV mit und ohne Kopfzeile', () => {
    const p = parseChecklist('nummer;name;verein;kategorie\n1;Wappen;1. FC Saarbrücken;Wappen\n2;"Müller; Thomas";1. FC Saarbrücken;Spieler', 'a.csv')
    expect(p.stickers[1].name).toBe('Müller; Thomas')
    expect(parseChecklist('10,Spieler X,Verein\n11,Spieler Y,Verein\n', 'a.csv').stickers.map((s) => s.number)).toEqual(['10', '11'])
  })
  it('Kategorie-Aliase', () => {
    expect(['Glitzer', 'special', 'badge', 'Teamfoto', 'coach', 'xyz'].map(parseCategory)).toEqual(['SONDER', 'SONDER', 'WAPPEN', 'MANNSCHAFT', 'TRAINER', 'SPIELER'])
  })
  it('Anzahlen bleiben beim Import erhalten', () => {
    const existing = [st('1', 0, 2, { clubId: 1 }), st('2', 1, 0), st('3', 2, 1), st('400', 3, 1)]
    const r = mergeChecklist(existing, [{ id: 1, name: 'Alt', position: 0 }],
      parseChecklist('{"clubs":["Neu"],"stickers":[{"number":"3","club":"Neu"},{"number":"1","club":"Neu"},{"number":"S1"}]}'))
    expect(r.stickers.map((s) => [s.number, s.count, s.clubId])).toEqual([['3', 1, 1], ['1', 2, 1], ['S1', 0, null]])
    expect(r.droppedWithCount).toEqual(['400'])
    expect(r.keptCounts).toBe(2)
    const keep = mergeChecklist(existing, [{ id: 1, name: 'Alt', position: 0 }], { name: null, clubs: [], stickers: placeholders(3), warnings: [] })
    expect(keep.clubs[0].name).toBe('Alt')
    expect(keep.stickers.map((s) => s.clubId)).toEqual([1, null, null])
  })
  it('mitgelieferte Checkliste 2026/27 ist vollständig', () => {
    const p = parseChecklist(readFileSync('../app/src/main/assets/checklist.json', 'utf8'), 'checklist.json')
    expect(p.stickers.map((s) => s.number)).toEqual(Array.from({ length: 423 }, (_, i) => String(i + 1)))
    expect(p.clubs.length).toBe(23)
    expect(p.stickers.filter((s) => s.category === 'TRAINER').map((s) => s.number))
      .toEqual(Array.from({ length: 20 }, (_, i) => String(61 + 18 * i)))
  })
})

describe('Tausch', () => {
  const mine = tradeListFrom([st('3', 0, 0), st('12', 1, 3), st('17', 2, 0), st('45', 3, 0), st('46', 4, 0), st('47', 5, 0),
    st('48', 6, 0), st('88', 7, 2), st('89', 8, 2), st('90', 9, 1), st('S1', 10, 2)])
  it('Liste und Text', () => {
    expect(mine.missing).toEqual(['3', '17', '45', '46', '47', '48'])
    const t = shareText(mine)
    expect(t).toContain('Fehlen (6): 3, 17, 45-48')
    expect(t).toContain('Doppelt (5): 12 (2x), 88-89, S1')
  })
  it('QR-Code ist identisch zur Android-App', () => {
    const payload = encodeTrade(mine)
    expect(payload).toBe('3LST:1|N:11|M:3,17,45-48|D:12x2,88-89,S1')
    const back = decodeTrade(payload)
    expect(back.missing).toEqual(mine.missing)
    expect([...back.duplicates]).toEqual([...mine.duplicates])
    expect(() => decodeTrade('https://example.com')).toThrow()
  })
  it('Tauschpaare in beide Richtungen', () => {
    const theirs = { missing: ['12', '88', '100'], duplicates: new Map([['45', 1], ['3', 2], ['200', 1]]), checklistSize: 11 }
    const m = matchTrade(mine, theirs, ['3', '12', '17', '45', '46', '47', '48', '88', '89', '90', 'S1', '100', '200'])
    expect(m.iGive).toEqual(['12', '88'])
    expect(m.iGet).toEqual(['3', '45'])
    expect(pairs(m)).toEqual([['12', '3'], ['88', '45']])
    expect(m.checklistMismatch).toBe(false)
  })
})

describe('Backup', () => {
  it('Rundreise und Kompatibilität mit dem Android-Format', () => {
    const clubs = [{ id: 1, name: '1. FC Saarbrücken', position: 0 }]
    const stickers = [st('1', 0, 1, { clubId: 1, category: 'WAPPEN' }), st('S1', 1, 0, { category: 'SONDER' })]
    const back = backupToDomain(decodeBackup(encodeBackup('x', 'T', clubs, stickers)))
    expect(back.clubs).toEqual(clubs)
    expect(back.stickers).toEqual(stickers)
    // Android-Export (kotlinx.serialization)
    const android = '{"app":"3liga-sticker-tracker","formatVersion":1,"exportedAt":"20.11.2026 10:00","checklistName":"",' +
      '"clubs":[{"id":1,"name":"A"}],"stickers":[{"number":"1","name":"","clubId":9,"category":"TRAINER","count":-2}]}'
    const s = backupToDomain(decodeBackup(android)).stickers[0]
    expect([s.clubId, s.count, s.category]).toEqual([null, 0, 'TRAINER'])
    expect(() => decodeBackup('{"app":"andere-app","stickers":[{"number":"1"}]}')).toThrow()
  })
})
