import { useState } from 'react'
import type { ExplainMode, Finding } from '../lib/api'
import { SeverityLabel } from './SeverityLabel'

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      onClick={() => {
        void navigator.clipboard.writeText(text).then(() => {
          setCopied(true)
          setTimeout(() => setCopied(false), 1500)
        })
      }}
      className="shrink-0 rounded px-2 py-1 font-sans text-xs font-medium text-ink-muted hover:text-ink focus-visible:outline-2 focus-visible:outline-ink"
    >
      {copied ? 'Copied' : 'Copy'}
    </button>
  )
}

interface FindingListProps {
  findings: Finding[]
  mode: ExplainMode
  onShowNode: (nodeId: number) => void
}

export function FindingList({ findings, mode, onShowNode }: FindingListProps) {
  if (findings.length === 0) {
    return (
      <section aria-labelledby="findings-heading">
        <h2 id="findings-heading" className="text-lg font-semibold tracking-tight">
          No bottlenecks found
        </h2>
        <p className="mt-1.5 max-w-prose text-sm/6 text-ink-muted">
          QueryForge checks for sequential scans that keep few rows, scans repeated per outer row, wrong row
          estimates, and sorts or hashes that spill to disk.
          {mode === 'estimate' && ' The last three need measurements: run the query to check them.'}
        </p>
      </section>
    )
  }

  return (
    <section aria-labelledby="findings-heading">
      <h2 id="findings-heading" className="text-lg font-semibold tracking-tight">
        {findings.length === 1 ? '1 finding' : `${findings.length} findings`}
      </h2>
      <ol className="mt-4 space-y-3">
        {findings.map((finding) => (
          <li
            key={`${finding.rule}-${finding.nodeId}`}
            className="rounded-lg border border-rule bg-surface px-4 py-4 sm:px-5"
          >
            <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
              <h3 className="font-medium">{finding.title}</h3>
              <SeverityLabel severity={finding.severity} />
            </div>
            <p className="mt-1.5 max-w-prose text-sm/6 text-ink-muted">{finding.detail}</p>
            {finding.suggestion && (
              <div className="mt-3 flex items-start justify-between gap-2 rounded-md bg-page py-2 pr-2 pl-3">
                <pre className="min-w-0 overflow-x-auto font-mono text-[0.8125rem]/6">
                  <code>{finding.suggestion}</code>
                </pre>
                <CopyButton text={finding.suggestion} />
              </div>
            )}
            <button
              type="button"
              onClick={() => onShowNode(finding.nodeId)}
              className="mt-3 text-sm font-medium underline decoration-rule underline-offset-4 hover:decoration-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
            >
              Show in plan
            </button>
          </li>
        ))}
      </ol>
    </section>
  )
}
