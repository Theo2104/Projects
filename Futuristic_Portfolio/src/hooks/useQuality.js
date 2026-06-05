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
  dpr: [1, 2], // Retina erlaubt
  sparkles: 120, // Partikelanzahl
  transmission: true, // teures Frosted-Glass aktiv
  samples: 6,
}

const mobile = {
  isMobile: true,
  dpr: [1, 1.5],
  sparkles: 40,
  transmission: false, // fällt auf günstigeres Material zurück
  samples: 2,
}
