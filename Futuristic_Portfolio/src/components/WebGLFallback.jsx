// ------------------------------------------------------------------
//  Reiner HTML/CSS-Fallback, falls der Browser kein WebGL unterstützt
//  (CLAUDE.md, Abschnitt 4.4). Zeigt dieselben (dynamisch geladenen)
//  Projekte als statische, futuristisch gestaltete Karten.
// ------------------------------------------------------------------
export default function WebGLFallback({ projects = [] }) {
  return (
    <div className="bg-stage min-h-screen w-full overflow-auto p-6 md:p-12">
      <header className="mb-12">
        <h1 className="font-display text-2xl font-semibold text-white">
          Theo Haase
        </h1>
        <p className="text-xs tracking-[0.25em] text-cyan-300/70 uppercase">
          Creative Developer
        </p>
        <p className="mt-4 max-w-lg text-sm text-white/50">
          Dein Browser unterstützt kein WebGL — hier ist die klassische
          Ansicht der Projekte.
        </p>
      </header>

      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {projects.map((project) => (
          <article
            key={project.id}
            className="glass-ui rounded-2xl p-6 transition-transform duration-300 hover:-translate-y-1"
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
            <h2 className="font-display mb-2 text-xl font-semibold text-white">
              {project.name}
            </h2>
            <p className="mb-4 text-sm leading-relaxed text-white/60">
              {project.description}
            </p>
            <div className="flex flex-wrap gap-2">
              {project.stack.map((tech) => (
                <span
                  key={tech}
                  className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[11px] text-white/70"
                >
                  {tech}
                </span>
              ))}
            </div>
          </article>
        ))}
      </div>
    </div>
  )
}
