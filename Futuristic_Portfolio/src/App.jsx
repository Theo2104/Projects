import { useMemo, useState } from 'react'
import Scene3D from './components/Scene3D'
import UIOverlay from './components/UIOverlay'
import WebGLFallback from './components/WebGLFallback'
import { isWebGLAvailable } from './utils/webgl'

// ------------------------------------------------------------------
//  Einstiegspunkt: hält die Auswahl (selectedId) als Single Source of
//  Truth. Sowohl die 3D-Szene als auch das UI-Overlay reagieren darauf.
//  Ohne WebGL-Support wird der HTML-Fallback gerendert.
// ------------------------------------------------------------------
export default function App() {
  const webgl = useMemo(() => isWebGLAvailable(), [])
  const [selectedId, setSelectedId] = useState(null)

  if (!webgl) {
    return <WebGLFallback />
  }

  return (
    <main className="bg-stage relative h-screen w-screen overflow-hidden">
      <Scene3D selectedId={selectedId} onSelect={setSelectedId} />
      <UIOverlay selectedId={selectedId} onSelect={setSelectedId} />
    </main>
  )
}
