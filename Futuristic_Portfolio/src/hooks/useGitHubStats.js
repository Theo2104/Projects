import { useEffect, useState } from 'react'

// ------------------------------------------------------------------
//  Lädt Live-Statistiken eines öffentlichen GitHub-Repos (Sterne,
//  letzter Push, Hauptsprache). Ergebnisse werden 1 h in localStorage
//  gecacht, um das ungeauthentifizierte Rate-Limit (60 Anfragen/h)
//  zu schonen. api.github.com erlaubt CORS, daher rein clientseitig.
//
//  `repo` im Format 'owner/name'. Ist es null/leer, passiert nichts.
// ------------------------------------------------------------------
const TTL = 1000 * 60 * 60 // 1 Stunde

export function useGitHubStats(repo) {
  const [data, setData] = useState(null)
  const [state, setState] = useState('idle') // idle | loading | done | error

  useEffect(() => {
    if (!repo) {
      setState('idle')
      setData(null)
      return
    }

    let cancelled = false
    const cacheKey = `gh:${repo}`

    // 1) Cache prüfen
    try {
      const raw = localStorage.getItem(cacheKey)
      if (raw) {
        const cached = JSON.parse(raw)
        if (Date.now() - cached.t < TTL) {
          setData(cached.d)
          setState('done')
          return
        }
      }
    } catch {
      /* localStorage nicht verfügbar – ignorieren */
    }

    // 2) Live laden
    setState('loading')
    fetch(`https://api.github.com/repos/${repo}`)
      .then((r) => {
        if (!r.ok) throw new Error(`GitHub ${r.status}`)
        return r.json()
      })
      .then((j) => {
        if (cancelled) return
        const d = {
          stars: j.stargazers_count,
          forks: j.forks_count,
          pushedAt: j.pushed_at,
          language: j.language,
          url: j.html_url,
        }
        setData(d)
        setState('done')
        try {
          localStorage.setItem(cacheKey, JSON.stringify({ t: Date.now(), d }))
        } catch {
          /* ignorieren */
        }
      })
      .catch(() => {
        if (!cancelled) setState('error')
      })

    return () => {
      cancelled = true
    }
  }, [repo])

  return { data, state }
}

// Kompakte, deutsche Relativzeit ("vor 3 Tagen").
export function timeAgo(iso) {
  if (!iso) return ''
  const diff = Date.now() - new Date(iso).getTime()
  const sec = Math.round(diff / 1000)
  const units = [
    ['Jahr', 'Jahren', 31536000],
    ['Monat', 'Monaten', 2592000],
    ['Woche', 'Wochen', 604800],
    ['Tag', 'Tagen', 86400],
    ['Stunde', 'Stunden', 3600],
    ['Minute', 'Minuten', 60],
  ]
  for (const [sing, plur, s] of units) {
    const v = Math.floor(sec / s)
    if (v >= 1) return `vor ${v} ${v === 1 ? sing : plur}`
  }
  return 'gerade eben'
}
