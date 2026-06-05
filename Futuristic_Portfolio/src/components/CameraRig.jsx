import { useEffect } from 'react'
import { useThree } from '@react-three/fiber'
import gsap from 'gsap'
import { projects, HOME_CAMERA } from '../data/projects'

// ------------------------------------------------------------------
//  Fliegt die Kamera per GSAP zum gewählten Planeten (CLAUDE.md 3.F).
//  Da Planeten kreisen, wird der ausgewählte Planet von Planet.jsx
//  „eingefroren"; seine aktuelle Position liefert `positionsRef`.
//  Die Kamera positioniert sich radial außerhalb des Planeten und
//  blickt nach innen (Planet vor der leuchtenden Sonne).
// ------------------------------------------------------------------
export default function CameraRig({ selectedId, controlsRef, positionsRef }) {
  const { camera } = useThree()

  useEffect(() => {
    const controls = controlsRef.current
    const project = projects.find((p) => p.id === selectedId)

    let camPos, lookTarget

    if (project) {
      // Aktuelle (eingefrorene) Planetenposition holen, sonst aus Bahn
      // berechnen als Fallback.
      const pos =
        positionsRef?.current?.[project.id] ??
        {
          x: Math.cos(project.startAngle) * project.orbitRadius,
          y: 0,
          z: Math.sin(project.startAngle) * project.orbitRadius,
        }

      const len = Math.hypot(pos.x, pos.z) || 1
      const rx = pos.x / len // radial (von der Sonne weg)
      const rz = pos.z / len
      const tx = -rz // tangential (seitlich)
      const tz = rx
      const dist = project.size * 3 + 3

      // Seitlich + leicht nach außen + erhöht: zeigt die sonnenbeschienene
      // Hälfte (Tag-/Nacht-Grenze) statt der reinen Nachtseite.
      camPos = {
        x: pos.x + tx * dist * 0.85 + rx * dist * 0.4,
        y: pos.y + project.size * 1.2 + 1.2,
        z: pos.z + tz * dist * 0.85 + rz * dist * 0.4,
      }
      lookTarget = { x: pos.x, y: pos.y, z: pos.z }
    } else {
      const [hx, hy, hz] = HOME_CAMERA.position
      const [tx, ty, tz] = HOME_CAMERA.target
      camPos = { x: hx, y: hy, z: hz }
      lookTarget = { x: tx, y: ty, z: tz }
    }

    if (controls) controls.enabled = false

    const tl = gsap.timeline({
      onComplete: () => {
        if (controls) controls.enabled = true
      },
    })

    tl.to(
      camera.position,
      { ...camPos, duration: 1.5, ease: 'power3.inOut' },
      0,
    )

    if (controls) {
      tl.to(
        controls.target,
        {
          ...lookTarget,
          duration: 1.5,
          ease: 'power3.inOut',
          onUpdate: () => controls.update(),
        },
        0,
      )
    }

    return () => {
      tl.kill()
    }
  }, [selectedId, camera, controlsRef, positionsRef])

  return null
}
