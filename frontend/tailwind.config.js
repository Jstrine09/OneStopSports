/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      fontFamily: {
        // Inter — precise, tight, built for dense sports data at any size
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
      },
      colors: {
        // Zinc has a slightly warmer cast than slate — the dark theme feels like a
        // stadium at night rather than a generic off-the-shelf "dark mode".
        surface: {
          DEFAULT: '#18181b', // zinc-900 — card / section background
          dark:    '#09090b', // zinc-950 — page background
          raised:  '#27272a', // zinc-800 — elevated within a surface
          border:  '#3f3f46', // zinc-700 — visible dividers
        },
      },
      // Safe area insets for iPhone notch / home indicator
      spacing: {
        'safe-bottom': 'env(safe-area-inset-bottom)',
      },
    },
  },
  plugins: [],
}
