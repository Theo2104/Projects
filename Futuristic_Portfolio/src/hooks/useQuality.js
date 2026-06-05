import { useEffect, useState } from 'react'

// ------------------------------------------------------------------
//  Passt die Render-Qualität an das Gerät an (CLAUDE.md, Abschnitt 4.3).
//  Auf mobilen / schmalen Viewports werden Partikelanzahl, DPR und
//  teure Material-Effekte automatisch reduziert.
// ------------------------------------------------------------------
export function useQuality() {
  const getQuality = () => {
    if (typeof window === 'undefined') return desktop
    const isMobile =
      window.matchMedia('(max-width: 768px)').matches ||
      window.matchMedia('(pointer: coarse)').matches
    return isMobile ? mobile : desktop
  }

  const [quality, setQuality] = useState(getQuality)

  useEffect(() => {
    const mq = window.matchMedia('(max-width: 768px)')
    const handler = () => setQuality(getQuality())
    mq.addEventListener('change', handler)
    window.addEventListener('resize', handler)
    return () => {
      mq.removeEventListener('change', handler)
      window.removeEventListener('resize', handler)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return quality
}

// Qualitätsprofile
const desktop = {
  isMobile: false,
  dpr: [1, 1.5], // hartes Limit gegen Fill-Rate-Last bei High-DPI
  stars: 5000, // Anzahl Hintergrundsterne
  bloom: true, // Postprocessing-Glühen aktiv
  planetSegments: 48, // Kugel-Auflösung der Planeten
  orbitRings: true, // Umlaufbahn-Linien sichtbar
}

const mobile = {
  isMobile: true,
  dpr: [1, 1.5],
  stars: 3000,
  bloom: false, // teures Postprocessing aus
  planetSegments: 24,
  orbitRings: true,
}
