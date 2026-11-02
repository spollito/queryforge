import type { ReactNode } from 'react'
import { useBackendStatus, type BackendStatus } from '../hooks/useBackendStatus'
import { apiClient } from '../lib/api'

type Tone = 'ok' | 'down' | 'pending'

interface Indicator {
  label: string
  tone: Tone
}

const toneStyle: Record<Tone, { dot: string; text: string }> = {
  ok: { dot: 'bg-ok', text: 'text-ok' },
  down: { dot: 'bg-down', text: 'text-down' },
  pending: { dot: 'bg-ink-muted/60', text: 'text-ink-muted' },
}

function apiIndicator(status: BackendStatus): Indicator {
  switch (status.state) {
    case 'checking':
      return { label: 'Checking', tone: 'pending' }
    case 'reachable':
      return { label: 'Reachable', tone: 'ok' }
    case 'unreachable':
      return { label: 'Not reachable', tone: 'down' }
  }
}

function databaseIndicator(status: BackendStatus): Indicator {
  if (status.state === 'checking') return { label: 'Checking', tone: 'pending' }
  if (status.state === 'unreachable') return { label: 'Unknown', tone: 'pending' }

  switch (status.health.components?.db?.status) {
    case 'UP':
      return { label: 'Up', tone: 'ok' }
    case 'DOWN':
      return { label: 'Down', tone: 'down' }
    default:
      return { label: 'Unknown', tone: 'pending' }
  }
}

function hintFor(status: BackendStatus): ReactNode {
  if (status.state === 'unreachable') {
    return (
      <>
        No response from the API. Start it from <code className="font-mono">apps/api</code> with{' '}
        <code className="font-mono">./mvnw spring-boot:run</code>, then check again.
      </>
    )
  }
  if (status.state === 'reachable' && status.health.components?.db?.status === 'DOWN') {
    return (
      <>
        The API is running but can't reach PostgreSQL. Start it from the repository root with{' '}
        <code className="font-mono">docker compose up -d</code>.
      </>
    )
  }
  return null
}

function StatusRow({ name, source, indicator }: { name: string; source: ReactNode; indicator: Indicator }) {
  const tone = toneStyle[indicator.tone]
  return (
    <div className="flex items-start justify-between gap-4 px-4 py-3.5">
      <div className="min-w-0">
        <dt className="text-sm font-medium">{name}</dt>
        <dd className="mt-0.5 truncate text-xs text-ink-muted">{source}</dd>
      </div>
      <dd className={`flex shrink-0 items-center gap-2 text-sm font-medium ${tone.text}`}>
        <span aria-hidden="true" className={`size-2 rounded-full ${tone.dot}`} />
        {indicator.label}
      </dd>
    </div>
  )
}

export function EnvironmentStatus() {
  const { status, recheck } = useBackendStatus()
  const hint = hintFor(status)

  return (
    <section aria-labelledby="environment-heading">
      <h2 id="environment-heading" className="text-lg font-semibold tracking-tight">
        Local environment
      </h2>
      <p className="mt-1.5 text-sm/6 text-ink-muted">Live checks against the services on this machine.</p>

      <div className="mt-5 rounded-lg border border-rule bg-surface">
        <dl aria-live="polite" className="divide-y divide-rule">
          <StatusRow
            name="API"
            source={<span className="font-mono">{apiClient.baseUrl}</span>}
            indicator={apiIndicator(status)}
          />
          <StatusRow name="Database" source="PostgreSQL, as seen by the API" indicator={databaseIndicator(status)} />
        </dl>

        {hint && <p className="border-t border-rule px-4 py-3 text-sm/6 text-ink-muted">{hint}</p>}

        <div className="flex items-center justify-between gap-4 border-t border-rule px-4 py-3">
          <p className="text-xs text-ink-muted">
            {status.state === 'checking' ? 'Checking now' : `Checked at ${status.checkedAt.toLocaleTimeString()}`}
          </p>
          <button
            type="button"
            onClick={recheck}
            disabled={status.state === 'checking'}
            className="rounded-md border border-rule px-3 py-1.5 text-sm font-medium transition-colors hover:bg-page focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink disabled:cursor-not-allowed disabled:opacity-60"
          >
            Check again
          </button>
        </div>
      </div>
    </section>
  )
}
