import { useEffect, useState } from 'preact/hooks'

/** Minimaler Hash-Router: #/album?filter=FEHLEND */
export type Route = { name: string; params: Record<string, string> }

function parse(): Route {
  const h = location.hash.replace(/^#\/?/, '')
  const [name, q] = h.split('?')
  return { name: name || 'overview', params: Object.fromEntries(new URLSearchParams(q ?? '')) }
}

export function navigate(name: string, params: Record<string, string> = {}, replace = false) {
  const q = new URLSearchParams(params).toString()
  const url = `#/${name}${q ? `?${q}` : ''}`
  if (replace) history.replaceState(null, '', url)
  else history.pushState(null, '', url)
  window.dispatchEvent(new HashChangeEvent('hashchange'))
}

export function useRoute(): Route {
  const [r, setR] = useState(parse)
  useEffect(() => {
    const on = () => { setR(parse()); window.scrollTo(0, 0) }
    window.addEventListener('hashchange', on)
    window.addEventListener('popstate', on)
    return () => { window.removeEventListener('hashchange', on); window.removeEventListener('popstate', on) }
  }, [])
  return r
}
