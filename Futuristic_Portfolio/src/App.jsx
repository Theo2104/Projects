import { useEffect, useMemo, useRef, useState } from 'react'
import Scene3D from './components/Scene3D'
import UIOverlay from './components/UIOverlay'
import WebGLFallback from './components/WebGLFallback'
import { isWebGLAvailable } from './utils/webgl'
import { getParams } from './utils/params'
import { projects } from './data/projects'

// Verweildauer pro Station der geführten Tour (inkl. ~1,5 s Kamera-Flug).
const TOUR_DWELL = 4800

// ------------------------------------------------------------------
//  Einstiegspunkt. Hält die Auswahl (selectedId) als Single Source of
//  Truth und orchestriert die geführte Tour. Personalisierung über
//  URL-Parameter (?for=…&highlight=…&tour=1) ordnet Projekte um und
//  startet die Tour optional automatisch.
// ------------------------------------------------------------------
export default function App() {
  const webgl = useMemo(() => isWebGLAvailable(), [])
  const params = useMemo(() => getParams(), [])

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
  }, [params.highlight])

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
      setSelectedId(null) // zurück zur Übersicht
      const t = setTimeout(() => setTouring(false), 1800)
      return () => clearTimeout(t)
    }
    setSelectedId(orderedProjects[step].id)
    const t = setTimeout(() => setStep((s) => s + 1), TOUR_DWELL)
    return () => clearTimeout(t)
  }, [touring, step, orderedProjects])

  // Auto-Start via ?tour=1 (z. B. in personalisierten Bewerbungs-Links),
  // außer der Nutzer bevorzugt reduzierte Bewegung.
  const autoStarted = useRef(false)
  useEffect(() => {
    if (!webgl || !params.autoplay) return
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduce) return
    // Guard im Timer (nicht davor), damit der StrictMode-Doppel-Mount
    // den Start nicht wegräumt: der erste Timer wird beim Cleanup
    // gelöscht, der zweite startet die Tour einmalig.
    const t = setTimeout(() => {
      if (autoStarted.current) return
      autoStarted.current = true
      startTour()
    }, 1600)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [webgl, params.autoplay])

  if (!webgl) {
    return <WebGLFallback />
  }

  return (
    <main className="bg-stage relative h-screen w-screen overflow-hidden">
      <Scene3D selectedId={selectedId} onSelect={handleSelect} />
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
