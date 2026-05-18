/**
 * StadiumBackdrop
 *
 * Atmospheric background for league-identity sections. Renders:
 *   - Two corner floodlight orbs (large, blurred, league-colored)
 *   - A subtle stadium bowl silhouette along the bottom edge
 *   - A faint dotted "crowd" texture inside the bowl
 *
 * Designed to sit behind content — all elements are non-interactive and
 * low-opacity so they read as atmosphere, not as the dominant element.
 *
 * The accent color is consumed via `currentColor`, so you pass it in via
 * a text-color class on the wrapper (e.g. `text-purple-500`). This avoids
 * having to wire Tailwind config for arbitrary color values inside SVGs.
 */
interface Props {
  /** Tailwind text-color class — controls the silhouette + floodlight color via currentColor */
  colorClass: string
  /** Intensity hint — "strong" used in league headers, "subtle" elsewhere */
  intensity?: 'strong' | 'subtle'
}

export default function StadiumBackdrop({ colorClass, intensity = 'strong' }: Props) {
  const orbOpacity = intensity === 'strong' ? 0.22 : 0.12
  const bowlOpacity = intensity === 'strong' ? 0.10 : 0.05
  const dotOpacity = intensity === 'strong' ? 0.20 : 0.10

  return (
    <div className={`pointer-events-none absolute inset-0 overflow-hidden ${colorClass}`} aria-hidden>
      <svg
        viewBox="0 0 800 240"
        preserveAspectRatio="none"
        className="absolute inset-0 h-full w-full"
      >
        <defs>
          {/* Floodlight glow — radial gradient that fades to transparent */}
          <radialGradient id="floodlight" cx="50%" cy="50%" r="50%">
            <stop offset="0%"   stopColor="currentColor" stopOpacity={orbOpacity} />
            <stop offset="60%"  stopColor="currentColor" stopOpacity={orbOpacity * 0.35} />
            <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
          </radialGradient>

          {/* Crowd dot pattern — small dots on a grid for the seating texture */}
          <pattern id="crowd" x="0" y="0" width="14" height="14" patternUnits="userSpaceOnUse">
            <circle cx="2" cy="2" r="1" fill="currentColor" opacity={dotOpacity} />
            <circle cx="9" cy="9" r="1" fill="currentColor" opacity={dotOpacity * 0.7} />
          </pattern>

          {/* Stadium bowl shape — used as a clip path so the crowd dots only
              appear inside the bowl area, not across the whole panel */}
          <clipPath id="bowl">
            <path d="M -50 240 L -50 180 Q 400 80 850 180 L 850 240 Z" />
          </clipPath>
        </defs>

        {/* Top-left floodlight orb */}
        <circle cx="80" cy="40" r="180" fill="url(#floodlight)" />
        {/* Top-right floodlight orb */}
        <circle cx="720" cy="40" r="180" fill="url(#floodlight)" />

        {/* Crowd texture — clipped to the bowl shape */}
        <rect x="0" y="0" width="800" height="240" fill="url(#crowd)" clipPath="url(#bowl)" />

        {/* Stadium bowl silhouette — slightly transparent so it sits behind crowd */}
        <path
          d="M -50 240 L -50 180 Q 400 80 850 180 L 850 240 Z"
          fill="currentColor"
          opacity={bowlOpacity}
        />

        {/* Bowl rim accent — a sharper line at the top of the bowl */}
        <path
          d="M -50 180 Q 400 80 850 180"
          fill="none"
          stroke="currentColor"
          strokeWidth="1"
          opacity={bowlOpacity * 2.5}
        />
      </svg>
    </div>
  )
}
