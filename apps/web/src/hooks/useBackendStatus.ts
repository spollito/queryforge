import { useCallback, useEffect, useState } from 'react'
import { apiClient, fetchHealth, type HealthReport } from '../lib/api'

export type BackendStatus =
  | { state: 'checking' }
  | { state: 'reachable'; health: HealthReport; checkedAt: Date }
  | { state: 'unreachable'; checkedAt: Date }

/** Checks API and database health once on mount and again whenever `recheck` is called. */
export function useBackendStatus() {
  const [status, setStatus] = useState<BackendStatus>({ state: 'checking' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    const settle = (next: BackendStatus) => {
      if (!controller.signal.aborted) setStatus(next)
    }

    void fetchHealth(apiClient, controller.signal).then(
      (health) => settle({ state: 'reachable', health, checkedAt: new Date() }),
      () => settle({ state: 'unreachable', checkedAt: new Date() }),
    )
    return () => controller.abort()
  }, [attempt])

  const recheck = useCallback(() => {
    setStatus({ state: 'checking' })
    setAttempt((n) => n + 1)
  }, [])

  return { status, recheck }
}
