export default function LoadingSpinner({ className = '' }: { className?: string }) {
  return (
    <div className={`flex items-center justify-center py-12 ${className}`}>
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-stone-300 border-t-amber-500 dark:border-zinc-700 dark:border-t-amber-400" />
    </div>
  )
}
