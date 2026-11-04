import { useCallback, useEffect, useRef, useState } from 'react'
import { analyzeQuery, apiClient, type Analysis, type ExplainMode } from '../lib/api'

export type AnalysisState =
  | { status: 'idle' }
  | { status: 'running'; mode: ExplainMode }
  | { status: 'done'; analysis: Analysis; sql: string }
  | { status: 'failed'; error: unknown; sql: string }

/** Runs analyses one at a time; starting a new one cancels the one in flight. */
export function useAnalysis() {
  const [state, setState] = useState<AnalysisState>({ status: 'idle' })
  const inFlight = useRef<AbortController | null>(null)

  useEffect(() => () => inFlight.current?.abort(), [])

  const run = useCallback((sql: string, mode: ExplainMode) => {
    inFlight.current?.abort()
    const controller = new AbortController()
    inFlight.current = controller
    setState({ status: 'running', mode })

    void analyzeQuery(apiClient, { sql, mode }, controller.signal).then(
      (analysis) => {
        if (!controller.signal.aborted) setState({ status: 'done', analysis, sql })
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setState({ status: 'failed', error, sql })
      },
    )
  }, [])

  return { state, run }
}
