import React from 'react'

type Props = {
  dealScore: number | null
  label?: string
  percentileText?: string
  showPercentile?: boolean
}

export default function DealMeter({ dealScore, label, percentileText }: Props) {
  const score = (typeof dealScore === 'number' && Number.isFinite(dealScore)) ? dealScore : null

  // Compute percent in 0..100 space. If `score` is 0..1 multiply by 100.
  // If score is already 0..100, clamp directly. Do not round — keep float for precise positioning.
  function toPct(s: number | null) {
    if (s === null) return 50
    const v = s > 1 ? s : s * 100
    return Math.max(0, Math.min(100, v))
  }

  const pct = toPct(score)
  const markerLeft = `${pct}%`

  function clamp(v: number, lo = 0, hi = 1) { return Math.max(lo, Math.min(hi, v)) }

  return (
    <div className="mt-3" >
      <div className="flex items-center justify-between mb-1">
        <div className="text-sm font-medium">Deal meter</div>
        <div className="text-xs text-gray-500">Relative to results in this search</div>
      </div>

      {/*
        DOM structure:
        - Outer wrapper: relative, overflow-visible, vertical padding only (py-2). NO horizontal padding.
        - Inner container: relative (positioning context), mx-2 to provide horizontal safe margins.
        - Track: the element that has overflow-hidden and rounded corners.
        - Marker: absolute inside the inner container, positioned with left: `${pct}%`, top:50% and
          transform translate(-50%,-50%) so it's perfectly centered; protrusion is achieved by
          making the marker larger than the track height.
      */}
      <div className="relative w-full overflow-visible py-2" style={{ minHeight: 22 }}>
        <div className="relative mx-2 h-3">
          {/* Track: clips the gradient and keeps rounded corners */}
          <div className="w-full h-3 rounded-full overflow-hidden" style={{ background: 'linear-gradient(to right, #d9534f 0%, #f0ad4e 50%, #5cb85c 100%)' }} />

          {/* Marker: positioned relative to inner container so left% maps exactly to track width
              marker centered via top:50% and translate(-50%,-50%); protrusion achieved by size, not by offset */}
          <div
            aria-hidden
            style={{
              position: 'absolute',
              left: markerLeft,
              top: '50%',
              transform: 'translate(-50%, -50%)',
              width: 16,
              height: 16,
              borderRadius: 9999,
              background: '#ffffff',
              border: '2px solid #000000',
              boxShadow: '0 6px 14px rgba(0,0,0,0.18)',
              pointerEvents: 'none'
            }}
          />
        </div>
      </div>

      <div className="mt-2 flex items-center justify-between text-sm">
        <div className="font-medium">{score === null ? (label || 'Not enough data') : (label || '')}</div>
        { (arguments[0] as any)?.showPercentile === false ? (
          <div />
        ) : (
          <div className="text-gray-600">{score === null ? 'Not enough data' : `${percentileText ?? (pct !== null ? `Top ${pct}%` : '')}`}</div>
        ) }
      </div>
    </div>
  )
}
