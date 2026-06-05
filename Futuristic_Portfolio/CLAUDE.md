# Projekt-Kontext: 3D Animated Showcase Portfolio

Dieses Dokument dient als zentrale Richtlinie und Kontext-Quelle für Claude Code zur Erstellung und Weiterentwicklung dieses Portfolios. Alle Code-Generierungen, Strukturierungen und UI-Entscheidungen müssen sich an den folgenden Vorgaben orientieren.

---

## 1. Tech Stack & Architektur

Das Projekt basiert auf einem modernen, hochperformanten Frontend-Setup. Es dürfen keine veralteten Bibliotheken verwendet werden.

* **Build Tool:** Vite (React + JavaScript)
* **Styling:** Tailwind CSS (für HTML-UI, Layouts und Typografie)
* **3D-Engine:** `@react-three/fiber` (R3F) & `three`
* **3D-Hilfswerkzeuge:** `@react-three/drei` (für Loader, Kamera-Controls, Licht und Materialien)
* **Animationen:** `gsap` (GreenSock Animation Platform) für komplexe UI- und Kamera-Kombinationen, sowie `useFrame` für mathematische Shader-/Render-Schleifen.

---

## 2. Visueller Stil & Design-Direktive

Das Design soll extrem modern, immersiv und futuristisch wirken (ähnlich zu Web-Awards-Webseiten wie Awwwards).

* **Farbschema:** Dunkler Modus (Dark Mode) als Standard. Tiefe Dunkeltöne (`#0b0b0f`, `#121214`), kombiniert mit leuchtenden Akzenten (Neon, Cyan, Violett, Chrome).
* **Glaseffekte:** 3D-Objekte im Raum sollen überwiegend den "Frosted Glass"-Look (Glassmorphismus) nutzen. Dafür ist das `MeshPhysicalMaterial` aus Drei zu verwenden (niedrige `roughness`, hohe `transmission`, spürbare `thickness`).
* **Interaktivität:** Die Webseite darf nicht statisch wirken. Jede Interaktion (Hover, Klick, Scroll) muss eine flüssige, visuelle Rückmeldung im 3D-Raum oder im UI auslösen.

---

## 3. Kern-Komponenten & Features

### A. Die 3D-Hauptszene (`3DScene.jsx`)
* Eine bildschirmfüllende Canvas, die im Hintergrund liegt oder sich organisch in das Layout einfügt.
* Steuerung via `OrbitControls`, jedoch mit Einschränkungen (z. B. kein unendlicher Zoom), um den Nutzer nicht die Orientierung verlieren zu lassen.
* Einbindung von dezenten Partikelsystemen (`<Sparkles>` oder `<Points>`), die sich organisch im Raum bewegen.

### B. Die Projekt-Objekte
* Externe 3D-Assets liegen im `.glb`-Format im `public/`-Ordner und werden performant über `useGLTF` geladen.
* Jedes Objekt besitzt eine schwebende Animation (Sinus-Welle via `useFrame`).
* **Hover-Effekt:** Bei Mauskontakt (`onPointerOver`) skaliert das Objekt flüssig via GSAP nach oben, Lichter intensivieren sich und Partikel reagieren.

### C. Das HTML-UI-Overlay
* Ein minimalistisches Navigationsmenü, das mittels Tailwind CSS absolut über die Canvas gelegt wird.
* Beim Klick auf ein Projekt im Menü animiert GSAP die Kameraposition (`camera.position`) flüssig zu dem entsprechenden 3D-Objekt. Gleichzeitig blendet sich eine informative Textkarte ein.

---

## 4. Performance & Optimierungs-Richtlinien

Da 3D-Webseiten schnell hardwarehungrig werden, müssen beim Schreiben von Code folgende Regeln strikt eingehalten werden:

1. **Lazy Loading & Suspense:** Alle 3D-Modelle müssen in eine React `<Suspense>`-Komponente eingepackt werden. Ein eleganter Ladebildschirm (`useProgress`) ist Pflicht.
2. **Geometrie- & Textur-Schonung:** Halte die Anzahl der Polygone niedrig. Animationen innerhalb der R3F-Renderschleife (`useFrame`) müssen performant gecodet werden (Zuweisungen direkt auf `.current.rotation` statt State-Updates).
3. **Responsive Design:** Die 3D-Szene muss sich an mobile Bildschirme anpassen. Auf Mobilgeräten sollten Effekte wie komplexe Shader oder Partikelanzahlen automatisch reduziert werden (über Media-Queries oder Breakpoint-Abfragen in React).
4. **WebGL Fallback:** Wenn der Browser des Nutzers kein WebGL unterstützt, muss eine saubere, rein HTML/CSS-basierte Fallback-Struktur angezeigt werden.
