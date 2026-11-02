/** QueryForge mark: a small plan tree whose root node is the optimized result. */
export function Logo({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={className} aria-hidden="true">
      <rect width="32" height="32" rx="7" className="fill-ink" />
      <path
        d="M11 10v12M11 16h8v6"
        fill="none"
        className="stroke-page"
        strokeWidth="2.25"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="11" cy="9" r="3.25" className="fill-molten" />
      <circle cx="11" cy="23" r="2.5" className="fill-page" />
      <circle cx="19" cy="23" r="2.5" className="fill-page" />
    </svg>
  )
}
