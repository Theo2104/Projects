import { useEffect, useMemo, useRef, useState } from 'react'
import { useFrame } from '@react-three/fiber'
import { RoundedBox, useGLTF } from '@react-three/drei'
import gsap from 'gsap'
import * as THREE from 'three'

// ------------------------------------------------------------------
//  Ein einzelnes, schwebendes Projekt-Objekt.
//
//  - Schwebe-/Rotations-Animation läuft performant über useFrame
//    (direkte Mutation von .rotation / .position, kein State).
//  - Hover skaliert das Objekt flüssig über GSAP und intensiviert das
//    begleitende Punktlicht.
//  - Geometrie ist standardmäßig ein gläserner Platzhalter; sobald
//    `project.model` gesetzt ist, wird stattdessen das .glb geladen.
// ------------------------------------------------------------------
export default function ProjectObject({
  project,
  isSelected,
  isDimmed,
  onSelect,
  quality,
}) {
  const groupRef = useRef()
  const lightRef = useRef()
  const [hovered, setHovered] = useState(false)

  const baseY = project.position[1]
  // Jedes Objekt bekommt eine eigene Phase, damit sie versetzt schweben.
  const phase = useMemo(() => Math.random() * Math.PI * 2, [])

  // Schwebe- & Rotationsschleife (performant, ohne Re-Render)
  useFrame((state, delta) => {
    const g = groupRef.current
    if (!g) return
    const t = state.clock.elapsedTime
    g.position.y = baseY + Math.sin(t * 0.9 + phase) * 0.18
    g.rotation.y += delta * 0.25
    g.rotation.x = Math.sin(t * 0.5 + phase) * 0.08
  })

  // Hover/Auswahl → GSAP-Skalierung + Lichtintensität
  useEffect(() => {
    const active = hovered || isSelected
    if (groupRef.current) {
      gsap.to(groupRef.current.scale, {
        x: active ? 1.25 : 1,
        y: active ? 1.25 : 1,
        z: active ? 1.25 : 1,
        duration: 0.6,
        ease: 'elastic.out(1, 0.6)',
      })
    }
    if (lightRef.current) {
      gsap.to(lightRef.current, {
        intensity: active ? 6 : 1.4,
        duration: 0.4,
        ease: 'power2.out',
      })
    }
  }, [hovered, isSelected])

  // Cursor-Feedback
  useEffect(() => {
    document.body.style.cursor = hovered ? 'pointer' : 'auto'
    return () => {
      document.body.style.cursor = 'auto'
    }
  }, [hovered])

  return (
    <group
      ref={groupRef}
      position={project.position}
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
      {project.model ? (
        <GLTFModel url={project.model} />
      ) : (
        <PlaceholderGeometry project={project} quality={quality} />
      )}

      {/* Begleitendes Punktlicht in Projektfarbe, reagiert auf Hover */}
      <pointLight
        ref={lightRef}
        color={project.color}
        intensity={1.4}
        distance={6}
        position={[0, 0, 1.2]}
      />

      {/* Sanfter Dimm-Schleier, wenn ein anderes Objekt fokussiert ist */}
      {isDimmed && (
        <mesh>
          <sphereGeometry args={[2.4, 16, 16]} />
          <meshBasicMaterial color="#0b0b0f" transparent opacity={0.35} />
        </mesh>
      )}
    </group>
  )
}

// ------------------------------------------------------------------
//  Platzhalter-Geometrie im Frosted-Glass-Look (MeshPhysicalMaterial).
//  Auf Mobilgeräten (quality.transmission === false) wird auf ein
//  günstigeres Standard-Material zurückgefallen.
// ------------------------------------------------------------------
function PlaceholderGeometry({ project, quality }) {
  const materialProps = quality.transmission
    ? {
        transmission: 1,
        thickness: 1.5,
        roughness: 0.25,
        ior: 1.4,
        clearcoat: 1,
        clearcoatRoughness: 0.2,
        attenuationColor: project.color,
        attenuationDistance: 2.5,
        color: project.color,
        emissive: project.color,
        emissiveIntensity: 0.12,
        transparent: true,
        opacity: 0.9,
      }
    : {
        // Mobiler Fallback: kein teures Transmission-Rendering
        color: project.color,
        emissive: project.color,
        emissiveIntensity: 0.25,
        roughness: 0.3,
        metalness: 0.4,
        transparent: true,
        opacity: 0.85,
      }

  const material = quality.transmission ? (
    <meshPhysicalMaterial {...materialProps} />
  ) : (
    <meshStandardMaterial {...materialProps} />
  )

  switch (project.geometry) {
    case 'bag':
      return (
        <RoundedBox args={[1.2, 1.5, 0.55]} radius={0.16} smoothness={4}>
          {material}
        </RoundedBox>
      )
    case 'bubble':
      return (
        <mesh>
          <sphereGeometry args={[0.95, 48, 48]} />
          {material}
        </mesh>
      )
    case 'cube':
    default:
      return (
        <mesh rotation={[0.4, 0.4, 0]}>
          <boxGeometry args={[1.25, 1.25, 1.25]} />
          {material}
        </mesh>
      )
  }
}

// ------------------------------------------------------------------
//  Lädt ein externes .glb-Modell. Wird nur gerendert, wenn ein Pfad
//  gesetzt ist; die Suspense-Grenze liegt in Scene3D.
// ------------------------------------------------------------------
function GLTFModel({ url }) {
  const { scene } = useGLTF(url)
  // Klonen, damit dasselbe Modell mehrfach genutzt werden könnte.
  const cloned = useMemo(() => scene.clone(true), [scene])
  return <primitive object={cloned} />
}
