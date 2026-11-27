import { getState } from './store'

let ctx: AudioContext | null = null

/** Kurzer Ton (Web Audio) + Vibration (nicht auf iPhones verfügbar). */
export function playFeedback(isNew: boolean) {
  const { sound, vibration } = getState().settings
  if (vibration && 'vibrate' in navigator) navigator.vibrate(isNew ? 45 : [35, 70, 35])
  if (!sound) return
  try {
    ctx ??= new AudioContext()
    const tones = isNew ? [880] : [660, 660]
    tones.forEach((f, i) => {
      const o = ctx!.createOscillator()
      const g = ctx!.createGain()
      o.frequency.value = f
      o.type = 'sine'
      const t = ctx!.currentTime + i * 0.14
      g.gain.setValueAtTime(0.0001, t)
      g.gain.exponentialRampToValueAtTime(0.25, t + 0.01)
      g.gain.exponentialRampToValueAtTime(0.0001, t + 0.11)
      o.connect(g).connect(ctx!.destination)
      o.start(t)
      o.stop(t + 0.12)
    })
  } catch { /* Audio nicht verfügbar */ }
}

/** Auf iOS darf Audio erst nach einer Nutzeraktion starten – beim ersten Tippen freischalten. */
export function unlockAudio() {
  try { ctx ??= new AudioContext(); void ctx.resume() } catch { /* egal */ }
}
