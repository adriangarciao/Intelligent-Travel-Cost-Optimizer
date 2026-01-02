import React, { useMemo, useState, useEffect } from 'react'
import useCompare from '../hooks/useCompare'
import DealMeter from '../components/DealMeter'
import { useNavigate } from 'react-router-dom'
import { computeDealScores, getOptionId } from '../utils/dealScore'
import { useQueryClient } from '@tanstack/react-query'
import { decodeSharePayload, OfferSnapshot } from '../utils/shareExport'
import { triggerToast } from '../components/Toast'
import ShareExportModal from '../components/ShareExportModal'

function parseDurationMinutes(d?: string | number): number | null {
  if (!d) return null
  if (typeof d === 'number') return d
  const m = String(d).match(/(?:(\d+)h)?\s*(?:(\d+)m)?/)
  if (!m) return null
  const hours = Number(m[1] || 0)
  const mins = Number(m[2] || 0)
  return hours * 60 + mins
}

export default function ComparePage() {
  const { ids, snapshots, clear } = useCompare()
  const compare = useCompare()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [showShare, setShowShare] = useState(false)

  const items = useMemo(() => ids.map(id => snapshots[id]).filter(Boolean), [ids, snapshots])

  // Import share payload from URL once per visit
  useEffect(() => {
    try {
      const params = new URLSearchParams(window.location.search)
      const sh = params.get('share')
      if (!sh) return
      const decoded = decodeSharePayload(sh)
      if (!decoded.ok) {
        triggerToast('Invalid share link')
        // clean URL
        params.delete('share')
        const newUrl = window.location.pathname + '?' + params.toString()
        window.history.replaceState({}, '', newUrl)
        return
      }
      const offers = decoded.payload.offers as OfferSnapshot[]
      // populate compare store (toggle each offer)
      offers.forEach(o => {
        try { compare.toggle(o.id, o) } catch (e) {}
      })
      // remove share param so import is not re-run
      params.delete('share')
      const newUrl = window.location.pathname + (params.toString() ? '?' + params.toString() : '')
      window.history.replaceState({}, '', newUrl)
      triggerToast('Imported shared compare')
    } catch (e) {
      // ignore
    }
    // run only once on mount
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Determine deal percentiles for items.
  // Strategy: prefer using the full search results cache (if available for the item's searchId),
  // otherwise compute percentiles among the compare set itself.
  // Currently the project's `computeDealScores` computes a "deal" percentile based on price (cheapest = best).
  // We'll use that as the canonical meaning: "Top X% (Deal)".
  // Log the basis for debugging/traceability.
  const scoreMap = useMemo(() => {
    if (!items || items.length === 0) return new Map()

    // Try to find a cached search result that contains these offers (use first item's searchId if present)
    const firstSearchId = items[0]?.searchId
    if (firstSearchId) {
      try {
        const queries = queryClient.getQueriesData() as Array<[any, any]>
        for (const [key, data] of queries) {
          if (Array.isArray(key) && key[0] === 'tripOptions' && key.includes(firstSearchId) && data) {
            const allOptions = (data as any).content || (data as any).options || []
            if (Array.isArray(allOptions) && allOptions.length > 0) {
              console.log('ComparePage: computing deal percentiles relative to full search results for searchId=', firstSearchId)
              return computeDealScores(allOptions)
            }
          }
        }
      } catch (e) {
        // fall back
      }
    }

    // Fallback: compute percentiles among the items being compared
    console.log('ComparePage: computing deal percentiles relative to the compare set (fallback)')
    return computeDealScores(items as any)
  }, [items, queryClient])

  // Compute rank by valueScore within compare set (higher valueScore is better)
  const valueRank = useMemo(() => {
    if (!items || items.length === 0) return { map: new Map<string, number>(), total: 0 }
    const entries = items.map((it: any) => ({ id: it.id, score: Number(it.valueScore ?? -Infinity) }))
    entries.sort((a, b) => b.score - a.score)
    const map = new Map<string, number>()
    for (let i = 0; i < entries.length; i++) {
      if (!map.has(entries[i].id)) map.set(entries[i].id, i + 1)
    }
    return { map, total: entries.length }
  }, [items])

  if (!items || items.length < 2) {
    return (
      <div>
        <h2 className="text-xl font-semibold mb-4">Compare Flights</h2>
        <div className="p-4 bg-white rounded shadow">
          <div className="text-sm text-gray-600 mb-3">Select at least 2 flights to compare.</div>
          <div className="flex gap-2">
            <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={() => navigate(-1)}>Back to results</button>
            <button className="px-3 py-1 border rounded" onClick={() => clear()}>Clear selection</button>
          </div>
        </div>
      </div>
    )
  }

  const lowestPrice = Math.min(...items.map(i => Number(i.totalPrice || Infinity)))
  const highestValue = Math.max(...items.map(i => Number(i.valueScore || -Infinity)))
  const fewestStops = Math.min(...items.map(i => (i.flight?.stops ?? Infinity)))
  const shortestDuration = Math.min(...items.map(i => parseDurationMinutes(i.flight?.durationText) ?? Infinity))

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-xl font-semibold">Compare Flights</h2>
        <div className="flex gap-2">
          <button className="px-3 py-1 border rounded" onClick={() => navigate(-1)}>Back</button>
          <button className="px-3 py-1 border rounded" onClick={() => clear()}>Clear</button>
          <button className="px-3 py-1 border rounded" onClick={() => setShowShare(true)}>Share / Export</button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {items.map((it: any) => {
          const isBestPrice = Number(it.totalPrice) === lowestPrice
          const isBestValue = Number(it.valueScore || 0) === highestValue
          const isFewestStops = (it.flight?.stops ?? Infinity) === fewestStops
          const dur = parseDurationMinutes(it.flight?.durationText)
          const isShortest = (dur ?? Infinity) === shortestDuration

          return (
            <div key={it.id} className="p-4 bg-white rounded shadow">
              <div className="flex items-center justify-between mb-2">
                <div>
                  <div className="font-medium">{it.flight?.airlineName ?? it.flight?.airline ?? 'Flight'}</div>
                  <div className="text-xs text-gray-500">{it.flight?.flightNumber ?? ''}</div>
                </div>
                <div className="text-right">
                  <div className="text-lg font-semibold">{it.currency} {it.totalPrice}</div>
                  {isBestPrice && <div className="text-xs text-green-600">Lowest price</div>}
                  {isBestValue && <div className="text-xs text-indigo-600">Best value</div>}
                  {/* Top % (Deal) badge: based on computeDealScores (price-based percentile). */}
                  {(() => {
                    const info = scoreMap.get(it.id)
                    const pctText = info?.percentileText || ''
                    if (!pctText) return null
                    const rank = valueRank.map.get(it.id) ?? '?'
                    const total = valueRank.total || items.length
                    return (
                      <div className="mt-1 text-xs text-gray-700 flex items-center gap-3">
                        <div className="px-2 py-0.5 bg-gray-100 rounded text-xs">{pctText} (Deal)</div>
                        <div className="px-2 py-0.5 bg-gray-100 rounded text-xs">Rank {rank} / {total} (Value)</div>
                        <svg className="text-gray-400" width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" title="Deal percentile: based on price (cheapest = best). Rank: based on Value Score (higher = better) within this compare set.">
                          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z" fill="#E5E7EB" />
                          <path d="M11 17h2v-6h-2v6zm0-8h2V7h-2v2z" fill="#9CA3AF" />
                        </svg>
                      </div>
                    )
                  })()}
                </div>
              </div>

              <div className="mb-2">
                <DealMeter dealScore={it.valueScore ?? null} showPercentile={false} />
              </div>

              <div className="text-sm mb-2">Stops: {typeof it.flight?.stops === 'number' ? (it.flight.stops === 0 ? 'Nonstop' : String(it.flight.stops)) : '—'}</div>
              <div className="text-sm mb-2">Duration: {it.flight?.durationText ?? '—'}</div>

              <div className="mt-3">
                <div className="text-sm font-medium mb-1">Segments</div>
                <ul className="text-sm list-inside list-decimal">
                  {(it.flight?.segments && Array.isArray(it.flight.segments) && it.flight.segments.length > 0) ? (
                    it.flight.segments.map((s: any, i: number) => (
                      <li key={i} className="py-1">
                        <div>{String(s)}</div>
                      </li>
                    ))
                  ) : (
                    <li className="py-1 text-gray-500">Flight details unavailable</li>
                  )}
                </ul>
              </div>
            </div>
          )
        })}
      </div>
      {showShare && (
        <ShareExportModal items={items as OfferSnapshot[]} onClose={() => setShowShare(false)} />
      )}
    </div>
  )
}
