// ------------------------------------------------------------------
//  Zentrale Projekt-Daten — jedes Projekt ist ein Planet im System.
//
//  Die Orbit-Parameter steuern die Umlaufbahn; color/size/ring das
//  Aussehen des Planeten. Derselbe Eintrag speist auch Menü & Info-Karte.
//
//  ➜ Echtes Modell:  `model` auf einen Pfad in public/ setzen (z. B.
//    '/planet1.glb'). Solange null, wird die prozedurale Kugel gerendert.
//  ➜ Textur:         `texture` auf '/textures/xyz.jpg' setzen (optional).
// ------------------------------------------------------------------

export const projects = [
  {
    id: 'commerce',
    name: 'Neon Commerce',
    tag: 'E-Commerce',
    description:
      'Eine futuristische Shopping-Experience mit Echtzeit-Warenkorb, animierten Produktkarten und einem gläsernen Checkout-Flow.',
    stack: ['React', 'Stripe', 'Framer Motion'],
    // --- Orbit ---
    orbitRadius: 6,
    orbitSpeed: 0.18, // innen = schneller
    startAngle: 0,
    // --- Aussehen ---
    size: 0.85,
    color: '#22d3ee', // Cyan
    ring: false,
    texture: null,
    model: null, // z. B. '/planet1.glb'
  },
  {
    id: 'chat',
    name: 'Liquid Chat',
    tag: 'Social / Realtime',
    description:
      'Eine Messaging-Plattform mit flüssigen Übergängen, Presence-Indikatoren und Ende-zu-Ende-Verschlüsselung im Chrome-Look.',
    stack: ['React', 'WebSocket', 'Node'],
    orbitRadius: 9.5,
    orbitSpeed: 0.12,
    startAngle: 2.2,
    size: 1.05,
    color: '#a855f7', // Violett
    ring: false,
    texture: null,
    model: null, // z. B. '/planet2.glb'
  },
  {
    id: 'matrix',
    name: 'Matrix Engine',
    tag: 'Tech / Coding',
    description:
      'Ein holografisches Daten-Visualisierungstool mit Cyberpunk-Ästhetik, Live-Graphen und einer GPU-beschleunigten Render-Pipeline.',
    stack: ['React Three Fiber', 'WebGL', 'GLSL'],
    orbitRadius: 13,
    orbitSpeed: 0.08,
    startAngle: 4.3,
    size: 0.95,
    color: '#f59e0b', // Bernstein
    ring: true, // Saturn-artiger Ring
    texture: null,
    model: null, // z. B. '/planet3.glb'
  },
]

// Standard-Kamera: flacher Blick mitten ins System (immersiv „im All").
export const HOME_CAMERA = {
  position: [0, 3.5, 20],
  target: [0, 0, 0],
}

// Eigenschaften der zentralen Sonne (Core).
export const SUN = {
  radius: 1.7,
  color: '#ffd27a',
  emissive: '#ff8a1f',
}
