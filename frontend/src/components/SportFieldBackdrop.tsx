import { useId } from 'react'

/**
 * SportFieldBackdrop
 *
 * Sport-aware playing-field background, drawn in PORTRAIT from real court/field
 * diagrams. One component, three variants:
 *   - "bowl"     → soccer pitch        (football)
 *   - "court"    → basketball court     (NBA)
 *   - "gridiron" → American-football    (NFL)
 *
 * Ported from the Claude Design handoff (project/backdrop.jsx), adapted to this
 * codebase's conventions: themed via `currentColor` (pass a Tailwind text-color
 * class) instead of a raw hex, so it reads correctly in light AND dark mode.
 *
 * Two motion layers, both gated behind `prefers-reduced-motion` and the `animate`
 * prop (which toggles the `oss-backdrop--anim` class — see index.css):
 *   1. Two floodlight glows that slowly "breathe" (opacity), top and bottom.
 *   2. A scatter of X and O markers + a ball that drift gently — the tactics-board
 *      motion we kept from the earlier soccer-only version, now over any field.
 *
 * Non-interactive (`pointer-events-none`) and low-opacity so it sits behind
 * content as atmosphere. Designed to be paired with `.glass-card` surfaces so the
 * field reads through tables/match rows without hurting text legibility.
 *
 * preserveAspectRatio="xMidYMid slice" — the portrait field scales to cover its
 * container, cropping the overflow, so it fills both short-wide match groups and
 * tall standings panels without distortion.
 */
interface Props {
  /** Tailwind text-color class — controls every line + marker via currentColor */
  colorClass: string
  /** Which field to draw */
  variant: 'bowl' | 'court' | 'gridiron'
  /** Intensity hint — "strong" for hero panels, "subtle" behind dense content */
  intensity?: 'strong' | 'subtle'
  /** Whether the breathing-glow + marker drift run (default true). Honoured
      together with the user's reduced-motion preference. */
  animate?: boolean
  /** Extra classes for the root wrapper — e.g. "hidden md:block" to drop the
      field on phone-sized screens. */
  className?: string
}

// ── Field geometry (portrait, viewBox 0 0 360 760) ──────────────────────────
// Shared field rectangle bounds, matching the Design source.
const L = 28, R = 332, T = 28, B = 732, MX = 180, MY = 380

// Soccer pitch — halfway line, centre circle, penalty + goal areas, D-arcs, corners.
function Soccer() {
  return (
    <g>
      <rect x={L} y={T} width={R - L} height={B - T} fill="none" strokeWidth={2.4} />
      <line x1={L} y1={MY} x2={R} y2={MY} strokeWidth={2.4} />
      <circle cx={MX} cy={MY} r={56} fill="none" strokeWidth={2.4} />
      <circle cx={MX} cy={MY} r={4} fill="currentColor" strokeWidth={0} />
      {/* top penalty + goal area */}
      <rect x={82} y={T} width={196} height={116} fill="none" strokeWidth={2.4} />
      <rect x={128} y={T} width={104} height={56} fill="none" strokeWidth={2.4} />
      <circle cx={180} cy={96} r={4} fill="currentColor" strokeWidth={0} />
      <path d="M132 144 Q180 198 228 144" fill="none" strokeWidth={2.4} />
      {/* bottom penalty + goal area */}
      <rect x={82} y={B - 116} width={196} height={116} fill="none" strokeWidth={2.4} />
      <rect x={128} y={B - 56} width={104} height={56} fill="none" strokeWidth={2.4} />
      <circle cx={180} cy={B - 96} r={4} fill="currentColor" strokeWidth={0} />
      <path d={`M132 ${B - 144} Q180 ${B - 198} 228 ${B - 144}`} fill="none" strokeWidth={2.4} />
      {/* corner arcs */}
      <path d={`M${L} ${T + 14} Q${L} ${T} ${L + 14} ${T}`} fill="none" strokeWidth={2} />
      <path d={`M${R - 14} ${T} Q${R} ${T} ${R} ${T + 14}`} fill="none" strokeWidth={2} />
      <path d={`M${L} ${B - 14} Q${L} ${B} ${L + 14} ${B}`} fill="none" strokeWidth={2} />
      <path d={`M${R - 14} ${B} Q${R} ${B} ${R} ${B - 14}`} fill="none" strokeWidth={2} />
    </g>
  )
}

// Basketball court — centre circle, keys, free-throw circles, backboards, hoops, 3pt arcs.
function Court() {
  return (
    <g>
      <rect x={L} y={T} width={R - L} height={B - T} fill="none" strokeWidth={2.4} />
      <line x1={L} y1={MY} x2={R} y2={MY} strokeWidth={2.4} />
      <circle cx={MX} cy={MY} r={46} fill="none" strokeWidth={2.4} />
      {/* top half */}
      <rect x={132} y={T} width={96} height={150} fill="none" strokeWidth={2.4} />
      <circle cx={180} cy={T + 150} r={46} fill="none" strokeWidth={2.4} />
      <line x1={150} y1={T + 22} x2={210} y2={T + 22} strokeWidth={3} />
      <circle cx={180} cy={T + 34} r={8} fill="none" strokeWidth={2.4} />
      <path d={`M70 ${T} L70 ${T + 86} Q70 ${T + 250} 180 ${T + 250} Q290 ${T + 250} 290 ${T + 86} L290 ${T}`} fill="none" strokeWidth={2.4} />
      {/* bottom half */}
      <rect x={132} y={B - 150} width={96} height={150} fill="none" strokeWidth={2.4} />
      <circle cx={180} cy={B - 150} r={46} fill="none" strokeWidth={2.4} />
      <line x1={150} y1={B - 22} x2={210} y2={B - 22} strokeWidth={3} />
      <circle cx={180} cy={B - 34} r={8} fill="none" strokeWidth={2.4} />
      <path d={`M70 ${B} L70 ${B - 86} Q70 ${B - 250} 180 ${B - 250} Q290 ${B - 250} 290 ${B - 86} L290 ${B}`} fill="none" strokeWidth={2.4} />
    </g>
  )
}

// American-football field — goal lines, yard lines, hashmark + sideline ticks.
function Gridiron() {
  const GT = 120, GB = 640 // goal lines
  const els: React.ReactNode[] = [
    <rect key="o" x={L} y={T} width={R - L} height={B - T} fill="none" strokeWidth={2.4} />,
    <line key="gt" x1={L} y1={GT} x2={R} y2={GT} strokeWidth={3} />,
    <line key="gb" x1={L} y1={GB} x2={R} y2={GB} strokeWidth={3} />,
  ]
  // yard lines (10 gaps between goal lines; midfield a touch heavier)
  const span = GB - GT, step = span / 10
  for (let i = 1; i < 10; i++) {
    const y = GT + step * i
    els.push(<line key={`y${i}`} x1={L} y1={y} x2={R} y2={y} strokeWidth={i === 5 ? 3 : 2} />)
  }
  // hashmark columns (two inner rows of ticks every half-yard)
  const hstep = step / 2
  let k = 0
  for (let y = GT + hstep; y < GB; y += hstep) {
    els.push(<line key={`hl${k}`} x1={132} y1={y - 4} x2={132} y2={y + 4} strokeWidth={1.4} />)
    els.push(<line key={`hr${k}`} x1={228} y1={y - 4} x2={228} y2={y + 4} strokeWidth={1.4} />)
    els.push(<line key={`sl${k}`} x1={L} y1={y - 3} x2={L + 12} y2={y - 3} strokeWidth={1.2} />)
    els.push(<line key={`sr${k}`} x1={R - 12} y1={y - 3} x2={R} y2={y - 3} strokeWidth={1.2} />)
    k++
  }
  return <g>{els}</g>
}

// Drifting X/O markers + ball, overlaid on whatever field is shown. Positioned in
// the central band so they stay visible after the "slice" crop on short panels.
function Markers({ opacity }: { opacity: number }) {
  return (
    <>
      <g stroke="currentColor" strokeWidth={4} strokeLinecap="round" opacity={opacity}>
        {/* O's */}
        <circle className="pitch-marker pitch-marker--a" cx={78}  cy={300} r={11} fill="none" />
        <circle className="pitch-marker pitch-marker--c" cx={284} cy={332} r={11} fill="none" />
        <circle className="pitch-marker pitch-marker--b" cx={96}  cy={462} r={11} fill="none" />
        {/* X's */}
        <g className="pitch-marker pitch-marker--b">
          <line x1={170} y1={290} x2={190} y2={310} />
          <line x1={190} y1={290} x2={170} y2={310} />
        </g>
        <g className="pitch-marker pitch-marker--d">
          <line x1={262} y1={462} x2={282} y2={482} />
          <line x1={282} y1={462} x2={262} y2={482} />
        </g>
        <g className="pitch-marker pitch-marker--a">
          <line x1={100} y1={408} x2={120} y2={428} />
          <line x1={120} y1={408} x2={100} y2={428} />
        </g>
      </g>
      <circle className="pitch-ball" cx={180} cy={420} r={4} fill="currentColor" opacity={opacity} />
    </>
  )
}

export default function SportFieldBackdrop({ colorClass, variant, intensity = 'strong', animate = true, className = '' }: Props) {
  // Unique gradient id per instance — multiple backdrops render on one page
  // (e.g. one per league group on Home), so a shared static id would clash.
  const glowId = useId()

  // Note: these are deliberately fairly high. The field is usually viewed THROUGH
  // a translucent .glass-card, which knocks the effective opacity down a lot, and
  // a bright accent at low opacity over a near-black dark theme has little contrast.
  const strong = intensity === 'strong'
  const lineOpacity   = strong ? 0.42 : 0.20
  const markerOpacity = strong ? 0.40 : 0.22
  const glowOpacity   = strong ? 0.26 : 0.13

  const field = variant === 'court' ? <Court /> : variant === 'gridiron' ? <Gridiron /> : <Soccer />

  return (
    <div
      className={`pointer-events-none absolute inset-0 overflow-hidden oss-backdrop ${animate ? 'oss-backdrop--anim' : ''} ${colorClass} ${className}`}
      aria-hidden
    >
      <svg viewBox="0 0 360 760" preserveAspectRatio="xMidYMid slice" className="absolute inset-0 h-full w-full">
        <defs>
          <radialGradient id={glowId} cx="50%" cy="50%" r="50%">
            <stop offset="0%"   stopColor="currentColor" stopOpacity={glowOpacity} />
            <stop offset="60%"  stopColor="currentColor" stopOpacity={glowOpacity * 0.3} />
            <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
          </radialGradient>
        </defs>

        {/* Floodlight glows — top & bottom, breathing (see index.css) */}
        <circle className="oss-flood oss-flood--l" cx={180} cy={90}  r={260} fill={`url(#${glowId})`} />
        <circle className="oss-flood oss-flood--r" cx={180} cy={670} r={260} fill={`url(#${glowId})`} />

        {/* Field lines */}
        <g stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" opacity={lineOpacity}>
          {field}
        </g>

        {/* Drifting X's, O's and a ball on top */}
        <Markers opacity={markerOpacity} />
      </svg>
    </div>
  )
}

// Maps a Sport.slug to the matching field variant. Centralised so every screen
// resolves the field the same way.
export function fieldVariantForSport(sportSlug: string | null | undefined): 'bowl' | 'court' | 'gridiron' {
  if (sportSlug === 'basketball') return 'court'
  if (sportSlug === 'american-football') return 'gridiron'
  return 'bowl' // football (soccer) + default
}
