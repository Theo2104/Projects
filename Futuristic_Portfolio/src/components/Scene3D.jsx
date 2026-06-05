import { Suspense, useRef } from 'react'
import { Canvas } from '@react-three/fiber'
import { OrbitControls, AdaptiveDpr, AdaptiveEvents } from '@react-three/drei'
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
//  erlauben freies 360°-Umsehen mit begrenztem Zoom. Bündelt Sterne,
//  Sonne, Planeten, Umlaufbahnen, Kamera-Fahrten und Bloom.
// ------------------------------------------------------------------
export default function Scene3D({ projects, selectedId, onSelect }) {
  const controlsRef = useRef()
  // Geteilte Live-Positionen der Planeten (für das CameraRig).
  const positionsRef = useRef({})
  const quality = useQuality()

  return (
    <Canvas
      className="absolute inset-0"
      dpr={quality.dpr}
      gl={{ antialias: true, powerPreference: 'high-performance' }}
      camera={{ position: HOME_CAMERA.position, fov: 50, near: 0.1, far: 200 }}
      onPointerMissed={() => onSelect(null)}
    >
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

      {/* Weltraum-Glühen (auf Mobil deaktiviert) */}
      <Effects enabled={quality.bloom} />

      <AdaptiveDpr pixelated />
      <AdaptiveEvents />
    </Canvas>
  )
}
