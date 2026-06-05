import { useEffect, useState } from 'react'
import { BRANCH } from '../data/projectMeta'
import { timeAgo } from './useGitHubStats'

// ------------------------------------------------------------------
//  Lädt pro Projekt (= Ordner im Repo) die Live-Details für die Karte:
//   - letzter Commit, der diesen Ordner betraf  → "Aktualisiert vor …"
//   - falls keine kuratierte Beschreibung existiert: ein Auszug aus der
//     README des Ordners (automatische, individuelle Beschreibung).
//
//  Ergebnisse werden 1 h in localStorage gecacht. Wird nur geladen,
//  solange die Karte sichtbar ist (Hook lebt in der Karten-Komponente).
// ------------------------------------------------------------------
const TTL = 1000 * 60 * 60

export function useProjectDetails(project) {
  const [updatedAt, setUpdatedAt] = useState(null)
  const [readme, setReadme] = useState(null)
  const [state, setState] = useState('idle')

  const repo = project?.repo
  const path = project?.path
  const needReadme = !project?.description
  const codeUrl = repo
    ? `https://github.com/${repo}/tree/${BRANCH}/${path}`
    : null

  useEffect(() => {
    if (!repo || !path) return
    let cancelled = false
    const cacheKey = `ghDetails:${repo}/${path}`

    // Cache
    try {
      const raw = localStorage.getItem(cacheKey)
      if (raw) {
        const c = JSON.parse(raw)
        if (Date.now() - c.t < TTL) {
          setUpdatedAt(c.d.updatedAt || null)
          setReadme(c.d.readme || null)
          setState('done')
          return
        }
      }
    } catch {
      /* ignorieren */
    }

    setState('loading')

    const commitReq = fetch(
      `https://api.github.com/repos/${repo}/commits?path=${encodeURIComponent(
        path,
      )}&sha=${BRANCH}&per_page=1`,
    )
      .then((r) => (r.ok ? r.json() : null))
      .then((j) => j?.[0]?.commit?.committer?.date || null)
      .catch(() => null)

    const readmeReq = needReadme
      ? fetch(
          `https://api.github.com/repos/${repo}/contents/${encodeURIComponent(
            path,
          )}/README.md?ref=${BRANCH}`,
        )
          .then((r) => (r.ok ? r.json() : null))
          .then((j) => (j?.content ? extractIntro(decodeBase64(j.content)) : null))
          .catch(() => null)
      : Promise.resolve(null)

    Promise.all([commitReq, readmeReq]).then(([date, intro]) => {
      if (cancelled) return
      setUpdatedAt(date)
      setReadme(intro)
      setState('done')
      try {
        localStorage.setItem(
          cacheKey,
          JSON.stringify({ t: Date.now(), d: { updatedAt: date, readme: intro } }),
        )
      } catch {
        /* ignorieren */
      }
    })

    return () => {
      cancelled = true
    }
  }, [repo, path, needReadme])

  return {
    description: project?.description || readme || null,
    updatedAtLabel: updatedAt ? timeAgo(updatedAt) : null,
    codeUrl,
    state,
  }
}

// Base64 (GitHub) → UTF-8-String (umlautsicher).
function decodeBase64(b64) {
  try {
    const bin = atob(b64.replace(/\n/g, ''))
    const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0))
    return new TextDecoder('utf-8').decode(bytes)
  } catch {
    return ''
  }
}

// Ersten sinnvollen Absatz aus einer README ziehen (Überschriften,
// Badges, Codeblöcke überspringen).
function extractIntro(md) {
  if (!md) return null
  const lines = md.split('\n')
  for (const raw of lines) {
    const line = raw.trim()
    if (!line) continue
    if (/^[#>!\-*`|]/.test(line)) continue // Heading, Badge, Liste, Code, Tabelle
    if (/^\[.*\]\(.*\)$/.test(line)) continue // reiner Link
    return line.length > 220 ? line.slice(0, 217).trimEnd() + '…' : line
  }
  return null
}
