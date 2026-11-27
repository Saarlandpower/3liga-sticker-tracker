import { useEffect, useRef, useState } from 'preact/hooks'

export type CameraError = 'denied' | 'notfound' | 'insecure' | 'other'

/** Startet die Rückkamera und hängt sie an ein <video>. Stoppt sie beim Verlassen. */
export function useCamera(active = true) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [error, setError] = useState<{ kind: CameraError; message: string } | null>(null)
  const [ready, setReady] = useState(false)
  const [torch, setTorch] = useState<{ supported: boolean; on: boolean }>({ supported: false, on: false })
  const trackRef = useRef<MediaStreamTrack | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!active) return
    let stream: MediaStream | null = null
    let cancelled = false
    setError(null)
    setReady(false)
    ;(async () => {
      if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
        setError({ kind: 'insecure', message: 'Die Kamera funktioniert nur über eine sichere Verbindung (https).' })
        return
      }
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          audio: false,
          video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
        })
        if (cancelled) { stream.getTracks().forEach((t) => t.stop()); return }
        const track = stream.getVideoTracks()[0]
        trackRef.current = track
        const caps = (track.getCapabilities?.() ?? {}) as MediaTrackCapabilities & { torch?: boolean }
        setTorch({ supported: !!caps.torch, on: false })
        const v = videoRef.current!
        v.srcObject = stream
        v.setAttribute('playsinline', 'true')
        v.muted = true
        await v.play()
        setReady(true)
      } catch (e) {
        const name = (e as DOMException).name
        if (name === 'NotAllowedError' || name === 'SecurityError') setError({ kind: 'denied', message: '' })
        else if (name === 'NotFoundError' || name === 'OverconstrainedError') setError({ kind: 'notfound', message: 'Keine Kamera gefunden.' })
        else setError({ kind: 'other', message: `Kamera konnte nicht gestartet werden (${name || String(e)}).` })
      }
    })()
    return () => {
      cancelled = true
      stream?.getTracks().forEach((t) => t.stop())
      trackRef.current = null
    }
  }, [active, attempt])

  const toggleTorch = async () => {
    const t = trackRef.current
    if (!t) return
    try {
      await t.applyConstraints({ advanced: [{ torch: !torch.on } as MediaTrackConstraintSet] })
      setTorch((x) => ({ ...x, on: !x.on }))
    } catch { setTorch({ supported: false, on: false }) }
  }

  return { videoRef, error, ready, torch, toggleTorch, retry: () => setAttempt((a) => a + 1) }
}
