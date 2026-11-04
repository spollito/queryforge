import { describe, expect, it } from 'vitest'
import { formatMs, formatNumber, formatShare } from './format'

describe('format', () => {
  it('groups numbers and keeps at most two decimals', () => {
    expect(formatNumber(500000)).toBe('500,000')
    expect(formatNumber(3.6666)).toBe('3.67')
  })

  it('scales milliseconds', () => {
    expect(formatMs(2.828)).toBe('2.8 ms')
    expect(formatMs(285.8)).toBe('286 ms')
    expect(formatMs(1234)).toBe('1.23 s')
  })

  it('never shows a non-zero share as 0%', () => {
    expect(formatShare(0)).toBe('0%')
    expect(formatShare(0.004)).toBe('<1%')
    expect(formatShare(0.878)).toBe('88%')
  })
})
