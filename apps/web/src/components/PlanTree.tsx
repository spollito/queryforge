import { flattenPlan, type Analysis, type ExplainMode, type Finding, type PlanNode } from '../lib/api'
import { formatMs, formatNumber, formatShare } from '../lib/format'
import { SeverityLabel } from './SeverityLabel'

function nodeLabel(node: PlanNode): string {
  const type = node.parallelAware ? `Parallel ${node.nodeType}` : node.nodeType
  if (!node.relationName) return type
  const alias = node.alias && node.alias !== node.relationName ? ` ${node.alias}` : ''
  return `${type} on ${node.relationName}${alias}`
}

// EXPLAIN's own parenthesized style, so the figures read like the output they come from.
function nodeFigures(node: PlanNode, mode: ExplainMode): string {
  if (mode === 'estimate' || node.actualRows === null) {
    return `(rows=${formatNumber(node.planRows)} cost=${formatNumber(node.totalCost)})`
  }
  if (node.actualLoops === 0) return '(never executed)'
  const loops = node.actualLoops !== null && node.actualLoops > 1 ? ` loops=${formatNumber(node.actualLoops)}` : ''
  const time = node.actualTotalTimeMs !== null ? ` time=${formatMs(node.actualTotalTimeMs)}` : ''
  return `(rows=${formatNumber(node.actualRows)}${loops}${time} est=${formatNumber(node.planRows)})`
}

function ShareBar({ share, mode }: { share: number; mode: ExplainMode }) {
  const hot = share >= 0.25
  return (
    <span
      className="flex w-20 shrink-0 items-center gap-2 sm:w-28"
      title={`${formatShare(share)} of the plan's ${mode === 'execute' ? 'time' : 'estimated cost'} is spent in this node itself`}
    >
      <span aria-hidden="true" className="h-1.5 flex-1 overflow-hidden rounded-full bg-rule/70">
        <span className="block h-full rounded-full bg-molten" style={{ width: `${share * 100}%` }} />
      </span>
      <span className={`w-8 text-right font-sans text-xs tabular-nums ${hot ? 'font-medium text-ink' : 'text-ink-muted'}`}>
        {formatShare(share)}
      </span>
    </span>
  )
}

interface PlanTreeProps {
  analysis: Analysis
  selectedNodeId: number | null
}

export function PlanTree({ analysis, selectedNodeId }: PlanTreeProps) {
  const findingsByNode = new Map<number, Finding[]>()
  for (const finding of analysis.findings) {
    findingsByNode.set(finding.nodeId, [...(findingsByNode.get(finding.nodeId) ?? []), finding])
  }

  return (
    <section aria-labelledby="plan-heading">
      <div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1">
        <h2 id="plan-heading" className="text-lg font-semibold tracking-tight">
          Execution plan
        </h2>
        <p className="text-xs text-ink-muted">
          Bars show each node's own share of the {analysis.mode === 'execute' ? 'total time' : 'estimated cost'}.
        </p>
      </div>
      <ol className="mt-4 overflow-hidden rounded-lg border border-rule bg-surface py-2 font-mono text-[0.8125rem]/6 [--plan-indent:0.875rem] sm:[--plan-indent:1.5rem]">
        {flattenPlan(analysis.plan).map(({ node, depth }) => {
          const nodeFindings = findingsByNode.get(node.id) ?? []
          const selected = node.id === selectedNodeId
          return (
            <li
              key={node.id}
              id={`plan-node-${node.id}`}
              tabIndex={-1}
              className={`flex items-start justify-between gap-4 px-4 py-1.5 outline-none sm:px-6 ${selected ? 'bg-molten/15' : ''}`}
            >
              <div className="min-w-0" style={{ paddingLeft: `calc(${depth} * var(--plan-indent))` }}>
                <div className="flex flex-wrap items-baseline gap-x-3">
                  <span>
                    {depth > 0 && (
                      <span aria-hidden="true" className="text-ink-muted">
                        -&gt;{'  '}
                      </span>
                    )}
                    <span className="font-medium">{nodeLabel(node)}</span>
                  </span>
                  <span className="text-ink-muted">{nodeFigures(node, analysis.mode)}</span>
                  {nodeFindings.map((finding) => (
                    <SeverityLabel key={finding.rule} severity={finding.severity} compact />
                  ))}
                </div>
                {Object.entries(node.details).map(([name, value]) => (
                  <div key={name} className={`text-ink-muted [overflow-wrap:anywhere] ${depth > 0 ? 'pl-[4ch]' : ''}`}>
                    {name}: {value}
                  </div>
                ))}
              </div>
              <ShareBar share={node.selfShare} mode={analysis.mode} />
            </li>
          )
        })}
      </ol>
    </section>
  )
}
