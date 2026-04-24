/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // App-wide dark palette
        surface: {
          DEFAULT: '#1e293b', // slate-800 — card background
          dark:    '#0f172a', // slate-900 — page background
          light:   '#334155', // slate-700 — borders / hover
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
