import { EffectComposer, Bloom, Vignette } from '@react-three/postprocessing'

// ------------------------------------------------------------------
//  Postprocessing (CLAUDE.md, Abschnitt 3.E). Bloom lässt Sonne und
//  Planeten strahlen — der Kern des „echten Weltall"-Looks. Ein hoher
//  luminanceThreshold sorgt dafür, dass nur helle/emissive Flächen
//  glühen. Wird auf Mobilgeräten komplett deaktiviert (Performance).
// ------------------------------------------------------------------
export default function Effects({ enabled = true }) {
  if (!enabled) return null

  return (
    <EffectComposer>
      <Bloom
        intensity={1.5}
        luminanceThreshold={0.35}
        luminanceSmoothing={0.9}
        mipmapBlur
        radius={0.85}
      />
      <Vignette offset={0.25} darkness={0.85} eskil={false} />
    </EffectComposer>
  )
}
