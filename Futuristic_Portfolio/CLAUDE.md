# Projekt-Kontext: Cosmic Portfolio — Begehbares Sonnensystem

Dieses Dokument ist die zentrale Richtlinie und Kontext-Quelle für Claude Code. Alle Code-Generierungen, Strukturierungen und UI-Entscheidungen müssen sich an den folgenden Vorgaben orientieren.

**Leitidee:** Das Portfolio ist ein immersives Sonnensystem im Weltall. Eine zentrale **Sonne (Core)** repräsentiert die Person/Marke; jeder **Planet** auf seiner Umlaufbahn steht für ein **Projekt**. Der Nutzer befindet sich **mittendrin** im All — umgeben von einem 360°-Sternenhimmel — und kann sich frei umsehen. Klickt er einen Planeten an, fliegt die Kamera sanft zu ihm und blendet eine Info-Karte ein.

---

## 1. Tech Stack & Architektur

Modernes, hochperformantes Frontend-Setup. Keine veralteten Bibliotheken.

* **Build Tool:** Vite (React + JavaScript)
* **Styling:** Tailwind CSS (für HTML-UI, Layouts und Typografie)
* **3D-Engine:** `@react-three/fiber` (R3F) & `three`
* **3D-Hilfswerkzeuge:** `@react-three/drei` (Loader, Kamera-Controls, `Stars`, Licht, Materialien, `useTexture`)
* **Postprocessing:** `@react-three/postprocessing` (`Bloom` für das Glühen von Sonne & Planeten — der Schlüssel zum „echten Weltall"-Look)
* **Animationen:** `gsap` für komplexe UI- und Kamera-Fahrten, sowie `useFrame` für Orbit-Mathematik und Render-Schleifen.

---

## 2. Visueller Stil & Design-Direktive

Das Design soll wie ein echtes, lebendiges Weltall wirken — tief, atmosphärisch und immersiv (Awwwards-Niveau).

* **Farbschema:** Tiefschwarzer Weltraum als Basis (`#05060a`, `#0b0b0f`). Akzente entstehen durch die **Eigenfarben der Planeten** (Cyan, Violett, Bernstein, Türkis …) und das warme Glühen der Sonne.
* **Glühen statt Glas:** Der dominierende Effekt ist **emissives Leuchten + Bloom**, nicht mehr Frosted Glass. Sonne und Planeten nutzen `emissive`/`emissiveIntensity`; der `Bloom`-Pass lässt helle Bereiche strahlen. Glassmorphismus bleibt ausschließlich dem **HTML-UI-Overlay** (Info-Karten, Menü) vorbehalten.
* **Tiefe & Atmosphäre:** 360°-Sternenfeld (`<Stars>`), optional dezenter Nebel (großer, halbtransparenter Sphären-/Shader-Layer), feiner `fog` für Tiefenstaffelung.
* **Interaktivität:** Nichts ist statisch. Planeten kreisen permanent, rotieren um sich selbst und reagieren auf Hover (Glow/Skalierung) und Klick (Kamera-Fokus).

---

## 3. Kern-Komponenten & Features

### A. Die Weltraum-Szene (`Scene3D.jsx`)
* Bildschirmfüllende Canvas mit tiefschwarzem Hintergrund und `fog`.
* Kamera liegt **nahe dem Zentrum** des Systems; `OrbitControls` mit `target` auf den Ursprung erlauben freies 360°-Umsehen (begrenzter `minDistance`/`maxDistance`-Zoom, damit der Nutzer die Orientierung behält).
* Bündelt Sternenfeld, Sonne, alle Planeten, Umlaufbahnen, `CameraRig` und den Postprocessing-Pass.

### B. Die zentrale Sonne / Core (`Sun.jsx`)
* Emissive Kugel im Ursprung mit zentralem `pointLight`, das das ganze System beleuchtet.
* Starkes Bloom-Glühen; optional eine pulsierende Animation (`useFrame`) und ein Corona-/Glow-Sprite.

### C. Die Planeten = Projekte (`Planet.jsx`)
* Jeder Planet ist eine low-poly Kugel mit Eigenfarbe bzw. (optional) Textur (`useTexture`).
* **Orbit-Bewegung** über `useFrame`: Position = `[cos(angle)·radius, y, sin(angle)·radius]`, `angle` wächst mit `orbitSpeed`. Zusätzlich Eigenrotation. Direkte Mutation von `ref.current.position/rotation` — kein State.
* Datengetrieben aus `src/data/projects.js` (`orbitRadius`, `orbitSpeed`, `size`, `color`, `startAngle`, optional `ring`, `texture`, `model`).
* **Hover:** GSAP skaliert den Planeten leicht hoch, `emissiveIntensity`/Glow steigen.
* **Klick:** wählt das Projekt aus → `CameraRig` fliegt hin, Info-Karte erscheint.
* Optional: Ringe (`ringGeometry`) und kleine Monde für markante Projekte.

### D. Umlaufbahnen & Sternenfeld (`OrbitRing.jsx`, `Starfield.jsx`)
* Dezente, halbtransparente Kreis-Linien visualisieren jede Umlaufbahn (Orientierungshilfe).
* `Starfield` nutzt `<Stars>` aus Drei (oder ein `<Points>`-Feld) als 360°-Hülle um die Kamera.

### E. Postprocessing (`Effects.jsx`)
* `EffectComposer` mit `Bloom` (moderate `intensity`, hoher `luminanceThreshold`, damit nur Sonne/Planeten glühen). Optional `Vignette`.
* Auf Mobilgeräten reduziert oder deaktiviert (Performance).

### F. Das HTML-UI-Overlay (`UIOverlay.jsx`)
* Minimalistisches, futuristisches Menü (Tailwind) absolut über der Canvas; listet die Projekte/Planeten.
* Klick im Menü **oder** auf den Planeten ⇒ GSAP animiert `camera.position` flüssig zum Planeten und schwenkt `controls.target` mit. Da Planeten sich bewegen, wird der Ziel-Planet beim Fokus „eingefroren" (aktuelle Position gemerkt) bzw. die Kamera folgt ihm pro Frame. Gleichzeitig blendet sich eine Glas-Info-Karte mit Projekt-Details ein.

---

## 4. Performance & Optimierungs-Richtlinien

3D-Webseiten werden schnell hardwarehungrig. Strikt einzuhalten:

1. **Lazy Loading & Suspense:** Alle Texturen/Modelle in `<Suspense>` kapseln. Eleganter Ladebildschirm via `useProgress` ist Pflicht.
2. **Geometrie- & Textur-Schonung:** Planeten low-poly halten (moderate Sphere-Segmente). Orbit-/Rotations-Animationen in `useFrame` performant codieren (direkte Mutation von `.current.position/.rotation`, keine State-Updates).
3. **Responsive Design:** Szene passt sich mobilen Bildschirmen an. Auf Mobilgeräten werden Sternenanzahl, `Bloom` und Textur-Auflösung automatisch reduziert (Breakpoint-/Quality-Hook).
4. **WebGL Fallback:** Ohne WebGL-Support wird eine saubere, rein HTML/CSS-basierte Fallback-Ansicht der Projekte gezeigt.

---

## 5. Daten- & Erweiterungs-Konvention

* Alle Projekte/Planeten werden zentral in `src/data/projects.js` gepflegt (steuert Planet **und** Menü **und** Info-Karte).
* Optionales echtes 3D-Modell: Feld `model` auf einen Pfad in `public/` setzen (z. B. `'/planet1.glb'`); solange `null`, wird die prozedurale Kugel gerendert.
* Optionale Planeten-Texturen liegen in `public/textures/` und werden über `useTexture` geladen (Quellen siehe Projekt-README/Anleitung).
