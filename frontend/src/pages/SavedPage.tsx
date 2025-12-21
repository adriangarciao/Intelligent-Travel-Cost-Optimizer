import React from 'react'
import useSavedItems from '../hooks/useSavedItems'
import { useNavigate } from 'react-router-dom'

export default function SavedPage() {
  const { list, removeItem } = useSavedItems()
  const navigate = useNavigate()

  return (
    <div>
      <h2 className="text-xl font-semibold mb-4">Saved Trips</h2>
      {list.length === 0 && <div className="text-sm text-gray-500">No saved items</div>}
      <div className="space-y-3">
        {list.map((s) => (
          <div key={s.id} className="bg-white p-3 rounded shadow flex justify-between items-center">
            <div>
              <div className="font-medium">{s.title ?? `${s.searchId} - ${s.optionId}`}</div>
              <div className="text-sm text-gray-500">{s.currency} {s.price}</div>
            </div>
            <div className="flex gap-2">
              <button className="text-blue-600 text-sm" onClick={() => navigate(`/results/${s.searchId}`)}>Open Results</button>
              <button className="text-red-600 text-sm" onClick={() => removeItem(s.id)}>Remove</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
