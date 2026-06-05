import { Suspense, useRef, useState } from 'react'
import { Canvas } from '@react-three/fiber'
import {
  OrbitControls,
  AdaptiveEvents,
  PerformanceMonitor,
} from '@react-three/drei'
import { HOME_CAMERA } from '../data/projects'
import { useQuality } from '../hooks/useQuality'
import Starfield from './Starfield'
import Sun from './Sun'
import Planet from './Planet'
import OrbitRing from './OrbitRing'
import CameraRig from './CameraRig'
import Effects from './Effects'
import Loader from './Loader'

// ------------------------------------------------------------------
//  Begehbares Sonnensystem (CLAUDE.md, Abschnitt 3.A).
//  Die Kamera sitzt mitten im System; OrbitControls (Ziel = Ursprung)
//  erlauben freies 360°-Umsehen mit begrenztem Zoom.
//
//  Performance: Der teuerste Faktor ist die Pixelmenge (Full-Screen ×
//  devicePixelRatio) in Kombination mit dem Bloom-Pass. Der
//  PerformanceMonitor misst die reale FPS und senkt dynamisch die
//  Auflösung bzw. schaltet Bloom ab, wenn das Gerät nicht mitkommt.
// ------------------------------------------------------------------
export default function Scene3D({ projects, selectedId, onSelect }) {
  const controlsRef = useRef()
  // Geteilte Live-Positionen der Planeten (für das CameraRig).
  const positionsRef = useRef({})
  const quality = useQuality()

  const maxDpr = quality.dpr[1]
  // Einmaliges Herunterschalten bei dauerhaft niedriger FPS (kein
  // ständiges Nachregeln → kein Re-Render-Thrashing).
  const [degraded, setDegraded] = useState(false)
  const dpr = degraded ? 1 : maxDpr
  const bloomOn = quality.bloom && !degraded

  return (
    <Canvas
      className="absolute inset-0"
      dpr={dpr}
      gl={{ antialias: true, powerPreference: 'high-performance' }}
      camera={{ position: HOME_CAMERA.position, fov: 50, near: 0.1, far: 200 }}
      onPointerMissed={() => onSelect(null)}
    >
      {/* Bei dauerhaft niedriger FPS einmalig auf dpr 1 + Bloom aus
          schalten (rettet schwache GPUs / High-DPI-Displays). */}
      <PerformanceMonitor flipflops={3} onDecline={() => setDegraded(true)} />

      <color attach="background" args={['#05060a']} />

      {/* Grundhelligkeit: hebt die Texturen auch auf den Nachtseiten an,
          damit die Planeten von überall lesbar bleiben (Showcase > Realismus). */}
      <ambientLight intensity={0.35} />

      <Suspense fallback={<Loader />}>
        <Starfield count={quality.stars} />

        {/* Zentrale Sonne (Lichtquelle des Systems) */}
        <Sun />

        {/* Umlaufbahnen + Planeten */}
        {quality.orbitRings &&
          projects.map((p) => (
            <OrbitRing key={`ring-${p.id}`} radius={p.orbitRadius} />
          ))}

        {projects.map((project) => (
          <Planet
            key={project.id}
            project={project}
            quality={quality}
            positionsRef={positionsRef}
            isSelected={selectedId === project.id}
            isDimmed={selectedId !== null && selectedId !== project.id}
            onSelect={onSelect}
          />
        ))}
      </Suspense>

      {/* Kamera-Fahrten bei Auswahl */}
      <CameraRig
        projects={projects}
        selectedId={selectedId}
        controlsRef={controlsRef}
        positionsRef={positionsRef}
      />

      {/* Freies Umsehen, begrenzter Zoom (keine Desorientierung) */}
      <OrbitControls
        ref={controlsRef}
        makeDefault
        enablePan={false}
        minDistance={4}
        maxDistance={32}
        minPolarAngle={0.15}
        maxPolarAngle={Math.PI * 0.9}
        rotateSpeed={0.5}
        zoomSpeed={0.6}
        target={HOME_CAMERA.target}
      />

      {/* Weltraum-Glühen (dynamisch abschaltbar bei schwacher Hardware) */}
      <Effects enabled={bloomOn} />

      <AdaptiveEvents />
    </Canvas>
  )
}
