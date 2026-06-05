import { Html, useProgress } from '@react-three/drei'

// ------------------------------------------------------------------
//  Eleganter Ladebildschirm via useProgress (CLAUDE.md, Abschnitt 4.1).
//  Wird als <Suspense fallback> innerhalb der Canvas genutzt.
// ------------------------------------------------------------------
export default function Loader() {
  const { progress, active } = useProgress()

  return (
    <Html center>
      <div className="flex w-56 flex-col items-center gap-3 select-none">
        <div className="text-xs tracking-[0.3em] text-cyan-300/80 uppercase">
          {active ? 'Lade Szene' : 'Bereit'}
        </div>
        <div className="h-px w-full overflow-hidden rounded bg-white/10">
          <div
            className="h-full rounded bg-gradient-to-r from-cyan-400 to-violet-500 transition-[width] duration-200 ease-out"
            style={{ width: `${progress}%` }}
          />
        </div>
        <div className="font-mono text-sm text-white/60">
          {Math.round(progress)}%
        </div>
      </div>
    </Html>
  )
}
