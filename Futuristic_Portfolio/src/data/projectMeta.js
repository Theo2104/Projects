// ------------------------------------------------------------------
//  Quelle der Planeten: die Top-Level-Ordner des öffentlichen Repos.
//
//  Jeder Ordner im Repo wird automatisch zu einem Planeten. Neue Ordner
//  erscheinen von selbst, gelöschte verschwinden — ohne Code-Änderung
//  (das Listing kommt zur Laufzeit aus der GitHub-API, siehe
//  hooks/useProjects.js).
//
//  META ist optional: kuratierte Overrides (Name, Tag, Beschreibung,
//  Stack, Farbe, Textur, Ring) je Ordnername. Fehlt ein Eintrag, wird
//  alles automatisch ergänzt — Beschreibung notfalls aus der README
//  des Ordners (siehe hooks/useProjectDetails.js).
// ------------------------------------------------------------------

export const REPO = 'Theo2104/Projects'
export const BRANCH = 'main'

// Ordner, die KEIN Planet werden sollen (zusätzlich zu allen ".*").
export const HIDDEN = new Set(['node_modules', 'dist', 'build'])

export const META = {
  Futuristic_Portfolio: {
    name: 'Cosmic Portfolio',
    tag: '3D / WebGL',
    stack: ['React Three Fiber', 'Drei', 'GSAP', 'Vite'],
    texture: '/textures/2k_earth_daymap.jpg',
    description:
      'Genau dieses Portfolio: ein begehbares 3D-Sonnensystem, in dem jeder Planet ein Projekt ist — gebaut mit React Three Fiber, Drei und GSAP.',
  },
  Llama: {
    name: 'Llama Voice Assistant',
    tag: 'KI / Accessibility',
    stack: ['LLaMA 3', 'GPT4All', 'Python', 'Flask', 'Kotlin'],
    texture: '/textures/2k_saturn.jpg',
    ring: true,
    description:
      'Autismus-unterstützender KI-Sprachassistent: ein lokales LLaMA-3-Modell über ein Flask-Backend, bedient von einer Android-App (Jetpack Compose, Retrofit).',
  },
  GPT4All_Assistant: {
    name: 'GPT4All Assistant',
    tag: 'KI / Android',
    stack: ['GPT4All', 'Python', 'Flask', 'Kotlin'],
    color: '#34d399',
    description:
      'KI-Sprachassistent auf Basis eines lokalen GPT4All-Modells mit Python/Flask-Backend und nativer Android-App.',
  },
  NLP_Assistant: {
    name: 'NLP Assistant',
    tag: 'NLP / Android',
    stack: ['Kotlin', 'Jetpack Compose'],
    color: '#60a5fa',
    description:
      'Android-Assistent mit Natural-Language-Processing zur Verarbeitung sprachlicher Eingaben.',
  },
  Recipe: {
    name: 'Chef Claude',
    tag: 'KI / Web',
    stack: ['React', 'Vite', 'Claude API', 'Hugging Face'],
    color: '#fb7185',
    description:
      'KI-Rezeptgenerator: gib deine Zutaten ein und Claude (Anthropic) bzw. Mistral schlägt passende Gerichte vor — React + Vite.',
  },
  'physical-vs-virtual-displays-main': {
    name: 'Physical vs. Virtual Displays',
    tag: 'XR / Forschung',
    stack: ['Unity', 'HoloLens 2', 'React', 'TypeScript'],
    texture: '/textures/2k_mercury.jpg',
    description:
      'Mixed-Reality-Forschungsstudie (HoloLens 2 / Unity): Vergleich physischer und virtueller Displays mit Web-Editor (React/TS) und QR-Code-Verankerung.',
  },
  database: {
    name: 'Database App',
    tag: 'Fullstack',
    stack: ['Node', 'Express', 'CSV'],
    color: '#facc15',
    description:
      'Fullstack-Web-App mit Node/Express-Backend und CSV-Datenimport, getrennt in Client und Server.',
  },
  lead_tracker: {
    name: 'Lead Tracker',
    tag: 'Web / Extension',
    stack: ['Vite', 'JavaScript'],
    color: '#c084fc',
    description:
      'Browser-Extension zum schnellen Speichern und Verwalten von Leads/URLs — ein Vite-Projekt aus dem Scrimba-Frontend-Pfad.',
  },
  shopping_list: {
    name: 'Shopping List',
    tag: 'Web / PWA',
    stack: ['Vite', 'JavaScript'],
    color: '#4ade80',
    description:
      'Installierbare Einkaufslisten-PWA mit Vite — ein Scrimba-Praxisprojekt.',
  },
  Android_apps: {
    name: 'Android Apps',
    tag: 'Android / Kotlin',
    stack: ['Kotlin', 'Jetpack Compose', 'Gradle'],
    color: '#38bdf8',
    description: 'Native Android-Anwendung in Kotlin mit Jetpack Compose.',
  },
  llama: {
    name: 'Llama (Android Client)',
    tag: 'Android',
    stack: ['Kotlin', 'Retrofit'],
    color: '#f472b6',
    description:
      'Schlanke Android-Client-Variante für den lokalen LLaMA-Sprachassistenten.',
  },
}

// Farbpalette für Projekte ohne kuratierte Farbe (deterministisch).
const PALETTE = [
  '#22d3ee',
  '#a855f7',
  '#f59e0b',
  '#34d399',
  '#f472b6',
  '#60a5fa',
  '#facc15',
  '#fb7185',
  '#4ade80',
  '#c084fc',
  '#38bdf8',
]

// Ordnername → lesbarer Titel ("physical-vs-virtual-displays-main"
// → "Physical Vs Virtual Displays").
export function prettify(name) {
  return name
    .replace(/[-_]+/g, ' ')
    .replace(/\bmain\b/i, '')
    .trim()
    .replace(/\b\w/g, (c) => c.toUpperCase())
}

function hashInt(str) {
  let h = 0
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) >>> 0
  return h
}

// ------------------------------------------------------------------
//  Baut aus einer Liste von Ordnernamen die Planeten-Objekte.
//  Orbit-Parameter werden gleichmäßig verteilt (goldener Winkel +
//  Kepler-artiges Tempo), Farbe/Größe deterministisch aus dem Namen.
// ------------------------------------------------------------------
export function buildProjects(dirNames) {
  const names = dirNames.filter(
    (n) => !n.startsWith('.') && !HIDDEN.has(n),
  )

  return names.map((name, i) => {
    const m = META[name] || {}
    const h = hashInt(name)
    const orbitRadius = 5.5 + i * 1.25

    return {
      id: name,
      path: name, // Ordner im Repo (für Links & letzten Commit)
      repo: REPO,
      name: m.name || prettify(name),
      tag: m.tag || 'Projekt',
      description: m.description || null, // null → README-Fallback in der Karte
      stack: m.stack || [],
      // Orbit
      orbitRadius,
      orbitSpeed: 0.55 / Math.sqrt(orbitRadius), // innen schneller
      startAngle: (i * 2.39996) % (Math.PI * 2), // goldener Winkel
      // Aussehen
      size: m.size || 0.6 + ((h % 100) / 100) * 0.35,
      color: m.color || PALETTE[h % PALETTE.length],
      ring: m.ring ?? false,
      texture: m.texture || null,
      model: null,
    }
  })
}
