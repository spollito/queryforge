import { useState } from 'react'
import type { Analysis } from '../lib/api'
import { formatMs } from '../lib/format'
import { FindingList } from './FindingList'
import { PlanTree } from './PlanTree'

function summary(analysis: Analysis): string {
  if (analysis.mode === 'estimate') {
    return 'Estimated plan: PostgreSQL planned the query without running it.'
  }
  const planning = analysis.planningTimeMs === null ? '' : ` after ${formatMs(analysis.planningTimeMs)} of planning`
  return `Measured plan: the query ran in ${formatMs(analysis.executionTimeMs ?? 0)}${planning}.`
}

export function AnalysisResults({ analysis }: { analysis: Analysis }) {
  const [selectedNodeId, setSelectedNodeId] = useState<number | null>(null)

  const showNode = (nodeId: number) => {
    setSelectedNodeId(nodeId)
    const row = document.getElementById(`plan-node-${nodeId}`)
    const smooth = !window.matchMedia('(prefers-reduced-motion: reduce)').matches
    row?.scrollIntoView({ block: 'center', behavior: smooth ? 'smooth' : 'auto' })
    row?.focus({ preventScroll: true })
  }

  return (
    <div className="space-y-10">
      <p className="text-sm text-ink-muted">{summary(analysis)}</p>
      <FindingList findings={analysis.findings} mode={analysis.mode} onShowNode={showNode} />
      <PlanTree analysis={analysis} selectedNodeId={selectedNodeId} />
    </div>
  )
}
