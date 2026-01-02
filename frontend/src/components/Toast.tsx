import React, { useEffect, useState } from 'react'

export function triggerToast(message: string, duration = 3000) {
  try {
    window.dispatchEvent(new CustomEvent('traveloptimizer.toast', { detail: { message, duration } }))
  } catch (e) {}
}

export default function Toast() {
  const [msg, setMsg] = useState<string | null>(null)

  useEffect(() => {
    function handler(e: any) {
      const m = e.detail?.message
      const d = e.detail?.duration || 3000
      if (!m) return
      setMsg(m)
      setTimeout(() => setMsg(null), d)
    }
    window.addEventListener('traveloptimizer.toast', handler as EventListener)
    return () => window.removeEventListener('traveloptimizer.toast', handler as EventListener)
  }, [])

  if (!msg) return null

  return (
    <div className="fixed bottom-20 left-1/2 transform -translate-x-1/2 bg-gray-900 text-white px-4 py-2 rounded shadow z-50">
      <div className="text-sm">{msg}</div>
    </div>
  )
}
