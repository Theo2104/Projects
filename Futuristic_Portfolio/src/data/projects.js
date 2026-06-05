// ------------------------------------------------------------------
//  Zentrale Projekt-Daten.
//
//  Jedes Projekt-Objekt steuert sowohl das 3D-Objekt in der Szene als
//  auch den Eintrag im UI-Menü und die Info-Karte.
//
//  ➜ Echtes Modell einhängen: setze `model` auf den Pfad deiner Datei
//    in public/ (z. B. '/projekt1.glb'). Solange `model` null ist, wird
//    automatisch die gläserne Platzhalter-Geometrie (`geometry`) genutzt.
// ------------------------------------------------------------------

export const projects = [
  {
    id: 'commerce',
    name: 'Neon Commerce',
    tag: 'E-Commerce',
    description:
      'Eine futuristische Shopping-Experience mit Echtzeit-Warenkorb, animierten Produktkarten und einem gläsernen Checkout-Flow.',
    stack: ['React', 'Stripe', 'Framer Motion'],
    // Position des Objekts im 3D-Raum [x, y, z]
    position: [-1.8, 0.8, 0.4],
    color: '#22d3ee', // Cyan
    geometry: 'bag', // Platzhalter bis dein .glb da ist
    model: null, // z. B. '/projekt1.glb'
  },
  {
    id: 'chat',
    name: 'Liquid Chat',
    tag: 'Social / Realtime',
    description:
      'Eine Messaging-Plattform mit flüssigen Übergängen, Presence-Indikatoren und Ende-zu-Ende-Verschlüsselung im Chrome-Look.',
    stack: ['React', 'WebSocket', 'Node'],
    position: [1.4, -0.2, -0.3],
    color: '#e5e7eb', // Chrome
    geometry: 'bubble',
    model: null, // z. B. '/projekt2.glb'
  },
  {
    id: 'matrix',
    name: 'Matrix Engine',
    tag: 'Tech / Coding',
    description:
      'Ein holografisches Daten-Visualisierungstool mit Cyberpunk-Ästhetik, Live-Graphen und einer GPU-beschleunigten Render-Pipeline.',
    stack: ['React Three Fiber', 'WebGL', 'GLSL'],
    position: [3.8, 0.7, 0.2],
    color: '#a855f7', // Violett
    geometry: 'cube',
    model: null, // z. B. '/projekt3.glb'
  },
]

// Standard-Kameraposition (Übersicht über alle Objekte)
export const HOME_CAMERA = {
  position: [1.2, 1.1, 9.5],
  target: [1.2, 0.2, 0],
}
