import { useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import { Sparkles } from '@react-three/drei'
import * as THREE from 'three'

// ------------------------------------------------------------------
//  Dezentes Partikelfeld (Drei <Sparkles>). Das gesamte Feld neigt
//  sich sanft in Richtung der Mausposition, wodurch der Raum lebendig
//  und reaktiv wirkt. Partikelanzahl skaliert mit der Geräte-Qualität.
// ------------------------------------------------------------------
export default function Particles({ count = 120 }) {
  const groupRef = useRef()
  const target = useRef(new THREE.Vector2())

  useFrame((state, delta) => {
    const g = groupRef.current
    if (!g) return
    // Zielneigung aus der Mausposition (state.pointer ist [-1, 1])
    target.current.set(state.pointer.y * 0.25, state.pointer.x * 0.4)
    // Sanftes Nachziehen (Lerp) für flüssige Reaktion
    g.rotation.x = THREE.MathUtils.damp(g.rotation.x, target.current.x, 3, delta)
    g.rotation.y = THREE.MathUtils.damp(g.rotation.y, target.current.y, 3, delta)
  })

  return (
    <group ref={groupRef}>
      <Sparkles
        count={count}
        scale={[14, 8, 10]}
        size={2.2}
        speed={0.35}
        opacity={0.6}
        color="#7dd3fc"
      />
      <Sparkles
        count={Math.round(count / 3)}
        scale={[12, 6, 8]}
        size={3.5}
        speed={0.2}
        opacity={0.5}
        color="#c4b5fd"
      />
    </group>
  )
}
