import { useState, type KeyboardEvent } from 'react'
import { useAnalysis } from '../hooks/useAnalysis'
import type { ExplainMode } from '../lib/api'
import { sampleQueries } from '../lib/samples'
import { AnalysisResults } from './AnalysisResults'
import { QueryError } from './QueryError'

const [firstSample] = sampleQueries

export function QueryWorkbench() {
  const [sql, setSql] = useState(firstSample.sql)
  const [mode, setMode] = useState<ExplainMode>(firstSample.mode)
  const { state, run } = useAnalysis()
  const running = state.status === 'running'

  const submit = () => {
    if (sql.trim() && !running) run(sql, mode)
  }

  const submitOnModEnter = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
      event.preventDefault()
      submit()
    }
  }

  return (
    <section aria-label="Query analysis">
      <form
        onSubmit={(event) => {
          event.preventDefault()
          submit()
        }}
      >
        <div role="group" aria-labelledby="samples-label" className="flex flex-wrap items-center gap-2">
          <span id="samples-label" className="mr-1 text-sm text-ink-muted">
            Sample queries
          </span>
          {sampleQueries.map((sample) => (
            <button
              key={sample.label}
              type="button"
              aria-pressed={sql === sample.sql}
              onClick={() => {
                setSql(sample.sql)
                setMode(sample.mode)
              }}
              className="rounded-full border border-rule px-3 py-1 text-sm transition-colors hover:bg-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink aria-pressed:border-ink aria-pressed:bg-surface"
            >
              {sample.label}
            </button>
          ))}
        </div>

        <label htmlFor="sql" className="sr-only">
          SQL query
        </label>
        <textarea
          id="sql"
          value={sql}
          onChange={(event) => setSql(event.target.value)}
          onKeyDown={submitOnModEnter}
          rows={8}
          spellCheck={false}
          autoCapitalize="off"
          autoCorrect="off"
          className="mt-4 block w-full resize-y rounded-lg border border-rule bg-surface px-4 py-3 font-mono text-[0.8125rem]/6 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
        />

        <div className="mt-4 flex flex-wrap items-center justify-between gap-x-6 gap-y-4">
          <label className="flex max-w-md items-start gap-3 text-sm">
            <input
              type="checkbox"
              checked={mode === 'execute'}
              onChange={(event) => setMode(event.target.checked ? 'execute' : 'estimate')}
              className="mt-0.5 size-4 shrink-0 accent-ink"
            />
            <span>
              Run the query (EXPLAIN ANALYZE)
              <span className="mt-0.5 block text-xs/5 text-ink-muted">
                Measures actual rows and time. The query runs read-only, is rolled back, and is stopped if it
                takes too long.
              </span>
            </span>
          </label>
          <div className="flex items-center gap-3">
            <span className="hidden text-xs text-ink-muted sm:inline">
              <kbd className="font-sans">⌘</kbd> or <kbd className="font-sans">Ctrl</kbd> + Enter
            </span>
            <button
              type="submit"
              disabled={running || !sql.trim()}
              className="rounded-md bg-ink px-4 py-2 text-sm font-medium text-page transition-opacity hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink disabled:cursor-not-allowed disabled:opacity-60"
            >
              {running ? 'Analyzing…' : 'Analyze query'}
            </button>
          </div>
        </div>
      </form>

      <div aria-live="polite" className="mt-10">
        {state.status === 'failed' && <QueryError error={state.error} sql={state.sql} />}
        {state.status === 'done' && <AnalysisResults key={state.sql + state.analysis.mode} analysis={state.analysis} />}
      </div>
    </section>
  )
}
