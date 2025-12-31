import React from 'react'
import useCompare from '../hooks/useCompare'
import { triggerToast } from './Toast'

export default function CompareToggleOffer({ id, option }: { id: string, option: any }) {
  const cmp = useCompare()
  const isSelected = cmp.has(id)
  const maxReached = cmp.ids.length >= 3 && !isSelected

  return (
    <button
      className={`px-2 py-1 rounded text-sm border ${isSelected ? 'bg-blue-600 text-white' : 'bg-white text-gray-700'} ${maxReached ? 'opacity-60 cursor-not-allowed' : ''}`}
      onClick={() => {
        if (maxReached) {
          triggerToast('You can compare up to 3 flights')
          return
        }
        const snap = {
          id,
          tripOptionId: option.id || id,
          totalPrice: option.totalPrice,
          currency: option.currency || 'USD',
          valueScore: option.valueScore ?? 0,
          flight: option.flight || option.__raw?.flight || {}
        }
        const res = cmp.toggle(id, snap as any)
        if (!res.ok && res.message) triggerToast(res.message)
      }}
      disabled={maxReached}
    >
      {isSelected ? 'Compare ✓' : 'Compare'}
    </button>
  )
}
