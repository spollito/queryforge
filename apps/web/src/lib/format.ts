const decimal = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 })

/** Rows, costs, and other plan figures: grouped, at most two decimals. */
export function formatNumber(value: number): string {
  return decimal.format(value)
}

/** Milliseconds, with less precision as the value grows. */
export function formatMs(ms: number): string {
  if (ms >= 1000) return `${(ms / 1000).toFixed(2)} s`
  if (ms >= 100) return `${Math.round(ms)} ms`
  return `${ms.toFixed(1)} ms`
}

/** A 0 to 1 share as a whole percentage, showing "<1%" rather than "0%" for small non-zero shares. */
export function formatShare(share: number): string {
  if (share > 0 && share < 0.01) return '<1%'
  return `${Math.round(share * 100)}%`
}
