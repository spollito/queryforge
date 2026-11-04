import { EnvironmentStatus } from './components/EnvironmentStatus'
import { Logo } from './components/Logo'
import { PipelinePlan } from './components/PipelinePlan'
import { QueryWorkbench } from './components/QueryWorkbench'

export default function App() {
  return (
    <div className="min-h-dvh">
      <header className="border-b border-rule">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-4 sm:px-8">
          <div className="flex items-center gap-2.5">
            <Logo className="size-7" />
            <span className="text-lg font-semibold tracking-tight">QueryForge</span>
          </div>
          <p className="text-sm text-ink-muted">Phase 1: query analysis</p>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 pt-10 pb-20 sm:px-8 sm:pt-14">
        <section aria-labelledby="intro-heading" className="max-w-2xl">
          <h1 id="intro-heading" className="text-2xl/tight font-semibold tracking-tight text-balance sm:text-3xl/tight">
            Find what makes a PostgreSQL query slow
          </h1>
          <p className="mt-3 text-base/relaxed text-ink-muted">
            Paste a query for the sample database. QueryForge plans it with EXPLAIN, reads the plan, and points to
            each bottleneck with a fix to try. Rewriting queries and verifying rewrites come next.
          </p>
        </section>

        <div className="mt-8">
          <QueryWorkbench />
        </div>

        <div className="mt-20 grid gap-12 border-t border-rule pt-12 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <PipelinePlan />
          <EnvironmentStatus />
        </div>
      </main>
    </div>
  )
}
