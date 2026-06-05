import { useEffect } from 'react'
import { useThree } from '@react-three/fiber'
import gsap from 'gsap'
import { projects, HOME_CAMERA } from '../data/projects'

// ------------------------------------------------------------------
//  Animiert die Kameraposition flüssig per GSAP, wenn ein Projekt
//  ausgewählt wird (CLAUDE.md, Abschnitt 3.C). Bei `selectedId === null`
//  kehrt die Kamera in die Übersicht (HOME_CAMERA) zurück.
//
//  controlsRef → Ref auf die <OrbitControls> (für das Schwenken des
//  Blickziels .target).
// ------------------------------------------------------------------
export default function CameraRig({ selectedId, controlsRef }) {
  const { camera } = useThree()

  useEffect(() => {
    const controls = controlsRef.current

    // Zielwerte bestimmen
    let camPos, lookTarget
    const project = projects.find((p) => p.id === selectedId)

    if (project) {
      const [x, y, z] = project.position
      // Etwas seitlich/vor dem Objekt positionieren für eine schöne Sicht
      camPos = { x: x + 1.4, y: y + 0.8, z: z + 4 }
      lookTarget = { x, y, z }
    } else {
      const [hx, hy, hz] = HOME_CAMERA.position
      const [tx, ty, tz] = HOME_CAMERA.target
      camPos = { x: hx, y: hy, z: hz }
      lookTarget = { x: tx, y: ty, z: tz }
    }

    // Während der Fahrt Controls deaktivieren, damit nichts ruckelt.
    if (controls) controls.enabled = false

    const tl = gsap.timeline({
      onComplete: () => {
        if (controls) controls.enabled = true
      },
    })

    tl.to(
      camera.position,
      {
        x: camPos.x,
        y: camPos.y,
        z: camPos.z,
        duration: 1.4,
        ease: 'power3.inOut',
      },
      0,
    )

    if (controls) {
      tl.to(
        controls.target,
        {
          x: lookTarget.x,
          y: lookTarget.y,
          z: lookTarget.z,
          duration: 1.4,
          ease: 'power3.inOut',
          onUpdate: () => controls.update(),
        },
        0,
      )
    }

    return () => {
      tl.kill()
    }
  }, [selectedId, camera, controlsRef])

  return null
}
