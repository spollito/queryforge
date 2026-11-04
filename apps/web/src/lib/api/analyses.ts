import type { ApiClient } from './client'

/** `estimate` plans the query without running it; `execute` runs it with EXPLAIN ANALYZE. */
export type ExplainMode = 'estimate' | 'execute'

export type Severity = 'high' | 'medium' | 'low'

export interface PlanNode {
  id: number
  nodeType: string
  /** PostgreSQL's text output prefixes such nodes with "Parallel". */
  parallelAware: boolean
  relationName: string | null
  alias: string | null
  parentRelationship: string | null
  startupCost: number
  totalCost: number
  /** Estimated rows per loop (per process in parallel plans), as PostgreSQL reports it. */
  planRows: number
  actualRows: number | null
  actualLoops: number | null
  actualTotalTimeMs: number | null
  /** The node's own share of total time (execute) or estimated cost (estimate), 0 to 1. */
  selfShare: number
  /** EXPLAIN properties worth showing, such as Filter or Sort Key, in display order. */
  details: Record<string, string>
  children: PlanNode[]
}

export interface Finding {
  rule: string
  severity: Severity
  nodeId: number
  title: string
  detail: string
  suggestion: string | null
}

export interface Analysis {
  mode: ExplainMode
  planningTimeMs: number | null
  executionTimeMs: number | null
  plan: PlanNode
  findings: Finding[]
}

// Execute mode can run up to the API's statement timeout (10 s by default), plus planning and transfer.
const ANALYSIS_TIMEOUT_MS = 30_000

export function analyzeQuery(
  client: ApiClient,
  request: { sql: string; mode: ExplainMode },
  signal?: AbortSignal,
): Promise<Analysis> {
  return client.post<Analysis>('/api/v1/analyses', request, { signal, timeoutMs: ANALYSIS_TIMEOUT_MS })
}

export interface PlanRow {
  node: PlanNode
  depth: number
}

/** The plan as rows in EXPLAIN's reading order: each node followed by its children, one level deeper. */
export function flattenPlan(root: PlanNode): PlanRow[] {
  const rows: PlanRow[] = []
  const visit = (node: PlanNode, depth: number) => {
    rows.push({ node, depth })
    for (const child of node.children) visit(child, depth + 1)
  }
  visit(root, 0)
  return rows
}
