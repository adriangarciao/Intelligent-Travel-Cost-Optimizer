import React, { useEffect, useState } from 'react'
import watch from '../lib/watch'

export default function NotificationsPanel({ onOpen }: { onOpen?: (offerId: string) => void } = {}) {
  const [notes, setNotes] = useState<any[]>([])

  useEffect(() => {
    setNotes(watch.getNotifications())
  }, [])

  function clearAll() {
    localStorage.removeItem('traveloptimizer.notifications.v1')
    setNotes([])
  }

  function clearOne(id: string) {
    const arr = watch.getNotifications().filter(n => n.id !== id)
    localStorage.setItem('traveloptimizer.notifications.v1', JSON.stringify(arr))
    setNotes(arr)
  }

  if (!notes || notes.length === 0) return <div className="p-3">No notifications</div>

  return (
    <div className="p-3 border rounded bg-white">
      <div className="flex justify-between items-center mb-2">
        <div className="font-medium">Price Alerts</div>
        <div className="flex gap-2">
          <button className="text-sm text-red-600" onClick={clearAll}>Clear all</button>
        </div>
      </div>
      <ul className="space-y-2 text-sm">
        {notes.slice(0, 20).map(n => (
          <li key={n.id} className="flex justify-between items-start">
            <div>
              <div className="font-medium">{n.origin} → {n.destination}</div>
              <div>{new Date(n.timestamp).toLocaleString()} — {n.baseline} → {n.current} ({n.delta >= 0 ? '+' : ''}{n.delta.toFixed(2)})</div>
            </div>
            <div className="flex flex-col items-end gap-1">
              <button className="text-xs text-blue-600" onClick={() => { if (onOpen) onOpen(n.offerId) }}>Open</button>
              <button className="text-xs text-red-600" onClick={() => clearOne(n.id)}>Clear</button>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}
