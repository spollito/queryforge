type StepStatus = 'done' | 'next' | 'planned'

interface PlanStep {
  operation: string
  detail: string
  status?: StepStatus
}

// Root first, as EXPLAIN prints it: each step consumes the output of the step below it.
// Phases match docs/roadmap.md.
const steps: PlanStep[] = [
  { operation: 'Optimize Query', detail: 'phases=0..4' },
  { operation: 'Benchmark Candidates', detail: 'phase=1', status: 'next' },
  { operation: 'Rank Candidates', detail: 'phase=3 model=xgboost', status: 'planned' },
  { operation: 'Verify Equivalence', detail: 'phase=2 solver=z3', status: 'planned' },
  { operation: 'Generate Rewrites', detail: 'phase=1..2 via=rules,llm', status: 'next' },
  { operation: 'Detect Bottlenecks', detail: 'phase=1', status: 'next' },
  { operation: 'Analyze Plan', detail: 'phase=1 parser=calcite', status: 'next' },
  { operation: 'Seq Scan on foundation', detail: 'phase=0', status: 'done' },
]

const statusLabel: Record<StepStatus, string> = {
  done: 'Done',
  next: 'Next',
  planned: 'Planned',
}

const statusStyle: Record<StepStatus, string> = {
  done: 'text-ok',
  next: 'rounded-sm bg-molten px-1.5 text-on-molten',
  planned: 'text-ink-muted',
}

export function PipelinePlan() {
  return (
    <section aria-labelledby="pipeline-heading">
      <h2 id="pipeline-heading" className="text-lg font-semibold tracking-tight">
        How a query will move through QueryForge
      </h2>
      <p className="mt-1.5 max-w-prose text-sm/6 text-ink-muted">
        Read it like EXPLAIN output: execution starts at the bottom, and each step feeds the one above it.
      </p>

      <ol
        aria-label="Optimization pipeline, outermost step first"
        className="mt-5 overflow-hidden rounded-lg border border-rule bg-surface py-3 font-mono text-[0.8125rem]/6 [--plan-indent:0.875rem] sm:[--plan-indent:1.5rem]"
      >
        {steps.map((step, depth) => (
          <li
            key={step.operation}
            // Rows appear leaf-first, in the order the plan would execute.
            style={{ animationDelay: `${(steps.length - 1 - depth) * 70}ms` }}
            className="flex items-baseline justify-between gap-3 px-4 py-1 motion-safe:animate-plan-row sm:px-6"
          >
            <span className="flex min-w-0 flex-wrap items-baseline gap-x-3" style={{ paddingLeft: `calc(${depth} * var(--plan-indent))` }}>
              <span>
                {depth > 0 && (
                  <span aria-hidden="true" className="text-ink-muted">
                    -&gt;{'  '}
                  </span>
                )}
                <span className="font-medium">{step.operation}</span>
              </span>
              <span className="text-ink-muted">({step.detail})</span>
            </span>
            {step.status && (
              <span className={`shrink-0 font-sans text-xs font-medium ${statusStyle[step.status]}`}>
                {statusLabel[step.status]}
              </span>
            )}
          </li>
        ))}
      </ol>
    </section>
  )
}
