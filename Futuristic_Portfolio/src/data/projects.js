// ------------------------------------------------------------------
//  Globale Szenen-Konstanten.
//
//  Die Planeten selbst werden NICHT mehr hier gepflegt, sondern zur
//  Laufzeit aus den Repo-Ordnern erzeugt — siehe hooks/useProjects.js
//  und data/projectMeta.js (kuratierte Overrides).
// ------------------------------------------------------------------

// Standard-Kamera: flacher Blick mitten ins System (immersiv „im All").
export const HOME_CAMERA = {
  position: [0, 5, 26],
  target: [0, 0, 0],
}

// Eigenschaften der zentralen Sonne (Core).
export const SUN = {
  radius: 1.7,
  color: '#ffd27a',
  emissive: '#ff8a1f',
  texture: '/textures/2k_sun.jpg', // Oberflächendetail des Leuchtkerns
}
