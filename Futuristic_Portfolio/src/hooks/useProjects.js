import { useEffect, useState } from 'react'
import { REPO, BRANCH, META, buildProjects } from '../data/projectMeta'

// ------------------------------------------------------------------
//  Lädt die Top-Level-Ordner des Repos über die GitHub-API und baut
//  daraus die Planeten. Dadurch erscheint/verschwindet ein Planet
//  automatisch, sobald ein Ordner gepusht/gelöscht wird.
//
//  - Ergebnis wird 30 min in localStorage gecacht (Rate-Limit schonen).
//  - Schlägt die API fehl, wird auf die kuratierten META-Projekte
//    zurückgefallen, damit immer etwas angezeigt wird.
// ------------------------------------------------------------------
const TTL = 1000 * 60 * 30 // 30 Minuten

export function useProjects() {
  const [projects, setProjects] = useState([])
  const [state, setState] = useState('loading') // loading | done | fallback

  useEffect(() => {
    let cancelled = false
    const cacheKey = `repoDirs:${REPO}`

    // 1) Cache
    try {
      const raw = localStorage.getItem(cacheKey)
      if (raw) {
        const c = JSON.parse(raw)
        if (Date.now() - c.t < TTL) {
          setProjects(buildProjects(c.d))
          setState('done')
          return
        }
      }
    } catch {
      /* ignorieren */
    }

    // 2) Live aus der GitHub-API
    fetch(`https://api.github.com/repos/${REPO}/contents?ref=${BRANCH}`)
      .then((r) => {
        if (!r.ok) throw new Error(`GitHub ${r.status}`)
        return r.json()
      })
      .then((items) => {
        if (cancelled) return
        const dirs = items
          .filter((it) => it.type === 'dir')
          .map((it) => it.name)
        setProjects(buildProjects(dirs))
        setState('done')
        try {
          localStorage.setItem(cacheKey, JSON.stringify({ t: Date.now(), d: dirs }))
        } catch {
          /* ignorieren */
        }
      })
      .catch(() => {
        if (cancelled) return
        // Fallback: kuratierte Projekte, damit die Szene nie leer ist.
        setProjects(buildProjects(Object.keys(META)))
        setState('fallback')
      })

    return () => {
      cancelled = true
    }
  }, [])

  return { projects, state }
}
