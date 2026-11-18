export function Logo({ size = 22 }: { size?: number }) {
  return (
    <span className="logo">
      <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
        <rect width="32" height="32" rx="8" fill="var(--accent)" />
        <path d="M4 17h6l3-8 5 14 3-6h7" fill="none" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      PulseWatch
    </span>
  );
}
