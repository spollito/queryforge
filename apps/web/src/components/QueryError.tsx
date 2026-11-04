import { ApiError, apiClient, locateProblem, problemFrom } from '../lib/api'

export function QueryError({ error, sql }: { error: unknown; sql: string }) {
  const problem = problemFrom(error)

  if (!problem) {
    return (
      <div role="alert" className="rounded-lg border border-down/40 bg-surface px-4 py-4 sm:px-5">
        <h2 className="font-medium text-down">The analysis did not complete</h2>
        <p className="mt-1 text-sm/6 text-ink-muted">
          {error instanceof ApiError ? (
            `The API answered with HTTP ${error.status}.`
          ) : (
            <>
              No response from the API at <code className="font-mono">{apiClient.baseUrl}</code>. Start it from{' '}
              <code className="font-mono">apps/api</code> with <code className="font-mono">./mvnw spring-boot:run</code>,
              then try again.
            </>
          )}
        </p>
      </div>
    )
  }

  const location = locateProblem(sql, problem)
  const gutter = location ? `${location.lineNumber} | ` : ''

  return (
    <div role="alert" className="rounded-lg border border-down/40 bg-surface px-4 py-4 sm:px-5">
      <h2 className="font-medium text-down">{problem.title}</h2>
      <p className="mt-1 max-w-prose text-sm/6">{problem.detail}</p>
      {problem.errors?.map((fieldError) => (
        <p key={fieldError.field} className="mt-1 text-sm/6 text-ink-muted">
          {fieldError.field}: {fieldError.message}
        </p>
      ))}
      {location && (
        <pre className="mt-3 overflow-x-auto rounded-md bg-page px-3 py-2 font-mono text-[0.8125rem]/6">
          <span className="text-ink-muted">{gutter}</span>
          {location.lineText}
          {'\n'}
          <span aria-hidden="true" className="text-down">
            {' '.repeat(gutter.length + location.column - 1)}^
          </span>
        </pre>
      )}
      {problem.code === 'target-unavailable' && (
        <p className="mt-2 text-sm/6 text-ink-muted">
          For local development, start the sample database from the repository root with{' '}
          <code className="font-mono">docker compose up -d --wait sample-db</code>.
        </p>
      )}
      {problem.sqlState && <p className="mt-2 text-xs text-ink-muted">SQLSTATE {problem.sqlState}</p>}
    </div>
  )
}
