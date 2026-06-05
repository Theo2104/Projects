import { useEffect, useMemo, useRef, useState } from 'react'
import { useFrame } from '@react-three/fiber'
import { Html, useTexture, useGLTF } from '@react-three/drei'
import gsap from 'gsap'

// ------------------------------------------------------------------
//  Ein Planet = ein Projekt (CLAUDE.md, Abschnitt 3.C).
//
//  - Umlaufbahn & Eigenrotation laufen performant über useFrame
//    (direkte Mutation, kein State).
//  - Beim Fokus (isSelected) wird die Umlaufbahn „eingefroren", damit
//    die Kamera ein stabiles Ziel hat. `positionsRef` teilt die aktuelle
//    Weltposition mit dem CameraRig.
//  - Hover skaliert via GSAP und verstärkt das Eigenleuchten.
// ------------------------------------------------------------------
export default function Planet({
  project,
  isSelected,
  isDimmed,
  onSelect,
  quality,
  positionsRef,
}) {
  const groupRef = useRef() // trägt die Orbit-Position
  const spinRef = useRef() // Eigenrotation des Planeten
  const matRef = useRef()
  const [hovered, setHovered] = useState(false)

  // Akkumulierter Bahnwinkel — wird nur fortgeschrieben, wenn NICHT
  // fokussiert (sauberes Pausieren & Fortsetzen ohne Sprung).
  const angle = useRef(project.startAngle)

  useFrame((_, delta) => {
    if (!isSelected) angle.current += delta * project.orbitSpeed
    const a = angle.current
    const g = groupRef.current
    if (g) {
      g.position.x = Math.cos(a) * project.orbitRadius
      g.position.z = Math.sin(a) * project.orbitRadius
      // Aktuelle Position für das CameraRig hinterlegen
      if (positionsRef) {
        positionsRef.current[project.id] = {
          x: g.position.x,
          y: g.position.y,
          z: g.position.z,
        }
      }
    }
    if (spinRef.current) spinRef.current.rotation.y += delta * 0.25
  })

  // Hover / Auswahl → GSAP-Skalierung + Leuchtkraft
  useEffect(() => {
    const active = hovered || isSelected
    if (spinRef.current) {
      gsap.to(spinRef.current.scale, {
        x: active ? 1.18 : 1,
        y: active ? 1.18 : 1,
        z: active ? 1.18 : 1,
        duration: 0.5,
        ease: 'power3.out',
      })
    }
    if (matRef.current) {
      gsap.to(matRef.current, {
        emissiveIntensity: active ? 1.1 : 0.35,
        duration: 0.5,
        ease: 'power2.out',
      })
    }
  }, [hovered, isSelected])

  useEffect(() => {
    document.body.style.cursor = hovered ? 'pointer' : 'auto'
    return () => {
      document.body.style.cursor = 'auto'
    }
  }, [hovered])

  return (
    <group ref={groupRef}>
      <group
        ref={spinRef}
        onPointerOver={(e) => {
          e.stopPropagation()
          setHovered(true)
        }}
        onPointerOut={() => setHovered(false)}
        onClick={(e) => {
          e.stopPropagation()
          onSelect(project.id)
        }}
      >
        {/* Planeten-Körper */}
        {project.model ? (
          <GLTFPlanet url={project.model} />
        ) : (
          <mesh>
            <sphereGeometry
              args={[project.size, quality.planetSegments, quality.planetSegments]}
            />
            <PlanetMaterial project={project} matRef={matRef} />
          </mesh>
        )}

        {/* Optionaler Saturn-Ring */}
        {project.ring && (
          <mesh rotation={[Math.PI / 2.4, 0, 0]}>
            <ringGeometry args={[project.size * 1.4, project.size * 2.1, 64]} />
            <meshBasicMaterial
              color={project.color}
              transparent
              opacity={0.35}
              side={2}
              depthWrite={false}
            />
          </mesh>
        )}

        {/* Name-Label nur beim Hover (bei Auswahl zeigt die Karte den Namen) */}
        {hovered && !isSelected && (
          <Html center distanceFactor={14} position={[0, project.size + 0.9, 0]}>
            <div className="pointer-events-none -translate-y-full rounded-full border border-white/10 bg-black/60 px-3 py-1 text-[11px] whitespace-nowrap text-white/90 backdrop-blur">
              {project.name}
            </div>
          </Html>
        )}
      </group>

      {/* Dimm-Schleier, wenn ein anderer Planet fokussiert ist */}
      {isDimmed && (
        <mesh>
          <sphereGeometry args={[project.size * 1.6, 16, 16]} />
          <meshBasicMaterial color="#05060a" transparent opacity={0.45} depthWrite={false} />
        </mesh>
      )}
    </group>
  )
}

// Farb-/Textur-Material des Planeten. Greift auf useTexture nur zu,
// wenn tatsächlich eine Textur gesetzt ist (Hook-Regeln beachtet).
function PlanetMaterial({ project, matRef }) {
  if (project.texture) {
    return <TexturedMaterial project={project} matRef={matRef} />
  }
  return (
    <meshStandardMaterial
      ref={matRef}
      color={project.color}
      emissive={project.color}
      emissiveIntensity={0.35}
      roughness={0.65}
      metalness={0.15}
    />
  )
}

function TexturedMaterial({ project, matRef }) {
  const map = useTexture(project.texture)
  return (
    <meshStandardMaterial
      ref={matRef}
      map={map}
      color={project.color}
      emissive={project.color}
      emissiveIntensity={0.35}
      roughness={0.8}
      metalness={0.1}
    />
  )
}

function GLTFPlanet({ url }) {
  const { scene } = useGLTF(url)
  const cloned = useMemo(() => scene.clone(true), [scene])
  return <primitive object={cloned} />
}
