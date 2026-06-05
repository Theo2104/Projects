import { useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import { Stars } from '@react-three/drei'

// ------------------------------------------------------------------
//  360°-Sternenhülle um die Kamera (CLAUDE.md, Abschnitt 3.D).
//  Dreht sich extrem langsam, damit der Weltraum lebendig wirkt, ohne
//  abzulenken. Sternanzahl skaliert mit der Geräte-Qualität.
// ------------------------------------------------------------------
export default function Starfield({ count = 6000 }) {
  const ref = useRef()

  useFrame((_, delta) => {
    if (ref.current) ref.current.rotation.y += delta * 0.005
  })

  return (
    <group ref={ref}>
      <Stars
        radius={80}
        depth={50}
        count={count}
        factor={7}
        saturation={0}
        fade
        speed={0.4}
      />
    </group>
  )
}
