import { projects } from '../data/projects'

// ------------------------------------------------------------------
//  HTML-Overlay über der Canvas (CLAUDE.md, Abschnitt 3.C).
//  Minimalistisches Menü links; Klick fokussiert das zugehörige
//  3D-Objekt (die Kamera-Fahrt erledigt CameraRig) und blendet rechts
//  eine Info-Karte ein.
//
//  pointer-events: das Overlay ist standardmäßig durchlässig (damit man
//  die Canvas drehen kann); nur die interaktiven Elemente fangen Klicks.
// ------------------------------------------------------------------
export default function UIOverlay({ selectedId, onSelect }) {
  const active = projects.find((p) => p.id === selectedId)

  return (
    <div className="pointer-events-none absolute inset-0 z-10 flex flex-col justify-between p-6 md:p-10">
      {/* Kopfzeile */}
      <header className="pointer-events-none flex items-start justify-between">
        <div>
          <h1 className="font-display text-lg font-semibold tracking-tight text-white md:text-xl">
            Theo Haase
          </h1>
          <p className="text-xs tracking-[0.25em] text-cyan-300/70 uppercase">
            Creative Developer
          </p>
        </div>
        <div className="hidden text-right text-[11px] text-white/40 md:block">
          <p>Ziehen zum Drehen</p>
          <p>Objekt anklicken zum Fokussieren</p>
        </div>
      </header>

      {/* Hauptbereich: Menü + Info-Karte */}
      <div className="flex flex-1 items-center justify-between gap-6 py-8">
        {/* Navigations-Menü */}
        <nav className="pointer-events-auto flex flex-col gap-1">
          {projects.map((project, i) => {
            const isActive = project.id === selectedId
            return (
              <button
                key={project.id}
                onClick={() => onSelect(isActive ? null : project.id)}
                className={`group flex items-center gap-3 rounded-lg px-3 py-2 text-left transition-all duration-300 ${
                  isActive ? 'bg-white/5' : 'hover:bg-white/[0.03]'
                }`}
              >
                <span
                  className="h-2 w-2 shrink-0 rounded-full transition-all duration-300"
                  style={{
                    backgroundColor: project.color,
                    boxShadow: isActive
                      ? `0 0 12px ${project.color}`
                      : 'none',
                    opacity: isActive ? 1 : 0.4,
                  }}
                />
                <span className="flex flex-col">
                  <span
                    className={`font-display text-sm font-medium transition-colors duration-300 ${
                      isActive
                        ? 'text-white'
                        : 'text-white/50 group-hover:text-white/80'
                    }`}
                  >
                    {String(i + 1).padStart(2, '0')} — {project.name}
                  </span>
                  <span className="text-[10px] tracking-wider text-white/30 uppercase">
                    {project.tag}
                  </span>
                </span>
              </button>
            )
          })}
        </nav>

        {/* Info-Karte (rechts), nur bei Auswahl */}
        {active && (
          <article
            key={active.id}
            className="glass-ui animate-fade-up pointer-events-auto w-full max-w-sm rounded-2xl p-6 md:p-7"
          >
            <div className="mb-3 flex items-center gap-2">
              <span
                className="h-2.5 w-2.5 rounded-full"
                style={{ backgroundColor: active.color, boxShadow: `0 0 12px ${active.color}` }}
              />
              <span className="text-[11px] tracking-[0.25em] text-white/50 uppercase">
                {active.tag}
              </span>
            </div>
            <h2 className="font-display mb-3 text-2xl font-semibold text-white">
              {active.name}
            </h2>
            <p className="mb-5 text-sm leading-relaxed text-white/60">
              {active.description}
            </p>
            <div className="mb-5 flex flex-wrap gap-2">
              {active.stack.map((tech) => (
                <span
                  key={tech}
                  className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[11px] text-white/70"
                >
                  {tech}
                </span>
              ))}
            </div>
            <button
              onClick={() => onSelect(null)}
              className="text-xs tracking-wider text-cyan-300/80 uppercase transition-colors hover:text-cyan-200"
            >
              ← Zurück zur Übersicht
            </button>
          </article>
        )}
      </div>

      {/* Fußzeile */}
      <footer className="pointer-events-none flex items-center justify-between text-[11px] text-white/30">
        <span>© {new Date().getFullYear()} Theo Haase</span>
        <span className="hidden md:inline">Built with R3F · Drei · GSAP</span>
      </footer>
    </div>
  )
}
