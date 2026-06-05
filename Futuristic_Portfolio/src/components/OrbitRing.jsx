import { useMemo } from 'react'
import * as THREE from 'three'

// ------------------------------------------------------------------
//  Dezente Umlaufbahn-Linie (CLAUDE.md, Abschnitt 3.D). Ein flacher
//  Kreis in der XZ-Ebene als Orientierungshilfe.
// ------------------------------------------------------------------
export default function OrbitRing({ radius, color = '#3a3a55', opacity = 0.25 }) {
  const points = useMemo(() => {
    const segments = 128
    const pts = []
    for (let i = 0; i <= segments; i++) {
      const a = (i / segments) * Math.PI * 2
      pts.push(new THREE.Vector3(Math.cos(a) * radius, 0, Math.sin(a) * radius))
    }
    return pts
  }, [radius])

  const geometry = useMemo(
    () => new THREE.BufferGeometry().setFromPoints(points),
    [points],
  )

  return (
    <line geometry={geometry}>
      <lineBasicMaterial color={color} transparent opacity={opacity} />
    </line>
  )
}
