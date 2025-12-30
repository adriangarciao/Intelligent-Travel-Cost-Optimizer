import React from 'react'

type Props = {
  dealScore: number | null
  label?: string
  percentileText?: string
}

export default function DealMeter({ dealScore, label, percentileText }: Props) {
  const score = (typeof dealScore === 'number' && Number.isFinite(dealScore)) ? dealScore : null
  const pct = score === null ? null : Math.round(score * 100)

  const markerLeft = score === null ? '50%' : `${clamp(score, 0, 1) * 100}%`

  function clamp(v: number, lo = 0, hi = 1) { return Math.max(lo, Math.min(hi, v)) }

  return (
    <div className="mt-3">
      <div className="flex items-center justify-between mb-1">
        <div className="text-sm font-medium">Deal meter</div>
        <div className="text-xs text-gray-500">Relative to results in this search</div>
      </div>
      <div className="relative h-3 rounded-full overflow-hidden" style={{ background: 'linear-gradient(to right, #d9534f 0%, #f0ad4e 50%, #5cb85c 100%)' }}>
        <div style={{ position: 'absolute', left: markerLeft, top: '-6px', transform: 'translateX(-50%)' }}>
          <div style={{ width: 2, height: 18, background: '#222' }} />
          <div style={{ width: 10, height: 10, borderRadius: 5, background: '#222', marginTop: -1 }} />
        </div>
      </div>
      <div className="mt-2 flex items-center justify-between text-sm">
        <div className="font-medium">{score === null ? (label || 'Not enough data') : (label || '')}</div>
        <div className="text-gray-600">{score === null ? 'Not enough data' : `${percentileText ?? (pct !== null ? `Top ${pct}%` : '')}`}</div>
      </div>
    </div>
  )
}
