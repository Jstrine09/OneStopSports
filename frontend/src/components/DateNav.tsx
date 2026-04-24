import { ChevronLeft, ChevronRight } from 'lucide-react'

interface Props {
  date: string           // YYYY-MM-DD
  onChange: (d: string) => void
}

function addDays(dateStr: string, days: number): string {
  const d = new Date(dateStr)
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

function label(dateStr: string): string {
  const today = new Date().toISOString().slice(0, 10)
  if (dateStr === today)              return 'Today'
  if (dateStr === addDays(today, -1)) return 'Yesterday'
  if (dateStr === addDays(today, 1))  return 'Tomorrow'
  return new Date(dateStr).toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' })
}

export default function DateNav({ date, onChange }: Props) {
  return (
    <div className="flex items-center justify-between rounded-xl bg-slate-800 px-2 py-1">
      <button
        onClick={() => onChange(addDays(date, -1))}
        className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-700 hover:text-white active:scale-95"
        aria-label="Previous day"
      >
        <ChevronLeft size={20} />
      </button>

      <span className="text-sm font-semibold">{label(date)}</span>

      <button
        onClick={() => onChange(addDays(date, 1))}
        className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-700 hover:text-white active:scale-95"
        aria-label="Next day"
      >
        <ChevronRight size={20} />
      </button>
    </div>
  )
}
