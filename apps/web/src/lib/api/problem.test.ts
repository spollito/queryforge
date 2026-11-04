import { describe, expect, it } from 'vitest'
import { ApiError } from './client'
import { locateProblem, problemFrom } from './problem'

describe('problemFrom', () => {
  it('reads problem details from an API error body', () => {
    const error = new ApiError(422, { title: 'Unsupported query', code: 'unsupported-query' }, 'failed')

    expect(problemFrom(error)).toMatchObject({ title: 'Unsupported query', code: 'unsupported-query' })
  })

  it('returns null for network failures and non-problem bodies', () => {
    expect(problemFrom(new TypeError('Failed to fetch'))).toBeNull()
    expect(problemFrom(new ApiError(502, 'Bad Gateway', 'failed'))).toBeNull()
  })
})

describe('locateProblem', () => {
  const sql = '  SELECT id\nFROM orders\nWHERE nope = 1;'

  it('maps a PostgreSQL character position to its line, ignoring leading whitespace', () => {
    // "nope" starts at character 29 of the trimmed statement.
    expect(locateProblem(sql, { position: 29 })).toEqual({ lineNumber: 3, lineText: 'WHERE nope = 1;', column: 7 })
  })

  it('uses line and column from parse errors directly', () => {
    expect(locateProblem(sql, { line: 2, column: 6 })).toEqual({ lineNumber: 2, lineText: 'FROM orders', column: 6 })
  })

  it('returns null when the problem has no location or it is out of range', () => {
    expect(locateProblem(sql, { title: 'Statement timed out' })).toBeNull()
    expect(locateProblem(sql, { line: 9, column: 1 })).toBeNull()
    expect(locateProblem(sql, { position: 500 })).toBeNull()
  })
})
