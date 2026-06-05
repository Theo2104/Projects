# Planeten-Texturen

Lege hier deine Planeten-Texturen ab (z. B. `commerce.jpg`, `chat.jpg`, `matrix.jpg`)
und trage den Pfad im jeweiligen Projekt in `src/data/projects.js` ein:

```js
texture: '/textures/commerce.jpg',
```

## Anforderungen an die Bilder
- **Equirectangular** (Seitenverhältnis 2:1, z. B. 2048×1024) — so wickeln sie
  sich nahtlos um die Kugel.
- **JPG** für Farbtexturen (kleiner) — PNG nur, wenn Transparenz nötig ist.
- Kantenlänge als Zweierpotenz (1024 / 2048) ist ideal für die GPU.

## Kostenlose Quellen (frei nutzbar)
- **Solar System Scope** – https://www.solarsystemscope.com/textures
  (echte Planeten in 2K/8K, Lizenz: Attribution 4.0 International)
- **NASA 3D Resources / SVS** – https://nasa3d.arc.nasa.gov  ·  https://svs.gsfc.nasa.gov
  (gemeinfreie Original-Aufnahmen)

> Optional: Eine zusätzliche Normal-/Bump-Map gibt Oberflächenrelief. Dafür müsste
> das Material in `Planet.jsx` um `normalMap`/`bumpMap` erweitert werden — sag
> Bescheid, dann baue ich das ein.
