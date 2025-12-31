import React, { useEffect, useState } from 'react'
import useCompare from '../hooks/useCompare'
import { triggerToast } from './Toast'
import { useNavigate } from 'react-router-dom'

export default function CompareBar() {
  const { ids, clear } = useCompare()
  const [msg, setMsg] = useState<string | null>(null)
  const navigate = useNavigate()

  useEffect(() => {
    if (!msg) return
    const t = setTimeout(() => setMsg(null), 3000)
    return () => clearTimeout(t)
  }, [msg])

  if (!ids || ids.length === 0) return null

  return (
    <div className="fixed bottom-4 left-1/2 transform -translate-x-1/2 bg-white border rounded shadow-lg px-4 py-2 z-40 flex items-center gap-4">
      <div className="text-sm font-medium">Comparing {ids.length} flights</div>
      {ids.length >= 3 && (
        <div className="text-xs text-red-600">Maximum reached (3). Remove one to add another.</div>
      )}
      <div className="flex gap-2">
        <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={() => navigate('/compare')}>Compare now</button>
        <button className="px-3 py-1 border rounded" onClick={() => clear()}>Clear</button>
      </div>
      {msg && <div className="text-xs text-red-600 ml-2">{msg}</div>}
    </div>
  )
}
