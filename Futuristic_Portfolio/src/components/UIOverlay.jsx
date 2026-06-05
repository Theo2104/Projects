import { useState } from 'react'
import { useGitHubStats, timeAgo } from '../hooks/useGitHubStats'

// ------------------------------------------------------------------
//  HTML-Overlay über der Canvas. Enthält:
//   - personalisierte Begrüßung (Pro-Firma-Links)
//   - Steuerung der geführten Tour
//   - Projekt-Menü (hervorgehobene Projekte zuerst + "Empfohlen"-Badge)
//   - Info-Karte mit Live-GitHub-Daten und Links
// ------------------------------------------------------------------
export default function UIOverlay({
  selectedId,
  onSelect,
  projects,
  company,
  highlight = [],
  tour,
  onStartTour,
  onStopTour,
}) {
  const active = projects.find((p) => p.id === selectedId)
  const [greetingDismissed, setGreetingDismissed] = useState(false)

  return (
    <div className="pointer-events-none absolute inset-0 z-10 flex flex-col justify-between p-6 md:p-10">
      {/* Kopfzeile */}
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="font-display text-lg font-semibold tracking-tight text-white md:text-xl">
            Theo Haase
          </h1>
          <p className="text-xs tracking-[0.25em] text-cyan-300/70 uppercase">
            Creative Developer
          </p>
        </div>

        {/* Tour-Steuerung */}
        <div className="pointer-events-auto">
          {tour?.active ? (
            <div className="glass-ui flex items-center gap-3 rounded-full px-4 py-2">
              <span className="flex items-center gap-2 text-xs text-white/80">
                <span className="h-2 w-2 animate-pulse rounded-full bg-cyan-400" />
                Tour · {tour.index}/{tour.total}
              </span>
              <button
                onClick={onStopTour}
                className="text-xs tracking-wider text-white/60 uppercase transition-colors hover:text-white"
              >
                ◼ Stop
              </button>
            </div>
          ) : (
            <button
              onClick={onStartTour}
              className="glass-ui rounded-full px-4 py-2 text-xs tracking-wider text-cyan-200 uppercase transition-colors hover:text-white"
            >
              ▶ Geführte Tour
            </button>
          )}
        </div>
      </header>

      {/* Personalisierte Begrüßung (Pro-Firma-Link) */}
      {company && !greetingDismissed && (
        <div className="animate-fade-up pointer-events-auto absolute top-24 left-1/2 -translate-x-1/2">
          <div className="glass-ui flex items-center gap-3 rounded-full px-5 py-2.5">
            <span className="text-sm text-white/90">
              👋 Willkommen, <span className="font-semibold">{company}</span> —
              kuratiert für euch.
            </span>
            <button
              onClick={() => setGreetingDismissed(true)}
              className="text-white/40 transition-colors hover:text-white"
              aria-label="Schließen"
            >
              ✕
            </button>
          </div>
        </div>
      )}

      {/* Hauptbereich: Menü + Info-Karte */}
      <div className="flex flex-1 items-center justify-between gap-6 py-8">
        {/* Navigations-Menü */}
        <nav className="pointer-events-auto flex flex-col gap-1">
          {projects.map((project, i) => {
            const isActive = project.id === selectedId
            const isRecommended = highlight.includes(project.id)
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
                    boxShadow: isActive ? `0 0 12px ${project.color}` : 'none',
                    opacity: isActive ? 1 : 0.4,
                  }}
                />
                <span className="flex flex-col">
                  <span className="flex items-center gap-2">
                    <span
                      className={`font-display text-sm font-medium transition-colors duration-300 ${
                        isActive
                          ? 'text-white'
                          : 'text-white/50 group-hover:text-white/80'
                      }`}
                    >
                      {String(i + 1).padStart(2, '0')} — {project.name}
                    </span>
                    {isRecommended && (
                      <span className="rounded-full bg-cyan-400/15 px-2 py-0.5 text-[9px] font-semibold tracking-wider text-cyan-300 uppercase">
                        ★ Empfohlen
                      </span>
                    )}
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
        {active && <ProjectCard project={active} onSelect={onSelect} />}
      </div>

      {/* Fußzeile */}
      <footer className="flex items-center justify-between text-[11px] text-white/30">
        <span>© {new Date().getFullYear()} Theo Haase</span>
        <span className="hidden md:inline">Built with R3F · Drei · GSAP</span>
      </footer>
    </div>
  )
}

// ------------------------------------------------------------------
//  Info-Karte eines Projekts inkl. Live-GitHub-Daten und Links.
//  Eigene Komponente, damit der GitHub-Hook nur lebt, solange eine
//  Karte sichtbar ist.
// ------------------------------------------------------------------
function ProjectCard({ project, onSelect }) {
  const { data, state } = useGitHubStats(project.repo)
  const codeUrl = project.repo ? `https://github.com/${project.repo}` : null

  return (
    <article
      key={project.id}
      className="glass-ui animate-fade-up pointer-events-auto w-full max-w-sm rounded-2xl p-6 md:p-7"
    >
      <div className="mb-3 flex items-center gap-2">
        <span
          className="h-2.5 w-2.5 rounded-full"
          style={{
            backgroundColor: project.color,
            boxShadow: `0 0 12px ${project.color}`,
          }}
        />
        <span className="text-[11px] tracking-[0.25em] text-white/50 uppercase">
          {project.tag}
        </span>
      </div>

      <h2 className="font-display mb-3 text-2xl font-semibold text-white">
        {project.name}
      </h2>
      <p className="mb-5 text-sm leading-relaxed text-white/60">
        {project.description}
      </p>

      {/* Live-GitHub-Daten */}
      {project.repo && (
        <div className="mb-5 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-white/55">
          {state === 'loading' && (
            <span className="text-white/40">GitHub-Daten werden geladen …</span>
          )}
          {state === 'error' && (
            <span className="text-white/30">GitHub aktuell nicht erreichbar</span>
          )}
          {state === 'done' && data && (
            <>
              <span className="flex items-center gap-1">★ {data.stars}</span>
              {data.language && (
                <span className="flex items-center gap-1.5">
                  <span
                    className="h-2 w-2 rounded-full bg-cyan-400"
                    aria-hidden
                  />
                  {data.language}
                </span>
              )}
              {data.pushedAt && (
                <span>Aktualisiert {timeAgo(data.pushedAt)}</span>
              )}
            </>
          )}
        </div>
      )}

      {/* Tech-Stack */}
      <div className="mb-5 flex flex-wrap gap-2">
        {project.stack.map((tech) => (
          <span
            key={tech}
            className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[11px] text-white/70"
          >
            {tech}
          </span>
        ))}
      </div>

      {/* Links */}
      <div className="mb-5 flex flex-wrap gap-3">
        {project.demo && (
          <a
            href={project.demo}
            target="_blank"
            rel="noreferrer"
            className="rounded-full bg-cyan-400/15 px-4 py-1.5 text-xs font-medium tracking-wider text-cyan-200 uppercase transition-colors hover:bg-cyan-400/25"
          >
            Live-Demo ↗
          </a>
        )}
        {codeUrl && (
          <a
            href={codeUrl}
            target="_blank"
            rel="noreferrer"
            className="rounded-full border border-white/15 px-4 py-1.5 text-xs font-medium tracking-wider text-white/70 uppercase transition-colors hover:text-white"
          >
            Code ↗
          </a>
        )}
      </div>

      <button
        onClick={() => onSelect(null)}
        className="text-xs tracking-wider text-cyan-300/80 uppercase transition-colors hover:text-cyan-200"
      >
        ← Zurück zur Übersicht
      </button>
    </article>
  )
}
