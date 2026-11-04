import type { Severity } from '../lib/api'

const style: Record<Severity, { dot: string; text: string; label: string }> = {
  high: { dot: 'bg-down', text: 'text-down', label: 'High impact' },
  medium: { dot: 'bg-molten', text: 'text-ink', label: 'Medium impact' },
  low: { dot: 'bg-ink-muted', text: 'text-ink-muted', label: 'Low impact' },
}

export function SeverityLabel({ severity, compact = false }: { severity: Severity; compact?: boolean }) {
  const { dot, text, label } = style[severity]
  return (
    <span className={`inline-flex shrink-0 items-center gap-1.5 font-sans text-xs font-medium ${text}`}>
      <span aria-hidden="true" className={`size-2 rounded-full ${dot}`} />
      {compact ? <span className="sr-only">{label}</span> : label}
    </span>
  )
}
