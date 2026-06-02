/**
 * PitchBackdrop
 *
 * Animated soccer-pitch background, drawn in PORTRAIT orientation — goals at the
 * top and bottom, the halfway line running across the middle. This vertical
 * layout suits a full-page background, where the viewport (and the scrolling
 * content column) is taller than it is wide.
 *
 * Renders the pitch markings (touchlines, halfway line, centre circle, penalty
 * + six-yard boxes top and bottom) plus a scatter of X and O markers that drift
 * gently and a "ball" that loops slowly down the pitch — a living tactics board.
 *
 * Pure SVG, themed via `currentColor` (pass a Tailwind text-color class on the
 * wrapper), non-interactive and low-opacity so it reads as atmosphere behind
 * content. The API mirrors StadiumBackdrop so they're drop-in interchangeable.
 *
 * Motion: the drift + ball animations are CSS keyframes in index.css (the
 * `pitch-*` rules). Only `transform` is animated, which the browser composites
 * on the GPU (no layout/paint per frame). The whole set is disabled under
 * `prefers-reduced-motion`, so the pitch becomes a still tactics diagram for
 * anyone who opts out of motion.
 *
 * Soccer-only v1. Basketball (court) and NFL (gridiron) backdrops will follow
 * the same shape. If motion ambitions grow (choreographed plays, staggered
 * routes), Framer Motion is the planned upgrade path — but this needs no deps.
 */
interface Props {
  /** Tailwind text-color class — controls every line + marker via currentColor */
  colorClass: string
  /** Intensity hint — "strong" used in headers, "subtle" for the page background */
  intensity?: 'strong' | 'subtle'
  /**
   * How the pitch fits its container:
   *  - "contain" (default, full-page background): scale uniformly so the WHOLE
   *    pitch is visible (preserveAspectRatio "meet") — nothing is cropped, so you
   *    see both goals, both penalty boxes, and the centre circle. The pitch is
   *    centred; any leftover space around it shows the page background.
   *  - "banner": stretch to fill the container exactly (preserveAspectRatio
   *    "none"). Only sensible in a container whose shape roughly matches the
   *    portrait viewBox.
   */
  variant?: 'contain' | 'banner'
}

export default function PitchBackdrop({ colorClass, intensity = 'strong', variant = 'contain' }: Props) {
  // Opacity tiers — the moving markers read a touch stronger than the static
  // chalk lines so the eye is drawn to the movement, not the markings.
  const lineOpacity   = intensity === 'strong' ? 0.18 : 0.10
  const markerOpacity = intensity === 'strong' ? 0.28 : 0.16
  const glowOpacity   = intensity === 'strong' ? 0.18 : 0.09

  return (
    <div className={`pointer-events-none absolute inset-0 overflow-hidden ${colorClass}`} aria-hidden>
      {/* Portrait viewBox (480 wide × 900 tall). Translate values in the keyframes
          (index.css) are in these user units, so ~12 units is a small, calm drift. */}
      <svg
        viewBox="0 0 480 900"
        preserveAspectRatio={variant === 'banner' ? 'none' : 'xMidYMid meet'}
        className="absolute inset-0 h-full w-full"
      >
        <defs>
          {/* Floodlight glow — shared visual language with StadiumBackdrop. */}
          <radialGradient id="pitch-glow" cx="50%" cy="50%" r="50%">
            <stop offset="0%"   stopColor="currentColor" stopOpacity={glowOpacity} />
            <stop offset="70%"  stopColor="currentColor" stopOpacity={glowOpacity * 0.3} />
            <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
          </radialGradient>
        </defs>

        {/* Glow behind each goal — top and bottom */}
        <circle cx="240" cy="20"  r="240" fill="url(#pitch-glow)" />
        <circle cx="240" cy="880" r="240" fill="url(#pitch-glow)" />

        {/* ── Pitch markings (static chalk) ───────────────────────────────── */}
        <g fill="none" stroke="currentColor" strokeWidth="2.5" opacity={lineOpacity}>
          {/* Touchlines (the full field boundary) */}
          <rect x="30" y="40" width="420" height="820" rx="3" />
          {/* Halfway line — horizontal across the middle */}
          <line x1="30" y1="450" x2="450" y2="450" />
          {/* Centre circle */}
          <circle cx="240" cy="450" r="70" />
          {/* Top penalty box + six-yard box */}
          <rect x="130" y="40" width="220" height="110" />
          <rect x="185" y="40" width="110" height="46" />
          {/* Bottom penalty box + six-yard box */}
          <rect x="130" y="750" width="220" height="110" />
          <rect x="185" y="814" width="110" height="46" />
        </g>

        {/* Centre + penalty spots (filled dots) */}
        <g fill="currentColor" opacity={lineOpacity}>
          <circle cx="240" cy="450" r="3" />
          <circle cx="240" cy="120" r="2.5" />
          <circle cx="240" cy="780" r="2.5" />
        </g>

        {/* ── Moving markers: O's = attackers, X's = defenders ─────────────── */}
        <g stroke="currentColor" strokeWidth="4" strokeLinecap="round" opacity={markerOpacity}>
          {/* O's (circles), each on its own drift loop */}
          <circle className="pitch-marker pitch-marker--a" cx="140" cy="210" r="14" fill="none" />
          <circle className="pitch-marker pitch-marker--c" cx="340" cy="320" r="14" fill="none" />
          <circle className="pitch-marker pitch-marker--b" cx="150" cy="610" r="14" fill="none" />
          <circle className="pitch-marker pitch-marker--d" cx="350" cy="710" r="14" fill="none" />

          {/* X's (two crossing strokes), grouped so each X drifts as one unit */}
          <g className="pitch-marker pitch-marker--b">
            <line x1="237" y1="237" x2="263" y2="263" />
            <line x1="263" y1="237" x2="237" y2="263" />
          </g>
          <g className="pitch-marker pitch-marker--d">
            <line x1="347" y1="497" x2="373" y2="523" />
            <line x1="373" y1="497" x2="347" y2="523" />
          </g>
          <g className="pitch-marker pitch-marker--a">
            <line x1="115" y1="457" x2="141" y2="483" />
            <line x1="141" y1="457" x2="115" y2="483" />
          </g>
          <g className="pitch-marker pitch-marker--c">
            <line x1="287" y1="637" x2="313" y2="663" />
            <line x1="313" y1="637" x2="287" y2="663" />
          </g>
        </g>

        {/* The ball — a small solid dot looping slowly down the pitch */}
        <circle
          className="pitch-ball"
          cx="220" cy="430" r="5"
          fill="currentColor"
          opacity={markerOpacity}
        />
      </svg>
    </div>
  )
}
