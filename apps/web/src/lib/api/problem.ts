import { ApiError } from './client'

/** RFC 9457 problem details, with the extension fields the QueryForge API adds. */
export interface Problem {
  title?: string
  detail?: string
  status?: number
  /** Stable identifier, such as `unsupported-query` or `statement-timeout`. */
  code?: string
  sqlState?: string
  /** 1-based character position in the submitted SQL (PostgreSQL errors). */
  position?: number
  /** 1-based line and column in the submitted SQL (parse errors). */
  line?: number
  column?: number
  errors?: { field: string; message: string }[]
}

export function problemFrom(error: unknown): Problem | null {
  if (!(error instanceof ApiError) || typeof error.body !== 'object' || error.body === null) return null
  const body = error.body as Record<string, unknown>
  return typeof body.title === 'string' || typeof body.detail === 'string' ? body : null
}

export interface SqlLocation {
  lineNumber: number
  lineText: string
  /** 1-based column within `lineText`. */
  column: number
}

/**
 * Where in the SQL a problem points, if it says. The API strips surrounding
 * whitespace before parsing, so positions are resolved against the trimmed text.
 */
export function locateProblem(sql: string, problem: Problem): SqlLocation | null {
  const statement = sql.trim()
  const lines = statement.split('\n')

  if (problem.line !== undefined && problem.column !== undefined) {
    const lineText = lines[problem.line - 1]
    return lineText === undefined ? null : { lineNumber: problem.line, lineText, column: problem.column }
  }
  if (problem.position !== undefined) {
    let remaining = problem.position
    for (const [index, lineText] of lines.entries()) {
      if (remaining <= lineText.length + 1) return { lineNumber: index + 1, lineText, column: remaining }
      remaining -= lineText.length + 1
    }
  }
  return null
}
