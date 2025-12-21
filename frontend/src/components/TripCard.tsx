import React from 'react'
import { SavedItem } from '../hooks/useSavedItems'

type Props = {
  searchId: string
  option: any
  onSave?: (item: SavedItem) => void
}

export default function TripCard({ searchId, option, onSave }: Props) {
  const optionId = option.id || option.optionId || JSON.stringify(option)
  const totalPrice = option.totalPrice || option.price || 0
  const currency = option.currency || 'USD'

  return (
    <div className="bg-white p-4 rounded shadow flex justify-between">
      <div>
        <div className="text-sm text-gray-500">Price</div>
        <div className="text-lg font-semibold">{currency} {totalPrice}</div>
        <div className="mt-2 text-sm">Value score: {option.valueScore ?? '—'}</div>
        <div className="mt-2 text-sm text-gray-600">{option.summary ?? option.flightSummary ?? 'Flight + Lodging'}</div>
        {option.mlNote && <div className="mt-2 text-xs italic text-gray-500">{option.mlNote}</div>}
      </div>
      <div className="flex flex-col items-end gap-2">
        <button
          className="bg-green-600 text-white px-3 py-1 rounded"
          onClick={() => onSave && onSave({
            id: `${searchId}:${optionId}`,
            searchId,
            optionId: String(optionId),
            title: option.summary || option.flightSummary,
            price: totalPrice,
            currency,
            createdAt: new Date().toISOString()
          })}
        >
          Save
        </button>
      </div>
    </div>
  )
}
