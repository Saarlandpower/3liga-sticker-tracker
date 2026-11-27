import type { Worker } from 'tesseract.js'
import type { OcrToken } from '../domain/ocr'

/**
 * Texterkennung mit Tesseract.js – läuft komplett im Browser.
 * Alle Dateien (Worker, WASM-Kern, Sprachmodell) liegen unter /ocr/ in der App und werden vom
 * Service Worker zwischengespeichert, sodass die Erkennung nach dem ersten Laden offline funktioniert.
 */
let workerPromise: Promise<Worker> | null = null

export type OcrStatus = (progress: number, status: string) => void

export function getOcrWorker(onProgress?: OcrStatus): Promise<Worker> {
  if (!workerPromise) {
    const base = `${location.origin}${import.meta.env.BASE_URL}ocr`
    workerPromise = (async () => {
      const { createWorker, OEM, PSM } = await import('tesseract.js')
      const worker = await createWorker('eng', OEM.LSTM_ONLY, {
        workerPath: `${base}/worker.min.js`,
        corePath: base,
        langPath: base,
        gzip: true,
        workerBlobURL: false,
        logger: (m) => onProgress?.(m.progress, m.status),
      })
      await worker.setParameters({
        tessedit_pageseg_mode: PSM.SPARSE_TEXT,
        tessedit_char_whitelist: 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789#',
      })
      return worker
    })()
    workerPromise.catch(() => { workerPromise = null })
  }
  return workerPromise
}

/** Erkennt alle Wörter in einem (bereits zugeschnittenen) Bild. */
export async function recognizeTokens(canvas: HTMLCanvasElement): Promise<OcrToken[]> {
  const worker = await getOcrWorker()
  const { data } = await worker.recognize(canvas, {}, { blocks: true, text: false })
  const tokens: OcrToken[] = []
  for (const block of data.blocks ?? []) {
    for (const para of block.paragraphs) {
      for (const line of para.lines) {
        // kurze Zeile zusätzlich als ein Token ("1 25" → "125")
        if (line.words.length >= 2 && line.words.length <= 3 && line.text.trim().length <= 7) {
          const b = line.bbox
          tokens.push({ text: line.text.replace(/\s/g, ''), box: { left: b.x0, top: b.y0, right: b.x1, bottom: b.y1 } })
        }
        for (const w of line.words) {
          if (w.confidence < 30) continue
          const b = w.bbox
          tokens.push({ text: w.text, box: { left: b.x0, top: b.y0, right: b.x1, bottom: b.y1 } })
        }
      }
    }
  }
  return tokens
}

/**
 * Schneidet den Scan-Rahmen aus dem Videobild aus, skaliert ihn und verstärkt den Kontrast
 * (Graustufen + Histogramm-Streckung) – das hilft Tesseract bei Handyfotos deutlich.
 * frame: Rahmen relativ zum sichtbaren Video (0..1).
 */
export function grabFrame(video: HTMLVideoElement, frame: { x: number; y: number; w: number; h: number }, view: { w: number; h: number }): HTMLCanvasElement {
  const vw = video.videoWidth
  const vh = video.videoHeight
  // object-fit: cover – sichtbaren Ausschnitt des Videos berechnen
  const scale = Math.max(view.w / vw, view.h / vh)
  const visW = view.w / scale
  const visH = view.h / scale
  const offX = (vw - visW) / 2
  const offY = (vh - visH) / 2
  const sx = offX + frame.x * visW
  const sy = offY + frame.y * visH
  const sw = frame.w * visW
  const sh = frame.h * visH
  const targetH = Math.min(900, Math.max(500, sh))
  const k = targetH / sh
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(sw * k)
  canvas.height = Math.round(sh * k)
  const ctx = canvas.getContext('2d', { willReadFrequently: true })!
  ctx.drawImage(video, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height)
  const img = ctx.getImageData(0, 0, canvas.width, canvas.height)
  const d = img.data
  let min = 255
  let max = 0
  const gray = new Uint8ClampedArray(d.length / 4)
  for (let i = 0, j = 0; i < d.length; i += 4, j++) {
    const g = (d[i] * 299 + d[i + 1] * 587 + d[i + 2] * 114) / 1000
    gray[j] = g
    if (g < min) min = g
    if (g > max) max = g
  }
  const range = Math.max(1, max - min)
  for (let i = 0, j = 0; i < d.length; i += 4, j++) {
    const v = ((gray[j] - min) * 255) / range
    d[i] = d[i + 1] = d[i + 2] = v
  }
  ctx.putImageData(img, 0, 0)
  return canvas
}
