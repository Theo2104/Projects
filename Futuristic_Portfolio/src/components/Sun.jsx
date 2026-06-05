import { useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import { useTexture } from '@react-three/drei'
import * as THREE from 'three'
import { SUN } from '../data/projects'

// ------------------------------------------------------------------
//  Zentrale Sonne / Core im Ursprung (CLAUDE.md, Abschnitt 3.B).
//  Emissive Kugel + zentrales Punktlicht, das alle Planeten beleuchtet.
//  Sanftes „Atmen" via useFrame; das eigentliche Strahlen erzeugt der
//  Bloom-Pass in Effects.jsx.
// ------------------------------------------------------------------
export default function Sun() {
  const coreRef = useRef()
  const haloRef = useRef()

  const sunMap = useTexture(SUN.texture, (t) => {
    t.colorSpace = THREE.SRGBColorSpace
  })

  useFrame((state) => {
    const pulse = 1 + Math.sin(state.clock.elapsedTime * 1.5) * 0.03
    if (coreRef.current) coreRef.current.scale.setScalar(pulse)
    if (haloRef.current)
      haloRef.current.material.opacity =
        0.5 + Math.sin(state.clock.elapsedTime * 1.5) * 0.08
  })

  return (
    <group>
      {/* Leuchtkern — hell & ungetonemappt, damit der Bloom-Pass ihn
          kräftig zum Strahlen bringt. */}
      <mesh ref={coreRef}>
        <sphereGeometry args={[SUN.radius, 64, 64]} />
        <meshBasicMaterial map={sunMap} color={SUN.color} toneMapped={false} />
      </mesh>

      {/* Weiches Glühen über ein kameragerichtetes Sprite (radialer
          Verlauf) — ergänzt den Bloom-Halo. */}
      <sprite ref={haloRef} scale={SUN.radius * 4.5}>
        <spriteMaterial
          map={glowTexture}
          color={SUN.emissive}
          transparent
          opacity={0.45}
          blending={THREE.AdditiveBlending}
          depthWrite={false}
        />
      </sprite>

      {/* Lichtquelle des Systems */}
      <pointLight color="#fff1d6" intensity={400} distance={120} decay={2} />
    </group>
  )
}

// Radialer Glühverlauf als wiederverwendbare Canvas-Textur.
const glowTexture = (() => {
  const size = 128
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = size
  const ctx = canvas.getContext('2d')
  const g = ctx.createRadialGradient(
    size / 2,
    size / 2,
    0,
    size / 2,
    size / 2,
    size / 2,
  )
  g.addColorStop(0, 'rgba(255,255,255,1)')
  g.addColorStop(0.25, 'rgba(255,255,255,0.6)')
  g.addColorStop(1, 'rgba(255,255,255,0)')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, size, size)
  const tex = new THREE.CanvasTexture(canvas)
  return tex
})()
