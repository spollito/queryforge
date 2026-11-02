import { EnvironmentStatus } from './components/EnvironmentStatus'
import { Logo } from './components/Logo'
import { PipelinePlan } from './components/PipelinePlan'

export default function App() {
  return (
    <div className="min-h-dvh">
      <header className="border-b border-rule">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-4 sm:px-8">
          <div className="flex items-center gap-2.5">
            <Logo className="size-7" />
            <span className="text-lg font-semibold tracking-tight">QueryForge</span>
          </div>
          <p className="text-sm text-ink-muted">Phase 0: foundation</p>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 pt-12 pb-20 sm:px-8 sm:pt-16">
        <section aria-labelledby="intro-heading" className="max-w-2xl">
          <h1
            id="intro-heading"
            className="text-3xl/tight font-semibold tracking-tight text-balance sm:text-[2.5rem]/[1.15]"
          >
            Find what makes a PostgreSQL query slow, rewrite it, and check that the rewrite returns the same rows.
          </h1>
          <p className="mt-5 text-lg/relaxed text-ink-muted">
            QueryForge reads execution plans to locate bottlenecks, proposes rewrites from optimization rules and a
            language model, verifies equivalence for a supported SQL subset, and benchmarks each candidate against the
            original query.
          </p>
          <p className="mt-3 text-base/relaxed text-ink-muted">
            This build is the foundation: the API, web client, and local database the engine will run on. The
            optimizer itself is not built yet.
          </p>
        </section>

        <div className="mt-14 grid gap-12 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <PipelinePlan />
          <EnvironmentStatus />
        </div>
      </main>
    </div>
  )
}
