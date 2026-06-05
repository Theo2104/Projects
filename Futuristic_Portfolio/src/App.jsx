import { useEffect, useMemo, useRef, useState } from 'react'
import Scene3D from './components/Scene3D'
import UIOverlay from './components/UIOverlay'
import WebGLFallback from './components/WebGLFallback'
import { isWebGLAvailable } from './utils/webgl'
import { getParams } from './utils/params'
import { useProjects } from './hooks/useProjects'

// Verweildauer pro Station der geführten Tour (inkl. ~1,5 s Kamera-Flug).
const TOUR_DWELL = 4800

// ------------------------------------------------------------------
//  Einstiegspunkt. Lädt die Projekte dynamisch aus dem Repo, hält die
//  Auswahl (selectedId) als Single Source of Truth und orchestriert die
//  geführte Tour. Personalisierung über URL-Parameter
//  (?for=…&highlight=…&tour=1) ordnet Projekte um und startet die Tour.
// ------------------------------------------------------------------
export default function App() {
  const webgl = useMemo(() => isWebGLAvailable(), [])
  const params = useMemo(() => getParams(), [])
  const { projects, state: projectsState } = useProjects()

  const [selectedId, setSelectedId] = useState(null)
  const [touring, setTouring] = useState(false)
  const [step, setStep] = useState(0)

  // Hervorgehobene Projekte zuerst (für Menü + Tour-Reihenfolge).
  const orderedProjects = useMemo(() => {
    if (!params.highlight.length) return projects
    const hi = params.highlight
      .map((id) => projects.find((p) => p.id === id))
      .filter(Boolean)
    const rest = projects.filter((p) => !params.highlight.includes(p.id))
    return [...hi, ...rest]
  }, [params.highlight, projects])

  // Nutzer-Auswahl (Klick) beendet eine laufende Tour.
  const handleSelect = (id) => {
    if (touring) setTouring(false)
    setSelectedId(id)
  }

  const startTour = () => {
    if (!orderedProjects.length) return
    setStep(0)
    setTouring(true)
  }
  const stopTour = () => {
    setTouring(false)
    setSelectedId(null)
  }

  // Tour-Ablauf: Station für Station weiterschalten, dann heimfliegen.
  useEffect(() => {
    if (!touring) return
    if (step >= orderedProjects.length) {
      setSelectedId(null)
      const t = setTimeout(() => setTouring(false), 1800)
      return () => clearTimeout(t)
    }
    setSelectedId(orderedProjects[step].id)
    const t = setTimeout(() => setStep((s) => s + 1), TOUR_DWELL)
    return () => clearTimeout(t)
  }, [touring, step, orderedProjects])

  // Auto-Start via ?tour=1, sobald die Projekte geladen sind.
  const autoStarted = useRef(false)
  useEffect(() => {
    if (!webgl || !params.autoplay || !orderedProjects.length) return
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduce) return
    const t = setTimeout(() => {
      if (autoStarted.current) return
      autoStarted.current = true
      startTour()
    }, 1600)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [webgl, params.autoplay, orderedProjects.length])

  if (!webgl) {
    return <WebGLFallback projects={projects} />
  }

  // Erstladebildschirm, bis die Projektliste steht.
  if (!projects.length && projectsState === 'loading') {
    return <BootScreen />
  }

  return (
    <main className="bg-stage relative h-screen w-screen overflow-hidden">
      <Scene3D
        projects={projects}
        selectedId={selectedId}
        onSelect={handleSelect}
      />
      <UIOverlay
        selectedId={selectedId}
        onSelect={handleSelect}
        projects={orderedProjects}
        company={params.company}
        highlight={params.highlight}
        tour={{
          active: touring,
          index: Math.min(step + 1, orderedProjects.length),
          total: orderedProjects.length,
        }}
        onStartTour={startTour}
        onStopTour={stopTour}
      />
    </main>
  )
}

// Schlichter Ladebildschirm, während die Repo-Projekte abgerufen werden.
function BootScreen() {
  return (
    <main className="bg-stage flex h-screen w-screen flex-col items-center justify-center gap-4">
      <div className="h-10 w-10 animate-spin rounded-full border-2 border-white/15 border-t-cyan-400" />
      <p className="text-xs tracking-[0.3em] text-cyan-300/70 uppercase">
        System wird kartiert …
      </p>
    </main>
  )
}
