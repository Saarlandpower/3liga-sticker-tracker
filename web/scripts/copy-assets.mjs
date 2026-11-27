// Kopiert generierte Dateien nach public/ (nicht eingecheckt):
// - die Checkliste aus der Android-App (eine gemeinsame Quelle)
// - Tesseract-Worker, WASM-Kern und Sprachmodell, damit die Texterkennung offline läuft
// - die Datenschutzerklärung als HTML
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { marked } from 'marked'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const nm = (p) => resolve(root, 'node_modules', p)
const pub = (p) => resolve(root, 'public', p)
mkdirSync(pub('ocr'), { recursive: true })

copyFileSync(resolve(root, '../app/src/main/assets/checklist.json'), pub('checklist.json'))
copyFileSync(nm('tesseract.js/dist/worker.min.js'), pub('ocr/worker.min.js'))
for (const f of ['tesseract-core-lstm.wasm.js', 'tesseract-core-simd-lstm.wasm.js']) copyFileSync(nm(`tesseract.js-core/${f}`), pub(`ocr/${f}`))
copyFileSync(nm('@tesseract.js-data/eng/4.0.0_best_int/eng.traineddata.gz'), pub('ocr/eng.traineddata.gz'))

const md = readFileSync(resolve(root, '../docs/datenschutz.md'), 'utf8').replace(/<!--[\s\S]*?-->/g, '')
writeFileSync(pub('datenschutz.html'), `<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Datenschutz – Sticker-Tracker</title>
<style>body{font:16px/1.55 system-ui,sans-serif;max-width:760px;margin:0 auto;padding:16px;color:#1b1f24;background:#fff}
table{border-collapse:collapse}td,th{border:1px solid #ccd;padding:4px 8px}a{color:#0057b8}
@media(prefers-color-scheme:dark){body{background:#0e1116;color:#e6e8ee}td,th{border-color:#334}a{color:#7fb2ff}}</style>
</head><body>${marked.parse(md)}<p><a href="./">← zurück zur App</a></p></body></html>`)
console.log('Assets kopiert.')
