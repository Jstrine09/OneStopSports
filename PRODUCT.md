# Product

## Register

product

## Users

Sports fans who follow football (soccer), NBA, and NFL. They check in during or after games — often on mobile, often in lower ambient light. The primary task on any screen is finding out what's happening in a match right now, or how their team sits in the table. Secondary tasks are browsing rosters, reading match history, and saving favourite teams and players.

## Product Purpose

OneStopSports consolidates football, basketball, and American football into one app — live scores, standings, match detail, team rosters, and player profiles. Inspired by Fotmob. Success means a user never needs to open a separate app per sport.

## Brand Personality

Bold, alive, focused. The app should feel tuned in — like a live match is always happening somewhere. Energy without noise. Three words: electric, precise, present.

## Anti-references

- **Generic SaaS / dashboard** — white cards + blue accent indistinguishable from a project management tool. OneStopSports is not Jira with scores.
- **Old-school ESPN / Sky Sports** — heavy gradients, cramped layouts, every stat crammed onto one screen. Busyness is not energy.
- **Cookie-cutter dark mode** — light mode with `background: #1a1a1a` pasted on. Dark theme must have its own logic, depth, and atmosphere — not just inverted slate.

## Design Principles

1. **Live feels alive** — the UI communicates immediacy and energy, especially on live score and match pages. Static screens still feel like something could update at any moment.
2. **Dark by conviction** — dark is the primary theme, chosen because users are checking scores in stadiums, on sofas, at night. It has depth and intention, not just low brightness.
3. **Sport over chrome** — team crests, scores, and stats are the content. Navigation, borders, and decorative elements serve them and stay out of the way.
4. **Density with rhythm** — sports data is information-dense. Pack it tightly but with clear hierarchy and breathing room between groups, never uniform padding everywhere.
5. **Motion earns its place** — real-time score updates and live state changes justify animation. Transitions reinforce that things are happening; they don't exist to look polished.

## Accessibility & Inclusion

WCAG AA minimum. Dark-first design requires rigorous contrast ratio checks — low-contrast text on dark surfaces is the most common failure mode. Respect `prefers-reduced-motion` for all non-data animations.
