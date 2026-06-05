import { Suspense, useRef } from 'react'
import { Canvas } from '@react-three/fiber'
import {
  OrbitControls,
  Environment,
  Lightformer,
  AdaptiveDpr,
  AdaptiveEvents,
} from '@react-three/drei'
import { projects, HOME_CAMERA } from '../data/projects'
import { useQuality } from '../hooks/useQuality'
import ProjectObject from './ProjectObject'
import Particles from './Particles'
import CameraRig from './CameraRig'
import Loader from './Loader'

// ------------------------------------------------------------------
//  Bildschirmfüllende 3D-Szene (CLAUDE.md, Abschnitt 3.A).
//  Hält Lichter, Umgebung, Partikel, alle Projekt-Objekte sowie die
//  GSAP-gesteuerte Kamera. OrbitControls ist auf Schwenken begrenzt
//  (kein Zoom), damit der Nutzer die Orientierung behält.
// ------------------------------------------------------------------
export default function Scene3D({ selectedId, onSelect }) {
  const controlsRef = useRef()
  const quality = useQuality()

  return (
    <Canvas
      className="absolute inset-0"
      dpr={quality.dpr}
      gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
      camera={{ position: HOME_CAMERA.position, fov: 45, near: 0.1, far: 100 }}
      onPointerMissed={() => onSelect(null)}
    >
      {/* Grund-Beleuchtung */}
      <ambientLight intensity={0.35} />
      <directionalLight position={[5, 8, 5]} intensity={1.2} color="#ffffff" />
      <pointLight position={[-8, -4, -6]} intensity={40} color="#a855f7" distance={30} />
      <pointLight position={[8, 6, -4]} intensity={30} color="#22d3ee" distance={30} />

      <Suspense fallback={<Loader />}>
        {/* Selbst-erzeugte Umgebung für Glas-Reflexionen (offline-fähig) */}
        <Environment resolution={quality.isMobile ? 128 : 256}>
          <Lightformer
            intensity={2}
            position={[0, 4, -6]}
            scale={[10, 6, 1]}
            color="#22d3ee"
          />
          <Lightformer
            intensity={1.6}
            position={[-6, 1, 2]}
            scale={[6, 6, 1]}
            color="#a855f7"
          />
          <Lightformer
            intensity={1}
            position={[6, -2, 2]}
            scale={[6, 6, 1]}
            color="#ffffff"
          />
        </Environment>

        {/* Alle Projekt-Objekte */}
        {projects.map((project) => (
          <ProjectObject
            key={project.id}
            project={project}
            quality={quality}
            isSelected={selectedId === project.id}
            isDimmed={selectedId !== null && selectedId !== project.id}
            onSelect={onSelect}
          />
        ))}

        {/* Reaktives Partikelfeld */}
        <Particles count={quality.sparkles} />
      </Suspense>

      {/* Kamera-Fahrten bei Auswahl */}
      <CameraRig selectedId={selectedId} controlsRef={controlsRef} />

      {/* Eingeschränkte Steuerung: kein Zoom, begrenzte Vertikaldrehung */}
      <OrbitControls
        ref={controlsRef}
        makeDefault
        enableZoom={false}
        enablePan={false}
        minPolarAngle={Math.PI / 3}
        maxPolarAngle={Math.PI / 1.8}
        rotateSpeed={0.5}
        target={HOME_CAMERA.target}
      />

      {/* Automatische Performance-Anpassung */}
      <AdaptiveDpr pixelated />
      <AdaptiveEvents />
    </Canvas>
  )
}
